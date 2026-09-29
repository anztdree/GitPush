package com.gitpush.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GhException(message: String, val code: Int = 0, val retryAfterMs: Long = 0, val kind: String = "") : Exception(message)

/** Callback progres untuk bulkUpload — dipanggil dari thread IO (aman untuk state Compose). */
class UploadHooks(
    val onStage: (String) -> Unit = {},
    val onTotal: (files: Int, bytes: Long) -> Unit = { _, _ -> },
    val onCurrent: (path: String, sent: Long, total: Long) -> Unit = { _, _, _ -> },
    val onFileDone: (path: String, size: Long) -> Unit = { _, _ -> },
    val onFileSkipped: (path: String, size: Long, reason: String) -> Unit = { _, _, _ -> },
    /** BARU: total byte TERKIRIM semua file paralel (akurat — dulu hanya 1 file terhitung). */
    val onAggregate: (bytesSentAll: Long) -> Unit = {},
    /** BARU: progres pembacaan/checksum file saat tahap analisis (file besar bisa lama). */
    val onHash: (path: String, read: Long, total: Long) -> Unit = { _, _, _ -> },
    /** BARU: notifikasi retry per file agar pengguna tahu proses masih berjalan. */
    val onRetry: (path: String, attempt: Int, maxAttempts: Int, waitMs: Long) -> Unit = { _, _, _, _ -> },
    /** BARU: jumlah file yang selesai dianalisis (persiapan paralel) — (selesai, total). */
    val onAnalyzed: (done: Int, total: Int) -> Unit = { _, _ -> },
    val isCancelled: () -> Boolean = { false }
)

data class UploadResult(
    val commitSha: String,
    val uploaded: Int,
    val skipped: List<Pair<String, String>>,
    val elapsedMs: Long
)

object GitHubApi {

    const val API = "https://api.github.com"

    /** File di atas ambang ini otomatis dikirim via Git LFS.
     *  16 MB: di atas ukuran ini jalur blob API memboroskan 33% (base64 + JSON) dan
     *  PUT biner langsung ke storage LFS terbukti jauh lebih cepat & stabil di jaringan
     *  seluler — keluhan "35 MB stuck lama". Bila kuota LFS habis, otomatis fallback
     *  ke blob API (file ≤99 MB masih diterima GitHub). */
    const val LFS_THRESHOLD_BYTES = 16L * 1024 * 1024

    /** Ambang maksimum fallback blob API (limit blob GitHub 100 MB — sambungi margin). */
    const val MAX_BLOB_FALLBACK_BYTES = 99L * 1024 * 1024

    /** SHA blob kosong yang dikenal git — tidak perlu request API untuk file 0 byte. */
    const val EMPTY_BLOB_SHA = "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        // Write timeout OkHttp = batas IDLE antar operasi tulis, bukan total — koneksi
        // lambat yang terus mengirim data tidak akan terputus. 10 menit idle = sangat aman
        // untuk PUT LFS 100 MB+ di jaringan seluler lambat.
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val lfsMedia = "application/vnd.git-lfs+json".toMediaType()

    private const val B64_PREFIX = "{\"content\":\""
    private const val B64_SUFFIX = "\",\"encoding\":\"base64\"}"

