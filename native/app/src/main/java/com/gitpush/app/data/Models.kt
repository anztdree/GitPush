package com.gitpush.app.data

import android.net.Uri
import java.io.File

data class GhUser(
    val login: String,
    val name: String,
    val avatarUrl: String,
    val bio: String?,
    val followers: Int,
    val following: Int,
    val publicRepos: Int,
    // Field tambahan untuk profil ala GitHub (boleh null — dipakai edit profil)
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    val email: String? = null,
    val createdAt: String = ""
)

data class GhRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val ownerAvatar: String,
    val description: String?,
    val isPrivate: Boolean,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val issues: Int,
    val defaultBranch: String,
    val updatedAt: String,
    val sizeKb: Long = 0, // ukuran repository (KB) dari API — untuk kuota 2 GB
    val homepage: String? = null,
    val watchers: Int = 0
)

data class GhNode(
    val name: String,
    val path: String,
    val type: String, // "file" | "dir"
    val size: Long,
    val sha: String,
    val isLfs: Boolean = false // true = file Git LFS (ukuran riil sudah dihitung dari pointer)
)

data class GhBranch(val name: String, val commitSha: String)

data class GhCommit(
    val sha: String,
    val message: String,
    val author: String,
    val avatarUrl: String?,
    val date: String
)

data class GhNotification(
    val id: String,
    val repoFullName: String,
    val subjectTitle: String,
    val subjectType: String,
    val reason: String,
    val unread: Boolean,
    val updatedAt: String
)

data class GhFileContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String,
    val contentB64: String?,
    val isLfs: Boolean = false // pointer LFS terdeteksi (size = ukuran asli)
)

data class TreeNode(
    val path: String,
    val sha: String,
    val type: String, // "blob" | "tree"
    val size: Long,
    val mode: String = "100644",
    val isLfs: Boolean = false
)

data class HistoryEntry(
    val kind: String, // upload | edit | rename | delete | create | repo | download
    val label: String,
    val repo: String,
    val time: Long
)

// ================= Model fitur baru (ala GitHub lengkap) =================

data class GhIssue(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String, // open | closed
    val author: String,
    val avatarUrl: String?,
    val comments: Int,
    val createdAt: String,
    val updatedAt: String,
    val labels: List<String> = emptyList(),
    val isPr: Boolean = false // item daftar issues yang sebenarnya PR
)

data class GhPull(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val author: String,
    val avatarUrl: String?,
    val createdAt: String,
    val headRef: String,
    val baseRef: String,
    val mergeable: Boolean?,
    val draft: Boolean
)

data class GhComment(
    val author: String,
    val avatarUrl: String?,
    val body: String,
    val createdAt: String
)

data class GhReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadCount: Int,
    val contentType: String
)

data class GhRelease(
    val id: Long,
    val name: String,
    val tagName: String,
    val body: String?,
    val publishedAt: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val authorLogin: String,
    val assets: List<GhReleaseAsset> = emptyList()
)

data class GhEmail(
    val email: String,
    val primary: Boolean,
    val verified: Boolean,
    val visibility: String?
)

data class GhKey(
    val id: Long,
    val title: String,
    val key: String,
    val createdAt: String
)

data class GhGist(
    val id: String,
    val description: String?,
    val firstFileName: String?,
    val fileCount: Int,
    val updatedAt: String,
    val isPublic: Boolean
)

data class GhOrg(
    val login: String,
    val avatarUrl: String,
    val description: String?
)

data class GhUserLite(
    val login: String,
    val avatarUrl: String
)

data class GhEvent(
    val type: String,
    val repo: String,
    val createdAt: String,
    val detail: String // teks ringkas dalam Bahasa Indonesia
)

/**
 * File yang dipilih untuk upload. Byte TIDAK di-load ke RAM saat memilih —
 * dibaca baru saat upload (hemat memori untuk folder besar).
 * Sumber bisa: file fisik (file manager bawaan), Uri SAF, atau byte langsung.
 */
data class PickedFile(
    val path: String,
    val size: Long,
    val bytes: ByteArray? = null,
    val file: File? = null,
    val uri: Uri? = null
)
