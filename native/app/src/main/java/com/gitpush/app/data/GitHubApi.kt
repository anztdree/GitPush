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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GhException(message: String, val code: Int = 0) : Exception(message)

object GitHubApi {

    const val API = "https://api.github.com"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

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
                if (!r.isSuccessful) throw GhException("HTTP ${r.code}: ${text.take(180)}", r.code)
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

    fun humanError(e: Throwable): String {
        val code = (e as? GhException)?.code ?: 0
        return when {
            code == 401 -> "Token tidak valid atau kedaluwarsa (401)"
            code == 403 -> "Akses ditolak / limit API tercapai (403)"
            code == 404 -> "Tidak ditemukan (404)"
            code == 409 -> "Repository kosong (409)"
            code == 422 -> "Data tidak valid (422) — cek nama/isi"
            e.message?.contains("Unable to resolve host", true) == true -> "Tidak ada koneksi internet"
            else -> e.message ?: "Terjadi kesalahan"
        }
    }

    // ============ USER & REPO ============

    suspend fun fetchUser(token: String): GhUser = withContext(Dispatchers.IO) {
        val o = call(token, "GET", "/user") ?: throw GhException("Respons kosong")
        GhUser(
            login = o.optString("login"),
            name = if (o.isNull("name")) o.optString("login") else o.optString("name"),
            avatarUrl = o.optString("avatar_url"),
            bio = if (o.isNull("bio")) null else o.optString("bio"),
            followers = o.optInt("followers"),
            following = o.optInt("following"),
            publicRepos = o.optInt("public_repos")
        )
    }

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
        updatedAt = o.optString("updated_at")
    )

    suspend fun createRepo(
        token: String, name: String, description: String, isPrivate: Boolean, autoInit: Boolean
    ): GhRepo = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).put("private", isPrivate).put("auto_init", autoInit)
        if (description.isNotEmpty()) body.put("description", description)
        val o = call(token, "POST", "/user/repos", body) ?: throw GhException("Respons kosong")
        parseRepo(o)
    }

    // ============ CONTENTS ============

    suspend fun fetchContents(token: String, owner: String, repo: String, path: String, ref: String): List<GhNode> =
        withContext(Dispatchers.IO) {
            val p = if (path.isEmpty()) "/repos/$owner/$repo/contents" else "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}"
            val arr = callArray(token, "GET", "$p?ref=${Uri.encode(ref)}")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GhNode(
                    name = o.optString("name"),
                    path = o.optString("path"),
                    type = o.optString("type"),
                    size = o.optLong("size"),
                    sha = o.optString("sha")
                )
            }
        }

    suspend fun fetchFileMeta(token: String, owner: String, repo: String, path: String, ref: String): GhFileContent =
        withContext(Dispatchers.IO) {
            val o = call(token, "GET", "/repos/$owner/$repo/contents/${Uri.encode(path, "/")}?ref=${Uri.encode(ref)}")
                ?: throw GhException("File tidak ditemukan")
            GhFileContent(
                name = o.optString("name"),
                path = o.optString("path"),
                sha = o.optString("sha"),
                size = o.optLong("size"),
                type = o.optString("type"),
                contentB64 = if (o.isNull("content")) null else o.optString("content")
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

    private suspend fun commitTreeSha(token: String, owner: String, repo: String, commitSha: String): String =
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
                    size = e.optLong("size")
                )
            }
        }

    // ============ BULK UPLOAD (1 COMMIT) ============

    suspend fun bulkUpload(
        token: String, owner: String, repo: String, branch: String,
        files: List<PickedFile>, message: String,
        resolver: android.content.ContentResolver?,
        onProgress: (String, Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        onProgress("Menyiapkan commit", 0, files.size)
        val baseCommitSha = try {
            refSha(token, owner, repo, branch)
        } catch (e: GhException) {
            if (e.code == 404 || e.code == 409) null else throw e
        }
        val baseTreeSha = baseCommitSha?.let { commitTreeSha(token, owner, repo, it) }

        val shas = arrayOfNulls<String>(files.size)
        val done = AtomicInteger(0)
        val sem = Semaphore(3)
        coroutineScope {
            files.mapIndexed { idx, f ->
                async {
                    sem.withPermit {
                        // Baca byte SAAT UPLOAD (bukan saat memilih) — hanya ±3 file di RAM
                        val bytes = when {
                            f.file != null -> runCatching { f.file.readBytes() }.getOrElse {
                                throw GhException("Gagal membaca: ${f.path}")
                            }
                            f.uri != null && resolver != null -> runCatching {
                                resolver.openInputStream(f.uri)?.use { it.readBytes() }
                            }.getOrNull() ?: throw GhException("Gagal membaca: ${f.path}")
                            else -> f.bytes ?: throw GhException("Sumber file tidak ada: ${f.path}")
                        }
                        if (bytes.isEmpty()) throw GhException("File kosong/tidak terbaca: ${f.path}")
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val res = call(
                            token, "POST", "/repos/$owner/$repo/git/blobs",
                            JSONObject().put("content", b64).put("encoding", "base64")
                        ) ?: throw GhException("Gagal membuat blob")
                        shas[idx] = res.optString("sha")
                        onProgress("Mengunggah file", done.incrementAndGet(), files.size)
                    }
                }
            }.awaitAll()
        }

        onProgress("Membuat tree", 0, 1)
        val treeArr = JSONArray()
        files.forEachIndexed { idx, f ->
            treeArr.put(
                JSONObject()
                    .put("path", f.path)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", shas[idx] ?: throw GhException("Blob belum lengkap"))
            )
        }
        val treeBody = JSONObject().put("tree", treeArr)
        if (baseTreeSha != null) treeBody.put("base_tree", baseTreeSha)
        val tree = call(token, "POST", "/repos/$owner/$repo/git/trees", treeBody)
            ?: throw GhException("Gagal membuat tree")

        onProgress("Membuat commit", 0, 1)
        val parents = baseCommitSha?.let { JSONArray().put(it) } ?: JSONArray()
        val commit = call(
            token, "POST", "/repos/$owner/$repo/git/commits",
            JSONObject().put("message", message).put("tree", tree.optString("sha")).put("parents", parents)
        ) ?: throw GhException("Gagal membuat commit")
        val commitSha = commit.optString("sha")

        onProgress("Memperbarui branch", 0, 1)
        if (baseCommitSha == null) {
            call(
                token, "POST", "/repos/$owner/$repo/git/refs",
                JSONObject().put("ref", "refs/heads/$branch").put("sha", commitSha)
            )
        } else {
            call(
                token, "PATCH", "/repos/$owner/$repo/git/refs/heads/${Uri.encode(branch)}",
                JSONObject().put("sha", commitSha).put("force", false)
            )
        }
        commitSha
    }

    // ============ RENAME (1 COMMIT VIA GIT DATA API) ============

    suspend fun renameFile(
        token: String, owner: String, repo: String, branch: String, oldPath: String, newPath: String
    ): String = withContext(Dispatchers.IO) {
        val commitSha = refSha(token, owner, repo, branch)
        val oldTreeSha = commitTreeSha(token, owner, repo, commitSha)
        val tree = fetchTreeRecursive(token, owner, repo, oldTreeSha)
        val target = tree.firstOrNull { it.path == oldPath && it.type == "blob" }
            ?: throw GhException("File tidak ditemukan di tree")
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

    fun saveToDownloads(context: Context, fileName: String, mime: String, writer: (OutputStream) -> Unit): String {
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

    suspend fun downloadFile(
        context: Context, token: String, owner: String, repo: String, node: GhNode, ref: String
    ): String = withContext(Dispatchers.IO) {
        val meta = fetchFileMeta(token, owner, repo, node.path, ref)
        val bytes = if (meta.contentB64 != null) {
            Base64.decode(meta.contentB64, Base64.DEFAULT)
        } else {
            fetchBlobBytes(token, owner, repo, meta.sha)
        }
        saveToDownloads(context, node.name, "application/octet-stream") { it.write(bytes) }
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

    private suspend fun zipBlobs(
        context: Context, zipName: String, token: String, owner: String, repo: String,
        blobs: List<TreeNode>, onProgress: (Int, Int) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val totalSize = blobs.sumOf { it.size }
        if (blobs.size > 400) throw GhException("Terlalu banyak file (${blobs.size}). Maksimal 400 file per unduhan ZIP.")
        if (totalSize > 150L * 1024 * 1024) throw GhException("Total ukuran melebihi 150 MB — unduh manual lewat web GitHub")
        val contents = ArrayList<ByteArray>(blobs.size)
        blobs.forEachIndexed { i, n ->
            contents.add(fetchBlobBytes(token, owner, repo, n.sha))
            onProgress(i + 1, blobs.size)
        }
        saveToDownloads(context, zipName, "application/zip") { out ->
            ZipOutputStream(BufferedOutputStream(out, 128 * 1024)).use { zip ->
                contents.forEachIndexed { i, bytes ->
                    zip.putNextEntry(ZipEntry(blobs[i].path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun downloadFolderZip(
        context: Context, token: String, owner: String, repo: String, branch: String,
        folderPath: String, onProgress: (Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val blobs = collectBlobs(token, owner, repo, branch, folderPath)
        val zipName = "${(folderPath.ifEmpty { repo }).replace('/', '-')}-$branch.zip"
        zipBlobs(context, zipName, token, owner, repo, blobs, onProgress)
        zipName
    }

    suspend fun downloadRepoZip(
        context: Context, token: String, owner: String, repo: String, branch: String,
        isPrivate: Boolean, onProgress: (Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (isPrivate) {
            val blobs = collectBlobs(token, owner, repo, branch, "")
            val zipName = "$repo-$branch.zip"
            zipBlobs(context, zipName, token, owner, repo, blobs, onProgress)
            zipName
        } else {
            val req = Request.Builder()
                .url("$API/repos/$owner/$repo/zipball/${Uri.encode(branch)}")
                .header("User-Agent", "GitPush-Android")
                .header("Accept", "application/vnd.github+json")
                .build()
            val fileName = "$repo-$branch.zip"
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw GhException("Gagal mengunduh repository (HTTP ${r.code})")
                saveToDownloads(context, fileName, "application/zip") { out ->
                    r.body?.byteStream()?.use { src -> src.copyTo(out, 128 * 1024) }
                        ?: throw GhException("Stream kosong")
                }
            }
            fileName
        }
    }
}
