package com.gitpush.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import com.gitpush.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- Format helpers ----------

fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun isoToMs(iso: String): Long = try {
    isoFormat.parse(iso)?.time ?: 0L
} catch (e: Exception) {
    0L
}

fun timeAgoMs(ms: Long): String {
    if (ms <= 0) return "-"
    val diff = System.currentTimeMillis() - ms
    val m = diff / 60000
    if (m < 1) return "baru saja"
    if (m < 60) return "$m menit lalu"
    val h = m / 60
    if (h < 24) return "$h jam lalu"
    val d = h / 24
    if (d < 30) return "$d hari lalu"
    val mo = d / 30
    if (mo < 12) return "$mo bulan lalu"
    return "${mo / 12} tahun lalu"
}

fun timeAgo(iso: String): String = timeAgoMs(isoToMs(iso))

fun langColor(lang: String?): Color = when (lang) {
    "Kotlin" -> Color(0xFFA97BFF)
    "JavaScript" -> Color(0xFFF1E05A)
    "TypeScript" -> Color(0xFF3178C6)
    "Python" -> Color(0xFF3572A5)
    "Java" -> Color(0xFFB07219)
    "Dart" -> Color(0xFF00B4AB)
    "HTML" -> Color(0xFFE34C26)
    "CSS" -> Color(0xFF563D7C)
    "Go" -> Color(0xFF00ADD8)
    "Rust" -> Color(0xFFDEA584)
    "PHP" -> Color(0xFF4F5D95)
    "Ruby" -> Color(0xFF701516)
    "Swift" -> Color(0xFFF05138)
    "Shell" -> Color(0xFF89E051)
    "C++" -> Color(0xFFF34B7D)
    "C#" -> Color(0xFF178600)
    "C" -> Color(0xFF555555)
    null -> GrayMuted
    else -> GrayMuted
}

private val textExts = setOf(
    "md", "txt", "json", "js", "jsx", "ts", "tsx", "css", "scss", "html", "xml",
    "yml", "yaml", "py", "rb", "go", "rs", "java", "kt", "kts", "php", "c", "h",
    "cpp", "cs", "swift", "sh", "bash", "bat", "ps1", "sql", "toml", "ini", "cfg",
    "conf", "env", "csv", "log", "vue", "dart", "svg", "gradle", "properties",
    "mjs", "cjs", "astro", "svelte", "prisma", "dockerfile", "makefile", "gitignore",
    "editorconfig", "lock", "http", "graphql", "gql", "sum", "mod", "pubspec"
)

fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun isTextFile(name: String): Boolean {
    val lower = name.lowercase()
    if (lower == "dockerfile" || lower == "makefile" || lower == ".gitignore" ||
        lower == ".env" || lower == "license" || lower == "readme"
    ) return true
    return extOf(name) in textExts
}

fun isImageFile(name: String): Boolean =
    extOf(name) in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

// ---------- Komponen umum ----------

@Composable
fun Loading(text: String = "Memuat…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GreenPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text, color = GrayMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Coba lagi")
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Ikon dalam medali berwarna — jauh lebih hidup daripada ikon abu telanjang
            Box(
                Modifier.size(66.dp).background(GreenPrimary.copy(alpha = 0.12f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = GrayMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
fun Avatar(url: String, size: Dp) {
    val bmp = remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                GitHubApi.http.newCall(
                    Request.Builder().url(url).header("User-Agent", "GitPush-Android").build()
                ).execute().use { r ->
                    if (r.isSuccessful) bmp.value = BitmapFactory.decodeStream(r.body?.byteStream())
                }
            }
        }
    }
    val b = bmp.value
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "Avatar",
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = GrayMuted, fontSize = 14.sp)
        }
    }
}

