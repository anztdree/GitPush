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
    val publicRepos: Int
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
    val sizeKb: Long = 0 // ukuran repository (KB) dari API — untuk kuota 2 GB
)

data class GhNode(
    val name: String,
    val path: String,
    val type: String, // "file" | "dir"
    val size: Long,
    val sha: String
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
    val contentB64: String?
)

data class TreeNode(
    val path: String,
    val sha: String,
    val type: String, // "blob" | "tree"
    val size: Long,
    val mode: String = "100644"
)

data class HistoryEntry(
    val kind: String, // upload | edit | rename | delete | create | repo | download
    val label: String,
    val repo: String,
    val time: Long
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
