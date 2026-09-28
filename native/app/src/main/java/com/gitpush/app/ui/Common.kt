package com.gitpush.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = GrayMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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

// ---------- Markdown minimal ----------

fun inlineStyled(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
    var last = 0
    for (m in re.findAll(s)) {
        append(s.substring(last, m.range.first))
        if (s[m.range.first] == '*') {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(s.substring(m.range.first + 2, m.range.last - 1))
            }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp)) {
                append(s.substring(m.range.first + 1, m.range.last))
            }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

@Composable
fun MarkdownText(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lines = md.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") -> {
                    val buf = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        buf.appendLine(lines[i])
                        i++
                    }
                    i++
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            buf.toString().trimEnd(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(10.dp).fillMaxWidth()
                        )
                    }
                }
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    val text = trimmed.dropWhile { it == '#' }.trim()
                    Text(
                        text,
                        fontSize = when (level) {
                            1 -> 21.sp
                            2 -> 18.sp
                            3 -> 16.sp
                            else -> 15.sp
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (level <= 2) 6.dp else 2.dp)
                    )
                    if (level <= 2) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Text(inlineStyled("• " + trimmed.drop(2)), fontSize = 14.sp, lineHeight = 20.sp)
                }
                trimmed.startsWith("!") && trimmed.startsWith("[") -> {
                    Text("[gambar]", color = GrayMuted, fontSize = 13.sp)
                }
                Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                    Text(inlineStyled(trimmed), fontSize = 14.sp, lineHeight = 20.sp)
                }
                trimmed.startsWith(">") -> {
                    Text(
                        trimmed.drop(1).trim(),
                        color = GrayMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                trimmed.isBlank() -> { }
                else -> Text(inlineStyled(trimmed), fontSize = 14.sp, lineHeight = 20.sp)
            }
            i++
        }
    }
}
