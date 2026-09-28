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
    var bytesDone by mutableStateOf(0L); private set
    var currentFile by mutableStateOf(""); private set
    var currentSent by mutableStateOf(0L); private set
    var currentTotal by mutableStateOf(0L); private set
    var speedBps by mutableStateOf(0L); private set
    var elapsedMs by mutableStateOf(0L); private set
    var skipped by mutableStateOf(listOf<Pair<String, String>>()); private set
    var commitSha by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)

    fun progressBytes(): Long = bytesDone + currentSent

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
        bytesDone = 0L
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
                val hooks = UploadHooks(
                    onStage = { s ->
                        stage = s
                        phase = when {
                            s.startsWith("Menyiapkan") -> "prepare"
                            s.startsWith("Mengunggah") -> "upload"
                            else -> "commit"
                        }
                    },
                    onTotal = { f, b ->
                        filesTotal = f
                        bytesTotal = b
                    },
                    onCurrent = { p, sent, total ->
                        currentFile = p
                        currentSent = sent
                        currentTotal = total
                    },
                    onFileDone = { _, sz ->
                        bytesDone += sz
                        filesDone += 1
                        currentFile = ""
                        currentSent = 0L
                        currentTotal = 0L
                    },
                    onFileSkipped = { p, sz, reason ->
                        skipped = skipped + (p to reason)
                        bytesTotal = (bytesTotal - sz).coerceAtLeast(0L)
                        currentFile = ""
                        currentSent = 0L
                        currentTotal = 0L
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
