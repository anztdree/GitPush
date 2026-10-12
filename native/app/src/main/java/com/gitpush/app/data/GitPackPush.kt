package com.gitpush.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater

/**
 * Jalur unggah cepat "PAKET GIT" — protokol smart-HTTP yang sama dipakai `git push`
 * di terminal: SATU koneksi HTTPS membawa SEMUA objek (blob + tree + commit) dalam
 * satu packfile biner.
 *
 * Kenapa ini cepat (jawaban "kenapa terminal jauh lebih cepat"):
 * - Tidak ada request per-file → tidak menyentuh limit sekunder REST API (±80 req/menit)
 *   sehingga TANPA jeda pacing antar file.
 * - Isi dikompres zlib (file teks mengecil drastis), header biner ringkas.
 * - File yang isinya sudah ada di repo (content-addressed, sha sama) TIDAK dikirim ulang —
 *   persis perilaku git.
 *
 * Protokol (terverifikasi langsung ke github.com lewat probe):
 * 1. GET  .../git/info/refs?service=git-receive-pack → advertisement ref. WAJIB kirim
 *    header Cache-Control: no-cache — tanpa itu edge cache GitHub membalas posisi branch
 *    yang BASI → push selalu ditolak non-fast-forward.
 * 2. POST .../git-receive-pack → pkt-line perintah + packfile → balasan "unpack ok" +
 *    "ok refs/heads/<branch>".
 *
 * Semua kegagalan dilempar sebagai GhException → pemanggil (bulkUpload) otomatis
 * beralih ke jalur API biasa (blob satu-per-satu) yang sudah terbukti andal.
 */
object GitPackPush {

    class Result(val commitSha: String, val packBytes: Long, val objects: Int)

    /** Satu file yang ikut dalam packfile. */
    class Entry(
        val path: String,
        val size: Long,              // ukuran konten blob (ukuran POINTER utk LFS)
        val gitSha: String,          // sha1 blob lokal (content-addressed)
        val sendBlob: Boolean,       // false = blob sudah ada di server → cukup dirujuk di tree
        val bytes: ByteArray? = null,   // konten kecil di RAM (pointer LFS / file byte)
        val source: PickedFile? = null, // streaming dari SAF/file fisik
        val onSent: (Long) -> Unit = {} // progres byte objek ini (kumulatif, utk UI)
    )

    class PushArgs(
        val token: String,
        val authUser: String,        // username Basic auth (login GitHub)
        val owner: String, val repo: String, val branch: String,
        val baseCommitSha: String?, val baseTreeSha: String?,
        val listing: List<TreeNode>,          // isi tree base (recursive) — boleh kosong
        val entries: List<Entry>,             // SEMUA file (termasuk yang hanya dirujuk)
        val message: String,
        val identName: String, val identEmail: String,
        val isCancelled: () -> Boolean,
        val onLog: (String) -> Unit,
        val onPackByte: (Long) -> Unit,       // byte pack mentah terkirim (kumulatif) — utk log
        // lambda utk retry non-fast-forward (branch bergerak saat proses):
        val fetchRefSha: suspend () -> String?,
        val fetchListing: suspend (commitSha: String) -> Pair<String?, List<TreeNode>>,
        val openSource: (PickedFile) -> InputStream
    )

    private const val GIT_HOST = "https://github.com"
    private val ZERO_OID = "0".repeat(40)
    private val pktMedia = "application/x-git-receive-pack-request".toMediaType()

    private fun fmt(n: Long): String = when {
        n >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", n / 1073741824.0)
        n >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", n / 1048576.0)
        n >= 1024L -> "${n / 1024} KB"
        else -> "$n B"
    }

    // ================= SHA1 OBJEK GIT =================

    /** sha1 blob git ("blob <len>\0" + isi) — dihitung lokal, tanpa request. */
    fun blobShaOf(bytes: ByteArray): String = objSha("blob", bytes)

    /** sha1 blob POINTER LFS — dipakai deteksi "file besar identik sudah di repo". */
    fun blobShaOfPointer(sha256: String, size: Long): String {
        val ptr = "version https://git-lfs.github.com/spec/v1\noid sha256:$sha256\nsize $size\n"
        return objSha("blob", ptr.toByteArray(Charsets.UTF_8))
    }

