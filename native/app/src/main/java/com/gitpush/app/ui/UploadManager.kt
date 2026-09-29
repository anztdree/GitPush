package com.gitpush.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.PickedFile
import com.gitpush.app.data.UploadHooks
import com.gitpush.app.data.UploadResult
import com.gitpush.app.TransferService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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

    /** Log LIVE (salinan pendek dari events) — tampil langsung di panel unggahan
     *  selama proses berjalan: permintaan user "log ditampilkan juga biar keliatan jelas". */
    var logLines by mutableStateOf(listOf<String>()); private set

    /** Detik TANPA satu byte pun maju (0 = data mengalir) — indikator jujur di panel. */
    var stallSec by mutableStateOf(0L); private set

    var skipped by mutableStateOf(listOf<Pair<String, String>>()); private set
    var commitSha by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var speedBps by mutableStateOf(0L); private set
    var elapsedMs by mutableStateOf(0L); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)

    // ===== Log diagnostik unggahan (disalin dari panel bila upload bermasalah) =====
    private val events = ArrayDeque<String>()
    private val evLock = Any()

    fun logEvent(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(evLock) {
            events.addLast("[$ts] $msg")
            while (events.size > 250) events.removeFirst()
            logLines = events.toList().takeLast(80)
        }
    }

    /** Seluruh isi log diagnostik — dipakai tombol "Salin log unggahan". */
    fun logText(): String = synchronized(evLock) { events.joinToString("\n") }

    // Jaga CPU & koneksi Wi-Fi tetap hidup selama unggahan besar (layar boleh mati,
    // ponsel boleh tidur — dulu dugaan kuat proses beku = kirim diam di tengah jalan)
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireLocks(context: Context?) {
        if (context == null) return
        try {
            val appCtx = context.applicationContext
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "GitPush:upload")?.apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // maks 6 jam
            }
            val wm = appCtx.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiLock = wm?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "GitPush:upload")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { }
    }

    private fun releaseLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) { }
        wakeLock = null
        wifiLock = null
    }

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
        resolver: android.content.ContentResolver?,
        context: Context? = null
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

        // Log diagnostik mulai dari nol untuk sesi ini
        synchronized(evLock) { events.clear() }
        logLines = emptyList()
        stallSec = 0L
        logEvent("MULAI: ${files.size} file (${formatBytes(files.sumOf { it.size })}) → $repoFullName@$branch")
        // Jenis jaringan utk diagnosa (Wi-Fi vs data seluler — perilaku unggahan beda jauh)
        try {
            val appCtx = context?.applicationContext
            val cm = appCtx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            val jenis = when {
                caps == null -> "tidak diketahui"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "data seluler"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "lainnya"
            }
            logEvent("Jaringan: $jenis")
        } catch (_: Exception) { }
        acquireLocks(context)
        // Progress bar di STATUS BAR selama unggahan (foreground service — v1.0 finishing)
        try { TransferService.start(context) } catch (_: Exception) { }

        job = scope.launch {
            val started = System.currentTimeMillis()
            var lastTick = started
            var lastBytes = 0L
            val sampler = launch {
                var lastMove = started
                while (isActive) {
                    delay(600)
                    val now = System.currentTimeMillis()
                    val b = progressBytes()
                    if (b != lastBytes) lastMove = now
                    val dt = now - lastTick
                    if (dt > 0) {
                        val sp = (b - lastBytes) * 1000 / dt
                        if (sp >= 0) speedBps = sp
                        lastTick = now
                        lastBytes = b
                    }
                    stallSec = (now - lastMove) / 1000
                    elapsedMs = now - started
                }
            }
            try {
                // Peta path → (sent, total) untuk daftar file aktif
                val actives = LinkedHashMap<String, Pair<Long, Long>>()
                // Posisi progres terakhir per file (waktu, byte) — untuk diagnosa macet
                val lastProg = ConcurrentHashMap<String, Pair<Long, Long>>()
                var stallEvents = 0
                var lastStageLog = ""
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
                        if (s != lastStageLog) {
                            lastStageLog = s
                            logEvent("Tahap: $s")
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
                        val first = !lastProg.containsKey(p)
                        lastProg[p] = System.currentTimeMillis() to sent
                        if (first) logEvent("Kirim: ${p.substringAfterLast('/')} (${formatBytes(total)})")
                        snapshot()
                        retryMsg = null // ada kemajuan → bukan sedang mengulang
                    },
                    onFileDone = { p, sz ->
                        filesDone += 1
                        actives.remove(p)
                        lastProg.remove(p)
                        snapshot()
                        retryMsg = null
                        logEvent("✓ Selesai: ${p.substringAfterLast('/')} (${formatBytes(sz)})")
                    },
                    onFileSkipped = { p, sz, reason ->
                        skipped = skipped + (p to reason)
                        bytesTotal = (bytesTotal - sz).coerceAtLeast(0L)
                        actives.remove(p)
                        lastProg.remove(p)
                        snapshot()
                        retryMsg = null
                        logEvent("✗ GAGAL: ${p.substringAfterLast('/')} — $reason")
                    },
                    onRetry = { p, attempt, max, waitMs, reason ->
                        val lp = lastProg[p]
                        val gap = lp?.let { (System.currentTimeMillis() - it.first) / 1000 } ?: 0L
                        val at = lp?.second ?: 0L
                        val stall = reason.contains("macet") || gap >= 25
                        if (stall) stallEvents++
                        val advice = if (stallEvents >= 3) " • SARAN: coba Wi-Fi / jaringan lain" else ""
                        retryMsg = "${p.substringAfterLast('/')} — ${
                            if (reason.isNotBlank()) reason else "percobaan $attempt/$max"
                        }${if (waitMs > 0) " (jeda ${waitMs / 1000} d)" else ""}$advice"
                        logEvent(
                            "⟳ ${p.substringAfterLast('/')}: percobaan $attempt/$max — $reason " +
                                "(posisi ${formatBytes(at)}, diam ${gap} dtk)"
                        )
                    },
                    onFallback = { p ->
                        retryMsg = "${p.substringAfterLast('/')} — jalur LFS bermasalah, memakai jalur cadangan…"
                        logEvent("⇄ ${p.substringAfterLast('/')}: LFS gagal → beralih ke jalur cadangan (blob API)")
                    },
                    onLog = { msg -> logEvent(msg) },
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
                logEvent("SELESAI: ${res.uploaded} file masuk commit ${res.commitSha.take(10)} (durasi ${(res.elapsedMs + 999) / 1000} dtk)")
                GitHubApi.invalidateUsage(owner, repo) // kuota Beranda/Upload dihitung ulang
                Store.log("upload", "Upload ${res.uploaded} file (1 commit)", repoFullName)
            } catch (e: CancellationException) {
                elapsedMs = System.currentTimeMillis() - started
                phase = "cancel"
                logEvent("DIBATALKAN oleh pengguna")
            } catch (e: Exception) {
                elapsedMs = System.currentTimeMillis() - started
                if (cancelled.get()) {
                    phase = "cancel"
                    logEvent("DIBATALKAN oleh pengguna")
                } else {
                    phase = "error"
                    error = GitHubApi.humanError(e)
                    logEvent("ERROR: $error")
                }
            } finally {
                sampler.cancel()
                releaseLocks()
                active = false
            }
        }
    }
}
