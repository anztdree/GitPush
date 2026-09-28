package com.gitpush.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhUser
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun histIcon(kind: String) = when (kind) {
    "upload" -> Icons.Filled.Upload
    "edit" -> Icons.Filled.Edit
    "create" -> Icons.Filled.Add
    "delete" -> Icons.Filled.Delete
    "repo" -> Icons.Filled.Folder
    "download" -> Icons.Filled.Download
    else -> Icons.Filled.Description
}

@Composable
fun ProfileScreen() {
    val ctx = LocalContext.current
    var u by remember { mutableStateOf<GhUser?>(Store.user.value) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val fresh = withContext(Dispatchers.IO) {
                GitHubApi.fetchUser(Store.token.value)
            }
            u = fresh
            Store.user.value = fresh
        } catch (e: Exception) {
            // pakai cache
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u?.avatarUrl ?: "", 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            u?.name ?: u?.login ?: "Memuat…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            "@${u?.login ?: "…"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        if (!u?.bio.isNullOrBlank()) {
                            Text(
                                u!!.bio!!,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("${u?.publicRepos ?: 0}", "repo publik")
                    StatChip("${u?.followers ?: 0}", "pengikut")
                    StatChip("${u?.following ?: 0}", "mengikuti")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Riwayat aktivitas", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        val hist = Store.history.value
        if (hist.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Belum ada aktivitas. Riwayat upload, edit, rename, hapus, dan unduh akan tercatat di sini.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    hist.take(20).forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                histIcon(h.kind),
                                contentDescription = h.kind,
                                tint = GreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.label, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    h.repo,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Text(timeAgoMs(h.time), color = GrayMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
            TextButton(onClick = { Store.clearHistory() }) { Text("Bersihkan riwayat", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(18.dp))
        Text("Tentang", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GitPush", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            "v1.3 NATIVE",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aplikasi Android asli — Kotlin + Jetpack Compose, tanpa webview/wrapper. " +
                        "Upload massal 1 commit, edit/rename/hapus file, download file & folder & repository, " +
                        "notifikasi, dan buat repository baru.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/anztdree/GitPush"))
                        )
                    }
                }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Source code di GitHub", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { confirmSignOut = true },
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Keluar dari GitPush", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Keluar dari GitPush?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Token akan dihapus dari perangkat ini. Anda perlu login lagi dengan Personal Access Token.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    Store.signOut()
                }) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
