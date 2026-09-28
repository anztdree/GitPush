package com.gitpush.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhFileContent
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ViewerScreen(s: Screen.Viewer) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var meta by remember { mutableStateOf<GhFileContent?>(null) }
    var raw by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // (oid, ukuran asli) bila file ternyata pointer Git LFS
    var lfsInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(s.sha) {
        try {
            val m = withContext(Dispatchers.IO) {
                GitHubApi.fetchFileMeta(Store.token.value, s.owner, s.name, s.path, s.branch)
            }
            meta = m
            val rawBytes = if (m.contentB64 != null) {
                Base64.decode(m.contentB64, Base64.DEFAULT)
            } else {
                withContext(Dispatchers.IO) {
                    GitHubApi.fetchBlobBytes(Store.token.value, s.owner, s.name, s.sha)
                }
            }
            val ptr = if (rawBytes.size <= 2048) GitHubApi.lfsPointerInfo(String(rawBytes)) else null
            if (ptr != null) {
                lfsInfo = ptr
                bytes = null
            } else {
                lfsInfo = null
                bytes = rawBytes
            }
        } catch (e: Exception) {
            error = GitHubApi.humanError(e)
        } finally {
            loading = false
        }
    }

    val fileName = s.path.substringAfterLast('/')

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Store.pop() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${s.branch} • ${formatBytes(lfsInfo?.second ?: bytes?.size?.toLong() ?: s.size)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = {
                scope.launch {
                    Store.showOp("Mengunduh file", fileName)
                    try {
                        val node = com.gitpush.app.data.GhNode(fileName, s.path, "file", s.size, s.sha)
                        val loc = withContext(Dispatchers.IO) {
                            GitHubApi.downloadFile(
                                ctx, Store.token.value, s.owner, s.name, node, s.branch,
                                onStage = { Store.opDetail(it) },
                                onProgress = { sent, total -> Store.opProgress(sent, total) }
                            )
                        }
                        Store.log("download", "Download $fileName", "${s.owner}/${s.name}")
                        toast("Tersimpan: $loc")
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    } finally {
                        Store.hideOp()
                    }
                }
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh file", tint = GreenPrimary)
            }
        }

        when {
            loading -> Loading()
            error != null -> ErrorCard(error!!) {
                loading = true
                error = null
                scope.launch {
                    try {
                        bytes = withContext(Dispatchers.IO) {
                            GitHubApi.fetchBlobBytes(Store.token.value, s.owner, s.name, s.sha)
                        }
                    } catch (e2: Exception) {
                        error = GitHubApi.humanError(e2)
                    } finally {
                        loading = false
                    }
                }
            }
            else -> {
                val b = bytes ?: ByteArray(0)
                when {
                    lfsInfo != null -> LfsInfoCard(
                        fileName = fileName,
                        size = lfsInfo!!.second,
                        onDownload = {
                            scope.launch {
                                Store.showOp("Mengunduh file (Git LFS)", fileName)
                                try {
                                    val node = com.gitpush.app.data.GhNode(fileName, s.path, "file", lfsInfo?.second ?: s.size, s.sha)
                                    val loc = withContext(Dispatchers.IO) {
                                        GitHubApi.downloadFile(
                                            ctx, Store.token.value, s.owner, s.name, node, s.branch,
                                            onStage = { Store.opDetail(it) },
                                            onProgress = { sent, total -> Store.opProgress(sent, total) }
                                        )
                                    }
                                    Store.log("download", "Download $fileName", "${s.owner}/${s.name}")
                                    toast("Tersimpan: $loc")
                                } catch (e: Exception) {
                                    toast("Gagal: ${GitHubApi.humanError(e)}")
                                } finally {
                                    Store.hideOp()
                                }
                            }
                        }
                    )
                    isImageFile(fileName) -> {
                        val bmp = remember(b) {
                            runCatching { BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull()
                        }
                        if (bmp != null) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = fileName,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            BinaryInfo(fileName, s)
                        }
                    }
                    isTextFile(fileName) -> {
                        val text = String(b)
                        val isMd = fileName.lowercase().endsWith(".md") || fileName.lowercase().endsWith(".markdown")
                        Column(Modifier.fillMaxSize()) {
                            if (isMd) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !raw,
                                        onClick = { raw = false },
                                        label = { Text("Tampilan") }
                                    )
                                    FilterChip(
                                        selected = raw,
                                        onClick = { raw = true },
                                        label = { Text("Mentah") }
                                    )
                                }
                                if (raw) RawText(text) else {
                                    ResponsiveBox {
                                        Column(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                                        ) {
                                            MarkdownText(text)
                                            Spacer(Modifier.height(24.dp))
                                        }
                                    }
                                }
                            } else {
                                RawText(text)
                            }
                        }
                    }
                    else -> BinaryInfo(fileName, s)
                }
            }
        }

        if (!loading && error == null && lfsInfo == null && isTextFile(fileName)) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val text = String(bytes ?: ByteArray(0))
                        clipboard.setText(AnnotatedString(text))
                        toast("Isi file disalin")
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Salin")
                    }
                    OutlinedButton(onClick = {
                        val m = meta
                        val text = String(bytes ?: ByteArray(0))
                        if ((m?.contentB64 == null && bytes?.size ?: 0 > 1024 * 1024) ||
                            (bytes?.size ?: 0) > 1024 * 1024
                        ) {
                            toast("File terlalu besar untuk diedit (maks 1 MB)")
                        } else {
                            Store.push(
                                Screen.Editor(
                                    owner = s.owner, name = s.name, branch = s.branch,
                                    basePath = s.path.substringBeforeLast('/', ""),
                                    path = s.path, initial = text, sha = m?.sha ?: s.sha
                                )
                            )
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Hapus")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus $fileName?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Tindakan ini membuat satu commit dan tidak bisa dibatalkan dari GitPush.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            val m = meta
                            if (m == null) {
                                toast("Metadata file belum siap — coba lagi")
                                return@launch
                            }
                            withContext(Dispatchers.IO) {
                                GitHubApi.deleteFile(
                                    Store.token.value, s.owner, s.name, s.path,
                                    "Hapus ${s.path.substringAfterLast('/')} via GitPush",
                                    m.sha, s.branch
                                )
                            }
                            Store.log("delete", "Hapus $fileName", "${s.owner}/${s.name}")
                            toast("File dihapus ✓")
                            Store.pop()
                        } catch (e: Exception) {
                            toast("Gagal: ${GitHubApi.humanError(e)}")
                        }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun RawText(text: String) {
    val lines = text.lines().take(3000)
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(lines) { i, line ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    (i + 1).toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp).padding(top = 3.dp)
                )
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).padding(top = 2.dp)
                )
            }
        }
        if (text.lines().size > 3000) {
            item {
                Text(
                    "… file terlalu panjang, hanya 3000 baris pertama ditampilkan. Gunakan Unduh untuk melihat lengkap.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BinaryInfo(fileName: String, s: Screen.Viewer) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = GrayMuted,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(fileName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(
            "Pratinjau tidak tersedia untuk file biner.\nGunakan tombol unduh untuk menyimpannya.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun EditorScreen(s: Screen.Editor) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    val isCreate = s.path == null
    var fileName by remember { mutableStateOf(s.path?.substringAfterLast('/') ?: "") }
    var content by remember { mutableStateOf(s.initial) }
    var msg by remember {
        mutableStateOf(
            if (isCreate) "Tambah file via GitPush" else "Update ${s.path?.substringAfterLast('/')} via GitPush"
        )
    }
    var busy by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    BackHandlerDiscard(enabled = !busy && content != s.initial) { confirmDiscard = true }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (content != s.initial) confirmDiscard = true else Store.pop() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isCreate) "Buat file baru" else "Edit file",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "${s.owner}/${s.name} • ${s.branch}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isCreate) {
                if (s.basePath.isNotEmpty()) {
                    Text(
                        s.basePath + "/",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nama file (boleh dengan subfolder)") },
                    placeholder = { Text("contoh: src/index.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Isi file") },
                modifier = Modifier.fillMaxWidth().height(340.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${content.length} karakter • ${content.lines().size} baris • ${formatBytes(content.toByteArray().size.toLong())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                label = { Text("Pesan commit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val path = if (isCreate) {
                        val base = if (s.basePath.isEmpty()) "" else s.basePath + "/"
                        (base + fileName.trim()).trimStart('/')
                    } else s.path!!
                    val tooBig = content.toByteArray().size > 1024 * 1024
                    when {
                        isCreate && fileName.trim().isEmpty() ->
                            toast("Nama file tidak boleh kosong")
                        tooBig ->
                            toast("Ukuran isi melebihi 1 MB — pecah file atau gunakan upload massal")
                        else -> {
                            busy = true
                            scope.launch {
                                try {
                                    val sha = withContext(Dispatchers.IO) {
                                        if (isCreate) {
                                            GitHubApi.putFile(
                                                Store.token.value, s.owner, s.name, path,
                                                msg.trim(), content.toByteArray(), s.branch, null
                                            )
                                        } else {
                                            val m = GitHubApi.fetchFileMeta(
                                                Store.token.value, s.owner, s.name, s.path!!, s.branch
                                            )
                                            GitHubApi.putFile(
                                                Store.token.value, s.owner, s.name, path,
                                                msg.trim(), content.toByteArray(), s.branch, m.sha
                                            )
                                        }
                                    }
                                    Store.log(
                                        if (isCreate) "create" else "edit",
                                        (if (isCreate) "Buat " else "Edit ") + path.substringAfterLast('/'),
                                        "${s.owner}/${s.name}"
                                    )
                                    GitHubApi.invalidateUsage(s.owner, s.name) // kuota ikut berubah
                                    toast("Commit ${sha.take(7)} ✓")
                                    Store.pop()
                                } catch (e: Exception) {
                                    toast("Gagal: ${GitHubApi.humanError(e)}")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (isCreate) "Commit file baru" else "Commit perubahan", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Buang perubahan?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Perubahan Anda belum di-commit dan akan hilang.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    Store.pop()
                }) { Text("Buang", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Lanjut mengedit") }
            }
        )
    }
}

@Composable
private fun BackHandlerDiscard(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

/** Kartu info file Git LFS — isi asli tidak dirender, sediakan unduh konten asli. */
@Composable
private fun LfsInfoCard(fileName: String, size: Long, onDownload: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.padding(18.dp).size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("File Git LFS", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "$fileName • ${formatBytes(size)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "File ini tersimpan sebagai objek Git LFS sehingga isinya tidak bisa " +
                "dipratinjau langsung. Ukuran di atas adalah ukuran asli file. " +
                "Unduh untuk mendapatkan isi lengkapnya ke folder Download/GitPush.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onDownload,
            colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text("Unduh file asli (${formatBytes(size)})", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
    }
}
