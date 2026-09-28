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
import androidx.compose.foundation.clickable
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
const val MAX_SCAN_FILES = 2000

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
 * Mengembalikan (jumlah file, jumlah dilewati, daftar file dengan path relatif).
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
 * File Manager bawaan GitPush — menampilkan direktori/file HP apa adanya
 * (termasuk file tersembunyi) lewat akses penyimpanan penuh.
 * File dikembalikan sebagai PickedFile(file=…) tanpa membaca byte ke RAM.
 */
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
    val baseAbs = baseDir.absolutePath.trimEnd('/')

    fun toggle(f: File) {
        val abs = f.absolutePath
        selFiles = if (abs in selPaths) {
            selFiles.filterNot { it.file?.absolutePath == abs }
        } else {
            val rel = abs.removePrefix(baseAbs).trimStart('/').ifEmpty { f.name }
            selFiles + PickedFile(rel, f.length(), file = f)
        }
    }

    fun pickWholeFolder() {
        if (scanning != null) return
        scanning = "Memindai \"${cur.name}\"…"
        scope.launch {
            val (n, skipped, files) = withContext(Dispatchers.IO) { scanFolderRecursive(cur) }
            scanning = null
            if (files.isEmpty()) {
                toast("Tidak ada file yang bisa dibaca di folder ini")
            } else {
                val merged = (selFiles + files).distinctBy { it.file?.absolutePath ?: it.path }
                if (skipped > 0) toast("$skipped file/folder tidak dapat dibaca (dilewati)")
                if (n >= MAX_SCAN_FILES) toast("Dibatasi maksimal $MAX_SCAN_FILES file")
                onPicked(merged)
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
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { if (isDir) cur = f else toggle(f) }
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
                                        if (isDir) "Folder" else formatBytes(f.length()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    )
                                }
                                if (!isDir) {
                                    Checkbox(
                                        checked = f.absolutePath in selPaths,
                                        onCheckedChange = { toggle(f) }
                                    )
                                }
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
                                        "${selFiles.size} file dipilih",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        formatBytes(selFiles.sumOf { it.size }),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                TextButton(
                                    onClick = { pickWholeFolder() },
                                    enabled = scanning == null
                                ) {
                                    Text("Pilih Folder Ini", fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = { onPicked(selFiles) },
                                    enabled = selFiles.isNotEmpty() && scanning == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenDeep)
                                ) {
                                    Text("Tambahkan", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
