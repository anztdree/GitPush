package com.gitpush.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhEvent
import com.gitpush.app.data.GhUser
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Statistik hero profil — bisa diklik (pengikut/mengikuti) dengan penanda panah kecil. */
@Composable
private fun ProfileHeroStat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp)
            if (onClick != null) {
                Spacer(Modifier.width(3.dp))
                Text(":", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
    var showEditProfile by remember { mutableStateOf(false) }
    var showGists by remember { mutableStateOf(false) }
    var showOrgs by remember { mutableStateOf(false) }
    var showFollowers by remember { mutableStateOf(false) }
    var showFollowing by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<GhEvent>?>(null) }

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
        // Umpan aktivitas publik (ala GitHub) — diam-diam bila gagal
        events = runCatching {
            withContext(Dispatchers.IO) {
                GitHubApi.fetchEvents(Store.token.value, Store.user.value?.login ?: u?.login ?: "")
            }
        }.getOrNull() ?: emptyList()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ===== Kartu profil — hero gradien + statistik =====
        HeroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(66.dp)
                        .background(Color.White.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape)
                        .padding(2.dp)
                ) {
                    Avatar(u?.avatarUrl ?: "", 62.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        u?.name ?: u?.login ?: "Memuat…",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "@${u?.login ?: "…"}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp
                    )
                }
                Surface(
                    onClick = { showEditProfile = true },
                    enabled = u != null,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text("Edit", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!u?.bio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    u!!.bio!!,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!u?.company.isNullOrBlank() || !u?.location.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                val meta = listOfNotNull(u?.company, u?.location).joinToString("  •  ")
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileHeroStat("${u?.publicRepos ?: 0}", "repo publik")
                ProfileHeroStat("${u?.followers ?: 0}", "pengikut") { showFollowers = true }
                ProfileHeroStat("${u?.following ?: 0}", "mengikuti") { showFollowing = true }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { showGists = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text("Gist saya", fontSize = 12.sp, color = Color.White)
                    }
                }
                Surface(
                    onClick = { showOrgs = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text("Organisasi", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // ===== Aktivitas terbaru (umpan ala GitHub) =====
        Spacer(Modifier.height(20.dp))
        Text("Aktivitas terbaru", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                events == null -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                events!!.isEmpty() -> Text(
                    "Belum ada aktivitas publik. Push, star, fork, dan commit Anda akan tampil di sini.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(14.dp)
                )
                else -> Column(Modifier.padding(vertical = 4.dp)) {
                    EventsList(events!!)
                }
            }
        }

        // ===== Riwayat aktivitas lokal =====
        Spacer(Modifier.height(20.dp))
        Text("Riwayat GitPush", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        val hist = Store.history.value
        if (hist.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp),
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
                                Text(h.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    h.repo,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(timeAgoMs(h.time), color = GrayMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
            TextButton(onClick = { Store.clearHistory() }) { Text("Bersihkan riwayat", fontSize = 12.sp) }
        }

        // ===== Tentang =====
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
                            "v1.0 NATIVE",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aplikasi Android asli — Kotlin + Jetpack Compose + font Inter, tanpa webview/wrapper. " +
                        "File manager penyimpanan awan: upload massal 1 commit, edit/rename/pindah/hapus, " +
                        "unduh file & folder & repo, issue, pull request, release, gist, dan pengaturan akun lengkap.",
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

    if (showEditProfile) {
        u?.let { uu ->
            EditProfileDialog(
                user = uu,
                onDismiss = { showEditProfile = false },
                onSaved = {
                    showEditProfile = false
                    Toast.makeText(ctx, "Profil diperbarui ✓", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    if (showGists) GistsDialog(onDismiss = { showGists = false })
    if (showOrgs) OrgsDialog(onDismiss = { showOrgs = false })
    if (showFollowers) {
        UsersListDialog(
            title = "Pengikut Anda",
            fetcher = {
                withContext(Dispatchers.IO) {
                    GitHubApi.fetchFollowers(Store.token.value, u?.login ?: "")
                }
            },
            onDismiss = { showFollowers = false }
        )
    }
    if (showFollowing) {
        UsersListDialog(
            title = "Yang Anda ikuti",
            fetcher = {
                withContext(Dispatchers.IO) {
                    GitHubApi.fetchFollowing(Store.token.value, u?.login ?: "")
                }
            },
            onDismiss = { showFollowing = false }
        )
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
private fun StatChip(value: String, label: String, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(9.dp),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
