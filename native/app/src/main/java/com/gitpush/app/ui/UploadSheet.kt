package com.gitpush.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhException
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sheet "Unggah di sini" — DESAIN BARU ala aplikasi cloud (Google Drive/Dropbox):
 * tujuan unggahan = folder yang SEDANG dibuka di file browser, bukan dipilih
 * dari layar terpisah. Pengguna menjelajah ke folder tujuan → ketuk FAB →
 * pilih file/folder dari HP → file masuk TEPAT di folder itu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadHereSheet(
    owner: String,
    repo: String,
    repoFullName: String,
    branch: String,
    targetPath: String,
    onDismiss: () -> Unit,
    onNewFile: () -> Unit,
    onCreated: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    var picked by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var safScanning by remember { mutableStateOf(false) }
    var showFileManager by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }

    val upActive = UploadManager.active

    // ===== Picker SAF: file tunggal/lebih (byte dibaca saat upload, bukan sekarang) =====
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val input = uris.toList()
        if (input.isNotEmpty()) {
            scope.launch {
                var failed = 0
                val list = withContext(Dispatchers.IO) {
                    input.mapNotNull { u: Uri ->
                        runCatching {
                            ctx.contentResolver.takePersistableUriPermission(
                                u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        val name = queryDisplayName(ctx, u)
                        if (name == null) {
                            failed++
                            null
                        } else {
                            PickedFile(name, querySize(ctx, u), uri = u)
                        }
                    }
                }
                picked = mergePicked(picked, list)
                if (failed > 0) toast("$failed file tidak dapat dibaca (dilewati)")
                if (list.isNotEmpty()) toast("${list.size} file ditambahkan")
            }
        }
    }

    // ===== Picker SAF: folder penuh (pindai pohon, path relatif dipertahankan) =====
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                safScanning = true
                val scan = scanSafTree(ctx.contentResolver, uri, "")
                safScanning = false
                if (scan.files.isEmpty()) {
                    toast("Folder kosong atau tidak dapat dibaca")
                } else {
                    picked = mergePicked(picked, scan.files)
                    var m = "${scan.files.size} file dari folder ditambahkan"
                    if (scan.skipped > 0) m += " • ${scan.skipped} dilewati"
                    if (scan.truncated) m += " • dibatasi $MAX_SCAN_FILES file"
                    toast(m)
                }
            }
        }
    }

    val startUpload: () -> Unit = {
        when {
            upActive -> toast("Masih ada upload yang berjalan — tunggu sampai selesai")
            picked.isEmpty() -> toast("Pilih file atau folder dulu")
            else -> {
                val message = msg.ifBlank { "Tambah ${picked.size} file via GitPush" }
                UploadManager.start(
                    token = Store.token.value,
                    owner = owner,
                    repo = repo,
                    repoFullName = repoFullName,
                    branch = branch,
                    targetFolder = targetPath,
                    files = picked,
                    message = message,
                    resolver = ctx.contentResolver,
                    context = ctx
                )
                onDismiss() // chip progres global yang meneruskan
            }
        }
    }

    val lfsCount = picked.count { it.size > GitHubApi.LFS_THRESHOLD_BYTES }
    val pickedBytes = picked.sumOf { it.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // ===== Header =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Upload, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Unggah ke folder ini", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "$repoFullName • $branch",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // ===== Kartu tujuan — folder yang sedang dibuka =====
            Surface(
                color = GreenPrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Folder tujuan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (targetPath.isBlank()) "root ( / )" else "/$targetPath",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                "File akan masuk TEPAT di folder yang sedang Anda buka — seperti aplikasi cloud pada umumnya.",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "DARI PENYIMPANAN HP",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = GrayMuted,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih file", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih folder", fontSize = 13.sp)
                }
            }
            TextButton(onClick = { showFileManager = true }) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = BlueAccent)
                Spacer(Modifier.width(6.dp))
                Text("Telusuri File Manager HP", fontSize = 12.sp)
            }
            if (safScanning) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Memindai folder…", fontSize = 12.sp)
                }
            }

            // ===== Daftar pilihan =====
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${picked.size} file dipilih • ${formatBytes(pickedBytes)}" +
                            (if (lfsCount > 0) " • $lfsCount via LFS" else ""),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { picked = emptyList() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                        Text("Bersihkan", fontSize = 12.sp)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        picked.take(50).forEach { pf ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = GrayMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pf.path,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatBytes(pf.size) +
                                            if (pf.size > GitHubApi.LFS_THRESHOLD_BYTES) " • Git LFS otomatis" else "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { picked = picked - pf },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Hapus ${pf.path}", tint = GrayMuted, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                        if (picked.size > 50) {
                            Text(
                                "… dan ${picked.size - 50} file lainnya",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                label = { Text("Pesan commit (opsional)") },
                placeholder = { Text("Tambah ${picked.size} file via GitPush") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = startUpload,
                enabled = picked.isNotEmpty() && !upActive,
                colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (picked.isEmpty()) "Pilih file dulu" else "Unggah ${picked.size} file",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text(
                "BUAT BARU DI FOLDER INI",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = GrayMuted,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showNewFolder = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp), tint = YellowWarn)
                    Spacer(Modifier.width(6.dp))
                    Text("Folder baru", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNewFile()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("File baru", fontSize = 13.sp)
                }
            }
        }
    }

    // ===== File Manager bawaan (telusur penyimpanan HP, multi-pilih) =====
    if (showFileManager) {
        FilePickerDialog(
            baseDir = remember { defaultStorageDir() },
            onDismiss = { showFileManager = false },
            onPicked = { list ->
                showFileManager = false
                picked = mergePicked(picked, list)
                if (list.isNotEmpty()) toast("${list.size} file ditambahkan dari HP")
            }
        )
    }

    // ===== Dialog folder baru (langsung terbentuk di folder yang sedang dibuka) =====
    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!creatingFolder) showNewFolder = false },
            title = { Text("Folder baru", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column {
                    Text(
                        "Dibuat di: ${if (targetPath.isBlank()) "/" else "/$targetPath"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama folder") },
                        singleLine = true,
                        enabled = !creatingFolder
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !creatingFolder && name.isNotBlank() &&
                        !name.contains('/') && name != "." && name != "..",
                    onClick = {
                        creatingFolder = true
                        scope.launch {
                            try {
                                val dir = if (targetPath.isBlank()) name.trim() else "$targetPath/${name.trim()}"
                                val keep = "$dir/.gitkeep"
                                try {
                                    GitHubApi.putFile(
                                        Store.token.value, owner, repo, keep,
                                        "Buat folder $dir", ByteArray(0), branch, null
                                    )
                                } catch (e: GhException) {
                                    // .gitkeep sudah ada → perbarui dengan sha yang ada
                                    if (e.code == 422 || e.code == 409) {
                                        val meta = GitHubApi.fetchFileMeta(Store.token.value, owner, repo, keep, branch)
                                        GitHubApi.putFile(
                                            Store.token.value, owner, repo, keep,
                                            "Buat folder $dir", ByteArray(0), branch, meta.sha
                                        )
                                    } else throw e
                                }
                                showNewFolder = false
                                onDismiss()
                                toast("Folder \"$name\" dibuat ✓")
                                onCreated()
                            } catch (e: Exception) {
                                creatingFolder = false
                                toast("Gagal: ${GitHubApi.humanError(e)}")
                            }
                        }
                    }
                ) {
                    if (creatingFolder) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                    } else {
                        Text("Buat", color = GreenPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !creatingFolder, onClick = { showNewFolder = false }) { Text("Batal") }
            }
        )
    }

    // Jika upload dimulai dari luar sheet saat sheet terbuka → tutup sendiri
    LaunchedEffect(UploadManager.active) {
        if (UploadManager.active) onDismiss()
    }
}
