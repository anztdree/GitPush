package com.gitpush.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pusat transfer unggahan — pengganti desain lama "tab Unggah" yang salah.
 * Unggahan kini dimulai DARI folder yang sedang dibuka (FAB di RepoScreen),
 * sedangkan di sini hanya berisi: chip progres global (tampil di layar mana pun,
 * ala aplikasi cloud) + dialog progres lengkap + helper pemilihan file.
 */

// ==================== HELPER PEMILIHAN FILE (SAF) ====================

fun queryDisplayName(ctx: Context, uri: Uri): String? {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0) ?: uri.lastPathSegment?.substringAfterLast('/')
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

fun querySize(ctx: Context, uri: Uri): Long {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
    }
    return 0L
}

/** Gabungkan pilihan baru; path yang sama ditimpa, tidak diduplikasi */
fun mergePicked(old: List<PickedFile>, add: List<PickedFile>): List<PickedFile> {
    if (add.isEmpty()) return old
    val addPaths = add.map { it.path }.toSet()
    return old.filterNot { it.path in addPaths } + add
}

private data class SafEntry(val docId: String, val name: String, val mime: String?, val size: Long)
data class SafScan(val files: List<PickedFile>, val skipped: Int, val truncated: Boolean)

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
suspend fun scanSafTree(
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

// ==================== CHIP PROGRES GLOBAL (ala aplikasi cloud) ====================

/**
 * Chip kecil di bawah layar yang tampil di SEMUA tab saat ada unggahan berjalan
 * (atau hasilnya belum ditutup) — persis seperti aplikasi cloud pada umumnya:
 * pengguna tetap bisa menjelajah file saat mengunggah. Ketuk → dialog progres lengkap.
 */
@Composable
fun UploadTray(modifier: Modifier = Modifier) {
    val active = UploadManager.active
    val panelOpen = UploadManager.showPanel
    if (!active && !panelOpen) return

    val ctx = LocalContext.current
    // Layar tetap menyala selama unggahan berjalan
    val activity = ctx as? Activity
    DisposableEffect(active) {
        if (active) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (!UploadManager.active) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showFull by remember { mutableStateOf(false) }
    // Tombol "Tutup" di panel menutup chip juga (showPanel = false)
    LaunchedEffect(UploadManager.showPanel, UploadManager.active) {
        if (!UploadManager.showPanel && !UploadManager.active) showFull = false
    }

    val phase = UploadManager.phase
    Box(modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            onClick = { showFull = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = cardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        active -> CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        phase == "error" -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                        phase == "cancel" -> Icon(Icons.Filled.Cancel, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(17.dp))
                        else -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                active -> "Mengunggah…"
                                phase == "done" -> "Upload selesai ✓"
                                phase == "error" -> "Upload gagal"
                                phase == "cancel" -> "Upload dibatalkan"
                                else -> "Upload"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            UploadManager.repoFull,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${UploadManager.filesDone}/${UploadManager.filesTotal}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Buka detail unggahan",
                        tint = GrayMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                if (active && UploadManager.bytesTotal > 0) {
                    val frac = (UploadManager.progressBytes().toFloat() / UploadManager.bytesTotal).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(4.dp),
                        color = GreenPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else if (active) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(top = 7.dp).height(4.dp),
                        color = GreenPrimary
                    )
                }
            }
        }
    }

    if (showFull) {
        UploadProgressDialog(onDismiss = { showFull = false })
    }
}

// ==================== DIALOG PROGRES LENGKAP ====================

@Composable
fun UploadProgressDialog(onDismiss: () -> Unit) {
    var askCancel by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                UploadProgressPanel(
                    onAskCancel = { askCancel = true },
                    onOpenCommit = { url ->
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                )
            }
        }
    }

    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Batalkan upload?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("File yang sudah terkirim belum di-commit, jadi tidak ada perubahan di repository.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    askCancel = false
                    UploadManager.requestCancel()
                }) { Text("Batalkan upload", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askCancel = false }) { Text("Lanjutkan upload") }
            }
        )
    }
}

// ==================== PANEL PROGRES UPLOAD ====================