    private fun objSha(type: String, body: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("$type ${body.size}".toByteArray(Charsets.UTF_8))
        md.update(0)
        md.update(body)
        return hex(md.digest())
    }

    private fun hex(b: ByteArray): String = buildString(b.size * 2) {
        for (x in b) {
            val v = x.toInt() and 0xff
            append("0123456789abcdef"[v ushr 4])
            append("0123456789abcdef"[v and 0xf])
        }
    }

    // ================= pkt-line =================

    private fun pktLine(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        val len = b.size + 4
        val out = ByteArray(len)
        String.format("%04x", len).toByteArray(Charsets.US_ASCII).copyInto(out)
        b.copyInto(out, 4)
        return out
    }

    private val FLUSH = "0000".toByteArray(Charsets.US_ASCII)

    /** Payload pkt-line (flush-pkt dilewati). */
    private fun parsePkt(buf: ByteArray): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i + 4 <= buf.size) {
            val len = String(buf, i, 4, Charsets.US_ASCII).toIntOrNull(16) ?: break
            if (len == 0) { i += 4; continue }
            if (len < 4 || i + len > buf.size) break
            out.add(String(buf, i + 4, len - 4, Charsets.UTF_8).trimEnd('\n'))
            i += len
        }
        return out
    }

    // ================= TREE =================

    private class TDir {
        val dirs = LinkedHashMap<String, TDir>()
        val files = LinkedHashMap<String, Pair<String, String>>() // nama → (mode, sha)
    }

    /**
     * Bangun pohon tree dari (listing base + file baru). Objek tree yang sha-nya
     * BELUM ada di server dikumpulkan body-nya untuk masuk pack.
     * @return sha root tree baru.
     */
    private fun buildTree(
        baseListing: List<TreeNode>, entries: List<Entry>,
        serverShas: Set<String>, outBodies: LinkedHashMap<String, ByteArray>
    ): String {
        val root = TDir()
        // 1. isi base dipertahankan (file & subfolder lama, mode asli termasuk 100755)
        for (e in baseListing) {
            if (e.type != "blob") continue // submodule (160000) & tree lewat listing blob saja
            val segs = e.path.split('/')
            var node = root
            var valid = true
            for (i in 0 until segs.size - 1) {
                val s = segs[i]
                if (s.isEmpty()) { valid = false; break }
                node = node.dirs.getOrPut(s) { TDir() }
            }
            if (!valid || segs.last().isEmpty()) continue
            node.files[segs.last()] = (if (e.mode == "100755") "100755" else "100644") to e.sha
        }
        // 2. file baru — menimpa path lama bila ada
        for (en in entries) {
            val segs = en.path.split('/')
            var node = root
            var valid = true
            for (i in 0 until segs.size - 1) {
                val s = segs[i]
                if (s.isEmpty()) { valid = false; break }
                node = node.dirs.getOrPut(s) { TDir() }
            }
            if (!valid || segs.last().isEmpty()) continue
            node.files[segs.last()] = "100644" to en.gitSha
        }
        return buildTreeRec(root, serverShas, outBodies)
    }

    private fun buildTreeRec(node: TDir, serverShas: Set<String>, outBodies: LinkedHashMap<String, ByteArray>): String {
        class E(val name: String, val dir: Boolean, val mode: String, val sha: String)
        val list = ArrayList<E>(node.dirs.size + node.files.size)
        for ((name, d) in node.dirs) list.add(E(name, true, "40000", buildTreeRec(d, serverShas, outBodies)))
        for ((name, f) in node.files) list.add(E(name, false, f.first, f.second))
        // Urutan git: byte ascending; direktori diurutkan seolah bernama "nama/"
        list.sortWith { a, b ->
            val ka = if (a.dir) a.name + "/" else a.name
            val kb = if (b.dir) b.name + "/" else b.name
            ka.compareTo(kb)
        }
        var size = 0
        for (e in list) size += e.mode.length + 1 + e.name.toByteArray(Charsets.UTF_8).size + 1 + 20
        val body = ByteArray(size)
        var p = 0
        for (e in list) {
            val head = "${e.mode} ${e.name}\u0000".toByteArray(Charsets.UTF_8)
            head.copyInto(body, p); p += head.size
            hexShaToBytes(e.sha).copyInto(body, p); p += 20
        }
        val sha = objSha("tree", body)
        if (!serverShas.contains(sha)) outBodies[sha] = body
        return sha
    }

    private fun hexShaToBytes(sha: String): ByteArray {
        val out = ByteArray(20)
        for (i in 0 until 20) out[i] = sha.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    // ================= COMMIT =================

    private fun commitObject(treeSha: String, parent: String?, ident: String, message: String): ByteArray {
        val sb = StringBuilder()
        sb.append("tree ").append(treeSha).append('\n')
        if (parent != null) sb.append("parent ").append(parent).append('\n')
        sb.append("author ").append(ident).append('\n')
        sb.append("committer ").append(ident).append('\n')
        sb.append('\n')
        sb.append(message)
        if (!message.endsWith("\n")) sb.append('\n')
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ================= PACK WRITER =================

    private class PackWriter(
        val sink: BufferedSink,
        val def: Deflater,
        val md: MessageDigest,
        val counter: AtomicLong,          // total byte pack (lintas percobaan ulang)
        val onPackByte: (Long) -> Unit,
        val onActivity: () -> Unit,       // laporkan ke watchdog koneksi (SETIAP tulis) —
                                          // DULU tidak ada laporan → watchdog mengira koneksi
                                          // mati di detik ke-12 walau data mengalir → push
                                          // besar selalu terputus menjelang selesai
        val isCancelled: () -> Boolean
    ) {
        private val outBuf = ByteArray(64 * 1024)

        private fun raw(b: ByteArray) { sink.write(b); md.update(b); onPackByte(counter.addAndGet(b.size.toLong())); onActivity() }
        private fun raw(b: ByteArray, off: Int, n: Int) {
            if (n <= 0) return
            sink.write(b, off, n); md.update(b, off, n)
            onPackByte(counter.addAndGet(n.toLong())); onActivity()
        }

        fun header() {
            raw("PACK".toByteArray(Charsets.US_ASCII))
            raw(byteArrayOf(0, 0, 0, 2))
        }

        fun count(n: Int) {
            raw(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        }

        /** Satu objek: header varint + isi zlib. feeder: -1 = habis. */
        fun writeObject(typeNum: Int, size: Long, feeder: (ByteArray) -> Int, onObj: (Long) -> Unit) {
            var s = size
            var first = (typeNum shl 4) or (s and 0xf).toInt()
            s = s shr 4
            if (s > 0) first = first or 0x80
            val hb = ArrayList<Byte>(5)
            hb.add(first.toByte())
            while (s > 0) {
                var b = (s and 0x7f).toInt()
                s = s shr 7
                if (s > 0) b = b or 0x80
                hb.add(b.toByte())
            }
            val bytes = ByteArray(hb.size)
            for (i in hb.indices) bytes[i] = hb[i]
            raw(bytes)

            def.reset()
            val inBuf = ByteArray(256 * 1024)
            var objBytes = 0L
            while (true) {
                if (isCancelled()) throw IOException("Dibatalkan")
                val n = feeder(inBuf)
                if (n < 0) break
                if (n > 0) {
                    def.setInput(inBuf, 0, n)
                    while (!def.needsInput()) {
                        val w = def.deflate(outBuf)
                        if (w > 0) raw(outBuf, 0, w)
                    }
                    objBytes += n
                    onObj(objBytes)
                }
            }
            def.finish()
            while (!def.finished()) {
                val w = def.deflate(outBuf)
                if (w > 0) raw(outBuf, 0, w)
            }
        }
    }

    // ================= PUSH =================

    private fun basicAuth(user: String, token: String): String =
        "Basic " + Base64.encodeToString("$user:$token".toByteArray(), Base64.NO_WRAP)

    suspend fun push(args: PushArgs): Result = withContext(Dispatchers.IO) {
        val auth = basicAuth(args.authUser, args.token)
        val counter = AtomicLong(0) // byte pack mentah — dipertahankan lintas percobaan ulang

        // ---- 1. advertisement: posisi branch ----
        var old = ZERO_OID
        var adv = false
        var lastErr: Exception? = null
        val refsUrl = "$GIT_HOST/${args.owner}/${args.repo}.git/info/refs?service=git-receive-pack"
        for (attempt in 1..3) {
            if (args.isCancelled()) throw GhException("Dibatalkan", 499)
            try {
                val req = Request.Builder().url(refsUrl)
                    .header("Authorization", auth)
                    .header("User-Agent", "git/GitPush-Android")
                    .header("Accept", "*/*")
                    // WAJIB: tanpa ini edge cache GitHub membalas posisi branch BASI
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .get().build()
                GitHubApi.httpH1.newCall(req).execute().use { r ->
                    val body = r.body?.bytes() ?: ByteArray(0)
                    val ct = r.header("Content-Type") ?: ""
                    when {
                        r.code == 401 -> throw GhException("Token tidak valid atau kedaluwarsa (401)", 401)
                        r.code == 403 -> throw GhException("Akses push ditolak (403) — cek izin token", 403)
                        r.code == 404 -> throw GhException("Repository tidak ditemukan — kemungkinan sudah dihapus (404)", 404)
                        r.code !in 200..299 || !ct.contains("x-git-receive-pack-advertisement") ->
                            throw GhException("Push paket tidak tersedia (HTTP ${r.code})", r.code)
                    }
                    for (line in parsePkt(body)) {
                        val parts = line.substringBefore('\u0000').split(' ')
                        // baris ref: "<oid> refs/heads/<nama>" (SATU oid; capabilities^{} punya dua)
                        if (parts.size >= 2 && parts[1] == "refs/heads/${args.branch}" && parts[0].length == 40) {
                            old = parts[0]
                        }
                    }
                    adv = true
                }
                break
            } catch (e: Exception) {
                lastErr = e
                if (e is GhException && e.code in 401..404) throw e // fatal — jangan diulang
                if (args.isCancelled()) throw GhException("Dibatalkan", 499)
                delay(1000L * attempt)
            }
        }
        if (!adv) throw lastErr ?: GhException("Gagal membaca posisi branch (info/refs)")

        // ---- 2. bangun tree+commit+pack → kirim; rebuild maks 2x saat branch bergerak ----
        var attempt = 0
        var baseCommit = args.baseCommitSha
        var baseTree = args.baseTreeSha
        var listing = args.listing
        while (true) {
            attempt++
            val serverShas = HashSet<String>(listing.size + 8)
            for (e in listing) serverShas.add(e.sha)
            baseCommit?.let { serverShas.add(it) }
            baseTree?.let { serverShas.add(it) }

            val treeBodies = LinkedHashMap<String, ByteArray>()
            val rootTreeSha = buildTree(listing, args.entries, serverShas, treeBodies)
            val ident = "${args.identName} <${args.identEmail}> ${System.currentTimeMillis() / 1000} ${tzOffset()}"
            val commit = commitObject(rootTreeSha, baseCommit, ident, args.message)
            val commitSha = objSha("commit", commit)

            val sendBlobEntries = args.entries.filter { it.sendBlob }
            val objCount = sendBlobEntries.size + treeBodies.size + 1
            args.onLog(
                "Paket: $objCount objek (${sendBlobEntries.size} isi file, ${treeBodies.size} tree, 1 commit) — satu koneksi…"
            )

            var respOk = false
            var packBytesAtResp = 0L

            for (putAttempt in 1..3) {
                if (args.isCancelled()) throw GhException("Dibatalkan", 499)
                args.onLog("Mengirim paket (percobaan $putAttempt/3)…")
                val def = Deflater()
                // Watchdog dipasang SEBELUM body dan diberi laporan SETIAP byte tertulis;
                // setelah body selesai ia masuk fase "menunggu respons" dgn batas longgar
                // (server memproses pack: index-pack + fsck — pack besar butuh waktu, fase
                // ini memang WAJAR diam). Dulu tidak ada laporan sama sekali → watchdog
                // membunuh koneksi di detik ke-12 TEPAT saat progres hampir 100% → kirim
                // diulang dari nol → jatuh ke jalur lambat. Akar "hampir selesai lalu
                // mengulang dari awal dengan kecepatan rendah" ada di sini.
                val wd = GitHubApi.StallWatchdog { counter.get() }
                val body = object : RequestBody() {
                    override fun contentType() = pktMedia
                    override fun contentLength() = -1L // chunked — persis git utk push besar

                    override fun writeTo(sink: BufferedSink) {
                        sink.write(pktLine("${old} ${commitSha} refs/heads/${args.branch}\u0000report-status agent=git/GitPush-Android"))
                        sink.write(FLUSH)
                        val w = PackWriter(sink, def, MessageDigest.getInstance("SHA-1"), counter,
                            onPackByte = args.onPackByte, onActivity = { wd.progress() },
                            isCancelled = args.isCancelled)
                        w.header(); w.count(objCount)
                        for (e in sendBlobEntries) {
                            val ins: InputStream = e.bytes?.let { ByteArrayInputStream(it) }
                                ?: args.openSource(e.source ?: throw GhException("Sumber file tidak ada: ${e.path}"))
                            try {
                                w.writeObject(3, e.size, { buf -> ins.read(buf) }, e.onSent)
                            } finally {
                                try { ins.close() } catch (_: Exception) { }
                            }
                        }
                        for ((_, tb) in treeBodies) {
                            val ins = ByteArrayInputStream(tb)
                            w.writeObject(2, tb.size.toLong(), { buf -> ins.read(buf) }, { })
                        }
                        val ci = ByteArrayInputStream(commit)
                        w.writeObject(1, commit.size.toLong(), { buf -> ci.read(buf) }, { })
                        sink.write(w.md.digest()) // trailer sha1
                        wd.bodyDone() // body selesai → fase tunggu-respons (batas longgar)
                        args.onLog("Paket terkirim penuh — menunggu server memproses…")
                    }
                }

                val (code, text, ra) = try {
                    val req = Request.Builder()
                        .url("$GIT_HOST/${args.owner}/${args.repo}.git/git-receive-pack")
                        .header("Authorization", auth)
                        .header("User-Agent", "git/GitPush-Android")
                        .header("Accept", "application/x-git-receive-pack-result")
                        .post(body).build()
                    val c = GitHubApi.httpH1.newBuilder()
                        .readTimeout(600, TimeUnit.SECONDS).build().newCall(req)
                    wd.arm(c)
                    c.execute().use { r ->
                        Triple(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 0L)
                    }
                } catch (e: IOException) {
                    Triple(0, e.message ?: "Koneksi gagal", 0L)
                } finally {
                    wd.stop()
                    try { def.end() } catch (_: Exception) { }
                }

                // koneksi putus / server error → ulang pengiriman pack (idempotent)
                if (code == 0 || code in 500..599 || code == 429) {
                    if (putAttempt >= 3) {
                        throw GhException(
                            if (code == 0) "Koneksi terputus saat mengirim paket: $text"
                            else "Server gagal menerima paket (HTTP $code)", code, ra
                        )
                    }
                    args.onLog("⟳ kirim paket gagal (${if (code == 0) "koneksi" else "HTTP $code"}) — mengulang ${putAttempt + 1}/3…")
                    delay(retryWait(putAttempt, ra))
                    continue
                }
                when (code) {
                    401 -> throw GhException("Token tidak valid atau kedaluwarsa (401)", 401)
                    403 -> throw GhException("Akses push ditolak (403) — cek izin token", 403)
                    404 -> throw GhException("Repository tidak ditemukan — kemungkinan sudah dihapus (404)", 404)
                }

                // ---- parse report-status ----
                var unpackOk = false
                var unpackErr = ""
                var refOk = false
                var ngReason = ""
                for (line in parsePkt(text.toByteArray(Charsets.UTF_8))) {
                    when {
                        line == "unpack ok" -> unpackOk = true
                        line.startsWith("unpack ") -> unpackErr = line.removePrefix("unpack ")
                        line == "ok refs/heads/${args.branch}" -> refOk = true
                        line.startsWith("ng refs/heads/${args.branch} ") ->
                            ngReason = line.removePrefix("ng refs/heads/${args.branch} ").trim()
                        line.startsWith("ng ") && ngReason.isEmpty() -> ngReason = line.removePrefix("ng ").trim()
                    }
                }
                packBytesAtResp = counter.get()
                if (unpackOk && refOk) { respOk = true; break }

                val ff = ngReason.contains("fetch first", true) ||
                    ngReason.contains("non-fast-forward", true) ||
                    ngReason.contains("stale info", true)
                if (ff) break // dibangun ulang di loop luar

                throw GhException(
                    "Paket ditolak server: ${ngReason.ifEmpty { unpackErr.ifEmpty { "HTTP $code" } }}", code
                )
            }

            if (respOk) {
                args.onLog("✓ Server: unpack ok — refs/heads/${args.branch} diperbarui")
                // Verifikasi posisi branch lewat PROTOKOL GIT (info/refs — mesin yang sama
                // dgn push, tidak lewat cache REST) + REST API sebagai cadangan.
                // PENTING: laporan "unpack ok" + "ok refs/..." SUDAH konfirmasi resmi dari
                // server git. Verifikasi ini hanya pelengkap — DULU kegagalan verifikasi
                // (data REST basi / 403 sesaat setelah push besar) membuat unggahan yang
                // sukses dianggap gagal → SEMUA file dikirim ulang lewat jalur lambat.
                var verified = false
                for (v in 1..5) {
                    if (args.isCancelled()) throw GhException("Dibatalkan", 499)
                    if (advRefSha(auth, args) == commitSha) { verified = true; break }
                    val api = try { args.fetchRefSha() } catch (_: Exception) { null }
                    if (api == commitSha) { verified = true; break }
                    delay(1000)
                }
                if (verified) {
                    args.onLog("✓ Verifikasi: branch ${args.branch} = ${commitSha.take(10)}")
                } else {
                    args.onLog("⚠ Verifikasi belum terlihat (replikasi/cache API tertunda) — laporan 'unpack ok' server dianggap sah")
                }
                args.onLog("✓ Paket diterima server (${fmt(packBytesAtResp)} terkirim)")
                return@withContext Result(commitSha, packBytesAtResp, objCount)
            }

            if (attempt >= 3) {
                throw GhException("Branch terus bergerak — paket tidak bisa dikirim (non-fast-forward)", 422)
            }
            args.onLog("⟳ branch bergerak saat proses — membangun ulang paket (${attempt + 1}/3)…")
            val fresh = args.fetchRefSha()
            old = fresh ?: ZERO_OID
            baseCommit = fresh
            if (fresh != null) {
                val (t, l) = args.fetchListing(fresh)
                baseTree = t
                listing = l
            } else {
                baseTree = null
                listing = emptyList()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("loop push selesai tanpa hasil") // while(true) hanya keluar via return/throw
    }

    private fun retryWait(attempt: Int, retryAfterMs: Long): Long {
        if (retryAfterMs > 0) return retryAfterMs.coerceIn(1_000, 60_000)
        return (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000)
    }

    /** Baca posisi branch via protokol git (GET info/refs — endpoint yang sama dengan
     *  push sehingga langsung melihat kondisi backend git, tanpa cache REST API).
     *  null = gagal / branch belum terlihat. Dipakai verifikasi pasca-push. */
    private fun advRefSha(auth: String, args: PushArgs): String? = try {
        val req = Request.Builder()
            .url("$GIT_HOST/${args.owner}/${args.repo}.git/info/refs?service=git-receive-pack")
            .header("Authorization", auth)
            .header("User-Agent", "git/GitPush-Android")
            .header("Accept", "*/*")
            // WAJIB no-cache — tanpa ini edge cache bisa membalas posisi lama
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get().build()
        GitHubApi.httpH1.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val body = r.body?.bytes() ?: return@use null
            var sha: String? = null
            for (line in parsePkt(body)) {
                val parts = line.substringBefore('\u0000').split(' ')
                if (parts.size >= 2 && parts[1] == "refs/heads/${args.branch}" && parts[0].length == 40) sha = parts[0]
            }
            sha
        }
    } catch (_: Exception) { null }

    /** Offset zona waktu device, format git "+0700" / "-0530". */
    private fun tzOffset(): String {
        val tz = java.util.TimeZone.getDefault()
        val off = tz.getOffset(System.currentTimeMillis()) / 60000
        val sign = if (off >= 0) "+" else "-"
        val a = Math.abs(off)
        return String.format("%s%02d%02d", sign, a / 60, a % 60)
    }
}