/** Membungkus konten agar rapi di tablet/landscape (max width 760dp, tetap center). */
@Composable
fun ResponsiveBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > 760.dp) {
                Box(Modifier.fillMaxSize().widthIn(max = 760.dp)) { content() }
            } else {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

// ---------- Logo brand (identik dengan ikon launcher) ----------

/**
 * Logo GitPush — SELALU dari aset yang sama dengan ikon launcher (logo_gitpush.png
 * digenerate dari brand/icon-raw.png, sumber yang sama dengan mipmap/ic_launcher).
 * Dipakai di splash, layar masuk, dan titik branding lain agar tampilan konsisten.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    glowSize: Int = 118,
    logoSize: Int = 92
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // glow lembut di sekeliling logo
        Box(
            Modifier
                .size(glowSize.dp)
                .background(
                    Brush.radialGradient(listOf(GreenPrimary.copy(alpha = 0.22f), Color.Transparent))
                )
        )
        Image(
            painter = painterResource(R.drawable.logo_gitpush),
            contentDescription = "Logo GitPush",
            modifier = Modifier.size(logoSize.dp)
        )
    }
}

// ---------- Ikon tipe file (warna per kategori — identitas file manager) ----------

/** Palet warna ikon kategori file (terinspirasi GitHub octicon, kontras di dark & light). */
object FilePalette {
    val Folder = Color(0xFFF0B232)   // kuning emas — folder
    val Code = Color(0xFF3FB950)     // hijau — kode
    val Doc = Color(0xFF58A6FF)      // biru muda — dokumen teks
    val Image = Color(0xFFA371F7)    // ungu — gambar
    val Video = Color(0xFFFF7B72)    // merah muda — video
    val Audio = Color(0xFFFFA657)    // oranye — audio
    val Archive = Color(0xFFE3B341)  // amber — arsip
    val Pdf = Color(0xFFF85149)      // merah — PDF
    val Sheet = Color(0xFF2DD4BF)    // teal — spreadsheet/data
    val Generic = Color(0xFF9198A1)  // abu — lainnya
}

private val extOf: (String) -> String = { n -> n.substringAfterLast('.', "").lowercase() }

/** Jenis ikon + warna untuk nama file (atau folder). */
fun fileInfo(name: String, isDir: Boolean): Pair<ImageVector, Color> {
    if (isDir) return Icons.Filled.Folder to FilePalette.Folder
    val ext = extOf(name)
    return when (ext) {
        in setOf("kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "cs", "go", "rs", "rb", "php", "swift", "sh", "bat", "gradle", "cmake", "lua", "r", "scala", "dart", "sql", "asm", "s") ->
            Icons.Filled.Code to FilePalette.Code
        in setOf("html", "css", "scss", "vue", "xml", "ui") ->
            Icons.Filled.Code to FilePalette.Code
        in setOf("json", "yml", "yaml", "toml", "ini", "conf", "properties", "env", "lock") ->
            Icons.Filled.Tune to FilePalette.Code
        in setOf("md", "txt", "rtf", "log", "doc", "docx", "odt") ->
            if (ext == "md") Icons.Filled.Article to FilePalette.Doc else Icons.Filled.Description to FilePalette.Doc
        in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "heic", "tiff") ->
            Icons.Filled.Image to FilePalette.Image
        in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v") ->
            Icons.Filled.Movie to FilePalette.Video
        in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus") ->
            Icons.Filled.MusicNote to FilePalette.Audio
        in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "jar", "aab") ->
            Icons.Filled.FolderZip to FilePalette.Archive
        "pdf" -> Icons.Filled.PictureAsPdf to FilePalette.Pdf
        in setOf("csv", "xlsx", "xls", "ods", "tsv") ->
            Icons.Filled.TableChart to FilePalette.Sheet
        in setOf("ppt", "pptx", "odp") ->
            Icons.Filled.Slideshow to FilePalette.Video
        in setOf("ttf", "otf", "woff", "woff2") ->
            Icons.Filled.TextFields to FilePalette.Doc
        in setOf("exe", "dll", "so", "bin", "deb", "rpm", "dmg", "iso") ->
            Icons.Filled.Memory to FilePalette.Generic
        else -> Icons.Filled.InsertDriveFile to FilePalette.Generic
    }
}

/**
 * Ikon tipe file dalam kotak bulat berwarna (alpha 15%) — tampilan premium ala
 * file manager modern. Dipakai di RepoScreen dan dialog lain.
 */
