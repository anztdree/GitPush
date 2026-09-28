package com.gitpush.app.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var msgDraft by remember { mutableStateOf(Store.defaultMsg.value) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Pengaturan",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Text(
            "Preferensi disimpan di perangkat ini",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )

        SectionLabel("Tampilan")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Tema aplikasi", fontSize = 14.sp)
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

        SectionLabel("Commit")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Pesan commit default", fontSize = 14.sp)
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

        SectionLabel("Unduhan")
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Lokasi file unduhan", fontSize = 14.sp)
                    Text(
                        "File, folder (ZIP), dan repository (ZIP) tersimpan di Download/GitPush",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        SectionLabel("Data")
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Riwayat aktivitas", fontSize = 14.sp)
                    Text(
                        "${Store.history.value.size} entri tersimpan lokal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = {
                    Store.clearHistory()
                    Toast.makeText(ctx, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
                }) { Text("Bersihkan") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Token,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Token akses", fontSize = 14.sp)
                    Text(
                        "Hanya tersimpan di perangkat ini, dikirim langsung ke api.github.com",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                TextButton(onClick = { confirmSignOut = true }) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SectionLabel("Tentang")
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("GitPush v1.3 — Native Android", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Kotlin + Jetpack Compose • Git Data API • OkHttp — murni native, tanpa webview.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
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
