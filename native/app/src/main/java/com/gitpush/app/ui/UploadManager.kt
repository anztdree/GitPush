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

    /** File yang GAGAL diunggah — TERSIMPAN agar bisa dicoba ulang kapan saja
     *  (permintaan user: "file gagal jangan hilang, ada try again khusus untuk
     *  file ini dan akan ikut bersama teman file yang sudah di-upload").
     *  Kosong = semua file sudah masuk commit. */
    var failedFiles by mutableStateOf(listOf<PickedFile>()); private set

    /** Parameter run terakhir — dipakai retryFailed() mengulang dengan konteks sama. */
    private data class RunParams(
        val token: String, val owner: String, val repo: String, val repoFull: String,
        val branch: String, val message: String,
        val resolver: android.content.ContentResolver?, val context: Context?
    )
    private var lastRun: RunParams? = null

    /** Path yang sudah dihitung "Selesai" — mencegah hitung ganda saat jalur cepat gagal
     *  di tengah lalu pengiriman diulang lewat jalur API (progres byte mulai dari nol,
     *  tapi penghitung file TIDAK boleh naik dua kali untuk file yang sama). */
    private val donePaths = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

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

    /** Coba ulang HANYA file yang gagal (permintaan user): file gagal tetap tersimpan,
     *  diunggah ulang dalam 1 commit tersendiri, dan otomatis MENYATU dengan file
     *  yang sudah berhasil — commit baru dibangun di atas commit terakhir sehingga
     *  path folder-nya sama. Blob yang sebelumnya sudah sampai tidak dikirim dua kali
     *  (blob GitHub bersifat content-addressed → sha sama langsung diterima). */
    fun retryFailed() {
        val run = lastRun ?: return
        val files = failedFiles
        if (files.isEmpty() || active) return
        start(
            token = run.token, owner = run.owner, repo = run.repo, repoFullName = run.repoFull,
            branch = run.branch,
            // path file gagal SUDAH termasuk folder target → jangan berprefix ganda
            targetFolder = "",
            files = files, message = run.message, resolver = run.resolver,
            context = run.context, isRetry = true
        )
    }

    fun start(
        token: String, owner: String, repo: String, repoFullName: String,
        branch: String, targetFolder: String,
        files: List<PickedFile>, message: String,
        resolver: android.content.ContentResolver?,
        context: Context? = null,
        isRetry: Boolean = false
    ) {
        if (active) return
        cancelled.set(false)
        lastRun = RunParams(token, owner, repo, repoFullName, branch, message, resolver, context)
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
        failedFiles = emptyList()
        stage = "Menyiapkan"
        phase = "prepare"
        active = true
        showPanel = true

        // Log diagnostik mulai dari nol untuk sesi ini
        synchronized(evLock) { events.clear() }
        logLines = emptyList()
        stallSec = 0L
        donePaths.clear()
        logEvent(
            (if (isRetry) "MULAI (COBA ULANG): " else "MULAI: ") +
                "${files.size} file (${formatBytes(files.sumOf { it.size })}) → $repoFullName@$branch"
        )
        logEvent("Tanpa batas jumlah/ukuran file — jalur cepat paket git dicoba dulu, gagal → otomatis jalur API")
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
            // Pindah ke luar try: files2 dipakai juga di blok catch (set failedFiles)
            val target = targetFolder.trim().trimStart('/').trimEnd('/')
            val files2 = if (target.isEmpty()) files else files.map {
                it.copy(path = "$target/${it.path}")
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
                        if (donePaths.add(p)) {
                            filesDone += 1
                            actives.remove(p)
                            lastProg.remove(p)
                            snapshot()
                            retryMsg = null
                            logEvent("✓ Selesai: ${p.substringAfterLast('/')} (${formatBytes(sz)})")
                        }
                    },
                    onFileUnchanged = { p, sz ->
                        // Identik dengan isi repo — tidak dikirim ulang (persis git "up-to-date")
                        if (donePaths.add(p)) {
                            filesDone += 1
                            actives.remove(p)
                            lastProg.remove(p)
                            snapshot()
                            logEvent("• ${p.substringAfterLast('/')} (${formatBytes(sz)}) — sudah ada di repo, tidak dikirim ulang")
                        }
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
                val u = Store.user.value
                val aLogin = u?.login ?: owner
                val aName = u?.name?.takeIf { s -> s.isNotBlank() } ?: aLogin
                val res: UploadResult = withContext(Dispatchers.IO) {
                    GitHubApi.bulkUpload(
                        token, owner, repo, branch, files2, message, resolver, hooks,
                        authorLogin = aLogin, authorName = aName
                    )
                }
                elapsedMs = System.currentTimeMillis() - started
                commitSha = res.commitSha
                skipped = res.skipped
                filesDone = res.uploaded
                phase = "done"
                // File yang masih gagal TERSIMPAN (path sudah termasuk folder target)
                // — bisa dicoba ulang via retryFailed() dan nanti menyatu di repo yang sama.
                failedFiles = files2.filter { f -> res.skipped.any { it.first == f.path } }
                if (failedFiles.isNotEmpty()) {
                    logEvent("${failedFiles.size} file gagal — tetap tersimpan, bisa dicoba ulang (tombol Coba Lagi)")
                }
                // RINGKASAN AKHIR yang terbaca jelas (permintaan user: log harus jelas)
                val jalur = when {
                    res.viaPack > 0 && res.viaBlob == 0 -> "paket git (1 koneksi)"
                    res.viaPack > 0 -> "campuran"
                    res.viaBlob > 0 -> "API blob"
                    else -> "tanpa kirim baru"
                }
                val rincian = mutableListOf<String>()
                if (res.viaPack > 0) rincian.add("baru ${res.viaPack}")
                if (res.viaBlob > 0) rincian.add("blob API ${res.viaBlob}")
                if (res.viaLfs > 0) rincian.add("LFS ${res.viaLfs}")
                if (res.unchanged > 0) rincian.add("identik ${res.unchanged}")
                if (failedFiles.isNotEmpty()) rincian.add("gagal ${failedFiles.size}")
                val durasi = (res.elapsedMs + 999) / 1000
                val sp = if (durasi > 0) bytesTotal / durasi else 0L
                logEvent(
                    "SELESAI: ${res.uploaded}/${filesTotal} file masuk commit ${res.commitSha.take(10)} " +
                        "— ${rincian.joinToString(" · ")} — jalur $jalur — $durasi dtk (${formatBytes(sp)}/dtk)"
                )
                GitHubApi.invalidateUsage(owner, repo) // kuota Beranda/Upload dihitung ulang
                Store.log("upload", "Upload ${res.uploaded} file (1 commit)", repoFullName)
            } catch (e: CancellationException) {
                elapsedMs = System.currentTimeMillis() - started
                phase = "cancel"
                failedFiles = files2 // tidak ada commit — semua file layak dicoba ulang
                logEvent("DIBATALKAN oleh pengguna")
            } catch (e: Exception) {
                elapsedMs = System.currentTimeMillis() - started
                if (cancelled.get()) {
                    phase = "cancel"
                    failedFiles = files2
                    logEvent("DIBATALKAN oleh pengguna")
                } else {
                    phase = "error"
                    error = GitHubApi.humanError(e)
                    // Tidak ada commit yang tercipta → SEMUA file tersimpan utk dicoba ulang.
                    // Blob yang sudah terunggah tidak akan dikirim dua kali (idempotent).
                    failedFiles = files2
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
