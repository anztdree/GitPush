package com.gitpush.app.ui

import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitpush.app.data.GhBranch
import com.gitpush.app.data.GhCommit
import com.gitpush.app.data.GhNode
import com.gitpush.app.data.GhRepo
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
    // Pulihkan posisi folder terakhir (bertahan saat Viewer/Editor ditumpuk di atas layar ini)
    var path by remember { mutableStateOf(Store.lastRepoPath["${s.fullName}@${s.defaultBranch}"] ?: "") }
    var nodes by remember { mutableStateOf<List<GhNode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var branches by remember { mutableStateOf<List<GhBranch>>(emptyList()) }
    var showBranchMenu by remember { mutableStateOf(false) }
    var commits by remember { mutableStateOf<List<GhCommit>>(emptyList()) }
    var readme by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showReadme by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<GhNode?>(null) }
    var renameTarget by remember { mutableStateOf<GhNode?>(null) }
    var deleteTarget by remember { mutableStateOf<GhNode?>(null) }
    var showRepoMenu by remember { mutableStateOf(false) }
    var deleteRepoDialog by remember { mutableStateOf(false) }
    var repoInfo by remember { mutableStateOf<GhRepo?>(null) }
    var dirSizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var dirCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var realUsage by remember { mutableStateOf<Long?>(null) }
    // Commit terakhir per file/folder (ala website GitHub) — terisi progresif per baris
    val lastCommits = remember { mutableStateMapOf<String, GhCommit>() }
    // Status aksi repo ala GitHub: star, watch, dialog fitur
    var starred by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }
    var starBusy by remember { mutableStateOf(false) }
    var watchBusy by remember { mutableStateOf(false) }
    var forkBusy by remember { mutableStateOf(false) }
    var showIssues by remember { mutableStateOf(false) }
    var showPulls by remember { mutableStateOf(false) }
    var showReleases by remember { mutableStateOf(false) }
    var showEditRepo by remember { mutableStateOf(false) }
    var showCreateBranch by remember { mutableStateOf(false) }
    var deleteBranchTarget by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    val loadRepoInfo: () -> Unit = {
        scope.launch {
            repoInfo = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.fetchRepo(Store.token.value, s.owner, s.name)
                }
            }.getOrNull()
        }
    }

    // Cache isi folder (kunci "branch@path") — naik/turun folder tampil seketika tanpa nunggu jaringan
    val folderCache = remember { HashMap<String, List<GhNode>>() }

    val openDir: (String) -> Unit = { p ->
        val br = branch
        // Posisi folder di-update DETANG (sebelum fetch jaringan) —
        // tombol/gestur back selalu benar walau daftar folder masih dimuat
        path = p
        Store.lastRepoPath["${s.fullName}@$br"] = p
        error = null
        val cached = folderCache["$br@$p"]
        nodes = cached // cache tampil seketika; null → spinner
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) {
                    GitHubApi.fetchContents(Store.token.value, s.owner, s.name, p, br)
                }
                folderCache["$br@$p"] = fresh
                if (path == p && branch == br) nodes = fresh // masih di folder ini → terapkan
            } catch (e: Exception) {
                if (path == p && branch == br && cached == null) {
                    error = GitHubApi.humanError(e)
                    nodes = emptyList()
                }
                // Gagal saat cache tampil: biarkan tampilan cache, diulang lewat Segarkan
            }
        }
    }

    // Tombol/gestur back ala file manager: naik satu folder dulu;
    // saat sudah di root, back keluar dari layar repo (BackHandler MainScaffold).
    BackHandler(enabled = path.isNotEmpty()) {
        openDir(path.substringBeforeLast('/', ""))
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

    LaunchedEffect(nodes, branch) {
        // Status "commit terakhir" per baris file/folder — seperti website GitHub.
        // Daftar langsung tampil; baris info commit menyusul satu per satu (progresif).
        val list = nodes ?: return@LaunchedEffect
        lastCommits.clear()
        if (list.isEmpty()) return@LaunchedEffect
        GitHubApi.fetchLastCommits(
            Store.token.value, s.owner, s.name, branch, list.map { it.path }
        ) { p, info -> lastCommits[p] = info }
    }

    LaunchedEffect(s.fullName) {
        runCatching {
            branches = withContext(Dispatchers.IO) {
                GitHubApi.fetchBranches(Store.token.value, s.owner, s.name)
            }
        }
        loadRepoInfo()
        // Status star & watch (diam-diam bila gagal)
        scope.launch {
            starred = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.isStarred(Store.token.value, s.owner, s.name) }
            }.getOrDefault(false)
            watching = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.isWatching(Store.token.value, s.owner, s.name) }
            }.getOrDefault(false)
        }
    }
    LaunchedEffect(branch) {
        // Pulihkan folder terakhir branch ini (root bila belum pernah navigasi)
        openDir(Store.lastRepoPath["${s.fullName}@$branch"] ?: "")
        reloadMeta()
        // Latar belakang: ukuran per folder + total pemakaian riil (termasuk objek Git LFS)
        scope.launch {
            runCatching {
                val tree = withContext(Dispatchers.IO) {
                    val c = GitHubApi.refSha(Store.token.value, s.owner, s.name, branch)
                    val t = GitHubApi.commitTreeSha(Store.token.value, s.owner, s.name, c)
                    GitHubApi.fetchTreeRecursive(Store.token.value, s.owner, s.name, t)
                }
                val blobs = tree.filter { it.type == "blob" }
                val ptrs = withContext(Dispatchers.IO) {
                    GitHubApi.resolveLfsPointers(
                        Store.token.value, s.owner, s.name, blobs.map { it.sha to it.size }
                    )
                }
                val sums = HashMap<String, Long>()
                val counts = HashMap<String, Int>()
                var total = 0L
                for (b in blobs) {
                    val rs = ptrs[b.sha]?.second ?: b.size
                    total += rs
                    var parent = b.path.substringBeforeLast('/', "")
                    if (parent.isNotEmpty()) counts[parent] = (counts[parent] ?: 0) + 1
                    while (parent.isNotEmpty()) {
                        sums[parent] = (sums[parent] ?: 0L) + rs
                        parent = parent.substringBeforeLast('/', "")
                    }
                }
                dirSizes = sums
                dirCounts = counts
                realUsage = total
                // Bagikan hasil ke cache global → kartu Beranda & kuota Upload ikut akurat
                GitHubApi.putUsageCache(s.owner, s.name, branch, total)
            }
        }
    }

    // ---- aksi ----

    val downloadRepo: () -> Unit = {
        scope.launch {
            Store.showOp("Unduh repository (ZIP)", s.fullName, unit = "file")
            Store.opDetail("Mengumpulkan daftar file…")
            try {
                val zipName = withContext(Dispatchers.IO) {
                    GitHubApi.downloadRepoZip(
                        ctx, Store.token.value, s.owner, s.name, branch, s.isPrivate
                    ) { d, t, cur -> Store.opStep(d.toLong(), t.toLong(), cur.substringAfterLast('/')) }
                }
                Store.log("download", "Download repository → $zipName", s.fullName)
                toast("Repository tersimpan di Download/GitPush/$zipName")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            } finally {
                Store.hideOp()
            }
        }
    }

    val downloadFolder: (GhNode) -> Unit = { node ->
        scope.launch {
            Store.showOp("Unduh folder (ZIP)", node.name, unit = "file")
            Store.opDetail("Mengumpulkan daftar file…")
            try {
                val zipName = withContext(Dispatchers.IO) {
                    GitHubApi.downloadFolderZip(
                        ctx, Store.token.value, s.owner, s.name, branch, node.path
                    ) { d, t, cur -> Store.opStep(d.toLong(), t.toLong(), cur.substringAfterLast('/')) }
                }
                Store.log("download", "Download folder → $zipName", s.fullName)
                toast("Folder tersimpan di Download/GitPush/$zipName")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            } finally {
                Store.hideOp()
            }
        }
    }

    val downloadFile: (GhNode) -> Unit = { node ->
        scope.launch {
            Store.showOp("Mengunduh file", node.name)
            try {
                val loc = withContext(Dispatchers.IO) {
                    GitHubApi.downloadFile(
                        ctx, Store.token.value, s.owner, s.name, node, branch,
                        onStage = { Store.opDetail(it) },
                        onProgress = { sent, total -> Store.opProgress(sent, total) }
                    )
                }
                Store.log("download", "Download ${node.name}", s.fullName)
                toast("Tersimpan: $loc")
            } catch (e: Exception) {
                toast("Gagal: ${GitHubApi.humanError(e)}")
            } finally {
                Store.hideOp()
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
                if (node != null && GitHubApi.lfsPointerInfo(initial) != null) {
                    toast("File Git LFS tidak bisa diedit di sini — gunakan Unduh untuk file aslinya")
                    return@launch
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

    // ---- Aksi repo ala GitHub: star, watch, fork, salin URL ----

    val toggleStar: () -> Unit = {
        if (!starBusy) {
            starBusy = true
            val target = !starred
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        GitHubApi.setStarred(Store.token.value, s.owner, s.name, target)
                    }
                    starred = target
                    Store.log("repo", if (target) "Star ${s.name}" else "Unstar ${s.name}", s.fullName)
                    toast(if (target) "Repo ini sekarang Anda sukai ★" else "Bintang dilepas")
                } catch (e: Exception) {
                    toast("Gagal: ${GitHubApi.humanError(e)}")
                } finally {
                    starBusy = false
                }
            }
        }
    }

    val toggleWatch: () -> Unit = {
        if (!watchBusy) {
            watchBusy = true
            val target = !watching
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        GitHubApi.setWatching(Store.token.value, s.owner, s.name, target)
                    }
                    watching = target
                    toast(if (target) "Memantau repository — notifikasi aktif" else "Berhenti memantau")
                } catch (e: Exception) {
                    toast("Gagal: ${GitHubApi.humanError(e)}")
                } finally {
                    watchBusy = false
                }
            }
        }
    }

    val forkRepo: () -> Unit = {
        if (!forkBusy) {
            forkBusy = true
            scope.launch {
                try {
                    val forked = withContext(Dispatchers.IO) {
                        GitHubApi.forkRepo(Store.token.value, s.owner, s.name)
                    }
                    Store.log("repo", "Fork ${s.name}", forked.fullName)
                    toast("Fork dibuat: ${forked.fullName} ✓")
                } catch (e: Exception) {
                    toast("Gagal: ${GitHubApi.humanError(e)}")
                } finally {
                    forkBusy = false
                }
            }
        }
    }

    // ---- UI ----

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (path.isNotEmpty()) openDir(path.substringBeforeLast('/', "")) else Store.pop()
            }) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (s.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                        contentDescription = null,
                        tint = GrayMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (s.isPrivate) "Privat" else "Publik",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    repoInfo?.language?.let { l ->
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.size(8.dp).background(langColor(l), CircleShape))
                        Spacer(Modifier.size(4.dp))
                        Text(l, color = GrayMuted, fontSize = 11.sp)
                    }
                }
            }
            IconButton(onClick = {
                openDir(path)
                reloadMeta()
                loadRepoInfo()
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Segarkan")
            }
            IconButton(onClick = { showRepoMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu repository")
            }
            DropdownMenu(expanded = showRepoMenu, onDismissRequest = { showRepoMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit repository") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showRepoMenu = false
                        if (repoInfo != null) showEditRepo = true else toast("Info repository belum termuat")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Issues") },
                    leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                    onClick = { showRepoMenu = false; showIssues = true }
                )
                DropdownMenuItem(
                    text = { Text("Pull request") },
                    leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(17.dp), tint = GreenPrimary) },
                    onClick = { showRepoMenu = false; showPulls = true }
                )
                DropdownMenuItem(
                    text = { Text("Releases") },
                    leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(17.dp), tint = PurpleAccent) },
                    onClick = { showRepoMenu = false; showReleases = true }
                )
                DropdownMenuItem(
                    text = { Text("Branch baru…") },
                    leadingIcon = { Icon(Icons.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                    onClick = { showRepoMenu = false; showCreateBranch = true }
                )
                DropdownMenuItem(
                    text = { Text("Salin URL") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showRepoMenu = false
                        clipboard.setText(AnnotatedString("https://github.com/${s.fullName}"))
                        toast("URL repository disalin")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Buka di browser") },
                    leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                    onClick = {
                        showRepoMenu = false
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${s.fullName}"))
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                DropdownMenuItem(
                    text = { Text("Hapus repository", color = RedDanger) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = RedDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showRepoMenu = false
                        deleteRepoDialog = true
                    }
                )
            }
        }

        ResponsiveBox {
            LazyColumn(Modifier.fillMaxSize()) {
                // ===== Kartu ringkasan + aksi cepat ala GitHub =====
                item {
                    RepoOverviewCard(
                        fullName = s.fullName,
                        info = repoInfo,
                        isPrivate = s.isPrivate,
                        starred = starred,
                        watching = watching,
                        starBusy = starBusy,
                        watchBusy = watchBusy,
                        forkBusy = forkBusy,
                        usage = realUsage,
                        onStar = toggleStar,
                        onWatch = toggleWatch,
                        onFork = forkRepo,
                        onZip = downloadRepo
                    )
                }
                // ===== Toolbar ringkas: branch + file baru + riwayat + README =====
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showBranchMenu = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                branch,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pilih branch")
                        }
                        DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Branch baru dari $branch",
                                        color = GreenPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.CallSplit, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(17.dp))
                                },
                                onClick = {
                                    showBranchMenu = false
                                    showCreateBranch = true
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                                    trailingIcon = {
                                        if (b.name != s.defaultBranch) {
                                            IconButton(
                                                onClick = { showBranchMenu = false; deleteBranchTarget = b.name },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Hapus branch ${b.name}",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        showBranchMenu = false
                                        if (b.name != branch) branch = b.name
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.size(6.dp))
                        IconButton(onClick = { openEditor(null) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Buat file baru", tint = GreenPrimary)
                        }
                        IconButton(onClick = { showHistory = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.History, contentDescription = "Riwayat commit", tint = BlueAccent)
                        }
                        IconButton(onClick = { showReadme = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Article, contentDescription = "README", tint = GreenPrimary)
                        }
                    }
                }
                item {
                    BreadcrumbRow(path, onOpen = { openDir(it) })
                }
                if ((realUsage ?: 0L) > 0L || (repoInfo?.sizeKb ?: 0L) > 0L) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                                QuotaBar(usedBytes = realUsage ?: (repoInfo?.sizeKb ?: 0L) * 1024)
                            }
                        }
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
                                dirBytes = if (node.type == "dir") dirSizes[node.path] else null,
                                dirItems = if (node.type == "dir") dirCounts[node.path] else null,
                                lastCommit = lastCommits[node.path],
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
                                onMove = { moveTarget = node },
                                onDelete = { deleteTarget = node }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showHistory) {
        CommitHistoryDialog(
            owner = s.owner,
            name = s.name,
            branch = branch,
            preloaded = commits,
            onDismiss = { showHistory = false }
        )
    }

    if (showReadme) {
        ReadmeDialog(
            owner = s.owner,
            name = s.name,
            branch = branch,
            preloaded = readme,
            onDismiss = { showReadme = false }
        )
    }

    renameTarget?.let { node ->
        RenameDialog(
            node = node,
            currentDir = path,
            isFolder = node.type == "dir",
            onDismiss = { renameTarget = null },
            onDone = { old, new ->
                renameTarget = null
                scope.launch {
                    Store.showOp(if (node.type == "dir") "Rename folder" else "Rename file", "$old → $new")
                    try {
                        if (node.type == "dir") {
                            val (_, n) = withContext(Dispatchers.IO) {
                                GitHubApi.renameFolder(
                                    Store.token.value, s.owner, s.name, branch, old, new,
                                    onStage = { Store.opDetail(it) }
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("rename", "Rename folder ${old.substringAfterLast('/')} → ${new.substringAfterLast('/')}", s.fullName)
                            toast("Folder di-rename ✓ ($n file dipindah)")
                            openDir(navUpPath(path, old))
                        } else {
                            withContext(Dispatchers.IO) {
                                GitHubApi.renameFile(
                                    Store.token.value, s.owner, s.name, branch, old, new,
                                    onStage = { Store.opDetail(it) }
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("rename", "Rename ${old.substringAfterLast('/')} → ${new.substringAfterLast('/')}", s.fullName)
                            toast("File di-rename ✓")
                            openDir(path)
                        }
                        loadRepoInfo()
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    } finally {
                        Store.hideOp()
                    }
                }
            }
        )
    }

    moveTarget?.let { node ->
        MoveDialog(
            node = node,
            owner = s.owner,
            name = s.name,
            branch = branch,
            startDir = path,
            onDismiss = { moveTarget = null },
            onDone = { old, new ->
                moveTarget = null
                scope.launch {
                    Store.showOp(if (node.type == "dir") "Pindah folder" else "Pindah file", "$old → $new")
                    try {
                        if (node.type == "dir") {
                            val (_, n) = withContext(Dispatchers.IO) {
                                GitHubApi.renameFolder(
                                    Store.token.value, s.owner, s.name, branch, old, new,
                                    onStage = { Store.opDetail(it) }
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("move", "Pindah folder ${old.substringAfterLast('/')} → $new", s.fullName)
                            toast("Folder dipindah ✓ ($n file)")
                            openDir(navUpPath(path, old))
                        } else {
                            withContext(Dispatchers.IO) {
                                GitHubApi.renameFile(
                                    Store.token.value, s.owner, s.name, branch, old, new,
                                    onStage = { Store.opDetail(it) }
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("move", "Pindah ${old.substringAfterLast('/')} → $new", s.fullName)
                            toast("File dipindah ✓")
                            openDir(path)
                        }
                        loadRepoInfo()
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    } finally {
                        Store.hideOp()
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
                    Store.showOp(if (node.type == "dir") "Hapus folder" else "Hapus file", node.path)
                    try {
                        if (node.type == "dir") {
                            val (_, n) = withContext(Dispatchers.IO) {
                                GitHubApi.deleteFolder(
                                    Store.token.value, s.owner, s.name, branch, node.path,
                                    onStage = { Store.opDetail(it) }
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("delete", "Hapus folder ${node.name} ($n file)", s.fullName)
                            toast("Folder dihapus ✓ ($n file)")
                            openDir(navUpPath(path, node.path))
                        } else {
                            withContext(Dispatchers.IO) {
                                GitHubApi.deleteFile(
                                    Store.token.value, s.owner, s.name, node.path,
                                    it, node.sha, branch
                                )
                            }
                            GitHubApi.invalidateUsage(s.owner, s.name)
                            folderCache.clear()
                            Store.log("delete", "Hapus ${node.name}", s.fullName)
                            toast("File dihapus ✓")
                            openDir(path)
                        }
                        loadRepoInfo()
                    } catch (e: Exception) {
                        toast("Gagal: ${GitHubApi.humanError(e)}")
                    } finally {
                        Store.hideOp()
                    }
                }
            }
        )
    }

    if (deleteRepoDialog) {
        DeleteRepoDialog(
            owner = s.owner,
            name = s.name,
            onDismiss = { deleteRepoDialog = false },
            onDeleted = {
                deleteRepoDialog = false
                Store.pop() // repo sudah tidak ada — kembali ke Beranda
            }
        )
    }

    // ===== Dialog fitur lengkap ala GitHub =====

    if (showIssues) {
        IssuesDialog(owner = s.owner, name = s.name, onDismiss = { showIssues = false })
    }
    if (showPulls) {
        PullsDialog(owner = s.owner, name = s.name, onDismiss = { showPulls = false })
    }
    if (showReleases) {
        ReleasesDialog(owner = s.owner, name = s.name, onDismiss = { showReleases = false })
    }
    if (showEditRepo) {
        repoInfo?.let { ri ->
            EditRepoDialog(
                repo = ri,
                onDismiss = { showEditRepo = false },
                onSaved = { upd ->
                    showEditRepo = false
                    val oldFullName = ri.fullName
                    repoInfo = upd
                    if (upd.name != s.name) {
                        GitHubApi.invalidateUsage(s.owner, s.name)
                        // Ganti layar repo di tumpukan dengan nama baru — repo lama tidak ada lagi
                        Store.stack[Store.stack.lastIndex] = Screen.Repo(
                            owner = s.owner,
                            name = upd.name,
                            fullName = upd.fullName,
                            defaultBranch = upd.defaultBranch,
                            isPrivate = upd.isPrivate
                        )
                    }
                    Store.repos.value = Store.repos.value.map {
                        if (it.fullName == oldFullName) upd else it
                    }
                    toast("Perubahan repository tersimpan ✓")
                }
            )
        }
    }
    if (showCreateBranch) {
        CreateBranchDialog(
            owner = s.owner,
            name = s.name,
            fromBranch = branch,
            onDismiss = { showCreateBranch = false },
            onCreated = { newName ->
                showCreateBranch = false
                toast("Branch $newName dibuat ✓")
                scope.launch {
                    branches = runCatching {
                        withContext(Dispatchers.IO) {
                            GitHubApi.fetchBranches(Store.token.value, s.owner, s.name)
                        }
                    }.getOrDefault(branches)
                }
            }
        )
    }
    deleteBranchTarget?.let { bName ->
        AlertDialog(
            onDismissRequest = { deleteBranchTarget = null },
            title = { Text("Hapus branch $bName?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Text(
                    "Branch dan referensinya dihapus dari GitHub. Commit yang masih dirujuk branch lain tetap aman.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = bName
                    deleteBranchTarget = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.deleteBranch(Store.token.value, s.owner, s.name, target)
                            }
                            toast("Branch $target dihapus ✓")
                            if (target == branch) branch = s.defaultBranch
                            branches = runCatching {
                                withContext(Dispatchers.IO) {
                                    GitHubApi.fetchBranches(Store.token.value, s.owner, s.name)
                                }
                            }.getOrDefault(branches)
                        } catch (e: Exception) {
                            toast("Gagal: ${GitHubApi.humanError(e)}")
                        }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteBranchTarget = null }) { Text("Batal") } }
        )
    }
}

/**
 * Setelah rename/hapus folder: bila kita sedang berada DI DALAM folder tsb,
 * naik ke folder induknya; selain itu tetap di path sekarang.
 */
private fun navUpPath(current: String, target: String): String =
    if (current == target || current.startsWith("$target/")) {
        target.substringBeforeLast('/', "")
    } else current

@Composable
private fun BreadcrumbRow(path: String, onOpen: (String) -> Unit) {
    if (path.isEmpty()) return
    val parts = path.split("/")
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "root",
            color = BlueAccent,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .clickable { onOpen("") }
        )
        parts.forEachIndexed { i, p ->
            Text("  /  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(
                p,
                color = BlueAccent,
                fontSize = 12.sp,
                fontWeight = if (i == parts.size - 1) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable { onOpen(parts.take(i + 1).joinToString("/")) }
            )
        }
    }
}

@Composable
private fun FileRow(
    node: GhNode,
    dirBytes: Long? = null,
    dirItems: Int? = null,
    lastCommit: GhCommit? = null,
    onClick: () -> Unit,
    onDetail: () -> Unit,
    onDownloadFile: () -> Unit,
    onDownloadFolder: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeBadge(node.name, node.type == "dir", size = 40.dp, iconSize = 21.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        node.name,
                        fontSize = 14.sp,
                        fontWeight = if (node.type == "dir") FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (node.isLfs) {
                        Spacer(Modifier.width(6.dp))
                        LfsTag()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status update terakhir: HANYA waktu — tanpa pesan commit.
                    // (Pesan commit memotong tampilan waktu di layar sempit.)
                    val shownTime = lastCommit?.let { lc -> timeAgo(lc.date) }
                    if (shownTime != null && shownTime != "-") {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = GreenPrimary.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            shownTime,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        when {
                            node.type == "dir" -> buildString {
                                dirItems?.let { append("$it item") }
                                dirBytes?.let {
                                    if (isNotEmpty()) append(" • ")
                                    append(formatBytes(it))
                                }
                                if (isEmpty()) append("folder")
                            }
                            node.isLfs -> "Git LFS • ${formatBytes(node.size)}"
                            else -> formatBytes(node.size)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GrayMuted.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu ${node.name}", tint = GrayMuted, modifier = Modifier.size(17.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (node.type == "dir") {
                DropdownMenuItem(
                    text = { Text("Buka folder") },
                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = FilePalette.Folder) },
                    onClick = { menu = false; onClick() }
                )
                DropdownMenuItem(
                    text = { Text("Download folder (ZIP)") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDownloadFolder() }
                )
                DropdownMenuItem(
                    text = { Text("Pindah folder") },
                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                    onClick = { menu = false; onMove() }
                )
                DropdownMenuItem(
                    text = { Text("Rename folder") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Hapus folder", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onDelete() }
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
                    text = { Text("Pindah") },
                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                    onClick = { menu = false; onMove() }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp), tint = GrayMuted) },
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
    isFolder: Boolean,
    onDismiss: () -> Unit,
    onDone: (String, String) -> Unit
) {
    var newName by remember { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isFolder) "Rename folder" else "Rename file",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
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
                    label = { Text(if (isFolder) "Nama folder baru" else "Nama / path baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isFolder)
                        "Seluruh isi folder dipindah dalam 1 commit. Untuk memindahkan folder, ketik path baru, contoh: arsip/${node.name}."
                    else
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
                    val typed = newName.trim().trim('/')
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
    val isFolder = node.type == "dir"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isFolder) "Hapus folder ${node.name}?" else "Hapus ${node.name}?",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column {
                Text(
                    if (isFolder)
                        "Seluruh isi folder \"${node.path}\" akan dihapus dalam satu commit dan tidak bisa dibatalkan dari GitPush."
                    else
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

// ============ RIWAYAT COMMIT (dialog — tidak membanjiri layar repo) ============

@Composable
private fun CommitHistoryDialog(
    owner: String, name: String, branch: String,
    preloaded: List<GhCommit>,
    onDismiss: () -> Unit
) {
    var list by remember { mutableStateOf(preloaded) }
    var loading by remember { mutableStateOf(preloaded.isEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(branch) {
        if (preloaded.isNotEmpty()) return@LaunchedEffect
        try {
            list = withContext(Dispatchers.IO) {
                GitHubApi.fetchCommits(Store.token.value, owner, name, branch)
            }
        } catch (e: Exception) {
            err = GitHubApi.humanError(e)
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Riwayat commit", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$name@$branch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
                    err != null -> Text(
                        err!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                    list.isEmpty() -> Text(
                        "Belum ada commit pada branch ini.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.sha }) { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                FileTypeBadge("${c.sha.take(7)}.txt", false, size = 34.dp, iconSize = 17.dp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.message.lineSequence().firstOrNull() ?: "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
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
                                            "${c.author} • ${timeAgo(c.date)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.padding(start = 62.dp)
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ============ README (dialog layar penuh — render markdown) ============

@Composable
private fun ReadmeDialog(
    owner: String, name: String, branch: String,
    preloaded: String?,
    onDismiss: () -> Unit
) {
    var md by remember { mutableStateOf(preloaded) }
    var loading by remember { mutableStateOf(preloaded == null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(branch) {
        if (preloaded != null) return@LaunchedEffect
        try {
            md = withContext(Dispatchers.IO) {
                GitHubApi.fetchReadme(Store.token.value, owner, name, branch)
            }
        } catch (e: Exception) {
            err = GitHubApi.humanError(e)
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("README.md", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$name@$branch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
                    err != null -> Text(
                        err!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                    md == null -> Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        FileTypeBadge("README.md", false, size = 52.dp, iconSize = 28.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Repository ini belum punya README.md",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        MarkdownText(md!!)
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

// ============ PINDAH FILE/FOLDER (pilih folder tujuan) ============

/**
 * Dialog pindah: jelajahi folder repository (breadcrumb ala file manager),
 * pilih folder tujuan, konfirmasi. Move = 1 commit via Git Data API.
 */
@Composable
private fun MoveDialog(
    node: GhNode,
    owner: String,
    name: String,
    branch: String,
    startDir: String,
    onDismiss: () -> Unit,
    onDone: (String, String) -> Unit
) {
    val isFolder = node.type == "dir"
    var dest by remember { mutableStateOf(startDir) }
    var dirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var clash by remember { mutableStateOf(false) }

    suspend fun loadDirs(p: String) {
        loading = true
        err = null
        clash = false
        try {
            val nodes = withContext(Dispatchers.IO) {
                GitHubApi.fetchContents(Store.token.value, owner, name, p, branch)
            }
            dirs = nodes.filter { it.type == "dir" }.map { it.name }
            clash = nodes.any { it.name == node.name }
        } catch (e: Exception) {
            err = GitHubApi.humanError(e)
            dirs = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(dest) { loadDirs(dest) }

    // Validasi tujuan
    val newPath = if (dest.isEmpty()) node.name else "$dest/${node.name}"
    val invalidSelf = isFolder && (dest == node.path || dest.startsWith("${node.path}/"))
    val invalidSame = newPath == node.path
    val canMove = !invalidSelf && !invalidSame && !clash && !loading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isFolder) "Pindah folder" else "Pindah file",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column {
                Text(
                    node.path,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                // Breadcrumb tujuan
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "root",
                        color = BlueAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { dest = "" }
                    )
                    if (dest.isNotEmpty()) {
                        val parts = dest.split("/")
                        parts.forEachIndexed { i, p ->
                            Text("  /  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text(
                                p,
                                color = BlueAccent,
                                fontSize = 12.sp,
                                fontWeight = if (i == parts.size - 1) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clickable { dest = parts.take(i + 1).joinToString("/") }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Daftar folder di tujuan
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    when {
                        loading -> Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
                        err != null -> Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        dirs.isEmpty() -> Text(
                            "Tidak ada subfolder di sini.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            dirs.forEach { d ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { dest = if (dest.isEmpty()) d else "$dest/$d" }
                                        .padding(vertical = 9.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = FilePalette.Folder,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(d, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Path baru: $newPath",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canMove) GreenPrimary else MaterialTheme.colorScheme.error
                )
                if (invalidSelf) Text(
                    "Tidak bisa memindah folder ke dalam dirinya sendiri.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
                if (clash) Text(
                    "Sudah ada item bernama \"${node.name}\" di folder tujuan.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canMove, onClick = { onDone(node.path, newPath) }) {
                Text("Pindah ke sini", color = if (canMove) BlueAccent else GrayMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ============================================================
// ============ KARTU RINGKASAN + AKSI CEPAT REPO =============
// ============================================================

/** Kartu ringkasan repository: deskripsi, statistik, dan baris aksi cepat ala GitHub. */
@Composable
private fun RepoOverviewCard(
    fullName: String,
    info: GhRepo?,
    isPrivate: Boolean,
    starred: Boolean,
    watching: Boolean,
    starBusy: Boolean,
    watchBusy: Boolean,
    forkBusy: Boolean,
    usage: Long?,
    onStar: () -> Unit,
    onWatch: () -> Unit,
    onFork: () -> Unit,
    onZip: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        HeroPanel {
            // ===== Baris identitas: ikon kaca + nama + chip =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fullName,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        // chip visibilitas gelap-transparan di atas hero
                        Surface(shape = RoundedCornerShape(7.dp), color = Color.White.copy(alpha = 0.16f)) {
                            Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isPrivate) "Privat" else "Publik",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    info?.language?.let { l ->
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(langColor(l), CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text(l, color = Color.White.copy(alpha = 0.78f), fontSize = 11.5.sp)
                        }
                    }
                }
            }
            if (!info?.description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    info!!.description!!,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            // ===== Statistik 4 kolom ala profil GitHub =====
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat("${info?.stars ?: 0}", "star")
                HeroStat("${info?.forks ?: 0}", "fork")
                HeroStat("${info?.issues ?: 0}", "issue")
                HeroStat("${info?.watchers ?: 0}", "pantau")
                HeroStat(usage?.let { formatBytes(it) } ?: "…", "ukuran")
            }
            Spacer(Modifier.height(12.dp))
            // ===== 4 aksi cepat di dalam hero =====
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroAction(
                    icon = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    label = if (starred) "Disukai" else "Star",
                    active = starred,
                    enabled = !starBusy,
                    onClick = onStar,
                    modifier = Modifier.weight(1f)
                )
                HeroAction(
                    icon = Icons.Filled.CallSplit,
                    label = "Fork",
                    active = false,
                    enabled = !forkBusy,
                    onClick = onFork,
                    modifier = Modifier.weight(1f)
                )
                HeroAction(
                    icon = Icons.Filled.Visibility,
                    label = if (watching) "Dipantau" else "Pantau",
                    active = watching,
                    enabled = !watchBusy,
                    onClick = onWatch,
                    modifier = Modifier.weight(1f)
                )
                HeroAction(
                    icon = Icons.Filled.Download,
                    label = "ZIP",
                    active = false,
                    enabled = true,
                    onClick = onZip,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Statistik hero: angka tebal putih + label kecil transparan. */
@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
    }
}

/** Aksi cepat di atas hero: kotak kaca + ikon + label — aktif menyala kuning. */
@Composable
private fun HeroAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = if (active) 0.24f else 0.11f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color(0xFFFFE082) else Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color(0xFFFFE082) else Color.White.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

/** Tombol aksi kecil: ikon di atas label — ringkas, tidak memakan ruang vertikal. */
@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else GrayMuted
            )
        }
    }
}
