package com.gitpush.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private fun querySize(ctx: Context, uri: Uri): Long {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
    }
    return 0L
}

/** Gabungkan pilihan baru; path yang sama ditimpa, tidak diduplikasi */
private fun mergePicked(old: List<PickedFile>, add: List<PickedFile>): List<PickedFile> {
    if (add.isEmpty()) return old
    val addPaths = add.map { it.path }.toSet()
    return old.filterNot { it.path in addPaths } + add
}

private data class SafEntry(val docId: String, val name: String, val mime: String?, val size: Long)
private data class SafScan(val files: List<PickedFile>, val skipped: Int, val truncated: Boolean)

/** List isi folder SAF dalam SATU query — cepat & lengkap (pengganti DocumentFile.listFiles) */
private fun safChildren(resolver: android.content.ContentResolver, treeUri: Uri, docId: String): List<SafEntry> {
    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val proj = arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
        android.provider.DocumentsContract.Document.COLUMN_SIZE
    )
    val out = mutableListOf<SafEntry>()
    runCatching {
        resolver.query(childrenUri, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: id.substringAfterLast(':').ifEmpty { id }
                out.add(SafEntry(id, name, c.getString(2), c.getLong(3)))
            }
        }
    }
    return out
}

/**
 * Pindai pohon folder SAF (BFS) — byte TIDAK dibaca, hanya metadata.
 * Semua file tercatat; folder yang tak bisa dibaca dihitung sebagai "skipped".
 */
private suspend fun scanSafTree(
    resolver: android.content.ContentResolver, treeUri: Uri, baseRel: String
): SafScan = withContext(Dispatchers.IO) {
    val files = mutableListOf<PickedFile>()
    var skipped = 0
    val dirs = ArrayDeque<Pair<String, String>>() // docId to relative path
    dirs.add(android.provider.DocumentsContract.getTreeDocumentId(treeUri) to baseRel)
    while (dirs.isNotEmpty() && files.size < MAX_SCAN_FILES) {
        val (docId, rel) = dirs.removeFirst()
        for (ch in safChildren(resolver, treeUri, docId)) {
            val childRel = if (rel.isEmpty()) ch.name else "$rel/${ch.name}"
            when {
                ch.mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR -> {
                    if (files.size + dirs.size < MAX_SCAN_FILES) dirs.add(ch.docId to childRel) else skipped++
                }
                ch.mime == null || !ch.mime.startsWith("vnd.android.document") -> files.add(
                    PickedFile(
                        childRel, ch.size,
                        uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, ch.docId)
                    )
                )
                else -> skipped++
            }
        }
    }
    SafScan(files, skipped, files.size >= MAX_SCAN_FILES)
}