@Composable
fun FileTypeBadge(name: String, isDir: Boolean, size: Dp = 38.dp, iconSize: Dp = 20.dp) {
    val (icon, color) = fileInfo(name, isDir)
    Box(
        Modifier.size(size).background(color.copy(alpha = 0.15f), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

// ---------- Dialog progres global (semua proses panjang) ----------

@Composable
fun OperationOverlay() {
    if (!Store.operation.running) return
    Dialog(onDismissRequest = { }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(GreenPrimary.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Store.operation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (Store.operation.detail.isNotEmpty()) {
                            Text(
                                Store.operation.detail,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val t = Store.operation.total
                val d = Store.operation.done
                if (t > 0) {
                    LinearProgressIndicator(
                        progress = { (d.toFloat() / t).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenPrimary,
                        trackColor = GreenPrimary.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(8.dp))
                    val pct = ((d * 100) / t).toInt()
                    val amount = if (Store.operation.unit == "bytes")
                        "${formatBytes(d)} / ${formatBytes(t)}" else "$d / $t"
                    Text(
                        "$amount • $pct%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = GreenPrimary,
                        trackColor = GreenPrimary.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

// ---------- Komponen desain bersama (tipografi & tata letak konsisten) ----------

/**
 * Identitas bahasa desain "Modern Light": di tema terang kartu TANPA garis
 * pinggir — cukup permukaan putih di atas kanvas abu lembut (ala Files by
 * Google / GitHub mobile), sedangkan tema gelap tetap memakai garis tipis
 * untuk memisahkan permukaan.
 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Bayangan kartu lembut (kehijauan-abu, bukan hitam pekat) — hanya dirender
 * di tema terang agar tidak dobel dengan garis pinggir tema gelap.
 */
@Composable
fun Modifier.cardShadow(shape: Shape): Modifier {
    val dark = isDarkTheme()
    return if (dark) this else this.shadow(
        elevation = 5.dp,
        shape = shape,
        clip = false,
        ambientColor = Color(0xFF44546B),
        spotColor = Color(0xFF44546B)
    )
}

/** Garis pinggir kartu — hanya di tema gelap; tema terang bersih tanpa garis. */
@Composable
fun cardBorder() = if (isDarkTheme())
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
else null

/** Label seksi huruf kapital dengan jarak huruf — identitas tata letak GitPush. */
@Composable
fun SectionLabel2(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Header layar besar: judul display + subjudul + aksi di kanan. */
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp,
                letterSpacing = (-0.5).sp
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        actions?.invoke(this)
    }
}

/** Pil statistik: angka tebal + label kecil (profil, ringkasan repo). */
@Composable
fun StatPill(value: String, label: String, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(9.dp),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
        }
    }
}

/** Chip kecil ikon+teks untuk meta (language, ukuran, waktu). */
@Composable
fun MetaChip(icon: ImageVector, text: String, tint: Color = GrayMuted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = GrayMuted, fontSize = 11.sp)
    }
}

// ---------- Komponen desain premium (overhaul tampilan) ----------

/**
 * Panel hero bergradien hijau dengan lingkaran dekoratif — identitas visual baru.
 * Konten di dalamnya berwarna terang (putih/putih 70%).
 */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF12351E), Color(0xFF17502C), Color(0xFF1F6F33))))
    ) {
        // lingkaran dekoratif transparan — kedalaman tanpa gambar
        Box(
            Modifier
                .offset(x = (-34).dp, y = (-46).dp)
                .size(150.dp)
                .alpha(0.9f)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            Modifier
                .offset(x = 250.dp, y = 34.dp)
                .size(120.dp)
                .alpha(0.8f)
                .background(Color.White.copy(alpha = 0.045f), CircleShape)
        )
        Box(
            Modifier
                .offset(x = 190.dp, y = (-30).dp)
                .size(56.dp)
                .alpha(0.7f)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

/** Kartu standar GitPush: sudut membulat 18, garis tipis, permukaan rata — modern & bersih. */
@Composable
fun GpCard(
    modifier: Modifier = Modifier,
    container: Color? = null,
    corner: Dp = 18.dp,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val bg = container ?: MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = bg,
            border = cardBorder(),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
        }
    } else {
        Surface(shape = shape, color = bg, border = cardBorder(), modifier = modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
        }
    }
}

/** Kolom pencarian pil penuh — membulat, di atas permukaan varian, tanpa garis. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val dark = isDarkTheme()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = GrayMuted) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = GrayMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Bersihkan", Modifier.size(18.dp), tint = GrayMuted)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            // Terang: pil putih melayang dengan bayangan — bukan kotak abu datar
            unfocusedContainerColor = if (dark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface,
            focusedContainerColor = if (dark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = GreenPrimary.copy(alpha = 0.55f),
            cursorColor = GreenPrimary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true,
        modifier = modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))
    )
}

/**
 * Tile aksi cepat: ikon dalam kotak membulat berwarna + label di bawah.
 * Dipakai berjajar 4 di ringkasan repo & beranda.
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) tint.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(tint.copy(alpha = if (active) 0.28f else 0.15f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Baris kerangka (skeleton) yang berdenyut — pengganti spinner saat memuat daftar. */
@Composable
fun SkeletonRows(rows: Int = 6, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "sk")
    val a by tr.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a"
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = cardBorder()
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).alpha(a).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Box(Modifier.fillMaxWidth(0.55f).height(13.dp).alpha(a).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
                        Spacer(Modifier.height(7.dp))
                        Box(Modifier.fillMaxWidth(0.32f).height(10.dp).alpha(a).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)))
                    }
                }
            }
        }
    }
}

