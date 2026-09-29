package com.gitpush.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.PickedFile
import com.gitpush.app.data.UploadHooks
import com.gitpush.app.data.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * State upload tingkat proses — upload TETAP BERJALAN walau pindah tab,
 * panel progres bisa dibuka lagi kapan saja dari tab Unggah.
 *
 * Progres v2 (akurat untuk multi-file paralel):
 * - bytesUploaded = counter AGREGAT semua file paralel (dulu hanya file terakhir
 *   yang dihitung → bar "melompat" dan kecepatan/ETA salah).
 * - activeFiles = daftar file yang sedang dikirim (maks 4 ditampilkan).
 * - hashFile/hashSent/hashTotal = progres analisis/checksum file besar.
 * - retryMsg = info pengulangan otomatis ("Mengulang X (2/5)…").
 */
object UploadManager {

    var active by mutableStateOf(false); private set
    var showPanel by mutableStateOf(false); private set

    /** idle | prepare | upload | commit | done | error | cancel */
    var phase by mutableStateOf("idle"); private set
    var stage by mutableStateOf(""); private set

    var repoFull by mutableStateOf(""); private set
    var branch by mutableStateOf(""); private set

    var filesTotal by mutableStateOf(0); private set
    var filesDone by mutableStateOf(0); private set
    var bytesTotal by mutableStateOf(0L); private set

    /** Total byte terkirim SEMUA file paralel — sumber progres bar & kecepatan. */
    var bytesUploaded by mutableStateOf(0L); private set

    /** File yang sedang dikirim: (path, terkirim, total) — maks 4 terakhir. */
    var activeFiles by mutableStateOf(listOf<Triple<String, Long, Long>>()); private set

    /** Progres analisis (checksum) file besar pada tahap persiapan. */
    var hashFile by mutableStateOf(""); private set
    var hashSent by mutableStateOf(0L); private set
    var hashTotal by mutableStateOf(0L); private set
    var filesAnalyzed by mutableStateOf(0); private set

    /** Info retry otomatis yang sedang berlangsung, mis. "video.mp4 (2/5)". */
    var retryMsg by mutableStateOf<String?>(null); private set

    var skipped by mutableStateOf(listOf<Pair<String, String>>()); private set
    var commitSha by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var speedBps by mutableStateOf(0L); private set
    var elapsedMs by mutableStateOf(0L); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)

    /** Kompatibilitas lama: byte file terakhir (bar mini per file di panel). */
    var currentFile by mutableStateOf(""); private set
    var currentSent by mutableStateOf(0L); private set
    var currentTotal by mutableStateOf(0L); private set

    fun progressBytes(): Long = bytesUploaded
    fun requestCancel() {
        cancelled.set(true)
        job?.cancel()
    }

    fun dismiss() {
        if (!active) showPanel = false
    }

    fun start(
        token: String, owner: String, repo: String, repoFullName: String,
        branch: String, targetFolder: String,
        files: List<PickedFile>, message: String,
        resolver: android.content.ContentResolver?
    ) {
        if (active) return
        cancelled.set(false)
        repoFull = repoFullName
        this.branch = branch
        filesTotal = files.size
        filesDone = 0
        bytesTotal = 0L
        bytesUploaded = 0L
        activeFiles = emptyList()
        hashFile = ""
        hashSent = 0L
        hashTotal = 0L
        filesAnalyzed = 0
        retryMsg = null
        currentFile = ""
        currentSent = 0L
        currentTotal = 0L
        speedBps = 0L
        elapsedMs = 0L
        skipped = emptyList()
        commitSha = null
        error = null
        stage = "Menyiapkan"
        phase = "prepare"
        active = true
        showPanel = true

        job = scope.launch {
            val started = System.currentTimeMillis()
            var lastTick = started
            var lastBytes = 0L
            val sampler = launch {
                while (isActive) {
                    delay(600)
                    val now = System.currentTimeMillis()
                    val b = progressBytes()
                    val dt = now - lastTick
                    if (dt > 0) {
                        val sp = (b - lastBytes) * 1000 / dt
                        if (sp >= 0) speedBps = sp
                        lastTick = now
                        lastBytes = b
                    }
                    elapsedMs = now - started
                }
            }
            try {
                // Peta path → (sent, total) untuk daftar file aktif
                val actives = LinkedHashMap<String, Pair<Long, Long>>()
                fun snapshot() {
                    activeFiles = actives.entries.toList().takeLast(4)
                        .map { Triple(it.key, it.value.first, it.value.second) }
                }

                val hooks = UploadHooks(
                    onStage = { s ->
                        stage = s
                        phase = when {
                            s.startsWith("Menyiapkan") || s.startsWith("Menganalisis") -> "prepare"
                            s.startsWith("Mengunggah") -> "upload"
                            else -> "commit"
                        }
                    },
                    onTotal = { f, b ->
                        filesTotal = f
                        bytesTotal = b
                    },
                    onAggregate = { total -> bytesUploaded = total },
                    onHash = { p, read, total ->
                        hashFile = p
                        hashSent = read
                        hashTotal = total
                    },
                    onAnalyzed = { done, _ -> filesAnalyzed = done },
                    onCurrent = { p, sent, total ->
                        currentFile = p
                        currentSent = sent
                        currentTotal = total
                        actives[p] = sent to total
                        snapshot()
                        retryMsg = null // ada kemajuan → bukan sedang mengulang
                    },
                    onFileDone = { p, sz ->
                        filesDone += 1
                        actives.remove(p)
                        snapshot()
                        retryMsg = null
                    },
                    onFileSkipped = { p, sz, reason ->
                        skipped = skipped + (p to reason)
                        bytesTotal = (bytesTotal - sz).coerceAtLeast(0L)
                        actives.remove(p)
                        snapshot()
                        retryMsg = null
                    },
                    onRetry = { p, attempt, max, waitMs ->
                        retryMsg = "${p.substringAfterLast('/')} — percobaan $attempt/$max${if (waitMs > 0) " (jeda ${waitMs / 1000} d)" else ""}"
                    },
                    isCancelled = { cancelled.get() }
                )
                val target = targetFolder.trim().trimStart('/').trimEnd('/')
                val files2 = if (target.isEmpty()) files else files.map {
                    it.copy(path = "$target/${it.path}")
                }
                val res: UploadResult = withContext(Dispatchers.IO) {
                    GitHubApi.bulkUpload(token, owner, repo, branch, files2, message, resolver, hooks)
                }
                elapsedMs = System.currentTimeMillis() - started
                commitSha = res.commitSha
                skipped = res.skipped
                filesDone = res.uploaded
                phase = "done"
                GitHubApi.invalidateUsage(owner, repo) // kuota Beranda/Upload dihitung ulang
                Store.log("upload", "Upload ${res.uploaded} file (1 commit)", repoFullName)
            } catch (e: CancellationException) {
                elapsedMs = System.currentTimeMillis() - started
                phase = "cancel"
            } catch (e: Exception) {
                elapsedMs = System.currentTimeMillis() - started
                if (cancelled.get()) {
                    phase = "cancel"
                } else {
                    phase = "error"
                    error = GitHubApi.humanError(e)
                }
            } finally {
                sampler.cancel()
                active = false
            }
        }
    }
}