@Composable
fun UploadProgressPanel(onAskCancel: () -> Unit, onOpenCommit: (String) -> Unit) {
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

            // ===== Progres keseluruhan (byte AGREGAT semua file paralel) =====
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

                    // Info retry/pindah jalur otomatis — pengguna tahu proses belum mati
                    UploadManager.retryMsg?.let { rm ->
                        Text(
                            "⟳ $rm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = YellowWarn,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    // Indikator "tidak ada data" — jujur: pengguna tahu pengawas sedang memantau
                    val stallSecNow = UploadManager.stallSec
                    if (stallSecNow >= 4) {
                        Text(
                            "• tidak ada data ${stallSecNow} dtk — pengawas koneksi aktif",
                            fontSize = 11.sp,
                            color = YellowWarn,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = GreenPrimary)
                    Spacer(Modifier.height(6.dp))
                    // Tahap analisis: tampilkan progres checksum file yang sedang dibaca
                    val hf = UploadManager.hashFile
                    if (hf.isNotEmpty()) {
                        Text(
                            buildString {
                                append("Menganalisis ${UploadManager.filesTotal} file")
                                if (UploadManager.filesAnalyzed > 0) append(" — ${UploadManager.filesAnalyzed}/${UploadManager.filesTotal} siap")
                                append(" • ${hf.substringAfterLast('/')}")
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val ht = UploadManager.hashTotal
                        if (ht > 0) {
                            val hfFrac = (UploadManager.hashSent.toFloat() / ht).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { hfFrac },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                                color = GreenPrimary.copy(alpha = 0.6f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                "${formatBytes(UploadManager.hashSent)} / ${formatBytes(ht)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                "Membaca ${formatBytes(UploadManager.hashSent)}…",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            buildString {
                                append("Menganalisis ${UploadManager.filesTotal} file…")
                                if (UploadManager.filesAnalyzed > 0) append(" ${UploadManager.filesAnalyzed}/${UploadManager.filesTotal} siap")
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ===== File yang sedang dikirim (SEMUA slot paralel, bukan satu saja) =====
                if (phase == "upload") {
                    val actives = UploadManager.activeFiles
                    if (actives.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        actives.take(3).forEach { (p, sent, tot) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                Icon(
                                    Icons.Filled.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = BlueAccent
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.substringAfterLast('/'),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (tot > 0) {
                                        val cf = (sent.toFloat() / tot).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { cf },
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp),
                                            color = BlueAccent,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            "${formatBytes(sent)} / ${formatBytes(tot)}",
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp),
                                            color = BlueAccent
                                        )
                                    }
                                }
                            }
                        }
                        if (actives.size > 3) {
                            Text(
                                "+${actives.size - 3} file lain sedang dikirim…",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
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
                            "⚠ ${skipped.size} file gagal (tersimpan, bisa dicoba ulang):",
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

            // ===== TOMBOL COBA LAGI — khusus file yang gagal (file gagal tetap tersimpan;
            // permintaan user: file yang gagal diulang sendiri lalu menyatu dengan
            // teman-temannya yang sudah terunggah) =====
            val retryables = UploadManager.failedFiles
            if (retryables.isNotEmpty() && !UploadManager.active) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { UploadManager.retryFailed() },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Coba lagi ${retryables.size} file gagal", fontSize = 13.sp)
                }
                Text(
                    if (UploadManager.commitSha != null)
                        "File gagal tetap tersimpan — diunggah ulang dalam 1 commit dan menyatu " +
                            "dengan file yang sudah berhasil di repository yang sama."
                    else
                        "Belum ada commit yang tercipta — semua file akan diunggah ulang. " +
                            "Blob yang sudah sampai tidak dikirim dua kali, jadi cepat.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ===== LOG LANGSUNG — tampil saat proses berjalan (permintaan user:
            // "saat proses upload log ditampilkan juga, biar keliatan jelas") =====
            val liveLog = UploadManager.logLines
            if (liveLog.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFF12161C),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            "LOG LANGSUNG",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6B7684)
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                            val logScroll = rememberScrollState()
                            Column(Modifier.fillMaxWidth().verticalScroll(logScroll)) {
                                liveLog.forEach { line ->
                                    Text(
                                        line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.sp,
                                        color = Color(0xFFAEB9C6)
                                    )
                                }
                            }
                            // Gulir otomatis ke baris terbaru
                            LaunchedEffect(liveLog.size) {
                                logScroll.animateScrollTo(logScroll.maxValue)
                            }
                        }
                    }
                }
            }

            // ===== Salin log diagnostik (tampil di SEMUA tahap — bila upload bermasalah,
            // user tinggal salin & kirim ke pengembang: akar masalah langsung terlihat) =====
            val clipboard = LocalClipboardManager.current
            val panelCtx = LocalContext.current
            TextButton(
                onClick = {
                    val pi = try {
                        panelCtx.packageManager.getPackageInfo(panelCtx.packageName, 0)
                    } catch (_: Exception) { null }
                    val header = "GitPush v${pi?.versionName} (${pi?.longVersionCode}) — " +
                        "${UploadManager.repoFull}@${UploadManager.branch}\n"
                    clipboard.setText(AnnotatedString(header + UploadManager.logText()))
                    Toast.makeText(
                        panelCtx,
                        "Log unggahan disalin — kirimkan ke pengembang bila ada masalah",
                        Toast.LENGTH_LONG
                    ).show()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salin log unggahan", fontSize = 11.sp)
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
                            "Boleh pindah layar — upload tetap berjalan",
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
