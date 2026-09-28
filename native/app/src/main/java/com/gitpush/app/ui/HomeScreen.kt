package com.gitpush.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhException
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.GhRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(Store.repos.value.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    // Pemakaian riil per repository (termasuk objek Git LFS): fullName → byte (-1 = gagal hitung)
    var usageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    // Repo yang sedang diminta dihapus (tekan lama kartu)
    var deleteTarget by remember { mutableStateOf<GhRepo?>(null) }

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

    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) Store.repos.value else Store.repos.value.filter {
        it.name.lowercase().contains(q) ||
            (it.description?.lowercase()?.contains(q) == true) ||
            it.fullName.lowercase().contains(q)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("GitPush", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                val allKnown = Store.repos.value.isNotEmpty() &&
                    Store.repos.value.all { (usageMap[it.fullName] ?: -1L) >= 0L }
                Text(
                    if (allKnown) {
                        val total = Store.repos.value.sumOf { usageMap[it.fullName] ?: 0L }
                        "${Store.repos.value.size} repository • total ${formatBytes(total)}"
                    } else "${Store.repos.value.size} repository",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { load(true) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Segarkan")
            }
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Buat repository", tint = GreenPrimary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari repository…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Loading()
            error != null -> ErrorCard(error!!) { load(true) }
            filtered.isEmpty() -> EmptyState(
                Icons.Filled.Folder,
                if (query.isBlank()) "Belum ada repository" else "Tidak ditemukan",
                if (query.isBlank()) "Tekan + di kanan atas untuk membuat repository pertama Anda"
                    else "Coba kata kunci lain"
            )
            else -> ResponsiveBox {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { repo ->
                        RepoCard(repo, usageMap[repo.fullName], onLongClick = { deleteTarget = repo })
                    }
                }
            }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(repo: GhRepo, usage: Long?, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
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
        ).border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repo.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = BlueAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (repo.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                        contentDescription = if (repo.isPrivate) "Private" else "Public",
                        tint = GrayMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        if (repo.isPrivate) "Private" else "Public",
                        color = GrayMuted,
                        fontSize = 11.sp
                    )
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    repo.description!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(langColor(repo.language), CircleShape))
                Spacer(Modifier.size(5.dp))
                Text(repo.language ?: "-", color = GrayMuted, fontSize = 11.sp)
                Spacer(Modifier.size(12.dp))
                Icon(Icons.Filled.Star, contentDescription = null, tint = GrayMuted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(3.dp))
                Text("${repo.stars}", color = GrayMuted, fontSize = 11.sp)
                Spacer(Modifier.size(12.dp))
                // Ukuran riil isi repository (termasuk Git LFS) — field "size" API GitHub
                // tidak menghitung LFS sehingga bisa jauh lebih kecil dari kenyataan
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
                Spacer(Modifier.weight(1f))
                Text(timeAgo(repo.updatedAt), color = GrayMuted, fontSize = 11.sp)
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
 * Dipakai di Beranda (tekan lama kartu) dan di menu kebab layar repository.
 */
@Composable
fun DeleteRepoDialog(owner: String, name: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
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
