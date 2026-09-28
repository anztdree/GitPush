package com.gitpush.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gitpush.app.data.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Batas aman pemindaian folder rekursif */
const val MAX_SCAN_FILES = 10000

/** Apakah aplikasi boleh membaca seluruh penyimpanan (semua file) */
fun hasAllFilesAccess(ctx: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    else -> ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

/** Buka halaman pengaturan "Semua file" (Android 11+) */
fun openAllFilesSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val i = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${ctx.packageName}")
    )
    runCatching { ctx.startActivity(i) }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}

/** Direktori awal File Manager (penyimpanan internal) */
@Suppress("DEPRECATION")
fun defaultStorageDir(): File =
    Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")

/** Pintasan root penyimpanan: internal, folder umum, dan SD card */
private fun quickRoots(): List<Pair<String, File>> {
    val primary = defaultStorageDir()
    val roots = mutableListOf("Internal" to primary)
    primary?.let { p ->
        listOf("Download" to File(p, "Download"), "Documents" to File(p, "Documents"), "DCIM" to File(p, "DCIM"))
            .filter { it.second.isDirectory }
            .forEach { roots.add(it) }
    }
    runCatching {
        File("/storage").listFiles()?.filter {
            it.isDirectory && it.absolutePath != "/storage/emulated" &&
                it.absolutePath != "/storage/self" && it.absolutePath != primary?.absolutePath
        }?.forEach { roots.add("SD: ${it.name}" to it) }
    }
    return roots
}

/**
 * Pindai folder secara rekursif (BFS) langsung dari filesystem — cepat & lengkap.
 * Mengembalikan (jumlah file, jumlah dilewati, daftar file dengan path relatif terhadap root).
 */
private fun scanFolderRecursive(root: File): Triple<Int, Int, List<PickedFile>> {
    val out = mutableListOf<PickedFile>()
    var skipped = 0
    val base = root.absolutePath.trimEnd('/')
    val queue = ArrayDeque<File>().apply { add(root) }
    while (queue.isNotEmpty() && out.size < MAX_SCAN_FILES) {
        val dir = queue.removeFirst()
        val children = runCatching { dir.listFiles() }.getOrNull()
        if (children == null) {
            skipped++
            continue
        }
        for (c in children.sortedBy { it.name.lowercase() }) {
            when {
                c.isDirectory -> queue.add(c)
                c.isFile && out.size < MAX_SCAN_FILES -> {
                    val rel = c.absolutePath.removePrefix(base).trimStart('/').ifEmpty { c.name }
                    out.add(PickedFile(rel, c.length(), file = c))
                }
                else -> skipped++
            }
        }
    }
    return Triple(out.size, skipped, out)
}

/**
 * Petakan path file terpilih RELATIF terhadap folder leluhur bersama mereka.
 * Contoh: memilih "Download/Proyek/a.txt" saja → di-commit sebagai "a.txt"
 * (tanpa folder sumber ikut terbawa). Bila beberapa subfolder dipilih,
 * struktur di bawah leluhur bersama tetap dipertahankan.
 */
private fun mapRelative(batch: List<PickedFile>): List<PickedFile> {
    if (batch.isEmpty()) return batch
    if (batch.any { it.file == null }) return batch // sumber SAF/byte — path sudah relatif
    if (batch.size == 1) return listOf(batch[0].copy(path = batch[0].file!!.name))
    val paths = batch.map { it.file!!.absolutePath }
    var prefix = paths[0].substringBeforeLast('/', "")
    for (p in paths) {
        while (prefix.isNotEmpty() && !p.startsWith("$prefix/")) {
            prefix = prefix.substringBeforeLast('/', "")
        }
        if (prefix.isEmpty()) break
    }
    return batch.map { pf ->
        val abs = pf.file!!.absolutePath
        val rel = if (prefix.isEmpty()) abs.substringAfterLast('/') else abs.removePrefix("$prefix/").trimStart('/')
        pf.copy(path = rel.ifEmpty { abs.substringAfterLast('/') })
    }
}