/** Tag kecil "LFS" — penanda file tersimpan di Git LFS (bisa diunduh isi aslinya). */
@Composable
fun LfsTag() {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = PurpleAccent.copy(alpha = 0.16f)
    ) {
        Text(
            "LFS",
            color = PurpleAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/** Chip status visibilitas repo (Privat kuning / Publik hijau). */
@Composable
fun VisibilityChip(isPrivate: Boolean) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (isPrivate) YellowWarn.copy(alpha = 0.15f) else GreenPrimary.copy(alpha = 0.14f)
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                contentDescription = null,
                tint = if (isPrivate) YellowWarn else GreenPrimary,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isPrivate) "Privat" else "Publik",
                color = if (isPrivate) YellowWarn else GreenPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------- Markdown render (Markwon: gambar, tabel, HTML, tautan klik) ----------

/** URL relatif → absolut terhadap [baseUrl] (folder file .md di raw.githubusercontent). */
private fun resolveMdUrl(url: String, baseUrl: String?): String {
    val u = url.trim()
    if (baseUrl == null || u.isEmpty()) return u
    val lower = u.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") ||
        lower.startsWith("data:") || lower.startsWith("#") ||
        lower.startsWith("mailto:") || lower.startsWith("ftp://") ||
        lower.startsWith("file:")
    ) return u
    return try {
        java.net.URL(java.net.URL(baseUrl), u).toString()
    } catch (e: Exception) {
        u
    }
}

/** URL relatif di markdown (…](x.png)) dan atribut src/href HTML dijadikan absolut. */
private fun preprocessMd(md: String, baseUrl: String?): String {
    if (baseUrl == null) return md
    var out = Regex("""(!?\[[^\]]*\]\()\s*([^)\s]+)((?:\s+"[^"]*")?\s*\))""")
        .replace(md) { m -> "${m.groupValues[1]}${resolveMdUrl(m.groupValues[2], baseUrl)}${m.groupValues[3]}" }
    out = Regex("""(<img\b[^>]*?\ssrc=")([^"]+)(")""", RegexOption.IGNORE_CASE)
        .replace(out) { "${it.groupValues[1]}${resolveMdUrl(it.groupValues[2], baseUrl)}${it.groupValues[3]}" }
    out = Regex("""(<a\b[^>]*?\shref=")([^"]+)(")""", RegexOption.IGNORE_CASE)
        .replace(out) { "${it.groupValues[1]}${resolveMdUrl(it.groupValues[2], baseUrl)}${it.groupValues[3]}" }
    return out
}

/** Markwon siap-pakai: warna mengikuti tema aplikasi. Panggil via remember (mahal). */
private fun buildMarkwon(
    ctx: Context,
    muted: Int, link: Int, outline: Int, codeBg: Int,
    tableEven: Int, tableHeader: Int
): Markwon {
    val d = ctx.resources.displayMetrics.density
    fun px(v: Int) = (v * d).toInt()
    return Markwon.builder(ctx)
        .usePlugin(ImagesPlugin.create()) // gambar remote http(s)/data: + <img> HTML
        .usePlugin(TablePlugin.create { t ->
            t.tableBorderColor(outline)
                .tableBorderWidth(px(1))
                .tableCellPadding(px(6))
                .tableEvenRowBackgroundColor(tableEven)
                .tableHeaderRowBackgroundColor(tableHeader)
        })
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(ctx))
        .usePlugin(HtmlPlugin.create()) // <img>, <b>, <sub>, <u>, dst.
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .headingTextSizeMultipliers(floatArrayOf(1.5f, 1.3f, 1.15f, 1.0f, 0.95f, 0.9f))
                    .headingBreakHeight(px(1))
                    .headingBreakColor(outline)
                    .linkColor(link)
                    .isLinkUnderlined(false)
                    .codeBlockBackgroundColor(codeBg)
                    .codeBlockTextSize(px(12))
                    .blockQuoteColor(muted)
                    .blockQuoteWidth(px(3))
                    .thematicBreakColor(outline)
                    .thematicBreakHeight(px(1))
            }
        })
        .build()
}

/**
 * Markdown penuh via Markwon (TextView di Compose): heading, daftar bertingkat,
 * tabel, gambar (URL relatif di-resolve terhadap [baseUrl]), HTML, strikethrough,
 * task list, dan tautan bisa diklik. Dipakai dialog README, penampil file .md,
 * isi issue, dan catatan release.
 */
@Composable
fun MarkdownText(md: String, baseUrl: String? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cBody = MaterialTheme.colorScheme.onSurface
    val cMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val cOutline = MaterialTheme.colorScheme.outline
    val cCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val markwon = remember(cBody, cMuted, cOutline, cCodeBg) {
        buildMarkwon(
            ctx,
            muted = cMuted.toArgb(),
            link = BlueAccent.toArgb(),
            outline = cOutline.toArgb(),
            codeBg = cCodeBg.toArgb(),
            tableEven = cCodeBg.copy(alpha = 0.4f).toArgb(),
            tableHeader = cCodeBg.toArgb()
        )
    }
    val processed = remember(md, baseUrl) { preprocessMd(md, baseUrl) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { tctx ->
            TextView(tctx).apply {
                textSize = 14f
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = android.graphics.Color.TRANSPARENT
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            tv.setTextColor(cBody.toArgb())
            markwon.setMarkdown(tv, processed)
        }
    )
}