private fun formatEta(sec: Long): String {
    if (sec <= 0) return "—"
    if (sec > 3600 * 24) return "±${sec / 3600} jam"
    val m = sec / 60
    val s = sec % 60
    return when {
        m >= 60 -> "±${m / 60} j ${m % 60} m"
        m > 0 -> "±$m m ${s} d"
        else -> "±${s} d"
    }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0) return "—"
    val s = ms / 1000
    val m = s / 60
    return when {
        m >= 60 -> "${m / 60} j ${(m % 60)} m"
        m > 0 -> "$m m ${s % 60} d"
        else -> "$s d"
    }
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
    var repoUsedBytes by remember { mutableStateOf(0L) }

    var showBrowser by remember { mutableStateOf(false) }
    var safScanning by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(hasAllFilesAccess(ctx)) }
    var confirmCancel by remember { mutableStateOf(false) }

    val upActive = UploadManager.active
    val upPhase = UploadManager.phase

    // Layar tetap menyala selama upload berjalan
    val activity = ctx as? Activity
    DisposableEffect(upActive) {
        if (upActive) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (!UploadManager.active) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Berhasil → bersihkan daftar pilihan; gagal → biarkan untuk diulang
    LaunchedEffect(upPhase) {
        if (upPhase == "done") {
            picked = emptyList()
            Toast.makeText(ctx, "Upload selesai ✓", Toast.LENGTH_LONG).show()
            // Segarkan pemakaian kuota repository (riil — termasuk Git LFS)
            selected?.let { r ->
                runCatching {
                    repoUsedBytes = GitHubApi.fetchRepoUsage(
                        Store.token.value, r.owner, r.name, branch
                    )
                }
            }
        }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { storageGranted = hasAllFilesAccess(ctx) }

    // Perbarui status izin saat kembali dari pengaturan
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) storageGranted = hasAllFilesAccess(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

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
        // Pemakaian riil (termasuk LFS): pakai cache bila ada, lalu hitung ulang segar
        repoUsedBytes = GitHubApi.cachedUsage(r.owner, r.name, r.defaultBranch) ?: 0L
        scope.launch {
            runCatching {
                repoUsedBytes = GitHubApi.fetchRepoUsage(
                    Store.token.value, r.owner, r.name, r.defaultBranch, force = true
                )
            }
        }
        runCatching {
            branches = withContext(Dispatchers.IO) {
                GitHubApi.fetchBranches(Store.token.value, r.owner, r.name)
            }
        }
    }

    // ===== Picker SAF: file tunggal/lebih (byte dibaca saat upload, bukan sekarang) =====
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val input: List<Uri> = uris.toList()
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

    // ===== Picker SAF: folder penuh (scanner cepat via DocumentsContract) =====
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
        val r = selected
        val pickedBytes = picked.sumOf { it.size }
        when {
            upActive -> { }
            r == null -> toast("Pilih repository dulu")
            picked.isEmpty() -> toast("Pilih file atau folder dulu")
            repoUsedBytes + pickedBytes > REPO_QUOTA_BYTES -> toast(
                "Melebihi kuota 2 GB per repository — terpakai ${formatBytes(repoUsedBytes)}, " +
                    "pilihan ${formatBytes(pickedBytes)}, sisa kuota ${formatBytes((REPO_QUOTA_BYTES - repoUsedBytes).coerceAtLeast(0))}"
            )
            else -> {
                val message = msg.ifBlank { "Tambah ${picked.size} file via GitPush" }
                UploadManager.start(
                    token = Store.token.value,
                    owner = r.owner,
                    repo = r.name,
                    repoFullName = r.fullName,
                    branch = branch.trim(),
                    targetFolder = folder,
                    files = picked,
                    message = message,
                    resolver = ctx.contentResolver
                )
            }
        }
    }

    val lfsCount = picked.count { it.size > GitHubApi.LFS_THRESHOLD_BYTES }
    val pickedBytes = picked.sumOf { it.size }
    val overQuota = repoUsedBytes + pickedBytes > REPO_QUOTA_BYTES
    val showForm = !upActive

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Upload Massal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Banyak file/folder, tetap 1 commit — tanpa batas ukuran",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        ResponsiveBox {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // ===== PANEL PROGRES (tampil saat upload aktif / hasil belum ditutup) =====
                if (UploadManager.showPanel) {
                    UploadProgressPanel(
                        onAskCancel = { confirmCancel = true },
                        onOpenCommit = { url ->
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                }

                if (showForm) {
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
                        Spacer(Modifier.height(10.dp))
                        // ===== Kuota penyimpanan ala penyimpanan awan (2 GB / repo) =====
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                QuotaBar(usedBytes = repoUsedBytes, extraBytes = pickedBytes, compact = true)
                                if (overQuota) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Melebihi kuota 2 GB — kurangi file yang dipilih.",
                                        color = RedDanger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ===== Banner izin penyimpanan =====
                    if (!storageGranted) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = GrayMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Folder di HP belum terbaca sempurna. Beri izin \u201CSemua file\u201D " +
                                        "agar File Manager menampilkan semua folder & file.",
                                    Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                TextButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            openAllFilesSettings(ctx)
                                        } else {
                                            writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        }
                                    }
                                ) { Text("Beri Izin", fontSize = 12.sp) }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    // ===== Tombol utama: File Manager bawaan =====
                    Button(
                        onClick = { showBrowser = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Buka File Manager (pilih banyak file sekaligus)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pilih File", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pilih Folder", fontSize = 13.sp)
                        }
                    }
                    Text(
                        "File Manager GitPush: ketuk file untuk memilih banyak sekaligus, tekan-lama folder untuk " +
                            "mengambil seluruh isinya. Pilih File/Folder memakai penyimpanan sistem (SAF). " +
                            "Path di repository otomatis relatif — folder HP tidak ikut ter-upload.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    if (safScanning) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Memindai folder…", fontSize = 12.sp)
                        }
                    }

                    if (picked.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${picked.size} file dipilih • ${formatBytes(picked.sumOf { it.size })}" +
                                    (if (lfsCount > 0) " • $lfsCount via LFS" else ""),
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
                                                formatBytes(pf.size) +
                                                    if (pf.size > GitHubApi.LFS_THRESHOLD_BYTES) " • Git LFS otomatis" else "",
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

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = startUpload,
                        enabled = !upActive && selected != null && picked.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (upActive) {
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
    }

    if (showBrowser) {
        FilePickerDialog(
            baseDir = remember { defaultStorageDir() },
            onDismiss = { showBrowser = false },
            onPicked = { list ->
                showBrowser = false
                picked = mergePicked(picked, list)
                if (list.isNotEmpty()) toast("${list.size} file ditambahkan dari HP")
            }
        )
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

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Batalkan upload?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("File yang sudah terkirim belum di-commit, jadi tidak ada perubahan di repository.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    UploadManager.requestCancel()
                }) { Text("Batalkan upload", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Lanjutkan upload") }
            }
        )
    }
}

// ==================== PANEL PROGRES UPLOAD ====================

