package com.gitpush.app.ui

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gitpush.app.data.GhBranch
import com.gitpush.app.data.GhCommit
import com.gitpush.app.data.GhNode
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RepoScreen(s: Screen.Repo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    var branch by remember { mutableStateOf(s.defaultBranch) }
    var path by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<GhNode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var branches by remember { mutableStateOf<List<GhBranch>>(emptyList()) }
    var showBranchMenu by remember { mutableStateOf(false) }
    var commits by remember { mutableStateOf<List<GhCommit>>(emptyList()) }
    var readme by remember { mutableStateOf<String?>(null) }
    var zipProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var renameTarget by remember { mutableStateOf<GhNode?>(null) }
    var deleteTarget by remember { mutableStateOf<GhNode?>(null) }

    val openDir: (String) -> Unit = { p ->
        scope.launch {
            nodes = null
            error = null
            try {
                nodes = withContext(Dispatchers.IO) {
                    GitHubApi.fetchContents(Store.token.value, s.owner, s.name, p, branch)
                }
                path = p
            } catch (e: Exception) {
                error = GitHubApi.humanError(e)
                nodes = emptyList()
            }
        }
    }

    val reloadMeta: () -> Unit = {
        scope.launch {
            runCatching {
                commits = withContext(Dispatchers.IO) {
                    GitHubApi.fetchCommits(Store.token.value, s.owner, s.name, branch)
                }
            }
        }
        scope.launch {
            readme = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.fetchReadme(Store.token.value, s.owner, s.name, branch)
                }
            }.getOrNull()
        }
    }

    LaunchedEffect(s.fullName) {
        runCatching {
            branches = withContext(Dispatchers.IO) {
                GitHubApi.fetchBranches(Store.token.value, s.owner, s.name)
            }
        }
    }
    LaunchedEffect(branch) {
        openDir("")
        reloadMeta()
    }

    // ---- aksi ----

    val downloadRepo: () -> Unit = {
        scope.launch {
            zipProgress = 0 to 0
            try {
                val zipName = withContext(Dispatchers.IO) {
                    GitHubApi.downloadRepoZip(
                        ctx, Store.token.value, s.owner, s.name, branch, s.isPrivate
                    ) { d, t -> zipProgress = d to t }
                }
                Store.log("download", "Download repository → $zipName", s.fullName)
                toast("Repository tersimpan di Download/GitPush/$zipName")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            } finally {
                zipProgress = null
            }
        }
    }

    val downloadFolder: (GhNode) -> Unit = { node ->
        scope.launch {
            zipProgress = 0 to 0
            try {
                val zipName = withContext(Dispatchers.IO) {
                    GitHubApi.downloadFolderZip(
                        ctx, Store.token.value, s.owner, s.name, branch, node.path
                    ) { d, t -> zipProgress = d to t }
                }
                Store.log("download", "Download folder → $zipName", s.fullName)
                toast("Folder tersimpan di Download/GitPush/$zipName")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            } finally {
                zipProgress = null
            }
        }
    }

    val downloadFile: (GhNode) -> Unit = { node ->
        scope.launch {
            try {
                val loc = withContext(Dispatchers.IO) {
                    GitHubApi.downloadFile(ctx, Store.token.value, s.owner, s.name, node, branch)
                }
                Store.log("download", "Download ${node.name}", s.fullName)
                toast("Tersimpan: $loc")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            }
        }
    }

    val openEditor: (GhNode?) -> Unit = { node ->
        scope.launch {
            try {
                val initial = if (node == null) "" else withContext(Dispatchers.IO) {
                    val meta = GitHubApi.fetchFileMeta(Store.token.value, s.owner, s.name, node.path, branch)
                    if (meta.contentB64 != null) {
                        String(Base64.decode(meta.contentB64, Base64.DEFAULT))
                    } else {
                        String(GitHubApi.fetchBlobBytes(Store.token.value, s.owner, s.name, meta.sha))
                    }
                }
                Store.push(
                    Screen.Editor(
                        owner = s.owner, name = s.name, branch = branch,
                        basePath = path, path = node?.path, initial = initial,
                        sha = node?.sha
                    )
                )
            } catch (e: Exception) {
                toast("Gagal membuka editor: ${GitHubApi.humanError(e)}")
            }
        }
    }

    // ---- UI ----

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Store.pop() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    s.fullName + if (s.isPrivate) " • Private" else " • Public",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = {
                openDir(path)
                reloadMeta()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Segarkan")
            }
        }

        ResponsiveBox {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showBranchMenu = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(branch, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pilih branch")
                        }
                        DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                            if (branches.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(branch) },
                                    onClick = { showBranchMenu = false },
                                    enabled = false
                                )
                            }
                            branches.forEach { b ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            b.name,
                                            fontWeight = if (b.name == branch) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showBranchMenu = false
                                        if (b.name != branch) branch = b.name
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        IconButton(onClick = { openEditor(null) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Buat file baru", tint = GreenPrimary)
                        }
                    }
                }
                item {
                    BreadcrumbRow(path, onOpen = { openDir(it) })
                }
                item {
                    Button(
                        onClick = { if (zipProgress == null) downloadRepo() },
                        enabled = zipProgress == null,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenDeep),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (zipProgress != null) "Mengunduh repository…" else "Download repository (ZIP)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                when {
                    nodes == null -> item {
                        Row(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    }
                    error != null -> item { ErrorCard(error!!) { openDir(path) } }
                    nodes!!.isEmpty() -> item {
                        Text(
                            "Repository kosong — buat file dengan tombol + atau unggah massal dari tab Unggah.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    else -> {
                        val list = nodes ?: emptyList()
                        items(list, key = { it.path }) { node ->
                            FileRow(
                                node = node,
                                onClick = {
                                    if (node.type == "dir") openDir(node.path)
                                    else Store.push(
                                        Screen.Viewer(s.owner, s.name, branch, node.path, node.sha, node.size)
                                    )
                                },
                                onDetail = {
                                    Store.push(
                                        Screen.Viewer(s.owner, s.name, branch, node.path, node.sha, node.size)
                                    )
                                },
                                onDownloadFile = { downloadFile(node) },
                                onDownloadFolder = { downloadFolder(node) },
                                onEdit = { openEditor(node) },
                                onRename = { renameTarget = node },
                                onDelete = { deleteTarget = node }
                            )
                        }
                    }
                }
                if (commits.isNotEmpty()) {
                    item {
                        Text(
                            "Commit terbaru",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(commits, key = { "c-${it.sha}" }) { c ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Text(
                                        c.sha.take(7),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = BlueAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    c.message.lineSequence().firstOrNull() ?: "",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${c.author} • ${timeAgo(c.date)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                if (readme != null && path.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("README.md", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                MarkdownText(readme!!)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (zipProgress != null) {
        Dialog(onDismissRequest = { }) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Mengunduh ZIP…", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    val (d, t) = zipProgress!!
                    if (t > 0) {
                        LinearProgressIndicator(
                            progress = { d.toFloat() / t },
                            modifier = Modifier.fillMaxWidth(),
                            color = GreenPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("$d / $t file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Mengumpulkan file…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { node ->
        RenameDialog(
            node = node,
            currentDir = path,
            onDismiss = { renameTarget = null },
            onDone = { old, new ->
                renameTarget = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            GitHubApi.renameFile(Store.token.value, s.owner, s.name, branch, old, new)
                        }
                        Store.log("rename", "Rename ${old.substringAfterLast('/')} → ${new.substringAfterLast('/')}", s.fullName)
                        toast("File di-rename ✓")
                        openDir(path)
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    }
                }
            }
        )
    }

    deleteTarget?.let { node ->
        DeleteDialog(
            node = node,
            onDismiss = { deleteTarget = null },
            onDone = {
                deleteTarget = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            GitHubApi.deleteFile(
                                Store.token.value, s.owner, s.name, node.path,
                                it, node.sha, branch
                            )
                        }
                        Store.log("delete", "Hapus ${node.name}", s.fullName)
                        toast("File dihapus ✓")
                        openDir(path)
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    }
                }
            }
        )
    }
}

@Composable
private fun BreadcrumbRow(path: String, onOpen: (String) -> Unit) {
    if (path.isEmpty()) return
    val parts = path.split("/")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "root",
            color = BlueAccent,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        parts.forEachIndexed { i, p ->
            Text("  /  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                p,
                color = BlueAccent,
                fontSize = 12.sp,
                fontWeight = if (i == parts.size - 1) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FileRow(
    node: GhNode,
    onClick: () -> Unit,
    onDetail: () -> Unit,
    onDownloadFile: () -> Unit,
    onDownloadFolder: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.weight(1f)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        node.type == "dir" -> Icons.Filled.Folder
                        isImageFile(node.name) -> Icons.Filled.Image
                        isTextFile(node.name) -> Icons.Filled.Code
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = if (node.type == "dir") BlueAccent else GrayMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        if (node.type == "dir") "folder" else formatBytes(node.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu ${node.name}", tint = GrayMuted)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (node.type == "dir") {
                DropdownMenuItem(
                    text = { Text("Download folder (ZIP)") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDownloadFolder() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Detail file") },
                    leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDetail() }
                )
                DropdownMenuItem(
                    text = { Text("Unduh file") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDownloadFile() }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    node: GhNode,
    currentDir: String,
    onDismiss: () -> Unit,
    onDone: (String, String) -> Unit
) {
    var newName by remember { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename file", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    node.path,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nama / path baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Boleh pindah folder, contoh: docs/${node.name}. 1 commit via Git Data API.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val typed = newName.trim()
                    val full = if (typed.contains("/")) typed else
                        if (currentDir.isEmpty()) typed else "$currentDir/$typed"
                    if (typed.isNotEmpty() && full != node.path) onDone(node.path, full) else onDismiss()
                }
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DeleteDialog(
    node: GhNode,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var msg by remember { mutableStateOf("Hapus ${node.name} via GitPush") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus ${node.name}?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "Tindakan ini membuat satu commit dan tidak bisa dibatalkan dari GitPush.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    label = { Text("Pesan commit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (msg.isNotBlank()) onDone(msg.trim()) }) {
                Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
