package com.gitpush.app.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.gitpush.app.data.GitHubApi

/** Baris menu pengaturan ber-ikon: judul + keterangan + aksi kanan. */
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = GreenPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = tint.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            trailing?.invoke()
            if (onClick != null && trailing == null) {
                TextButton(onClick = onClick) { Text("Kelola") }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var msgDraft by remember { mutableStateOf(Store.defaultMsg.value) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showEmails by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }
    var scopes by remember { mutableStateOf<List<String>?>(null) }

    // Ambil scope PAT aktif (header X-OAuth-Scopes) untuk ditampilkan
    LaunchedEffect(Unit) {
        runCatching {
            scopes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GitHubApi.fetchTokenScopes(Store.token.value)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppHeader(
            title = "Pengaturan",
            subtitle = "Akun, tampilan, dan preferensi GitPush"
        )

        SectionLabel2("Akun")
        SettingsRow(
            icon = Icons.Filled.Person,
            title = "Profil publik",
            subtitle = "Nama, bio, perusahaan, lokasi, situs web, email publik",
            onClick = { showEditProfile = true }
        )
        SettingsRow(
            icon = Icons.Filled.AlternateEmail,
            title = "Email",
            subtitle = "Kelola alamat email akun GitHub Anda",
            onClick = { showEmails = true }
        )
        SettingsRow(
            icon = Icons.Filled.Key,
            title = "Kunci SSH",
            subtitle = "Daftar, tambah, dan hapus kunci SSH",
            tint = PurpleAccent,
            onClick = { showKeys = true }
        )
        SettingsRow(
            icon = Icons.Filled.Token,
            title = "Token akses (PAT)",
            subtitle = when {
                scopes == null -> "Memeriksa scope token…"
                scopes!!.isEmpty() -> "Token tanpa scope khusus"
                else -> "Scope: ${scopes!!.joinToString(", ")}"
            },
            tint = YellowWarn,
            trailing = {
                TextButton(onClick = { confirmSignOut = true }) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        SectionLabel2("Tampilan")
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Tema aplikasi", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ikuti sistem, gelap ala GitHub, atau terang",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    FilterChip(
                        selected = Store.themeMode.value == "system",
                        onClick = { Store.saveTheme("system") },
                        label = { Text("Sistem") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = Store.themeMode.value == "dark",
                        onClick = { Store.saveTheme("dark") },
                        label = { Text("Gelap") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = Store.themeMode.value == "light",
                        onClick = { Store.saveTheme("light") },
                        label = { Text("Terang") }
                    )
                }
            }
        }

        SectionLabel2("Commit")
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Pesan commit default", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dipakai untuk upload massal. Kosongkan agar GitPush membuat pesan otomatis (contoh: \"Tambah 5 file via GitPush\").",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = msgDraft,
                    onValueChange = { msgDraft = it },
                    label = { Text("Pesan commit") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    Store.saveDefaultMsg(msgDraft.trim())
                    Toast.makeText(ctx, "Pesan commit default tersimpan ✓", Toast.LENGTH_SHORT).show()
                }) { Text("Simpan") }
            }
        }

        SectionLabel2("Unduhan & Data")
        SettingsRow(
            icon = Icons.Filled.Download,
            title = "Lokasi file unduhan",
            subtitle = "File, folder (ZIP), dan repository (ZIP) tersimpan di Download/GitPush"
        )
        SettingsRow(
            icon = Icons.Filled.History,
            title = "Riwayat aktivitas",
            subtitle = "${Store.history.value.size} entri tersimpan lokal",
            tint = BlueAccent,
            trailing = {
                TextButton(onClick = {
                    Store.clearHistory()
                    Toast.makeText(ctx, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
                }) { Text("Bersihkan") }
            }
        )

        SectionLabel2("Tentang")
        SettingsRow(
            icon = Icons.Filled.Info,
            title = "GitPush v1.0 — Native Android",
            subtitle = "Kotlin + Jetpack Compose + Inter — murni native, tanpa webview. " +
                "Semua fitur berjalan langsung ke api.github.com memakai PAT Anda."
        )

        Spacer(Modifier.height(28.dp))
    }

    if (showEditProfile) {
        Store.user.value?.let { u ->
            EditProfileDialog(
                user = u,
                onDismiss = { showEditProfile = false },
                onSaved = {
                    showEditProfile = false
                    Toast.makeText(ctx, "Profil diperbarui ✓", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    if (showEmails) EmailsDialog(onDismiss = { showEmails = false })
    if (showKeys) KeysDialog(onDismiss = { showKeys = false })

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