@Composable
private fun UploadProgressPanel(onAskCancel: () -> Unit, onOpenCommit: (String) -> Unit) {
    val phase = UploadManager.phase
    val skipped = UploadManager.skipped
    var showAllSkipped by remember { mutableStateOf(false) }

    val (title, titleColor) = when (phase) {
        "done" -> "Upload selesai ✓" to GreenPrimary
        "error" -> "Upload gagal" to MaterialTheme.colorScheme.error
        "cancel" -> "Upload dibatalkan" to GrayMuted
        else -> "Sedang upload…" to MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            // ===== Judul =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (phase) {
                    "done" -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(22.dp))
                    "error" -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    "cancel" -> Icon(Icons.Filled.Cancel, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(22.dp))
                    else -> CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = titleColor)
                    Text(
                        "${UploadManager.repoFull} • ${UploadManager.branch}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // ===== Stepper tahap =====
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepItem("Persiapan", stageState(phase, 0))
                StepItem("Unggah", stageState(phase, 1))
                StepItem("Commit", stageState(phase, 2))
                StepItem("Selesai", stageState(phase, 3))
            }

            // ===== Progres keseluruhan (byte) =====
            if (phase == "prepare" || phase == "upload" || phase == "commit") {
                Spacer(Modifier.height(14.dp))
                val progressBytes = UploadManager.progressBytes()
                val total = UploadManager.bytesTotal
                if (phase == "commit") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = GreenPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(UploadManager.stage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else if (total > 0) {
                    val frac = (progressBytes.toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = GreenPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatBytes(progressBytes)} / ${formatBytes(total)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${(frac * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val speed = UploadManager.speedBps
                    val remaining = (total - progressBytes).coerceAtLeast(0)
                    val eta = if (speed > 0) remaining / speed else -1L
                    Text(
                        buildString {
                            append("File ${UploadManager.filesDone}/${UploadManager.filesTotal}")
                            if (speed > 0) append(" • ${formatBytes(speed)}/s")
                            if (eta > 0) append(" • sisa ${formatEta(eta)}")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = GreenPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Menganalisis ${UploadManager.filesTotal} file…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ===== File yang sedang dikirim =====
                if (phase == "upload" && UploadManager.currentFile.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sekarang: ${UploadManager.currentFile}",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val cur = UploadManager.currentTotal
                    if (cur > 0) {
                        val cf = (UploadManager.currentSent.toFloat() / cur).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { cf },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                            color = BlueAccent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "${formatBytes(UploadManager.currentSent)} / ${formatBytes(cur)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                            color = BlueAccent
                        )
                    }
                }
            }

            // ===== Ringkasan sukses =====
            if (phase == "done") {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${UploadManager.filesDone} file masuk dalam 1 commit • durasi ${formatElapsed(UploadManager.elapsedMs)}",
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                UploadManager.commitSha?.let { sha ->
                    Text(
                        "Commit ${sha.take(10)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onOpenCommit("https://github.com/${UploadManager.repoFull}/commit/$sha")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buka commit di GitHub", fontSize = 13.sp)
                    }
                }
            }

            // ===== Pesan gagal total =====
            if (phase == "error") {
                Spacer(Modifier.height(12.dp))
                Text(
                    UploadManager.error ?: "Terjadi kesalahan",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (phase == "cancel") {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Proses dihentikan — belum ada commit yang dibuat. Pilihan file tetap tersimpan.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== Daftar file gagal/dilewati =====
            if (skipped.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "⚠ ${skipped.size} file dilewati/gagal:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val shown = if (showAllSkipped) skipped else skipped.take(3)
                        shown.forEach { (p, r) ->
                            Text(
                                "• $p — $r",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        if (skipped.size > 3 && !showAllSkipped) {
                            TextButton(
                                onClick = { showAllSkipped = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) { Text("Lihat semua (${skipped.size})", fontSize = 11.sp) }
                        }
                    }
                }
            }

            // ===== Tombol aksi =====
            Spacer(Modifier.height(12.dp))
            when (phase) {
                "done", "error", "cancel" -> {
                    TextButton(
                        onClick = { UploadManager.dismiss() },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Tutup") }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Boleh pindah tab — upload tetap berjalan",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onAskCancel) {
                            Text("Batalkan", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun stageState(phase: String, idx: Int): String {
    val order = when (phase) {
        "prepare" -> 0
        "upload" -> 1
        "commit" -> 2
        "done" -> 4
        else -> 1
    }
    return when {
        phase == "error" || phase == "cancel" -> if (idx < order) "done" else "pending"
        idx < order -> "done"
        idx == order -> "active"
        else -> "pending"
    }
}

@Composable
private fun StepItem(label: String, state: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            "done" -> Icon(Icons.Filled.CheckCircle, contentDescription = label, tint = GreenPrimary, modifier = Modifier.size(20.dp))
            "active" -> CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = label, tint = GrayMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (state == "pending") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (state == "active") FontWeight.SemiBold else FontWeight.Normal
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
