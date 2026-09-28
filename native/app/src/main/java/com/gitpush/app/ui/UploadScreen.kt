package com.gitpush.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import com.gitpush.app.data.GhBranch
import com.gitpush.app.data.GhRepo
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun queryDisplayName(ctx: Context, uri: Uri): String? {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0) ?: uri.lastPathSegment?.substringAfterLast('/')
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

private suspend fun walkTree(
    ctx: Context, df: DocumentFile, rel: String, out: MutableList<PickedFile>
): MutableList<PickedFile> = withContext(Dispatchers.IO) {
    for (f in df.listFiles()) {
        val name = f.name
        if (f.isDirectory) {
            walkTree(ctx, f, if (rel.isEmpty()) (name ?: "") else "$rel/$name", out)
        } else if (name != null) {
            val bytes = runCatching {
                ctx.contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                out.add(PickedFile(if (rel.isEmpty()) name else "$rel/$name", bytes.size.toLong(), bytes))
            }
        }
    }
    out
}

@Composable
fun UploadScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    var selected by remember { mutableStateOf<GhRepo?>(null) }
    var showRepoPicker by remember { mutableStateOf(false) }
    var loadingRepos by remember { mutableStateOf(Store.repos.value.isEmpty()) }

    var branches by remember { mutableStateOf<List<GhBranch>>(emptyList()) }
    var branch by remember { mutableStateOf("main") }
    var showBranchMenu by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf("") }

    var picked by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var doneSha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (Store.repos.value.isEmpty()) {
            runCatching {
                Store.repos.value = withContext(Dispatchers.IO) {
                    GitHubApi.fetchRepos(Store.token.value)
                }
            }
            loadingRepos = false
        }
    }

    LaunchedEffect(selected?.id) {
        val r = selected ?: return@LaunchedEffect
        branch = r.defaultBranch
        runCatching {
            branches = withContext(Dispatchers.IO) {
                GitHubApi.fetchBranches(Store.token.value, r.owner, r.name)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val input: List<Uri> = uris.toList()
        if (input.isNotEmpty()) {
            scope.launch {
                val list = withContext(Dispatchers.IO) {
                    input.mapNotNull { u: Uri ->
                        val name = queryDisplayName(ctx, u) ?: return@mapNotNull null
                        val bytes = runCatching {
                            ctx.contentResolver.openInputStream(u)?.use { it.readBytes() }
                        }.getOrNull() ?: return@mapNotNull null
                        PickedFile(name, bytes.size.toLong(), bytes)
                    }
                }
                picked = picked + list
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val tree = DocumentFile.fromTreeUri(ctx, uri)
                if (tree == null) {
                    toast("Tidak bisa membuka folder")
                    return@launch
                }
                val list = walkTree(ctx, tree, "", mutableListOf())
                if (list.isEmpty()) {
                    toast("Folder kosong")
                } else {
                    picked = picked + list
                    toast("${list.size} file dari folder ditambahkan")
                }
            }
        }
    }

    val startUpload: () -> Unit = {
        val r = selected
        if (r == null) {
            toast("Pilih repository dulu")
        } else if (picked.isEmpty()) {
            toast("Pilih file atau folder dulu")
        } else if (picked.sumOf { it.size } > 95L * 1024 * 1024) {
            toast("Total ukuran melebihi 95 MB — kecilkan pilihan file")
        } else {
            val message = msg.ifBlank { "Tambah ${picked.size} file via GitPush" }
            busy = true
            doneSha = null
            progress = Triple("Menyiapkan commit", 0, picked.size)
            scope.launch {
                try {
                    val targetFolder = folder.trim().trimStart('/').trimEnd('/')
                    val files = if (targetFolder.isEmpty()) picked else picked.map {
                        PickedFile("$targetFolder/${it.path}", it.size, it.bytes)
                    }
                    val sha = withContext(Dispatchers.IO) {
                        GitHubApi.bulkUpload(
                            Store.token.value, r.owner, r.name, branch.trim(), files, message
                        ) { stage, d, t -> progress = Triple(stage, d, t) }
                    }
                    doneSha = sha
                    Store.log("upload", "Tambah ${picked.size} file dalam 1 commit", r.fullName)
                    Toast.makeText(
                        ctx, "Upload berhasil! Commit ${sha.take(7)}", Toast.LENGTH_LONG
                    ).show()
                    picked = emptyList()
                } catch (e: Exception) {
                    toast("Gagal: ${GitHubApi.humanError(e)}")
                } finally {
                    busy = false
                    progress = null
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Upload Massal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Banyak file/folder, tetap 1 commit — via Git Data API",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        ResponsiveBox {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                OutlinedButton(
                    onClick = { showRepoPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected?.fullName ?: "Pilih repository…",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selected != null) {
                            Text(
                                if (selected!!.isPrivate) "Private" else "Public",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Ganti repository")
                }

                if (selected != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showBranchMenu = true }, modifier = Modifier.weight(1f)) {
                            Text(branch, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pilih branch")
                        }
                        DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                            branches.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.name) },
                                    onClick = { showBranchMenu = false; branch = b.name }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Folder tujuan (opsional)") },
                        placeholder = { Text("contoh: src/lib") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih File")
                    }
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih Folder")
                    }
                }

                if (picked.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${picked.size} file dipilih • ${formatBytes(picked.sumOf { it.size })}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { picked = emptyList() }) { Text("Bersihkan", fontSize = 12.sp) }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            picked.take(50).forEach { pf ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = GrayMuted,
                                        modifier = Modifier.size(16.dp)
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
                                            formatBytes(pf.size),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    IconButton(onClick = { picked = picked - pf }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Hapus ${pf.path}", tint = GrayMuted, modifier = Modifier.size(14.dp))
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

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    label = { Text("Pesan commit") },
                    placeholder = { Text("Tambah ${picked.size} file via GitPush") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (busy) {
                    Spacer(Modifier.height(14.dp))
                    val (stage, d, t) = progress ?: Triple("", 0, 0)
                    Text(stage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (t > 0) {
                        LinearProgressIndicator(
                            progress = { d.toFloat() / t },
                            modifier = Modifier.fillMaxWidth(),
                            color = GreenPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$d / $t", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                    }
                }

                if (doneSha != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Upload berhasil ✓", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Commit ${doneSha?.take(7)} — semua file masuk dalam satu commit. Buka repository lewat tab Beranda untuk melihat hasilnya.",
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = startUpload,
                    enabled = !busy && selected != null && picked.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (picked.isEmpty()) "Commit ke repository" else "Commit ${picked.size} file (1 commit)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showRepoPicker) {
        RepoPickerDialog(
            loading = loadingRepos,
            onDismiss = { showRepoPicker = false },
            onPick = {
                selected = it
                showRepoPicker = false
            }
        )
    }
}

@Composable
private fun RepoPickerDialog(onDismiss: () -> Unit, onPick: (GhRepo) -> Unit, loading: Boolean) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) Store.repos.value else Store.repos.value.filter {
        it.name.lowercase().contains(q) || it.fullName.lowercase().contains(q)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Pilih repository", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filtered, key = { it.id }) { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onPick(r) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                        Text(
                                            r.fullName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!r.description.isNullOrBlank()) {
                                            Text(
                                                r.description!!,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "Tidak ada repository",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

