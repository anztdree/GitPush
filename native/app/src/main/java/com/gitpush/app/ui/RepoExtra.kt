package com.gitpush.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitpush.app.data.GhIssue
import com.gitpush.app.data.GhPull
import com.gitpush.app.data.GhRelease
import com.gitpush.app.data.GhRepo
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun issueStateColor(state: String): Color =
    if (state == "open") GreenPrimary else PurpleAccent

// ============================================================
// ============ ISSUES — daftar, buat, detail, komentar ========
// ============================================================

/**
 * Layar penuh Issues ala GitHub: filter Terbuka/Ditutup, buat issue,
 * detail + komentar, tutup/buka ulang.
 */
@Composable
fun IssuesDialog(
    owner: String,
    name: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("open") }
    var list by remember { mutableStateOf<List<GhIssue>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<GhIssue?>(null) }

    val load: () -> Unit = {
        scope.launch {
            err = null
            try {
                list = withContext(Dispatchers.IO) {
                    GitHubApi.fetchIssues(Store.token.value, owner, name, state)
                }
            } catch (e: Exception) {
                err = GitHubApi.humanError(e)
            }
        }
    }
    LaunchedEffect(state) { load() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Issues", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$owner/$name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Buat issue", tint = GreenPrimary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state == "open",
                        onClick = { state = "open" },
                        label = { Text("Terbuka") }
                    )
                    FilterChip(
                        selected = state == "closed",
                        onClick = { state = "closed" },
                        label = { Text("Ditutup") }
                    )
                }
                when {
                    list == null -> Loading()
                    err != null -> ErrorCard(err!!) { load() }
                    list!!.isEmpty() -> EmptyState(
                        Icons.Filled.BugReport,
                        if (state == "open") "Tidak ada issue terbuka" else "Tidak ada issue ditutup",
                        "Tekan ikon + untuk membuat issue pertama"
                    )
                    else -> {
                        val issues = list!!.filter { !it.isPr }
                        if (issues.isEmpty()) {
                            EmptyState(
                                Icons.Filled.BugReport,
                                "Tidak ada issue di sini",
                                "Item yang tersisa berupa pull request"
                            )
                        } else ResponsiveBox {
                            LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(issues, key = { it.number }) { iss ->
                                    IssueCard(iss, onClick = { detail = iss })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateIssueDialog(
            owner = owner, name = name,
            onDismiss = { showCreate = false },
            onCreated = { showCreate = false; state = "open"; load() }
        )
    }
    detail?.let { d ->
        IssueDetailDialog(
            issue = d,
            owner = owner, name = name,
            onDismiss = { detail = null },
            onUpdated = { fresh -> detail = fresh; load() }
        )
    }
}

@Composable
private fun IssueCard(issue: GhIssue, onClick: () -> Unit) {
    androidx.compose.material3.Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (issue.state == "open") Icons.Filled.BugReport else Icons.Filled.CheckCircle,
                contentDescription = issue.state,
                tint = issueStateColor(issue.state),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    issue.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "#${issue.number} dibuka ${timeAgo(issue.createdAt)} oleh ${issue.author}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (issue.labels.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        issue.labels.take(3).forEach { l ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                                Text(l, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            if (issue.comments > 0) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tag, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("${issue.comments}", color = GrayMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CreateIssueDialog(
    owner: String, name: String,
    onDismiss: () -> Unit, onCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Buat issue", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Judul") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("Keterangan (opsional)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.createIssue(Store.token.value, owner, name, title.trim(), body.trim())
                            }
                            onCreated()
                        } catch (e: Exception) {
                            err = GitHubApi.humanError(e)
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Buat issue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun IssueDetailDialog(
    issue: GhIssue,
    owner: String, name: String,
    onDismiss: () -> Unit,
    onUpdated: (GhIssue) -> Unit
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<GhComment2>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(issue.number) {
        try {
            val cs = withContext(Dispatchers.IO) {
                GitHubApi.fetchIssueComments(Store.token.value, owner, name, issue.number)
            }
            comments = cs.map { GhComment2(it.author, it.avatarUrl, it.body, it.createdAt) }
        } catch (_: Exception) { }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "#${issue.number} ${issue.title}",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = issueStateColor(issue.state).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    if (issue.state == "open") "Terbuka" else "Ditutup",
                                    color = issueStateColor(issue.state),
                                    fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${issue.author} • ${timeAgo(issue.createdAt)}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (!issue.body.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                MarkdownText(issue.body!!.take(4000))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(
                        "Komentar (${comments.size})",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (loading) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    } else if (comments.isEmpty()) {
                        Text(
                            "Belum ada komentar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                        )
                    } else {
                        comments.forEach { c ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${c.author} • ${timeAgo(c.createdAt)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(c.body, fontSize = 13.sp, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Panel aksi bawah: tulis komentar + tutup/buka ulang
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            placeholder = { Text("Tulis komentar…") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = draft.isNotBlank() && !busy,
                                onClick = {
                                    busy = true; err = null
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                GitHubApi.addIssueComment(Store.token.value, owner, name, issue.number, draft.trim())
                                            }
                                            val cs = withContext(Dispatchers.IO) {
                                                GitHubApi.fetchIssueComments(Store.token.value, owner, name, issue.number)
                                            }
                                            comments = cs.map { GhComment2(it.author, it.avatarUrl, it.body, it.createdAt) }
                                            draft = ""
                                        } catch (e: Exception) {
                                            err = GitHubApi.humanError(e)
                                        } finally { busy = false }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (busy) "Mengirim…" else "Kirim komentar") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true; err = null
                                    scope.launch {
                                        try {
                                            val open = issue.state != "open"
                                            withContext(Dispatchers.IO) {
                                                GitHubApi.setIssueState(Store.token.value, owner, name, issue.number, open)
                                            }
                                            onUpdated(issue.copy(state = if (open) "open" else "closed"))
                                        } catch (e: Exception) {
                                            err = GitHubApi.humanError(e)
                                        } finally { busy = false }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (issue.state == "open") "Tutup issue" else "Buka ulang")
                            }
                        }
                        if (err != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class GhComment2(
    val author: String, val avatarUrl: String?, val body: String, val createdAt: String
)

// ============================================================
// ============ PULL REQUEST — daftar & gabungkan ==============
// ============================================================

@Composable
fun PullsDialog(
    owner: String,
    name: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var state by remember { mutableStateOf("open") }
    var list by remember { mutableStateOf<List<GhPull>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var merging by remember { mutableStateOf<Int?>(null) }
    var confirmMerge by remember { mutableStateOf<GhPull?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val load: () -> Unit = {
        scope.launch {
            err = null
            try {
                list = withContext(Dispatchers.IO) {
                    GitHubApi.fetchPulls(Store.token.value, owner, name, state)
                }
            } catch (e: Exception) {
                err = GitHubApi.humanError(e)
            }
        }
    }
    LaunchedEffect(state) { load() }
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show()
            toast = null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Pull Request", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$owner/$name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = state == "open", onClick = { state = "open" }, label = { Text("Terbuka") })
                    FilterChip(selected = state == "closed", onClick = { state = "closed" }, label = { Text("Ditutup") })
                }
                when {
                    list == null -> Loading()
                    err != null -> ErrorCard(err!!) { load() }
                    list!!.isEmpty() -> EmptyState(
                        Icons.Filled.AccountTree,
                        "Tidak ada pull request",
                        "PR yang dibuat di GitHub akan muncul di sini"
                    )
                    else -> ResponsiveBox {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list!!, key = { it.number }) { pr ->
                                androidx.compose.material3.Card {
                                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.AccountTree,
                                                contentDescription = null,
                                                tint = if (pr.state == "open") GreenPrimary else PurpleAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "#${pr.number} ${pr.title}",
                                                fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${pr.author} • ${pr.headRef} → ${pr.baseRef} • ${timeAgo(pr.createdAt)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                        if (pr.state == "open" && !pr.draft) {
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                enabled = merging != pr.number,
                                                onClick = { confirmMerge = pr },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = GreenDeep)
                                            ) {
                                                if (merging == pr.number) {
                                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                                } else {
                                                    Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Gabungkan ke ${pr.baseRef}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmMerge?.let { pr ->
        AlertDialog(
            onDismissRequest = { confirmMerge = null },
            title = { Text("Gabungkan PR #${pr.number}?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("${pr.headRef} akan digabungkan ke ${pr.baseRef} dengan metode merge. Tindakan ini membuat commit baru di GitHub.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmMerge = null
                    merging = pr.number
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.mergePull(Store.token.value, owner, name, pr.number)
                            }
                            toast = "PR #${pr.number} berhasil digabungkan ✓"
                            load()
                        } catch (e: Exception) {
                            val msg = GitHubApi.humanError(e)
                            toast = when {
                                (e as? com.gitpush.app.data.GhException)?.code == 405 -> "Tidak bisa digabung (konflik/branch terlindungi)"
                                (e as? com.gitpush.app.data.GhException)?.code == 409 -> "Head branch berubah — segarkan lalu coba lagi"
                                else -> "Gagal: $msg"
                            }
                        } finally { merging = null }
                    }
                }) { Text("Gabungkan", color = GreenPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmMerge = null }) { Text("Batal") } }
        )
    }
}

// ============================================================
// ============ RELEASES — daftar rilis repository ============
// ============================================================

@Composable
fun ReleasesDialog(
    owner: String, name: String,
    onDismiss: () -> Unit
) {
    var list by remember { mutableStateOf<List<GhRelease>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            list = withContext(Dispatchers.IO) {
                GitHubApi.fetchReleases(Store.token.value, owner, name)
            }
        } catch (e: Exception) {
            err = GitHubApi.humanError(e)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                    Column(Modifier.weight(1f)) {
                        Text("Releases", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$owner/$name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$owner/$name/releases")))
                        }
                    }) {
                        Icon(Icons.Filled.Public, contentDescription = "Buka di GitHub", tint = BlueAccent)
                    }
                }
                when {
                    list == null -> Loading()
                    err != null -> ErrorCard(err!!) { }
                    list!!.isEmpty() -> EmptyState(
                        Icons.Filled.Tag,
                        "Belum ada release",
                        "Rilis yang dibuat di GitHub akan tampil di sini"
                    )
                    else -> ResponsiveBox {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(list!!, key = { it.tagName }) { r ->
                                androidx.compose.material3.Card {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Tag, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(17.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (r.isPrerelease) {
                                                Surface(color = YellowWarn.copy(alpha = 0.18f), shape = RoundedCornerShape(5.dp)) {
                                                    Text("prarilis", color = YellowWarn, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "${r.tagName} • ${timeAgo(r.publishedAt)} • ${r.assetCount} aset",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                        )
                                        if (!r.body.isNullOrBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                r.body!!.take(600),
                                                fontSize = 12.sp, lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// ============ EDIT REPOSITORY ================================
// ============================================================

@Composable
fun EditRepoDialog(
    repo: GhRepo,
    onDismiss: () -> Unit,
    onSaved: (GhRepo) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(repo.name) }
    var desc by remember { mutableStateOf(repo.description ?: "") }
    var homepage by remember { mutableStateOf(repo.homepage ?: "") }
    var isPrivate by remember { mutableStateOf(repo.isPrivate) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Pengaturan repository", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama repository") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Deskripsi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = homepage, onValueChange = { homepage = it },
                    label = { Text("Situs web (opsional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isPrivate) "Private" else "Public", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isPrivate) "Hanya Anda yang bisa melihat" else "Semua orang bisa melihat",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it }, enabled = !busy)
                }
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                GitHubApi.editRepo(
                                    Store.token.value, repo.owner, repo.name,
                                    name.trim(), desc.trim(), homepage.trim(), isPrivate
                                )
                            }
                            onSaved(updated)
                        } catch (e: Exception) {
                            err = GitHubApi.humanError(e)
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("Simpan") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

// ============================================================
// ============ BUAT BRANCH ====================================
// ============================================================

@Composable
fun CreateBranchDialog(
    owner: String, name: String, fromBranch: String,
    onDismiss: () -> Unit, onCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var branchName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Branch baru", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "Dibuat dari $fromBranch",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = branchName, onValueChange = { branchName = it },
                    label = { Text("Nama branch") },
                    placeholder = { Text("contoh: fitur/unduh-cepat") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = branchName.isNotBlank() && !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.createBranch(Store.token.value, owner, name, fromBranch, branchName.trim())
                            }
                            onCreated(branchName.trim())
                        } catch (e: Exception) {
                            err = when {
                                (e as? com.gitpush.app.data.GhException)?.code == 422 -> "Branch sudah ada (422)"
                                else -> GitHubApi.humanError(e)
                            }
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Buat")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}