    private fun req(token: String, method: String, url: String, body: JSONObject?): Request {
        val b = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "GitPush-Android")
        if (token.isNotEmpty()) b.header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> b.get()
            "DELETE" -> if (body != null) b.delete(body.toString().toRequestBody(jsonMedia)) else b.delete()
            "PATCH" -> b.patch((body ?: JSONObject()).toString().toRequestBody(jsonMedia))
            "PUT" -> b.put((body ?: JSONObject()).toString().toRequestBody(jsonMedia))
            else -> b.post((body ?: JSONObject()).toString().toRequestBody(jsonMedia))
        }
        return b.build()
    }

    private suspend fun call(token: String, method: String, path: String, body: JSONObject? = null): JSONObject? =
        withContext(Dispatchers.IO) {
            val url = if (path.startsWith("http")) path else API + path
            http.newCall(req(token, method, url, body)).execute().use { r ->
                val text = r.body?.string() ?: ""
                val ra = r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 0L
                if (!r.isSuccessful) throw GhException("HTTP ${r.code}: ${text.take(180)}", r.code, ra)
                if (text.isEmpty()) JSONObject() else JSONObject(text)
            }
        }

    private suspend fun callArray(token: String, method: String, path: String): JSONArray =
        withContext(Dispatchers.IO) {
            http.newCall(req(token, method, API + path, null)).execute().use { r ->
                val text = r.body?.string() ?: "[]"
                if (!r.isSuccessful) throw GhException("HTTP ${r.code}: ${text.take(180)}", r.code)
                JSONArray(text)
            }
        }

    /** Request dengan body mentah (streaming) — mengembalikan (kode, teks, retryAfterMs). */
    private suspend fun callStreamed(
        url: String, method: String, token: String,
        body: RequestBody, headers: Map<String, String> = emptyMap()
    ): Triple<Int, String, Long> = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("User-Agent", "GitPush-Android")
        if (token.isNotEmpty()) b.header("Authorization", "Bearer $token")
        headers.forEach { (k, v) -> b.header(k, v) }
        if (method == "PUT") b.put(body) else b.post(body)
        try {
            http.newCall(b.build()).execute().use { r ->
                // PENTING: body HARUS dibaca PENUH. Dulu dipotong .take(800) — padahal respons
                // batch Git LFS berisi URL presigned S3 + header AWS SigV4 yang panjangnya
                // lebih dari 800 karakter, sehingga JSONObject gagal dengan error
                // "Terminated string at character 800" dan upload file besar selalu gagal.
                val text = r.body?.string().orEmpty()
                val ra = r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 0L
                Triple(r.code, text, ra)
            }
        } catch (e: IOException) {
            throw GhException(e.message ?: "Koneksi gagal", 0)
        }
    }

    fun humanError(e: Throwable): String {
        val code = (e as? GhException)?.code ?: 0
        val msg = e.message ?: ""
        return when {
            code == 401 -> "Token tidak valid atau kedaluwarsa (401)"
            code == 403 && msg.contains("secondary", true) -> "Limit sementara GitHub tercapai — tunggu sebentar lalu coba lagi (403)"
            code == 403 -> if (msg.startsWith("Repository", true)) msg else "Akses ditolak / limit API tercapai (403)"
            code == 404 -> if (msg.startsWith("Repository", true)) msg else "Tidak ditemukan (404)"
            code == 409 -> "Repository kosong (409)"
            code == 422 -> "Data tidak valid (422) — cek nama/isi atau branch sudah berubah"
            code == 499 -> "Dibatalkan"
            msg.contains("Unable to resolve host", true) -> "Tidak ada koneksi internet"
            // Pesan JSON mentah (mis. "Terminated string at character 800 of {...}") TIDAK boleh
            // dibiarkan menggenangi layar — tampilkan ringkasan yang bisa dipahami.
            msg.contains("Terminated string", true) || msg.contains("Unterminated string", true) ||
                msg.contains("JSONException", true) -> "Respons GitHub terpotong — koneksi tidak stabil, silakan coba lagi"
            else -> msg.ifEmpty { "Terjadi kesalahan" }.let { if (it.length > 180) it.take(180) + "…" else it }
        }
    }

    // ============ USER & REPO ============

    suspend fun fetchUser(token: String): GhUser = withContext(Dispatchers.IO) {
        val o = call(token, "GET", "/user") ?: throw GhException("Respons kosong")
        parseUser(o)
    }

    private fun parseUser(o: JSONObject): GhUser = GhUser(
        login = o.optString("login"),
        name = if (o.isNull("name")) o.optString("login") else o.optString("name"),
        avatarUrl = o.optString("avatar_url"),
        bio = if (o.isNull("bio")) null else o.optString("bio"),
        followers = o.optInt("followers"),
        following = o.optInt("following"),
        publicRepos = o.optInt("public_repos"),
        company = if (o.isNull("company")) null else o.optString("company"),
        location = if (o.isNull("location")) null else o.optString("location"),
        blog = if (o.isNull("blog")) null else o.optString("blog"),
        email = if (o.isNull("email")) null else o.optString("email"),
        createdAt = o.optString("created_at")
    )

    suspend fun fetchRepos(token: String): List<GhRepo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<GhRepo>()
        var page = 1
        while (page <= 5) {
            val arr = callArray(token, "GET", "/user/repos?sort=updated&per_page=100&page=$page")
            for (i in 0 until arr.length()) out.add(parseRepo(arr.getJSONObject(i)))
            if (arr.length() < 100) break
            page++
        }
        out
    }

    private fun parseRepo(o: JSONObject): GhRepo = GhRepo(
        id = o.optLong("id"),
        name = o.optString("name"),
        fullName = o.optString("full_name"),
        owner = o.optJSONObject("owner")?.optString("login") ?: "",
        ownerAvatar = o.optJSONObject("owner")?.optString("avatar_url") ?: "",
        description = if (o.isNull("description")) null else o.optString("description"),
        isPrivate = o.optBoolean("private"),
        language = if (o.isNull("language")) null else o.optString("language"),
        stars = o.optInt("stargazers_count"),
        forks = o.optInt("forks_count"),
        issues = o.optInt("open_issues_count"),
        defaultBranch = o.optString("default_branch", "main").ifEmpty { "main" },
        updatedAt = o.optString("updated_at"),
        sizeKb = o.optLong("size"),
        homepage = if (o.isNull("homepage")) null else o.optString("homepage"),
        watchers = o.optInt("subscribers_count")
    )

    suspend fun createRepo(
        token: String, name: String, description: String, isPrivate: Boolean, autoInit: Boolean
    ): GhRepo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).put("private", isPrivate).put("auto_init", autoInit)
        if (description.isNotEmpty()) body.put("description", description)
        val o = call(token, "POST", "/user/repos", body) ?: throw GhException("Respons kosong")
        parseRepo(o)
    }

    /** Detail satu repository (untuk kuota penyimpanan 2 GB, dsb). */
    suspend fun fetchRepo(token: String, owner: String, name: String): GhRepo =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$name")
                ?: throw GhException("Repository tidak ditemukan", 404)
            parseRepo(o)
        }

    /** Hapus repository PERMANEN — semua file, commit, dan riwayat ikut hilang (tak bisa dibatalkan).
     *  PAT klasik memerlukan scope delete_repo; sukses = 204 (respons kosong ditangani call()). */
    suspend fun deleteRepo(token: String, owner: String, name: String): Unit =
        withContext(Dispatchers.IO) {
            call(token, "DELETE", "/repos/$owner/$name")
            Unit
        }

    // ============ PEMAKAIAN RIIL REPOSITORY (termasuk objek Git LFS) ============
    // Field "size" pada API GitHub TIDAK termasuk isi Git LFS — repo berisi file besar
    // via LFS bisa tercatat 10 MB padahal isinya ratusan MB. Fungsi di bawah menghitung
    // jumlah byte semua blob di tree + ukuran asli objek LFS, dengan cache memori 5 menit.

    private val usageCache = HashMap<String, Pair<Long, Long>>() // kunci → (byte, waktu dihitung)
    private const val USAGE_TTL_MS = 5L * 60 * 1000

    /** Buang cache pemakaian repository — panggil setelah upload/rename/hapus/commit.
     *  Sekalian buang cache "commit terakhir per file" agar status update terakhir ikut segar. */
    fun invalidateUsage(owner: String, repo: String) {
        synchronized(usageCache) {
            val prefix = "$owner/$repo@"
            usageCache.keys.removeAll { it.startsWith(prefix) }
        }
        invalidateLastCommits(owner, repo)
    }

    // ============ COMMIT TERAKHIR PER FILE/FOLDER (ala website GitHub) ============

    private val lastCommitCache = HashMap<String, Pair<Long, GhCommit>>()
    private const val LAST_COMMIT_TTL_MS = 10L * 60 * 1000

    /** Buang cache commit-terakhir-per-path (dipanggil otomatis oleh invalidateUsage). */
    fun invalidateLastCommits(owner: String, repo: String) {
        synchronized(lastCommitCache) {
            val prefix = "$owner/$repo@"
            lastCommitCache.keys.removeAll { it.startsWith(prefix) }
        }
    }

    /**
     * Commit terakhir untuk tiap path (file/folder) di daftar — persis seperti baris
     * "pesan commit • waktu" pada daftar file di website GitHub.
     * Dikerjakan paralel (maks 5 sekaligus, batas 100 path) dan HASIL DIKIRIM PROGRESIF
     * lewat onResult begitu tiap path selesai — daftar tidak perlu menunggu semuanya.
     * Cache 10 menit per repo@branch:path. Gagal per path diabaikan diam-diam
     * (baris tetap tampil tanpa info commit).
     */
    suspend fun fetchLastCommits(
        token: String, owner: String, repo: String, branch: String, paths: List<String>,
        onResult: (path: String, info: GhCommit) -> Unit
    ): Unit = coroutineScope {
        val sem = Semaphore(5)
        paths.take(100).forEach { p ->
            launch {
                sem.withPermit {
                    val key = "$owner/$repo@$branch:$p"
                    val cached = synchronized(lastCommitCache) { lastCommitCache[key] }
                    if (cached != null && System.currentTimeMillis() - cached.first < LAST_COMMIT_TTL_MS) {
                        onResult(p, cached.second)
                        return@withPermit
                    }
                    runCatching {
                        val arr = callArray(
                            token, "GET",
                            "/repos/$owner/$repo/commits?sha=${Uri.encode(branch)}&path=${Uri.encode(p)}&per_page=1"
                        )
                        if (arr.length() == 0) return@withPermit
                        val o = arr.getJSONObject(0)
                        val commitObj = o.optJSONObject("commit")
                        val info = GhCommit(
                            sha = o.optString("sha"),
                            message = commitObj?.optString("message")?.substringBefore('\n') ?: "",
                            author = commitObj?.optJSONObject("author")?.optString("name") ?: "",
                            avatarUrl = if (o.isNull("author")) null else o.optJSONObject("author")?.optString("avatar_url"),
                            date = commitObj?.optJSONObject("author")?.optString("date") ?: ""
                        )
                        synchronized(lastCommitCache) { lastCommitCache[key] = System.currentTimeMillis() to info }
                        onResult(p, info)
                    }
                }
            }
        }
    }

    /** Simpan hasil hitung manual (mis. dari layar repo yang sudah mem-parsing tree). */
    fun putUsageCache(owner: String, repo: String, branch: String, bytes: Long) {
        synchronized(usageCache) {
            usageCache["$owner/$repo@$branch"] = bytes to System.currentTimeMillis()
        }
    }

    /** Nilai cache tanpa hitung ulang (null bila belum pernah dihitung/kedaluwarsa). */
    fun cachedUsage(owner: String, repo: String, branch: String): Long? =
        synchronized(usageCache) { usageCache["$owner/$repo@$branch"]?.first }

    /**
     * Pemakaian riil repository dalam byte — jumlah ukuran semua file (blob) di tree,
     * termasuk ukuran ASLI objek Git LFS (pointer ±130 B dihitung dari baris "size"-nya).
     * Repository kosong → 0 B. [force] = hitung ulang walau cache masih segar.
     */
    suspend fun fetchRepoUsage(
        token: String, owner: String, repo: String, branch: String, force: Boolean = false
    ): Long {
        val key = "$owner/$repo@$branch"
        if (!force) {
            synchronized(usageCache) {
                usageCache[key]?.let { (bytes, at) ->
                    if (System.currentTimeMillis() - at < USAGE_TTL_MS) return bytes
                }
            }
        }
        val total = try {
            val commitSha = refSha(token, owner, repo, branch)
            val treeSha = commitTreeSha(token, owner, repo, commitSha)
            val blobs = fetchTreeRecursive(token, owner, repo, treeSha).filter { it.type == "blob" }
            val ptrs = resolveLfsPointers(token, owner, repo, blobs.map { it.sha to it.size })
            blobs.sumOf { ptrs[it.sha]?.second ?: it.size }
        } catch (e: GhException) {
            // 409 = repository kosong (belum ada commit), 404 = branch belum ada → 0 B
            if (e.code == 409 || e.code == 404) 0L else throw e
        }
        putUsageCache(owner, repo, branch, total)
        return total
    }

    // ============ CONTENTS ============

    suspend fun fetchContents(token: String, owner: String, repo: String, path: String, ref: String): List<GhNode> =
        withContext(Dispatchers.IO) {
            val p = if (path.isEmpty()) "/repos/$owner/$repo/contents" else "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}"
            val arr = callArray(token, "GET", "$p?ref=${Uri.encode(ref)}")
            val nodes = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhNode(
                    name = o.optString("name"),
                    path = o.optString("path"),
                    type = o.optString("type"),
                    size = o.optLong("size"),
                    sha = o.optString("sha")
                )
            }
            // Urutan file manager: FOLDER dulu (A→Z), lalu file (A→Z), tidak peka huruf besar/kecil
            val sorted = nodes.sortedWith(
                compareBy<GhNode> { it.type != "dir" }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.name }
            )
            // Ukuran riil untuk pointer Git LFS (file besar >95 MB hanya tampak ±130 B di git tree)
            val ptrs = resolveLfsPointers(
                token, owner, repo,
                sorted.filter { it.type == "file" }.map { it.sha to it.size }
            )
            if (ptrs.isEmpty()) sorted else sorted.map { n ->
                ptrs[n.sha]?.let { (_, real) -> n.copy(size = real, isLfs = true) } ?: n
            }
        }

    suspend fun fetchFileMeta(token: String, owner: String, repo: String, path: String, ref: String): GhFileContent =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}?ref=${Uri.encode(ref)}")
                ?: throw GhException("File tidak ditemukan")
            // PENTING: untuk file > 1 MB Contents API mengembalikan content = "" (string kosong,
            // BUKAN null) — dulu ini ter-decode jadi 0 byte dan tersimpan sebagai file 0 KB.
            val b64 = if (o.isNull("content")) null else o.optString("content").takeIf { it.isNotBlank() }
            var size = o.optLong("size")
            var isLfs = false
            if (b64 != null && size in 120..160) {
                val txt = runCatching { String(Base64.decode(b64, Base64.DEFAULT)) }.getOrDefault("")
                lfsPointerInfo(txt)?.let { (_, real) -> size = real; isLfs = true }
            }
            GhFileContent(
                name = o.optString("name"),
                path = o.optString("path"),
                sha = o.optString("sha"),
                size = size,
                type = o.optString("type"),
                contentB64 = b64,
                isLfs = isLfs
            )
        }

    suspend fun fetchReadme(token: String, owner: String, repo: String, ref: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val o = call(token, "GET", "/repos/$owner/$repo/readme?ref=${Uri.encode(ref)}") ?: return@withContext null
                val b64 = o.optString("content")
                if (b64.isEmpty()) null else String(Base64.decode(b64, Base64.DEFAULT))
            } catch (e: GhException) {
                if (e.code == 404) null else throw e
            }
        }

    suspend fun putFile(
        token: String, owner: String, repo: String, path: String,
        message: String, bytes: ByteArray, branch: String, existingSha: String?
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", branch)
        if (!existingSha.isNullOrEmpty()) body.put("sha", existingSha)
        val o = call(token, "PUT", "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}", body)
        o?.optJSONObject("commit")?.optString("sha") ?: ""
    }

    suspend fun deleteFile(
        token: String, owner: String, repo: String, path: String, message: String, sha: String, branch: String
    ) = withContext(Dispatchers.IO) {
        call(token, "DELETE", "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}",
            JSONObject().put("message", message).put("sha", sha).put("branch", branch))
        Unit
    }

    suspend fun fetchBlobBytes(token: String, owner: String, repo: String, sha: String): ByteArray =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/git/blobs/$sha") ?: throw GhException("Blob tidak ditemukan")
            val content = o.optString("content")
            if (content.isEmpty()) ByteArray(0) else Base64.decode(content, Base64.DEFAULT)
        }

    // ============ BRANCH / COMMIT / TREE ============

    suspend fun fetchBranches(token: String, owner: String, repo: String): List<GhBranch> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/repos/$owner/$repo/branches?per_page=100")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhBranch(name = o.optString("name"), commitSha = o.optJSONObject("commit")?.optString("sha") ?: "")
            }
        }

    suspend fun fetchCommits(token: String, owner: String, repo: String, branch: String): List<GhCommit> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/repos/$owner/$repo/commits?sha=${Uri.encode(branch)}&per_page=8")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val commitObj = o.optJSONObject("commit")
                GhCommit(
                    sha = o.optString("sha"),
                    message = commitObj?.optString("message") ?: "",
                    author = commitObj?.optJSONObject("author")?.optString("name") ?: "",
                    avatarUrl = if (o.isNull("author")) null else o.optJSONObject("author")?.optString("avatar_url"),
                    date = commitObj?.optJSONObject("author")?.optString("date") ?: ""
                )
            }
        }

    suspend fun refSha(token: String, owner: String, repo: String, branch: String): String =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/git/ref/heads/${Uri.encode(branch)}")
                ?: throw GhException("Branch tidak ditemukan", 404)
            o.optJSONObject("object")?.optString("sha") ?: throw GhException("Branch tidak ditemukan", 404)
        }

    suspend fun commitTreeSha(token: String, owner: String, repo: String, commitSha: String): String =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/git/commits/$commitSha")
                ?: throw GhException("Commit tidak ditemukan")
            o.optJSONObject("tree")?.optString("sha") ?: throw GhException("Tree tidak ditemukan")
        }

    suspend fun fetchTreeRecursive(token: String, owner: String, repo: String, treeSha: String): List<TreeNode> =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/git/trees/$treeSha?recursive=1")
                ?: throw GhException("Tree tidak ditemukan")
            val arr = o.optJSONArray("tree") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                TreeNode(
                    path = e.optString("path"),
                    sha = e.optString("sha"),
                    type = e.optString("type"),
                    size = e.optLong("size"),
                    mode = e.optString("mode").ifEmpty { "100644" }
                )
            }
        }

    // ============ GIT LFS (ukuran riil + unduh konten asli) ============

    /**
     * Deteksi pointer Git LFS dari isi blob → (oid sha256, ukuran asli) atau null.
     * Pointer LFS memang sengaja kecil (±130 B) di git tree; ukuran asli tersimpan di baris "size N".
     */
    fun lfsPointerInfo(text: String): Pair<String, Long>? {
        if (!text.startsWith("version https://git-lfs.github.com/spec/v1")) return null
        val oid = Regex("(?m)^oid\\s+sha256:([0-9a-fA-F]{64})\\s*$").find(text)
            ?.groupValues?.get(1)?.lowercase() ?: return null
        val size = Regex("(?m)^size\\s+(\\d+)\\s*$").find(text)
            ?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return oid to size
    }

    /**
     * Selidiki blob kecil (kandidat pointer LFS: 120–160 B — panjang pointer selalu 124–140 B,
     * maks 120 blob, 8 paralel) → peta blobSha → (oid, ukuran asli). Gagal per-blob diabaikan.
     * Ambang LFS kini 16 MB → lebih banyak file jadi pointer, jadi kapasitas dinaikkan dari 60.
     */
    suspend fun resolveLfsPointers(
        token: String, owner: String, repo: String,
        candidates: List<Pair<String, Long>> // sha → ukuran di tree
    ): Map<String, Pair<String, Long>> {
        val sel = candidates.filter { it.second in 120..160 }.take(120)
        if (sel.isEmpty()) return emptyMap()
        val sem = Semaphore(8)
        val out = ConcurrentHashMap<String, Pair<String, Long>>()
        coroutineScope {
            sel.forEach { (sha, _) ->
                launch {
                    sem.withPermit {
                        runCatching {
                            val b = fetchBlobBytes(token, owner, repo, sha)
                            if (b.size in 40..1024) lfsPointerInfo(String(b))?.let { out[sha] = it }
                        }
                    }
                }
            }
        }
        return out
    }

    /** href + header unduh objek LFS via batch API (URL presigned). */
    private suspend fun lfsDownloadAction(
        token: String, owner: String, repo: String, oid: String, size: Long
    ): Pair<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val basic = Base64.encodeToString("$token:x-oauth-basic".toByteArray(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("operation", "download")
            .put("transfers", JSONArray().put("basic"))
            .put("hash_algo", "sha256")
            .put("objects", JSONArray().put(JSONObject().put("oid", oid).put("size", size)))
        val (code, text, _) = callStreamed(
            "https://github.com/$owner/$repo.git/info/lfs/objects/batch", "POST", "",
            body.toString().toRequestBody(lfsMedia),
            mapOf("Accept" to "application/vnd.git-lfs+json", "Authorization" to "Basic $basic")
        )
        if (code !in 200..299) throw GhException("Git LFS ditolak (HTTP $code): ${text.take(140)}", code)
        val obj = JSONObject(text).optJSONArray("objects")?.optJSONObject(0)
            ?: throw GhException("Respons Git LFS tidak valid")
        obj.optJSONObject("error")?.let { e ->
            throw GhException("Git LFS: ${e.optString("message", "gagal")}", e.optInt("code"))
        }
        val dl = obj.optJSONObject("actions")?.optJSONObject("download")
            ?: throw GhException("Objek LFS tidak ditemukan di penyimpanan Git LFS")
        val href = dl.optString("href").takeIf { it.isNotEmpty() }
            ?: throw GhException("URL unduh LFS tidak tersedia")
        val headers = mutableMapOf<String, String>()
        dl.optJSONObject("header")?.let { h -> h.keys().forEach { k -> headers[k] = h.optString(k) } }
        href to headers
    }

    /** Stream isi blob MENTAH (Accept: vnd.github.raw) langsung ke OutputStream — hemat RAM, aman file besar. */
    private suspend fun streamBlobRaw(
        token: String, owner: String, repo: String, sha: String, out: OutputStream,
        onBytes: (Long) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val b = Request.Builder().url("$API/repos/$owner/$repo/git/blobs/$sha")
            .header("Accept", "application/vnd.github.raw")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "GitPush-Android")
            .header("Authorization", "Bearer $token")
            .get()
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw GhException("Gagal mengunduh blob (HTTP ${r.code})")
            val src = r.body?.byteStream() ?: throw GhException("Stream blob kosong")
            src.use { s ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onBytes(copied)
                }
            }
        }
    }

    /** Unduh konten ASLI objek LFS langsung ke OutputStream (streaming, hemat RAM). */
    suspend fun streamLfsContent(
        token: String, owner: String, repo: String, oid: String, size: Long, out: OutputStream,
        onBytes: (Long) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val (href, extraHeaders) = lfsDownloadAction(token, owner, repo, oid, size)
        val b = Request.Builder().url(href).header("User-Agent", "GitPush-Android")
        extraHeaders.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw GhException("Gagal mengunduh objek LFS (HTTP ${r.code})")
            val src = r.body?.byteStream() ?: throw GhException("Stream LFS kosong")
            src.use { s ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onBytes(copied)
                }
            }
        }
    }

    // ============ BULK UPLOAD (1 COMMIT — TANPA LIMIT UKURAN/JUMLAH) ============

    /**
     * - Blob dikirim via JSON base64 yang di-STREAM per chunk (RAM kecil, file ratusan MB aman).
     * - File > 95 MB otomatis via Git LFS (hash → batch → PUT → verify → commit pointer).
     * - File 0 byte memakai sha blob kosong bawaan git (tanpa request).
     * - 3 file paralel + throttle adaptif + retry otomatis (limit sekunder GitHub).
     * - File yang gagal dilompati (dicatat), sisanya tetap di-commit.
     * - Jika branch bergerak saat commit (non fast-forward), diulang maksimal 3x.
     */
    suspend fun bulkUpload(
        token: String, owner: String, repo: String, branch: String,
        filesIn: List<PickedFile>, message: String,
        resolver: android.content.ContentResolver?,
        hooks: UploadHooks
    ): UploadResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val throttle = Throttle()
        val ctx = UpCtx(token, owner, repo, resolver, hooks, throttle, AtomicLong(0))

        // Sanitasi path + buang duplikat
        val files = filesIn.map { f ->
            val p = f.path.trim().replace('\\', '/').trimStart('/').replace(Regex("/+"), "/")
            if (p != f.path) f.copy(path = p) else f
        }.distinctBy { it.path }
        if (files.isEmpty()) throw GhException("Tidak ada file untuk di-commit")

        hooks.onStage("Menyiapkan")
        val baseCommitSha = try {
            refSha(token, owner, repo, branch)
        } catch (e: GhException) {
            if (e.code == 404 || e.code == 409) null else throw e
        }
        val baseTreeSha = baseCommitSha?.let { commitTreeSha(token, owner, repo, it) }

        // Rencana: ukuran pasti (probe bila metadata 0) + sha256 utk LFS.
        // DIPARALELKAN (4): ratusan file besar tidak lagi dianalisis satu-per-satu —
        // dulu loop sekuensial membuat fase persiapan terasa macet sebelum upload mulai.
        // Progres tiap checksum dilaporkan via onHash, jumlah selesai via onAnalyzed.
        val plans = arrayOfNulls<Plan>(files.size)
        val totalBytes = AtomicLong(0)
        val analyzed = AtomicInteger(0)
        val semHash = Semaphore(4)
        coroutineScope {
            files.forEachIndexed { idx, f ->
                launch {
                    semHash.withPermit {
                        if (hooks.isCancelled()) return@withPermit
                        var size = f.size
                        var sha256: String? = null
                        if (size <= 0) {
                            hooks.onStage("Menganalisis file")
                            val p = hashAndSize(resolver, f, 0) { read -> hooks.onHash(f.path, read, 0) }
                            size = p.first
                            sha256 = p.second
                        }
                        val lfs = size > LFS_THRESHOLD_BYTES
                        if (lfs && sha256 == null) {
                            hooks.onStage("Menganalisis file")
                            sha256 = hashAndSize(resolver, f, size) { read -> hooks.onHash(f.path, read, size) }.second
                        }
                        plans[idx] = Plan(f, size, sha256, lfs)
                        totalBytes.addAndGet(size)
                        hooks.onAnalyzed(analyzed.incrementAndGet(), files.size)
                    }
                }
            }
        }
        if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)
        val planList = plans.map { it ?: throw GhException("Rencana upload tidak lengkap") }
        hooks.onTotal(files.size, totalBytes.get())

        // ===== Unggah blob — paralel TIERED (kunci kecepatan & keandalan): =====
        // - File kecil (<1 MB): 6 paralel — ratusan file selesai jauh lebih cepat.
        // - File menengah (1-16 MB): 3 paralel.
        // - File LFS (≥16 MB): 1 sekaligus SAJA. Dulu paralel 3 berebut bandwidth
        //   sehingga PUT ratusan MB mudah putus dan gagal berulang.
        hooks.onStage("Mengunggah file")
        val shas = arrayOfNulls<String>(files.size)
        val failed = ConcurrentHashMap<String, String>()
        val semFast = Semaphore(6)
        val semMid = Semaphore(3)
        val semLfs = Semaphore(1)
        // File kecil dulu (banyak kemenangan cepat → progres terasa hidup), besar terakhir
        val ordered = files.indices.sortedWith(
            compareBy<Int> { planList[it].lfs }.thenBy { planList[it].size }
        )
        coroutineScope {
            for (idx in ordered) {
                val f = files[idx]
                launch {
                    val plan = planList[idx]
                    val sem = when {
                        plan.lfs -> semLfs
                        plan.size >= 1L * 1024 * 1024 -> semMid
                        else -> semFast
                    }
                    sem.withPermit {
                        if (hooks.isCancelled()) return@withPermit
                        // Delta byte file ini → agregat global. Saat percobaan ULANG dimulai
                        // dari 0 (sent < lastLocal), aggregate DITARIK BALIK — dulu bar total
                        // MEMBEKU di posisi percobaan lama sampai file melewatinya; itulah
                        // keluhan "progress stuck terasa lama".
                        var lastLocal = 0L
                        val onP: (Long) -> Unit = { sent ->
                            if (sent < lastLocal) {
                                ctx.rewind(lastLocal - sent)
                                lastLocal = sent
                            }
                            val d = sent - lastLocal
                            lastLocal = sent
                            if (d > 0) ctx.addSent(d)
                            hooks.onCurrent(f.path, sent, plan.size)
                            if (hooks.isCancelled()) throw IOException("Dibatalkan")
                        }
                        try {
                            val blobSha: String = when {
                                plan.size == 0L -> EMPTY_BLOB_SHA
                                plan.lfs -> {
                                    val oid = plan.sha256 ?: hashAndSize(resolver, f).second
                                    try {
                                        ctx.lfsUpload(f, plan.size, oid, onP)
                                        // Pointer LFS di-commit sebagai blob kecil
                                        ctx.smallBlob(lfsPointer(oid, plan.size).toByteArray())
                                    } catch (e: GhException) {
                                        // Kuota LFS habis / LFS dimatikan → FALLBACK ke blob API
                                        // biasa (file ≤99 MB masih diterima GitHub) sehingga
                                        // upload tetap jalan walau kuota LFS habis.
                                        if (e.kind == "lfs_unavailable" && plan.size <= MAX_BLOB_FALLBACK_BYTES) {
                                            ctx.blobCreate(f, onP)
                                        } else throw e
                                    }
                                }
                                else -> ctx.blobCreate(f, onP)
                            }
                            if (blobSha.isEmpty()) throw GhException("SHA blob kosong dari GitHub")
                            shas[idx] = blobSha
                            hooks.onFileDone(f.path, plan.size)
                        } catch (e: Exception) {
                            val reason = if (e is kotlinx.coroutines.CancellationException) "Dibatalkan" else humanError(e)
                            failed[f.path] = reason
                            hooks.onFileSkipped(f.path, plan.size, reason)
                        }
                    }
                }
            }
        }
        if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)

        val uploadedIdx = files.indices.filter { shas[it] != null }
        if (uploadedIdx.isEmpty()) {
            throw GhException(
                if (failed.isNotEmpty()) "Semua file gagal — contoh: ${failed.entries.first().let { "${it.key}: ${it.value}" }}"
                else "Tidak ada file yang berhasil diunggah"
            )
        }

        // ===== Tree + Commit + Update ref (retry saat branch bergerak) =====
        var attempt = 0
        var commitShaOut = ""
        while (true) {
            attempt++
            hooks.onStage("Membuat commit")
            val headSha = try {
                refSha(token, owner, repo, branch)
            } catch (e: GhException) {
                if (e.code == 404 || e.code == 409) null else throw e
            }
            val headTree = headSha?.let { commitTreeSha(token, owner, repo, it) }

            val treeArr = JSONArray()
            for (i in uploadedIdx) {
                treeArr.put(
                    JSONObject()
                        .put("path", files[i].path)
                        .put("mode", "100644")
                        .put("type", "blob")
                        .put("sha", shas[i])
                )
            }
            val treeBody = JSONObject().put("tree", treeArr)
            if (headTree != null) treeBody.put("base_tree", headTree)
            val tree = callRetry(token, "POST", "/repos/$owner/$repo/git/trees", treeBody, hooks, throttle)
                ?: throw GhException("Gagal membuat tree")

            val parents = headSha?.let { JSONArray().put(it) } ?: JSONArray()
            val commit = callRetry(
                token, "POST", "/repos/$owner/$repo/git/commits",
                JSONObject().put("message", message).put("tree", tree.optString("sha")).put("parents", parents),
                hooks, throttle
            ) ?: throw GhException("Gagal membuat commit")
            val sha = commit.optString("sha")

            hooks.onStage("Memperbarui branch")
            try {
                if (headSha == null) {
                    callRetry(
                        token, "POST", "/repos/$owner/$repo/git/refs",
                        JSONObject().put("ref", "refs/heads/$branch").put("sha", sha), hooks, throttle
                    )
                } else {
                    callRetry(
                        token, "PATCH", "/repos/$owner/$repo/git/refs/heads/${Uri.encode(branch)}",
                        JSONObject().put("sha", sha).put("force", false), hooks, throttle
                    )
                }
                commitShaOut = sha
                break
            } catch (e: GhException) {
                val msg = e.message ?: ""
                val moved = e.code == 422 || msg.contains("fast", true)
                if (moved && attempt < 3) continue // branch bergerak — ulangi dengan parent terbaru
                throw e
            }
        }

        UploadResult(commitShaOut, uploadedIdx.size, failed.map { it.key to it.value }, System.currentTimeMillis() - started)
    }

    private data class Plan(val f: PickedFile, val size: Long, val sha256: String?, val lfs: Boolean)

    /**
     * Throttle adaptif dengan PELURUHAN. Dulu sekali kena limit (403/429) semua request
     * masuk gerbang 700 ms SELAMANYA → ratusan file terasa super lambat (±1,4 req/s).
     * Kini setiap 6 keberhasilan berturut-turut jeda dilonggarkan 25% sampai hilang.
     */
    private class Throttle {
        var minIntervalMs = 0
        private var lastStart = 0L
        private var okStreak = 0

        @Synchronized
        fun gate() {
            if (minIntervalMs <= 0) return
            val now = System.currentTimeMillis()
            val wait = lastStart + minIntervalMs - now
            if (wait > 0) try { Thread.sleep(wait) } catch (_: InterruptedException) { }
            lastStart = System.currentTimeMillis()
        }

        @Synchronized
        fun success() {
            okStreak++
            if (okStreak >= 6) {
                okStreak = 0
                if (minIntervalMs > 0) minIntervalMs = (minIntervalMs * 3) / 4
                if (minIntervalMs < 100) minIntervalMs = 0
            }
        }

        @Synchronized
        fun slowDown(ms: Int) {
            okStreak = 0
            if (ms > minIntervalMs) minIntervalMs = ms
        }
    }

    private class UpCtx(
        val token: String,
        val owner: String,
        val repo: String,
        val resolver: android.content.ContentResolver?,
        val hooks: UploadHooks,
        val throttle: Throttle,
        val aggregate: AtomicLong
    ) {
        /** Tambah delta byte terkirim dan laporkan total agregat (thread-safe). */
        fun addSent(delta: Long) {
            if (delta <= 0) return
            hooks.onAggregate(aggregate.addAndGet(delta))
        }

        /** Tarik balik byte saat percobaan diulang dari 0 — bar total MUNDUR jujur
         *  (tidak pernah membeku menunggu file melewati titik percobaan lama). */
        fun rewind(delta: Long) {
            if (delta <= 0) return
            hooks.onAggregate(aggregate.updateAndGet { cur -> (cur - delta).coerceAtLeast(0L) })
        }
    }

    /** call() dengan retry: IOException / 5xx / 429 / 403 limit sekunder. */
    private suspend fun callRetry(
        token: String, method: String, path: String, body: JSONObject?,
        hooks: UploadHooks, throttle: Throttle, maxAttempts: Int = 5
    ): JSONObject? = withContext(Dispatchers.IO) {
        var last: Exception? = null
        repeat(maxAttempts) { i ->
            if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)
            throttle.gate()
            try {
                val r = call(token, method, path, body)
                throttle.success()
                return@withContext r
            } catch (e: GhException) {
                last = e
                val secondary = e.code == 403 && (e.retryAfterMs > 0 || (e.message ?: "").contains("secondary", true))
                val retryable = e.code in 500..599 || e.code == 429 || secondary
                if (!retryable || i == maxAttempts - 1) throw e
                if (e.code == 403 || e.code == 429) throttle.slowDown(700)
                delay(if (e.retryAfterMs > 0) e.retryAfterMs.coerceAtMost(60_000) else 1000L * (1 shl i))
            } catch (e: IOException) {
                last = e
                if (i == maxAttempts - 1) throw GhException("Koneksi gagal: ${e.message}")
                delay(1000L * (1 shl i))
            }
        }
        throw last ?: GhException("Gagal")
    }

    private fun openSource(resolver: android.content.ContentResolver?, f: PickedFile): InputStream = when {
        f.file != null -> f.file.inputStream()
        f.uri != null && resolver != null ->
            resolver.openInputStream(f.uri) ?: throw IOException("Stream tidak tersedia")
        f.bytes != null -> ByteArrayInputStream(f.bytes)
        else -> throw IOException("Sumber file tidak ada")
    }

    /** Satu pass: hitung ukuran + sha256 (dipakai bila metadata ukuran tidak ada / file besar).
     *  onRead = progres pembacaan agar tahap analisis file besar tidak kelihatan macet. */
    private suspend fun hashAndSize(
        resolver: android.content.ContentResolver?, f: PickedFile,
        knownTotal: Long = 0, onRead: (Long) -> Unit = {}
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        var n = 0L
        openSource(resolver, f).use { ins ->
            val buf = ByteArray(512 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r < 0) break
                if (r > 0) {
                    md.update(buf, 0, r)
                    n += r
                    onRead(n)
                }
            }
        }
        if (knownTotal > 0) onRead(knownTotal) // pastikan 100% terlapor
        n to md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Body JSON {"content":"<base64>","encoding":"base64"} yang di-ENCODE saat dikirim
     * (chunk kelipatan 3 byte → hasil base64 identik dengan encode utuh, RAM tetap kecil).
     */
    private fun streamedBlobBody(
        f: PickedFile, resolver: android.content.ContentResolver?,
        isCancelled: () -> Boolean, onSent: (Long) -> Unit
    ): RequestBody = object : RequestBody() {
        override fun contentType() = jsonMedia

        override fun contentLength(): Long {
            val n = f.size
            return if (n > 0) B64_PREFIX.length + ((n + 2) / 3) * 4 + B64_SUFFIX.length else -1L
        }

        override fun writeTo(sink: BufferedSink) {
            val enc = java.util.Base64.getEncoder()
            val buf = ByteArray(3 * 65536) // kelipatan 3 → padding benar per chunk; 192 KB = syscall lebih sedikit
            sink.writeUtf8(B64_PREFIX)
            openSource(resolver, f).use { ins ->
                while (true) {
                    if (isCancelled()) throw IOException("Dibatalkan")
                    var read = 0
                    while (read < buf.size) {
                        val r = ins.read(buf, read, buf.size - read)
                        if (r < 0) break
                        read += r
                    }
                    if (read > 0) {
                        val chunk = if (read < buf.size) buf.copyOf(read) else buf
                        sink.writeUtf8(enc.encodeToString(chunk))
                        onSent(read.toLong())
                    }
                    if (read < buf.size) break
                }
            }
            sink.writeUtf8(B64_SUFFIX)
        }
    }

    /** Body biner mentah streaming (untuk PUT LFS ke storage). */
    private fun streamedRawBody(
        f: PickedFile, resolver: android.content.ContentResolver?, size: Long,
        isCancelled: () -> Boolean, onSent: (Long) -> Unit
    ): RequestBody = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = if (size > 0) size else -1L

        override fun writeTo(sink: BufferedSink) {
            val buf = ByteArray(512 * 1024) // 512 KB — syscall lebih sedikit untuk PUT ratusan MB
            openSource(resolver, f).use { ins ->
                while (true) {
                    if (isCancelled()) throw IOException("Dibatalkan")
                    val r = ins.read(buf)
                    if (r < 0) break
                    if (r > 0) {
                        sink.write(buf, 0, r)
                        onSent(r.toLong())
                    }
                }
            }
        }
    }

    /** Buat blob via API (JSON base64 streaming) dengan retry + throttle adaptif. */
    private suspend fun UpCtx.blobCreate(f: PickedFile, onSent: (Long) -> Unit): String =
        withContext(Dispatchers.IO) {
            var attempt = 0
            while (true) {
                if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)
                throttle.gate()
                attempt++
                val body = streamedBlobBody(f, resolver, hooks.isCancelled, onSent)
                val (code, text, ra) = callStreamed(
                    "$API/repos/$owner/$repo/git/blobs", "POST", token, body,
                    mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
                )
                if (code in 200..299) {
                    throttle.success()
                    return@withContext JSONObject(text).optString("sha")
                }
                val secondary = code == 403 && (ra > 0 || text.contains("secondary", true))
                // code == 0: koneksi putus saat mengirim body besar — blob bersifat content-addressed
                // (idempotent) sehingga aman diulang dari awal.
                val retryable = code in 500..599 || code == 429 || code == 0 || secondary
                if (!retryable || attempt >= 5) throw GhException("HTTP $code: ${text.take(160)}", code, ra)
                if (code == 403 || code == 429) throttle.slowDown(700)
                val wait = if (ra > 0) ra.coerceAtMost(60_000) else 1000L * attempt
                // Pengguna harus tahu proses masih berjalan saat mengulang (dulu diam = terlihat macet)
                hooks.onRetry(f.path, attempt, 5, wait)
                delay(wait)
            }
            @Suppress("UNREACHABLE_CODE")
            ""
        }

    /** Blob kecil (pointer LFS) via JSON biasa + retry. */
    private suspend fun UpCtx.smallBlob(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("encoding", "base64")
        val o = callRetry(token, "POST", "/repos/$owner/$repo/git/blobs", body, hooks, throttle)
            ?: throw GhException("Gagal membuat blob")
        o.optString("sha")
    }

    /** Cek apakah objek LFS sudah tersimpan di penyimpanan GitHub (batch download). */
    private suspend fun UpCtx.lfsObjectExists(oid: String, size: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val basic = Base64.encodeToString("$token:x-oauth-basic".toByteArray(), Base64.NO_WRAP)
                val body = JSONObject()
                    .put("operation", "download")
                    .put("transfers", JSONArray().put("basic"))
                    .put("hash_algo", "sha256")
                    .put("objects", JSONArray().put(JSONObject().put("oid", oid).put("size", size)))
                val res = callStreamed(
                    "https://github.com/$owner/$repo.git/info/lfs/objects/batch", "POST", "",
                    body.toString().toRequestBody(lfsMedia),
                    mapOf("Accept" to "application/vnd.git-lfs+json", "Authorization" to "Basic $basic")
                )
                if (res.first !in 200..299) return@withContext false
                val o = JSONObject(res.second).optJSONArray("objects")?.optJSONObject(0)
                    ?: return@withContext false
                !o.has("error") && o.optJSONObject("actions")?.has("download") == true
            } catch (e: GhException) {
                false
            }
        }

    private fun lfsPointer(oid: String, size: Long): String =
        "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize $size\n"

    /**
     * Git LFS: batch → PUT (streaming, progress) → verify.
     * Auth batch: Basic <token>:x-oauth-basic. Header upload/verify diambil persis dari respons.
     */
    private suspend fun UpCtx.lfsUpload(
        f: PickedFile, size: Long, oid: String, onSent: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val basic = Base64.encodeToString("$token:x-oauth-basic".toByteArray(), Base64.NO_WRAP)
        val batchBody = JSONObject()
            .put("operation", "upload")
            .put("transfers", JSONArray().put("basic"))
            .put("hash_algo", "sha256")
            .put("objects", JSONArray().put(JSONObject().put("oid", oid).put("size", size)))

        // Batch dengan retry tangguh: koneksi putus (code 0), server error 5xx/429,
        // dan respons terpotong (JSON rusak karena jaringan seluler flaky) semuanya diulang.
        var obj: JSONObject? = null
        var attempt = 0
        while (obj == null) {
            if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)
            attempt++
            var retryable = false
            try {
                val res = callStreamed(
                    "https://github.com/$owner/$repo.git/info/lfs/objects/batch", "POST", "",
                    batchBody.toString().toRequestBody(lfsMedia),
                    mapOf("Accept" to "application/vnd.git-lfs+json", "Authorization" to "Basic $basic")
                )
                when (res.first) {
                    in 200..299 -> obj = JSONObject(res.second).optJSONArray("objects")?.optJSONObject(0)
                    429, 500, 502, 503, 504 -> retryable = true
                    else -> {
                        // GitHub membalas 403 "Bad credentials" di endpoint LFS bila repo
                        // sudah DIHAPUS/tidak dapat diakses — auth ke api.github.com tetap valid.
                        if (res.first == 403 && res.second.contains("Bad credentials")) {
                            throw GhException("Repository tidak dapat diakses — kemungkinan sudah dihapus (403)", 403)
                        }
                        if (res.first == 404) {
                            throw GhException("Repository tidak ditemukan — kemungkinan sudah dihapus (404)", 404)
                        }
                        // 403 kuota LFS / 422 / penolakan lain — BUKAN masalah repo.
                        // kind="lfs_unavailable" memicu fallback ke blob API (file ≤99 MB).
                        throw GhException("Git LFS ditolak (HTTP ${res.first}): ${res.second.take(140)}", res.first, kind = "lfs_unavailable")
                    }
                }
            } catch (e: GhException) {
                if (e.code != 0) throw e
                retryable = true // koneksi gagal
            } catch (e: Exception) {
                retryable = true // JSON terpotong/rusak — jaringan tidak stabil
            }
            if (obj == null) {
                if (attempt >= 4) {
                    throw GhException(
                        if (retryable) "Koneksi Git LFS tidak stabil — coba lagi"
                        else "Respons Git LFS tidak valid"
                    )
                }
                delay(700L * attempt)
            }
        }
        val batchObj = obj ?: throw GhException("Respons Git LFS tidak valid")
        batchObj.optJSONObject("error")?.let { e ->
            throw GhException("Git LFS: ${e.optString("message", "gagal")}", e.optInt("code"), kind = "lfs_unavailable")
        }
        // Spesifikasi Git LFS: respons upload TANPA "actions" berarti objek SUDAH tersimpan
        // di penyimpanan LFS (upaya sebelumnya sempat terunggah lalu gagal di tahap commit).
        // JANGAN dianggap gagal — konfirmasi via batch download; bila benar ada, lewati PUT
        // (tidak mengulang unggah 100 MB) dan langsung lanjut ke commit pointer.
        val actions = batchObj.optJSONObject("actions")
        if (actions == null) {
            if (lfsObjectExists(oid, size)) return@withContext
            throw GhException(
                "Git LFS menolak unggahan file ini — kemungkinan kuota LFS habis. Cek: github.com/settings/billing",
                0, kind = "lfs_unavailable"
            )
        }

        // --- PUT ke storage (URL presigned; JANGAN tambah header auth sendiri) ---
        // Dulu 3x restart dari nol; di jaringan seluler sering semuanya gagal.
        // Kini 5x dengan backoff, DAN sebelum tiap pengulangan cek dulu apakah objek
        // ternyata SUDAH sampai di storage (respons saja yang hilang) — bila ya,
        // unggahan 100 MB tidak diulang, langsung lanjut.
        val up = actions.optJSONObject("upload") ?: throw GhException("URL upload LFS tidak tersedia")
        val upHeaders = mutableMapOf<String, String>()
        up.optJSONObject("header")?.let { h ->
            h.keys().forEach { k -> upHeaders[k] = h.optString(k) }
        }
        val maxPut = 5
        var putAttempt = 0
        while (true) {
            if (hooks.isCancelled()) throw GhException("Dibatalkan", 499)
            putAttempt++
            onSent(0)
            val body = streamedRawBody(f, resolver, size, hooks.isCancelled, onSent)
            // PUT file besar rawan koneksi putus di tengah jalan — GhException(code 0) diulang.
            val putCode = try {
                callStreamed(up.optString("href"), "PUT", "", body, upHeaders).first
            } catch (e: GhException) {
                if (e.code != 0) throw e
                0
            }
            if (putCode in 200..299) {
                throttle.success()
                break
            }
            if (putAttempt >= maxPut) throw GhException("Upload objek LFS gagal setelah $maxPut percobaan (HTTP $putCode)")
            // Respons hilang? Objek bisa jadi sudah tersimpan — verifikasi dulu sebelum
            // memutuskan mengunggah ulang ratusan MB dari nol.
            if (lfsObjectExists(oid, size)) break
            val wait = (1000L shl (putAttempt - 1)).coerceAtMost(10_000) // 1s,2s,4s,8s
            hooks.onRetry(f.path, putAttempt, maxPut, wait)
            delay(wait)
        }

        // --- Verify (opsional; header persis dari respons) ---
        actions.optJSONObject("verify")?.let { vf ->
            val vfHeaders = mutableMapOf("Content-Type" to "application/vnd.git-lfs+json")
            vf.optJSONObject("header")?.let { h ->
                h.keys().forEach { k -> vfHeaders[k] = h.optString(k) }
            }
            callStreamed(
                vf.optString("href"), "POST", "",
                JSONObject().put("oid", oid).put("size", size).toString().toRequestBody(lfsMedia),
                vfHeaders
            )
        }
    }

    // ============ RENAME (1 COMMIT VIA GIT DATA API) ============

    suspend fun renameFile(
        token: String, owner: String, repo: String, branch: String, oldPath: String, newPath: String,
        onStage: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        onStage("Menganalisis tree…")
        val commitSha = refSha(token, owner, repo, branch)
        val oldTreeSha = commitTreeSha(token, owner, repo, commitSha)
        val tree = fetchTreeRecursive(token, owner, repo, oldTreeSha)
        val target = tree.firstOrNull { it.path == oldPath && it.type == "blob" }
            ?: throw GhException("File tidak ditemukan di tree")
        onStage("Menyiapkan commit…")
        val arr = JSONArray()
        arr.put(
            JSONObject().put("path", newPath).put("mode", "100644").put("type", "blob").put("sha", target.sha)
        )
        arr.put(JSONObject().put("path", oldPath).put("mode", "100644").put("sha", JSONObject.NULL))
        val newTree = call(
            token, "POST", "/repos/$owner/$repo/git/trees",
            JSONObject().put("base_tree", oldTreeSha).put("tree", arr)
        ) ?: throw GhException("Gagal membuat tree")
        val newCommit = call(
            token, "POST", "/repos/$owner/$repo/git/commits",
            JSONObject()
                .put("message", "Rename $oldPath menjadi $newPath via GitPush")
                .put("tree", newTree.optString("sha"))
                .put("parents", JSONArray().put(commitSha))
        ) ?: throw GhException("Gagal membuat commit")
        call(
            token, "PATCH", "/repos/$owner/$repo/git/refs/heads/${Uri.encode(branch)}",
            JSONObject().put("sha", newCommit.optString("sha"))
        )
        newCommit.optString("sha")
    }

    // ============ FOLDER: RENAME & HAPUS (1 COMMIT VIA GIT DATA API) ============

    /** Rename folder (pindah seluruh isinya) dalam 1 commit. Return: commitSha to jumlah file. */
    suspend fun renameFolder(
        token: String, owner: String, repo: String, branch: String, oldPath: String, newPath: String,
        onStage: (String) -> Unit = {}
    ): Pair<String, Int> = moveOrDeleteFolder(token, owner, repo, branch, oldPath, newPath, onStage)

    /** Hapus folder beserta seluruh isinya dalam 1 commit. Return: commitSha to jumlah file terhapus. */
    suspend fun deleteFolder(
        token: String, owner: String, repo: String, branch: String, path: String,
        onStage: (String) -> Unit = {}
    ): Pair<String, Int> = moveOrDeleteFolder(token, owner, repo, branch, path, null, onStage)

    private suspend fun moveOrDeleteFolder(
        token: String, owner: String, repo: String, branch: String,
        folderPath: String, newFolderPath: String?, onStage: (String) -> Unit = {}
    ): Pair<String, Int> = withContext(Dispatchers.IO) {
        val prefix = "$folderPath/"
        var lastErr: Exception? = null
        // Diulang maks 3x bila branch bergerak saat update ref (non fast-forward)
        repeat(3) { attempt ->
            try {
                if (attempt > 0) onStage("Branch bergerak — mengulang (${attempt + 1}/3)…")
                else onStage("Menganalisis tree…")
                val commitSha = refSha(token, owner, repo, branch)
                val oldTreeSha = commitTreeSha(token, owner, repo, commitSha)
                val tree = fetchTreeRecursive(token, owner, repo, oldTreeSha)
                val inside = tree.filter { it.type == "blob" && it.path.startsWith(prefix) }
                if (inside.isEmpty()) {
                    throw GhException("Folder \"$folderPath\" kosong atau tidak ditemukan", 404)
                }
                onStage("Menyiapkan ${inside.size} file…")
                val arr = JSONArray()
                for (e in inside) {
                    arr.put(
                        JSONObject().put("path", e.path).put("mode", "100644").put("sha", JSONObject.NULL)
                    )
                    if (newFolderPath != null) {
                        val np = newFolderPath + e.path.substring(folderPath.length)
                        arr.put(
                            JSONObject()
                                .put("path", np)
                                .put("mode", e.mode.ifEmpty { "100644" })
                                .put("type", "blob")
                                .put("sha", e.sha)
                        )
                    }
                }
                val newTree = call(
                    token, "POST", "/repos/$owner/$repo/git/trees",
                    JSONObject().put("base_tree", oldTreeSha).put("tree", arr)
                ) ?: throw GhException("Gagal membuat tree")
                val verb = if (newFolderPath == null) "Hapus folder $folderPath" else "Rename folder $folderPath menjadi $newFolderPath"
                val newCommit = call(
                    token, "POST", "/repos/$owner/$repo/git/commits",
                    JSONObject()
                        .put("message", "$verb via GitPush")
                        .put("tree", newTree.optString("sha"))
                        .put("parents", JSONArray().put(commitSha))
                ) ?: throw GhException("Gagal membuat commit")
                call(
                    token, "PATCH", "/repos/$owner/$repo/git/refs/heads/${Uri.encode(branch)}",
                    JSONObject().put("sha", newCommit.optString("sha"))
                )
                return@withContext newCommit.optString("sha") to inside.size
            } catch (e: GhException) {
                if (e.code == 404) throw e // folder kosong/tidak ada — jangan diulang
                lastErr = e
                if (attempt == 2) throw e
            } catch (e: Exception) {
                lastErr = e
                if (attempt == 2) throw e
            }
        }
        throw lastErr ?: GhException("Gagal memproses folder")
    }

    // ============ NOTIFIKASI ============

    suspend fun fetchNotifications(token: String, all: Boolean): List<GhNotification> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/notifications?per_page=50${if (all) "&all=true" else ""}")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhNotification(
                    id = o.optString("id"),
                    repoFullName = o.optJSONObject("repository")?.optString("full_name") ?: "",
                    subjectTitle = o.optJSONObject("subject")?.optString("title") ?: "",
                    subjectType = o.optJSONObject("subject")?.optString("type") ?: "",
                    reason = o.optString("reason"),
                    unread = o.optBoolean("unread"),
                    updatedAt = o.optString("updated_at")
                )
            }
        }

    suspend fun markThreadRead(token: String, id: String) = withContext(Dispatchers.IO) {
        call(token, "PATCH", "/notifications/threads/$id")
        Unit
    }

    suspend fun markAllRead(token: String) = withContext(Dispatchers.IO) {
        call(token, "PUT", "/notifications", JSONObject().put("read", true))
        Unit
    }

    // ============ DOWNLOAD & ZIP ============

    suspend fun saveToDownloads(context: Context, fileName: String, mime: String, writer: suspend (OutputStream) -> Unit): String {
        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/GitPush")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: throw GhException("Tidak bisa membuat file di folder Download")
        try {
            resolver.openOutputStream(uri)?.use { writer(it) }
                ?: throw GhException("Tidak bisa membuka stream file")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return "Download/GitPush/$fileName"
    }

    /**
     * Unduh satu file. Pointer Git LFS otomatis di-resolve → konten ASLI di-stream
     * dari penyimpanan LFS ke Download (bukan pointer ±130 B).
     */
    suspend fun downloadFile(
        context: Context, token: String, owner: String, repo: String, node: GhNode, ref: String,
        onStage: (String) -> Unit = {}, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        onStage("Membaca metadata…")
        val meta = fetchFileMeta(token, owner, repo, node.path, ref)

        // Deteksi pointer LFS: (1) sudah terdeteksi fetchFileMeta (isLfs, pointer ±130 B selalu
        // dikembalikan penuh oleh Contents API), atau (2) file kecil → cek murah dari bytes.
        val ptr: Pair<String, Long>? = if (meta.isLfs) {
            val ptxt = if (!meta.contentB64.isNullOrEmpty())
                String(Base64.decode(meta.contentB64, Base64.DEFAULT))
            else String(fetchBlobBytes(token, owner, repo, meta.sha))
            lfsPointerInfo(ptxt)
        } else if (meta.size in 1..1024) {
            val bytes = if (!meta.contentB64.isNullOrEmpty())
                Base64.decode(meta.contentB64, Base64.DEFAULT)
            else fetchBlobBytes(token, owner, repo, meta.sha)
            lfsPointerInfo(String(bytes))
        } else null

        when {
            // Konten ASLI dari penyimpanan Git LFS — progres byte nyata
            ptr != null -> {
                val total = ptr.second
                onStage("Menyiapkan objek Git LFS…")
                saveToDownloads(context, node.name, "application/octet-stream") { out ->
                    onProgress(0, total)
                    streamLfsContent(token, owner, repo, ptr.first, total, out) { sent ->
                        onProgress(sent, total)
                    }
                }
            }
            // File ≥ 1 MB: Contents API TIDAK mengirim isi (content="") — stream mentah dari
            // Git Blobs API langsung ke Download tanpa memuat seluruh file ke RAM.
            meta.contentB64.isNullOrEmpty() -> {
                val total = if (meta.size > 0) meta.size else 0L
                saveToDownloads(context, node.name, "application/octet-stream") { out ->
                    if (total > 0) onProgress(0, total)
                    streamBlobRaw(token, owner, repo, meta.sha, out) { sent ->
                        if (total > 0) onProgress(sent, total)
                    }
                }
            }
            // File kecil biasa — isi sudah utuh di meta
            else -> {
                val bytes = Base64.decode(meta.contentB64, Base64.DEFAULT)
                val loc = saveToDownloads(context, node.name, "application/octet-stream") { it.write(bytes) }
                onProgress(bytes.size.toLong(), bytes.size.toLong())
                loc
            }
        }
    }

    private suspend fun collectBlobs(
        token: String, owner: String, repo: String, branch: String, prefix: String
    ): List<TreeNode> = withContext(Dispatchers.IO) {
        val commitSha = try {
            refSha(token, owner, repo, branch)
        } catch (e: GhException) {
            if (e.code == 409 || e.code == 404) throw GhException("Repository kosong — tidak ada yang bisa diunduh")
            throw e
        }
        val treeSha = commitTreeSha(token, owner, repo, commitSha)
        val all = fetchTreeRecursive(token, owner, repo, treeSha)
        all.filter { it.type == "blob" && (prefix.isEmpty() || it.path.startsWith("$prefix/")) }
    }

    /**
     * Rakit ZIP per-entry (streaming — satu file di RAM dalam satu waktu).
     * Pointer Git LFS di-resolve dan konten aslinya di-stream dari penyimpanan LFS.
     */
    private suspend fun zipBlobs(
        context: Context, zipName: String, token: String, owner: String, repo: String,
        blobs: List<TreeNode>, onProgress: (Int, Int, String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        if (blobs.size > 1500) throw GhException("Terlalu banyak file (${blobs.size}). Maksimal 1500 file per unduhan ZIP.")
        val ptrs = resolveLfsPointers(token, owner, repo, blobs.map { it.sha to it.size })
        val totalSize = blobs.sumOf { ptrs[it.sha]?.second ?: it.size }
        if (totalSize > 500L * 1024 * 1024) {
            throw GhException("Total ukuran melebihi 500 MB — unduh per folder lewat menu folder")
        }
        saveToDownloads(context, zipName, "application/zip") { out ->
            ZipOutputStream(BufferedOutputStream(out, 256 * 1024)).use { zip ->
                blobs.forEachIndexed { i, n ->
                    zip.putNextEntry(ZipEntry(n.path))
                    val p = ptrs[n.sha]
                    if (p != null) streamLfsContent(token, owner, repo, p.first, p.second, zip)
                    else streamBlobRaw(token, owner, repo, n.sha, zip)
                    zip.closeEntry()
                    onProgress(i + 1, blobs.size, n.path)
                }
            }
        }
    }

    suspend fun downloadFolderZip(
        context: Context, token: String, owner: String, repo: String, branch: String,
        folderPath: String, onProgress: (Int, Int, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val blobs = collectBlobs(token, owner, repo, branch, folderPath)
        val zipName = "${(folderPath.ifEmpty { repo }).replace('/', '-')}-$branch.zip"
        zipBlobs(context, zipName, token, owner, repo, blobs, onProgress)
        zipName
    }

    suspend fun downloadRepoZip(
        context: Context, token: String, owner: String, repo: String, branch: String,
        isPrivate: Boolean, onProgress: (Int, Int, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Selalu rakit ZIP sendiri: zipball bawaan GitHub memuat pointer LFS (±130 B),
        // bukan isi aslinya. Jalur ini men-resolve objek LFS sehingga ZIP berisi file utuh.
        val blobs = collectBlobs(token, owner, repo, branch, "")
        val zipName = "$repo-$branch.zip"
        zipBlobs(context, zipName, token, owner, repo, blobs, onProgress)
        zipName
    }

    // ==================================================================
    // ============ FITUR LENGKAP ALA GITHUB (semua via PAT) ============
    // ==================================================================

    // ---------- Star / Watch / Fork / Edit repository ----------

    /** Apakah repo ini sudah di-star oleh pengguna? (204 = ya, 404 = belum) */
    suspend fun isStarred(token: String, owner: String, repo: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                call(token, "GET", "/user/starred/$owner/$repo")
                true // 204 tanpa body → JSONObject() kosong
            } catch (e: GhException) {
                if (e.code == 404) false else throw e
            }
        }

    /** Star (true) / unstar (false) repository. */
    suspend fun setStarred(token: String, owner: String, repo: String, star: Boolean): Unit =
        withContext(Dispatchers.IO) {
            if (star) call(token, "PUT", "/user/starred/$owner/$repo")
            else call(token, "DELETE", "/user/starred/$owner/$repo")
            Unit
        }

    /** Apakah pengguna memantau (watch) repository ini? */
    suspend fun isWatching(token: String, owner: String, repo: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                call(token, "GET", "/repos/$owner/$repo/subscription")
                true
            } catch (e: GhException) {
                if (e.code == 404) false else throw e
            }
        }

    suspend fun setWatching(token: String, owner: String, repo: String, watch: Boolean): Unit =
        withContext(Dispatchers.IO) {
            if (watch) {
                call(
                    token, "PUT", "/repos/$owner/$repo/subscription",
                    JSONObject().put("subscribed", true)
                )
            } else {
                call(token, "DELETE", "/repos/$owner/$repo/subscription")
            }
            Unit
        }

    /** Fork repository ke akun pengguna (GitHub memproses secara asinkron). */
    suspend fun forkRepo(token: String, owner: String, repo: String): GhRepo =
        withContext(Dispatchers.IO) {
            val o = call(token, "POST", "/repos/$owner/$repo/forks", JSONObject())
                ?: throw GhException("Gagal fork repository")
            parseRepo(o)
        }

    /** Edit repository: nama baru, deskripsi, situs web, visibilitas. */
    suspend fun editRepo(
        token: String, owner: String, repo: String,
        newName: String, description: String, homepage: String, isPrivate: Boolean
    ): GhRepo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("private", isPrivate)
        if (newName.isNotBlank() && newName != repo) body.put("name", newName.trim())
        body.put("description", description)
        body.put("homepage", homepage)
        val o = call(token, "PATCH", "/repos/$owner/$repo", body)
            ?: throw GhException("Gagal menyimpan perubahan repository")
        parseRepo(o)
    }

    // ---------- Branch: buat / hapus ----------

    /** Buat branch baru dari branch sumber (ref sama dengan HEAD branch sumber). */
    suspend fun createBranch(
        token: String, owner: String, repo: String, fromBranch: String, newBranch: String
    ): Unit = withContext(Dispatchers.IO) {
        val sha = refSha(token, owner, repo, fromBranch)
        call(
            token, "POST", "/repos/$owner/$repo/git/refs",
            JSONObject().put("ref", "refs/heads/$newBranch").put("sha", sha)
        )
        Unit
    }

    /** Hapus branch (branch default tidak boleh dihapus — divalidasi di UI). */
    suspend fun deleteBranch(token: String, owner: String, repo: String, branch: String): Unit =
        withContext(Dispatchers.IO) {
            call(token, "DELETE", "/repos/$owner/$repo/git/refs/heads/${Uri.encode(branch)}")
            Unit
        }

    // ---------- Issues ----------

    private fun parseIssue(o: JSONObject): GhIssue = GhIssue(
        number = o.optInt("number"),
        title = o.optString("title"),
        body = if (o.isNull("body")) null else o.optString("body"),
        state = o.optString("state"),
        author = o.optJSONObject("user")?.optString("login") ?: "",
        avatarUrl = o.optJSONObject("user")?.optString("avatar_url"),
        comments = o.optInt("comments"),
        createdAt = o.optString("created_at"),
        updatedAt = o.optString("updated_at"),
        labels = o.optJSONArray("labels")?.let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).optString("name") }
        } ?: emptyList(),
        isPr = o.has("pull_request")
    )

    /** Daftar issue + PR (state: open | closed | all). */
    suspend fun fetchIssues(token: String, owner: String, repo: String, state: String): List<GhIssue> =
        withContext(Dispatchers.IO) {
            val arr = callArray(
                token, "GET",
                "/repos/$owner/$repo/issues?state=$state&sort=updated&per_page=50"
            )
            (0 until arr.length()).map { i -> parseIssue(arr.getJSONObject(i)) }
        }

    suspend fun createIssue(
        token: String, owner: String, repo: String, title: String, body: String
    ): GhIssue = withContext(Dispatchers.IO) {
        val b = JSONObject().put("title", title)
        if (body.isNotBlank()) b.put("body", body)
        val o = call(token, "POST", "/repos/$owner/$repo/issues", b)
            ?: throw GhException("Gagal membuat issue")
        parseIssue(o)
    }

    /** Tutup / buka ulang issue. */
    suspend fun setIssueState(token: String, owner: String, repo: String, number: Int, open: Boolean): Unit =
        withContext(Dispatchers.IO) {
            call(
                token, "PATCH", "/repos/$owner/$repo/issues/$number",
                JSONObject().put("state", if (open) "open" else "closed")
            )
            Unit
        }

    suspend fun fetchIssueComments(token: String, owner: String, repo: String, number: Int): List<GhComment> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/repos/$owner/$repo/issues/$number/comments?per_page=50")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhComment(
                    author = o.optJSONObject("user")?.optString("login") ?: "",
                    avatarUrl = o.optJSONObject("user")?.optString("avatar_url"),
                    body = o.optString("body"),
                    createdAt = o.optString("created_at")
                )
            }
        }

    suspend fun addIssueComment(
        token: String, owner: String, repo: String, number: Int, body: String
    ): Unit = withContext(Dispatchers.IO) {
        call(
            token, "POST", "/repos/$owner/$repo/issues/$number/comments",
            JSONObject().put("body", body)
        )
        Unit
    }

    // ---------- Pull Request ----------

    private fun parsePull(o: JSONObject): GhPull = GhPull(
        number = o.optInt("number"),
        title = o.optString("title"),
        body = if (o.isNull("body")) null else o.optString("body"),
        state = o.optString("state"),
        author = o.optJSONObject("user")?.optString("login") ?: "",
        avatarUrl = o.optJSONObject("user")?.optString("avatar_url"),
        createdAt = o.optString("created_at"),
        headRef = o.optJSONObject("head")?.optString("ref") ?: "",
        baseRef = o.optJSONObject("base")?.optString("ref") ?: "",
        mergeable = if (o.isNull("mergeable")) null else o.optBoolean("mergeable"),
        draft = o.optBoolean("draft")
    )

    suspend fun fetchPulls(token: String, owner: String, repo: String, state: String): List<GhPull> =
        withContext(Dispatchers.IO) {
            val arr = callArray(
                token, "GET", "/repos/$owner/$repo/pulls?state=$state&sort=updated&per_page=50"
            )
            (0 until arr.length()).map { i -> parsePull(arr.getJSONObject(i)) }
        }

    /** Gabungkan pull request (metode merge). Gagal 405/409 dilempar apa adanya. */
    suspend fun mergePull(
        token: String, owner: String, repo: String, number: Int
    ): Unit = withContext(Dispatchers.IO) {
        call(
            token, "PUT", "/repos/$owner/$repo/pulls/$number/merge",
            JSONObject().put("merge_method", "merge")
        )
        Unit
    }

    // ---------- Releases ----------

    suspend fun fetchReleases(token: String, owner: String, repo: String): List<GhRelease> =
        withContext(Dispatchers.IO) {
            try {
                val arr = callArray(token, "GET", "/repos/$owner/$repo/releases?per_page=20")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    GhRelease(
                        name = o.optString("name").ifEmpty { o.optString("tag_name") },
                        tagName = o.optString("tag_name"),
                        body = if (o.isNull("body")) null else o.optString("body"),
                        publishedAt = o.optString("published_at"),
                        assetCount = o.optJSONArray("assets")?.length() ?: 0,
                        isPrerelease = o.optBoolean("prerelease")
                    )
                }
            } catch (e: GhException) {
                if (e.code == 404) emptyList() else throw e
            }
        }

    // ---------- Pencarian global ----------

    /** Cari repository di seluruh GitHub (bukan hanya milik pengguna). */
    suspend fun searchRepos(token: String, query: String): List<GhRepo> =
        withContext(Dispatchers.IO) {
            // /search/repositories mengembalikan OBJEK { items: [...] } — bukan array,
            // sehingga memakai call() langsung, bukan callArray().
            val o = call(
                token, "GET",
                "/search/repositories?q=${Uri.encode(query)}&sort=updated&per_page=30"
            ) ?: throw GhException("Pencarian gagal")
            val list = o.optJSONArray("items") ?: return@withContext emptyList()
            (0 until list.length()).map { i -> parseRepo(list.getJSONObject(i)) }
        }

    // ---------- Pengguna lain: profil, followers, following, ikuti ----------

    suspend fun fetchUserPublic(token: String, login: String): GhUser = withContext(Dispatchers.IO) {
        val o = call(token, "GET", "/users/$login") ?: throw GhException("Pengguna tidak ditemukan", 404)
        parseUser(o)
    }

    private fun parseUserLite(o: JSONObject): GhUserLite = GhUserLite(
        login = o.optString("login"),
        avatarUrl = o.optString("avatar_url")
    )

    suspend fun fetchFollowers(token: String, login: String): List<GhUserLite> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/users/$login/followers?per_page=50")
            (0 until arr.length()).map { i -> parseUserLite(arr.getJSONObject(i)) }
        }

    suspend fun fetchFollowing(token: String, login: String): List<GhUserLite> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/users/$login/following?per_page=50")
            (0 until arr.length()).map { i -> parseUserLite(arr.getJSONObject(i)) }
        }

    /** Ikuti / berhenti mengikuti pengguna. */
    suspend fun setFollowing(token: String, login: String, follow: Boolean): Unit =
        withContext(Dispatchers.IO) {
            if (follow) call(token, "PUT", "/user/following/$login")
            else call(token, "DELETE", "/user/following/$login")
            Unit
        }

    /** Perbarui profil publik: nama, email, situs, perusahaan, lokasi, bio. */
    suspend fun updateProfile(
        token: String, name: String, email: String, blog: String,
        company: String, location: String, bio: String
    ): GhUser = withContext(Dispatchers.IO) {
        val o = call(
            token, "PATCH", "/user",
            JSONObject()
                .put("name", name)
                .put("email", email)
                .put("blog", blog)
                .put("company", company)
                .put("location", location)
                .put("bio", bio)
        ) ?: throw GhException("Gagal memperbarui profil")
        parseUser(o)
    }

    // ---------- Email (GET/POST/DELETE /user/emails) ----------

    suspend fun fetchEmails(token: String): List<GhEmail> = withContext(Dispatchers.IO) {
        val arr = callArray(token, "GET", "/user/emails")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhEmail(
                email = o.optString("email"),
                primary = o.optBoolean("primary"),
                verified = o.optBoolean("verified"),
                visibility = if (o.isNull("visibility")) null else o.optString("visibility")
            )
        }
    }

    suspend fun addEmail(token: String, email: String): Unit = withContext(Dispatchers.IO) {
        call(token, "POST", "/user/emails", JSONObject().put("emails", JSONArray().put(email.trim())))
        Unit
    }

    suspend fun deleteEmail(token: String, email: String): Unit = withContext(Dispatchers.IO) {
        call(token, "DELETE", "/user/emails", JSONObject().put("emails", JSONArray().put(email)))
        Unit
    }

    // ---------- Kunci SSH (GET/POST/DELETE /user/keys) ----------

    suspend fun fetchKeys(token: String): List<GhKey> = withContext(Dispatchers.IO) {
        val arr = callArray(token, "GET", "/user/keys?per_page=50")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhKey(
                id = o.optLong("id"),
                title = o.optString("title"),
                key = o.optString("key"),
                createdAt = o.optString("created_at")
            )
        }
    }

    suspend fun addKey(token: String, title: String, key: String): Unit = withContext(Dispatchers.IO) {
        call(token, "POST", "/user/keys", JSONObject().put("title", title.trim()).put("key", key.trim()))
        Unit
    }

    suspend fun deleteKey(token: String, id: Long): Unit = withContext(Dispatchers.IO) {
        call(token, "DELETE", "/user/keys/$id")
        Unit
    }

    // ---------- Gist (catatan cepat ala GitHub) ----------

    private fun parseGist(o: JSONObject): GhGist {
        val files = o.optJSONObject("files")
        var first: String? = null
        var count = 0
        if (files != null) {
            count = files.length()
            first = files.keys().asSequence().firstOrNull()
        }
        return GhGist(
            id = o.optString("id"),
            description = if (o.isNull("description")) null else o.optString("description"),
            firstFileName = first,
            fileCount = count,
            updatedAt = o.optString("updated_at"),
            isPublic = o.optBoolean("public")
        )
    }

    suspend fun fetchGists(token: String): List<GhGist> = withContext(Dispatchers.IO) {
        val arr = callArray(token, "GET", "/gists?per_page=30")
        (0 until arr.length()).map { i -> parseGist(arr.getJSONObject(i)) }
    }

    suspend fun createGist(
        token: String, fileName: String, content: String, description: String, isPublic: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val files = JSONObject().put(
            fileName.ifBlank { "catatan.txt" },
            JSONObject().put("content", content)
        )
        call(
            token, "POST", "/gists",
            JSONObject().put("files", files).put("description", description).put("public", isPublic)
        )
        Unit
    }

    /** Isi file pertama gist (untuk pratinjau). */
    suspend fun fetchGistContent(token: String, id: String): String = withContext(Dispatchers.IO) {
        val o = call(token, "GET", "/gists/$id") ?: throw GhException("Gist tidak ditemukan", 404)
        val files = o.optJSONObject("files") ?: return@withContext ""
        val first = files.keys().asSequence().firstOrNull() ?: return@withContext ""
        files.optJSONObject(first)?.optString("content") ?: ""
    }

    suspend fun deleteGist(token: String, id: String): Unit = withContext(Dispatchers.IO) {
        call(token, "DELETE", "/gists/$id")
        Unit
    }

    // ---------- Organisasi & aktivitas ----------

    suspend fun fetchOrgs(token: String): List<GhOrg> = withContext(Dispatchers.IO) {
        val arr = callArray(token, "GET", "/user/orgs?per_page=30")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhOrg(
                login = o.optString("login"),
                avatarUrl = o.optString("avatar_url"),
                description = if (o.isNull("description")) null else o.optString("description")
            )
        }
    }

    /** Umpan aktivitas publik pengguna (ala tab activity GitHub). */
    suspend fun fetchEvents(token: String, login: String): List<GhEvent> =
        withContext(Dispatchers.IO) {
            val arr = callArray(token, "GET", "/users/$login/events/public?per_page=30")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val type = o.optString("type")
                val repo = o.optJSONObject("repo")?.optString("name") ?: ""
                val p = o.optJSONObject("payload")
                GhEvent(
                    type = type,
                    repo = repo,
                    createdAt = o.optString("created_at"),
                    detail = when (type) {
                        "PushEvent" -> "${p?.optJSONArray("commits")?.length() ?: p?.optInt("size") ?: 1} commit di-push"
                        "CreateEvent" -> "Membuat ${p?.optString("ref_type") ?: "repo"}${p?.optString("ref")?.let { " $it" } ?: ""}"
                        "DeleteEvent" -> "Menghapus ${p?.optString("ref_type") ?: "ref"}${p?.optString("ref")?.let { " $it" } ?: ""}"
                        "WatchEvent" -> "Menyukai repository"
                        "ForkEvent" -> "Fork repository"
                        "IssuesEvent" -> "Issue #${p?.optJSONObject("issue")?.optInt("number") ?: "?"} ${p?.optString("action") ?: ""}"
                        "IssueCommentEvent" -> "Komentar di issue"
                        "PullRequestEvent" -> "PR #${p?.optInt("number") ?: "?"} ${p?.optString("action") ?: ""}"
                        "PullRequestReviewEvent" -> "Review pull request"
                        "PullRequestReviewCommentEvent" -> "Komentar review PR"
                        "ReleaseEvent" -> "Rilis ${p?.optJSONObject("release")?.optString("tag_name") ?: ""}"
                        "PublicEvent" -> "Repository dipublikasikan"
                        "MemberEvent" -> "Menambah kolaborator"
                        "GollumEvent" -> "Memperbarui wiki"
                        else -> type.removeSuffix("Event")
                    }
                )
            }
        }

    // ---------- Scope token ----------

    /**
     * Scope PAT aktif dari header respons `X-OAuth-Scopes` (hanya ada di /user).
     * Dipakai layar Pengaturan agar pengguna tahu kemampuan token-nya.
     */
    suspend fun fetchTokenScopes(token: String): List<String> = withContext(Dispatchers.IO) {
        val b = Request.Builder().url("$API/user")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GitPush-Android")
            .header("Authorization", "Bearer $token")
            .get()
        http.newCall(b.build()).execute().use { r ->
            val scopes = r.header("X-OAuth-Scopes")
            if (!r.isSuccessful) throw GhException("HTTP ${r.code}", r.code)
            scopes?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }
}