/**
 * File Manager bawaan GitPush — menampilkan direktori/file HP apa adanya
 * (termasuk file tersembunyi) lewat akses penyimpanan penuh.
 * - Ketuk file: pilih/batal. Tekan-lama file: sama (multi-pilih cepat).
 * - Checkbox/tekan-lama folder: pilih SELURUH isi folder (nama folder ikut sebagai prefix).
 * - "Pilih semua": semua file di folder yang sedang dibuka.
 * - "Pilih Folder Ini": seluruh isi folder ini TANPA nama folder (relatif).
 * File dikembalikan sebagai PickedFile(file=…) tanpa membaca byte ke RAM.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePickerDialog(
    baseDir: File,
    onDismiss: () -> Unit,
    onPicked: (List<PickedFile>) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    var granted by remember { mutableStateOf(hasAllFilesAccess(ctx)) }
    var cur by remember { mutableStateOf(baseDir) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selFiles by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var selFolders by remember { mutableStateOf<Map<String, List<PickedFile>>>(emptyMap()) }
    var scanning by remember { mutableStateOf<String?>(null) }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = hasAllFilesAccess(ctx) }

    // Perbarui status izin saat kembali dari Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = hasAllFilesAccess(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Muat isi folder
    LaunchedEffect(cur, granted) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val list = withContext(Dispatchers.IO) {
            runCatching { cur.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
        }
        entries = list.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        loading = false
    }

    val roots = remember { quickRoots() }
    val selPaths = remember(selFiles) { selFiles.mapNotNull { it.file?.absolutePath }.toSet() }
    val folderFileCount = selFolders.values.sumOf { it.size }
    val totalSel = selFiles.size + folderFileCount
    val totalSize = selFiles.sumOf { it.size } + selFolders.values.sumOf { l -> l.sumOf { it.size } }

    fun toggle(f: File) {
        val abs = f.absolutePath
        selFiles = if (abs in selPaths) {
            selFiles.filterNot { it.file?.absolutePath == abs }
        } else {
            selFiles + PickedFile(f.name, f.length(), file = f)
        }
    }

    fun toggleFolder(f: File) {
        val abs = f.absolutePath
        if (abs in selFolders.keys) {
            selFolders = selFolders - abs
            return
        }
        if (scanning != null) return
        scanning = "Memindai \"${f.name}\"…"
        scope.launch {
            val (n, skippedN, files) = withContext(Dispatchers.IO) { scanFolderRecursive(f) }
            scanning = null
            if (files.isEmpty()) {
                toast("Tidak ada file yang bisa dibaca di folder ini")
            } else {
                val prefix = f.name
                val entriesMapped = files.map { it.copy(path = "$prefix/${it.path}") }
                selFolders = selFolders + (abs to entriesMapped)
                var m = "${files.size} file dari \"$prefix\" dipilih"
                if (skippedN > 0) m += " • $skippedN dilewati"
                if (n >= MAX_SCAN_FILES) m += " • dibatasi $MAX_SCAN_FILES file"
                toast(m)
            }
        }
    }

    fun selectAllHere() {
        val here = entries.filter { it.isFile }.map { PickedFile(it.name, it.length(), file = it) }
        selFiles = (selFiles + here).distinctBy { it.file?.absolutePath }
        val n = here.count { it.file?.absolutePath !in selPaths }
        if (n > 0) toast("$n file di folder ini dipilih") else toast("Semua file di folder ini sudah dipilih")
    }

    /** Rakit hasil akhir + petakan relatif, lalu serahkan ke pemanggil. */
    fun assembleAndClose(extraFolderScan: List<PickedFile>? = null) {
        val individual = mapRelative(selFiles)
        val fromFolders = selFolders.values.flatten()
        val extra = extraFolderScan ?: emptyList()
        val all = (individual + fromFolders + extra).distinctBy { it.file?.absolutePath ?: it.path }
        onPicked(all)
    }

    fun pickWholeFolder() {
        if (scanning != null) return
        scanning = "Memindai \"${cur.name.ifEmpty { "Penyimpanan" }}\"…"
        scope.launch {
            val (n, skippedN, files) = withContext(Dispatchers.IO) { scanFolderRecursive(cur) }
            scanning = null
            if (files.isEmpty() && totalSel == 0) {
                toast("Tidak ada file yang bisa dibaca di folder ini")
            } else {
                if (skippedN > 0) toast("$skippedN file/folder tidak dapat dibaca (dilewati)")
                if (n >= MAX_SCAN_FILES) toast("Dibatasi maksimal $MAX_SCAN_FILES file")
                assembleAndClose(files)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                // ===== Header =====
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            cur.name.ifEmpty { "Penyimpanan" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            cur.absolutePath,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { cur = cur.parentFile ?: cur },
                        enabled = cur.parentFile != null
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Folder atas")
                    }
                }

                if (granted) {
                    // ===== Pintasan root =====
                    Row(
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roots.forEach { (label, dir) ->
                            val active = dir.absolutePath == cur.absolutePath
                            Surface(
                                onClick = { cur = dir },
                                shape = RoundedCornerShape(20.dp),
                                color = if (active) GreenPrimary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    label,
                                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ===== Isi =====
                when {
                    !granted -> Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = GrayMuted,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Izin akses penyimpanan diperlukan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Beri izin \u201CSemua file\u201D agar semua folder & file di HP Anda " +
                                "terbaca sempurna di sini — termasuk file tersembunyi. " +
                                "File hanya dibaca saat Anda mengunggahnya.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    openAllFilesSettings(ctx)
                                } else {
                                    writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenDeep)
                        ) {
                            Text("Beri Izin")
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onDismiss) {
                            Text("Nanti — pakai Pilih File/Folder saja", fontSize = 12.sp)
                        }
                    }

                    loading -> Row(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp)
                    }

                    entries.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = GrayMuted,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Folder kosong", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(entries, key = { it.absolutePath }) { f ->
                            val isDir = f.isDirectory
                            val folderChecked = isDir && f.absolutePath in selFolders.keys
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { if (isDir) cur = f else toggle(f) },
                                        onLongClick = { if (isDir) toggleFolder(f) else toggle(f) }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isDir) Icons.Filled.Folder else Icons.Filled.Description,
                                    contentDescription = if (isDir) "Folder ${f.name}" else "File ${f.name}",
                                    tint = if (isDir) BlueAccent else GrayMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        f.name,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (isDir) "Folder — tekan-lama untuk pilih isinya"
                                        else formatBytes(f.length()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    )
                                }
                                Checkbox(
                                    checked = if (isDir) folderChecked else f.absolutePath in selPaths,
                                    onCheckedChange = { if (isDir) toggleFolder(f) else toggle(f) }
                                )
                            }
                        }
                    }
                }

                // ===== Bar bawah =====
                if (granted) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (scanning != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = GreenPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(scanning ?: "", fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "$totalSel file dipilih",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        formatBytes(totalSize),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selFiles = emptyList()
                                        selFolders = emptyMap()
                                    },
                                    enabled = totalSel > 0
                                ) {
                                    Text("Bersihkan", fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { selectAllHere() },
                                    enabled = scanning == null && entries.any { it.isFile },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pilih semua", fontSize = 12.sp, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { pickWholeFolder() },
                                    enabled = scanning == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pilih Folder Ini", fontSize = 12.sp, maxLines = 1)
                                }
                                Button(
                                    onClick = { assembleAndClose() },
                                    enabled = totalSel > 0 && scanning == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tambahkan", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            Text(
                                "Path di repository = relatif terhadap folder asal file (folder HP tidak ikut ter-upload)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
