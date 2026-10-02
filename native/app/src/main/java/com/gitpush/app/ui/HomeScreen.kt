package com.gitpush.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhException
import com.gitpush.app.data.GhRepo
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private enum class SearchMode { MINE, GLOBAL }

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(Store.repos.value.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.MINE) }
    var filter by remember { mutableStateOf("all") } // all | public | private
    var showCreate by remember { mutableStateOf(false) }
    // Pemakaian riil per repository (termasuk objek Git LFS): fullName → byte (-1 = gagal hitung)
    var usageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    // Repo yang sedang diminta aksi (menu kartu)
    var deleteTarget by remember { mutableStateOf<GhRepo?>(null) }
    var forkTarget by remember { mutableStateOf<GhRepo?>(null) }
    var forkBusy by remember { mutableStateOf(false) }
    // Hasil pencarian global
    var globalResults by remember { mutableStateOf<List<GhRepo>?>(null) }
    var globalSearching by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    val computeUsages: (Boolean) -> Unit = { force ->
        val repos = Store.repos.value
        if (repos.isNotEmpty()) {
            scope.launch {
                val sem = Semaphore(3)
                coroutineScope {
                    repos.forEach { r ->
                        launch {
                            sem.withPermit {
                                val v = runCatching {
                                    GitHubApi.fetchRepoUsage(
                                        Store.token.value, r.owner, r.name, r.defaultBranch, force
                                    )
                                }.getOrDefault(-1L)
                                usageMap = usageMap + (r.fullName to v)
                            }
                        }
                    }
                }
            }
        }
    }

    val load: (Boolean) -> Unit = { force ->
        scope.launch {
            if (force || Store.repos.value.isEmpty()) {
                if (Store.repos.value.isEmpty()) loading = true
                error = null
                try {
                    Store.repos.value = withContext(Dispatchers.IO) {
                        GitHubApi.fetchRepos(Store.token.value)
                    }
                } catch (e: Exception) {
                    error = GitHubApi.humanError(e)
                } finally {
                    loading = false
                }
            }
            computeUsages(force)
        }
    }
    LaunchedEffect(Unit) { load(false) }

    // Pencarian global: debounce sederhana 500 ms
    val q = query.trim()
    LaunchedEffect(q, searchMode) {
        if (searchMode == SearchMode.GLOBAL && q.length >= 2) {
            globalSearching = true
            kotlinx.coroutines.delay(500)
            try {
                globalResults = withContext(Dispatchers.IO) {
                    GitHubApi.searchRepos(Store.token.value, q)
                }
            } catch (e: Exception) {
                globalResults = emptyList()
            }
            globalSearching = false
        } else {
            globalResults = null
            globalSearching = false
        }
    }

    val filtered = when {
        searchMode == SearchMode.GLOBAL -> globalResults ?: emptyList()
        q.isEmpty() -> Store.repos.value
        else -> Store.repos.value.filter {
            it.name.lowercase().contains(q.lowercase()) ||
                (it.description?.lowercase()?.contains(q.lowercase()) == true) ||
                it.fullName.lowercase().contains(q.lowercase())
        }
    }.let { base ->
        when (filter) {
            "public" -> base.filter { !it.isPrivate }
            "private" -> base.filter { it.isPrivate }
            else -> base
        }
    }

    val totalKnown = Store.repos.value.isNotEmpty() &&
        Store.repos.value.all { (usageMap[it.fullName] ?: -1L) >= 0L }
    val totalBytes = Store.repos.value.sumOf { usageMap[it.fullName] ?: 0L }
    val user = Store.user.value
    val userName = user?.name?.takeIf { it.isNotBlank() } ?: user?.login ?: ""
    val pubCount = Store.repos.value.count { !it.isPrivate }
    val privCount = Store.repos.value.count { it.isPrivate }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ResponsiveBox {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                // ===== HERO: sapaan + identitas + ringkasan penyimpanan =====
                item(key = "hero") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        HeroPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                        .padding(2.dp)
                                ) {
                                    Avatar(user?.avatarUrl ?: "", 48.dp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Halo, $userName",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 19.sp,
                                        letterSpacing = (-0.3).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "@${user?.login ?: "…"} • penyimpanan GitHub Anda",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { load(true) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Segarkan", tint = Color.White)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            // Ringkasan penyimpanan. CATATAN: batas 2 GB GitHub berlaku PER
                            // REPOSITORY, bukan total — angka utama = total SEMUA repo,
                            // sedangkan bar mengukur repo TERBESAR terhadap batas 2 GB/repo.
                            val largestRepo = Store.repos.value.maxByOrNull { usageMap[it.fullName] ?: -1L }
                            val largestUsage = largestRepo?.let { usageMap[it.fullName] } ?: -1L
                            val largestKnown = largestUsage >= 0L
                            val largestBytes = if (largestKnown) largestUsage else 0L
                            val perRepoRatio = (largestBytes.toFloat() / (2f * 1024 * 1024 * 1024)).coerceIn(0f, 1f)
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        formatBytes(totalBytes),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 21.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "terpakai — total semua repo",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${Store.repos.value.size} repo",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f))) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(perRepoRatio)
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Brush.horizontalGradient(listOf(Color(0xFF7EE787), Color(0xFF56D364))))
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (largestKnown)
                                        "Terbesar: ${largestRepo?.name ?: "—"} • ${formatBytes(largestBytes)} dari 2 GB (${(perRepoRatio * 100).toInt()}%)"
                                    else
                                        "batas GitHub 2 GB per repository",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$pubCount publik", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$privCount privat", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                                    }
                                    if (!totalKnown) {
                                        Text("menghitung penyimpanan…", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                // ===== PENCARIAN + FILTER =====
                item(key = "search") {
                    Column {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = if (searchMode == SearchMode.GLOBAL) "Cari di seluruh GitHub…" else "Cari repository saya…",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = searchMode == SearchMode.MINE,
                                onClick = { searchMode = SearchMode.MINE },
                                label = { Text("Repo saya") }
                            )
                            FilterChip(
                                selected = searchMode == SearchMode.GLOBAL,
                                onClick = { searchMode = SearchMode.GLOBAL },
                                label = { Text("Semua GitHub") }
                            )
                            if (searchMode == SearchMode.MINE) {
                                Spacer(Modifier.width(6.dp))
                                FilterChip(
                                    selected = filter == "all",
                                    onClick = { filter = "all" },
                                    label = { Text("Semua") }
                                )
                                FilterChip(
                                    selected = filter == "public",
                                    onClick = { filter = "public" },
                                    label = { Text("Publik") }
                                )
                                FilterChip(
                                    selected = filter == "private",
                                    onClick = { filter = "private" },
                                    label = { Text("Privat") }
                                )
                            }
                        }
                    }
                }
                when {
                    searchMode == SearchMode.GLOBAL && globalSearching ->
                        item(key = "loading") { SkeletonRows(5, Modifier.padding(top = 10.dp)) }
                    loading && searchMode == SearchMode.MINE ->
                        item(key = "loading") { SkeletonRows(6, Modifier.padding(top = 10.dp)) }
                    error != null && searchMode == SearchMode.MINE ->
                        item(key = "error") { ErrorCard(error!!) { load(true) } }
                    filtered.isEmpty() ->
                        item(key = "empty") { EmptyState(
                            Icons.Filled.Folder,
                            when {
                                searchMode == SearchMode.GLOBAL && q.length < 2 -> "Ketik minimal 2 huruf"
                                searchMode == SearchMode.GLOBAL -> "Tidak ditemukan di GitHub"
                                query.isBlank() -> "Belum ada repository"
                                else -> "Tidak ditemukan"
                            },
                            when {
                                searchMode == SearchMode.GLOBAL && q.length < 2 -> "Pencarian global mencari repository publik di seluruh GitHub"
                                searchMode == SearchMode.GLOBAL -> "Coba kata kunci lain"
                                query.isBlank() -> "Tekan + di kanan bawah untuk membuat repository pertama Anda"
                                else -> "Coba kata kunci lain"
                            }
                        ) }
                    else -> itemsIndexed(filtered, key = { _, r -> "${r.id}-${r.fullName}" }) { _, repo ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            RepoCard(
                                repo = repo,
                                usage = usageMap[repo.fullName],
                                onLongClick = { deleteTarget = repo },
                                onStar = {
                                    scope.launch {
                                        try {
                                            val starred = withContext(Dispatchers.IO) {
                                                GitHubApi.isStarred(Store.token.value, repo.owner, repo.name)
                                            }
                                            withContext(Dispatchers.IO) {
                                                GitHubApi.setStarred(Store.token.value, repo.owner, repo.name, !starred)
                                            }
                                            Toast.makeText(
                                                ctx,
                                                if (!starred) "Repo ini sekarang Anda sukai ★" else "Bintang dilepas",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(e)}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onFork = { forkTarget = repo },
                                onDelete = { deleteTarget = repo }
                            )
                        }
                    }
                }
                // ruang napas bawah agar kartu terakhir tidak menempel navbar
                item(key = "tail") { Spacer(Modifier.height(14.dp)) }
            }
            }
        }

        // Tombol buat repository melayang (FAB) — aksi utama beranda
        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            containerColor = GreenDeep,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Repo Baru", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showCreate) {
        CreateRepoDialog(onDismiss = { showCreate = false }, onCreated = { showCreate = false })
    }

    deleteTarget?.let { repo ->
        DeleteRepoDialog(
            owner = repo.owner,
            name = repo.name,
            onDismiss = { deleteTarget = null },
            onDeleted = { deleteTarget = null }
        )
    }

    forkTarget?.let { repo ->
        AlertDialog(
            onDismissRequest = { if (!forkBusy) forkTarget = null },
            title = { Text("Fork repository?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Text(
                    "Salinan \"${repo.fullName}\" akan dibuat ke akun Anda. GitHub memproses fork beberapa saat setelah permintaan dikirim.",
                    fontSize = 13.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    enabled = !forkBusy,
                    onClick = {
                        forkBusy = true
                        scope.launch {
                            try {
                                val forked = withContext(Dispatchers.IO) {
                                    GitHubApi.forkRepo(Store.token.value, repo.owner, repo.name)
                                }
                                Store.log("repo", "Fork ${repo.name}", forked.fullName)
                                Toast.makeText(ctx, "Fork dibuat: ${forked.fullName} ✓", Toast.LENGTH_LONG).show()
                                forkTarget = null
                                load(true)
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(e)}", Toast.LENGTH_LONG).show()
                            } finally {
                                forkBusy = false
                            }
                        }
                    }
                ) {
                    if (forkBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Fork")
                }
            },
            dismissButton = {
                TextButton(onClick = { forkTarget = null }, enabled = !forkBusy) { Text("Batal") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(
    repo: GhRepo,
    usage: Long?,
    onLongClick: () -> Unit,
    onStar: () -> Unit,
    onFork: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val accent = if (repo.isPrivate) YellowWarn else GreenPrimary
    val shape = RoundedCornerShape(18.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                Store.push(
                    Screen.Repo(
                        owner = repo.owner,
                        name = repo.name,
                        fullName = repo.fullName,
                        defaultBranch = repo.defaultBranch,
                        isPrivate = repo.isPrivate
                    )
                )
            },
            onLongClick = onLongClick
        ).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        // garis aksen tipis di atas kartu — privat kuning, publik hijau
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    if (repo.isPrivate) Brush.horizontalGradient(listOf(YellowWarn.copy(alpha = 0.7f), YellowWarn.copy(alpha = 0.15f)))
                    else Brush.horizontalGradient(listOf(GreenPrimary.copy(alpha = 0.75f), GreenPrimary.copy(alpha = 0.12f)))
                )
        )
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            // Badge ikon repo (kubah folder) — identitas file manager
            Box(
                Modifier.size(44.dp).background(
                    Brush.linearGradient(
                        listOf(
                            (if (repo.isPrivate) YellowWarn else GreenPrimary).copy(alpha = 0.22f),
                            (if (repo.isPrivate) YellowWarn else GreenPrimary).copy(alpha = 0.08f)
                        )
                    ),
                    RoundedCornerShape(15.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (repo.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                    contentDescription = if (repo.isPrivate) "Repository privat" else "Repository publik",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(7.dp))
                    VisibilityChip(repo.isPrivate)
                }
                if (!repo.description.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        repo.description!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(langColor(repo.language), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(repo.language ?: "-", color = GrayMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Star, contentDescription = null, tint = YellowWarn, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${repo.stars}", color = GrayMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    // Ukuran riil isi repository (termasuk Git LFS) — field "size" API GitHub
                    // tidak menghitung LFS sehingga bisa jauh lebih kecil dari kenyataan
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        when {
                            usage == null -> "…" // sedang menghitung
                            usage >= 0L -> formatBytes(usage)
                            repo.sizeKb > 0 -> formatBytes(repo.sizeKb * 1024) // gagal hitung → fallback API
                            else -> "0 B"
                        },
                        color = GrayMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Update, contentDescription = null, tint = GrayMuted.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("diperbarui ${timeAgo(repo.updatedAt)}", color = GrayMuted.copy(alpha = 0.85f), fontSize = 10.5.sp)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu ${repo.name}", tint = GrayMuted, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Sukai repo (star)") },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(17.dp), tint = YellowWarn) },
                        onClick = { menu = false; onStar() }
                    )
                    DropdownMenuItem(
                        text = { Text("Fork ke akun saya") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp), tint = BlueAccent) },
                        onClick = { menu = false; onFork() }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus repository", color = RedDanger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(17.dp), tint = RedDanger) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRepoDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var autoInit by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Buat repository baru", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama repository") },
                    placeholder = { Text("contoh: project-baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Deskripsi (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Private", fontSize = 14.sp)
                        Text("Hanya Anda yang bisa melihat", color = GrayMuted, fontSize = 11.sp)
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tambahkan README", fontSize = 14.sp)
                        Text("Inisialisasi commit pertama", color = GrayMuted, fontSize = 11.sp)
                    }
                    Switch(checked = autoInit, onCheckedChange = { autoInit = it })
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val repo = withContext(Dispatchers.IO) {
                                GitHubApi.createRepo(
                                    Store.token.value, name.trim(), desc.trim(), isPrivate, autoInit
                                )
                            }
                            Store.repos.value = listOf(repo) + Store.repos.value
                            Store.log("repo", "Buat repository ${repo.name}", repo.fullName)
                            Toast.makeText(ctx, "Repository ${repo.name} dibuat ✓", Toast.LENGTH_SHORT).show()
                            onCreated()
                        } catch (e: Exception) {
                            error = GitHubApi.humanError(e)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = name.isNotBlank() && !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Buat")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

/**
 * Dialog hapus repository PERMANEN — wajib mengetik nama repo sebagai konfirmasi.
 * Dipakai di Beranda (tekan lama kartu / menu) dan di menu kebab layar repository.
 */
@Composable
fun DeleteRepoDialog(owner: String, name: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Hapus repository?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "$owner/$name akan dihapus PERMANEN bersama semua file, commit, dan riwayatnya. Tindakan ini tidak bisa dibatalkan.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Ketik \"$name\" untuk konfirmasi") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                // Salin nama sekali klik — tidak perlu mengetik manual / buka browser
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(name))
                        Toast.makeText(ctx, "\"$name\" tersalin — tempel di kolom atas", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Salin nama \"$name\"", fontSize = 12.sp)
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.deleteRepo(Store.token.value, owner, name)
                            }
                            GitHubApi.invalidateUsage(owner, name)
                            Store.repos.value = Store.repos.value.filter { it.fullName != "$owner/$name" }
                            Store.log("delete", "Hapus repository $name", "$owner/$name")
                            Toast.makeText(ctx, "Repository $name dihapus permanen ✓", Toast.LENGTH_LONG).show()
                            onDeleted()
                        } catch (e: Exception) {
                            error = when {
                                (e as? GhException)?.code == 403 ->
                                    "Token ditolak (403) — PAT klasik memerlukan scope delete_repo untuk menghapus repository."
                                else -> GitHubApi.humanError(e)
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = confirm.trim() == name && !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedDanger,
                    contentColor = Color.White
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Hapus permanen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}
