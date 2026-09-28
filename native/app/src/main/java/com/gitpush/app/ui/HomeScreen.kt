package com.gitpush.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.GhRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(Store.repos.value.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

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
                Text(
                    "${Store.repos.value.size} repository",
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
                    items(filtered, key = { it.id }) { repo -> RepoCard(repo) }
                }
            }
        }
    }

    if (showCreate) {
        CreateRepoDialog(onDismiss = { showCreate = false }, onCreated = { showCreate = false })
    }
}

@Composable
private fun RepoCard(repo: GhRepo) {
    val shape = RoundedCornerShape(14.dp)
    Card(
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
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, shape)
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
                if (repo.sizeKb > 0) {
                    Spacer(Modifier.size(12.dp))
                    Text(formatBytes(repo.sizeKb * 1024), color = GrayMuted, fontSize = 11.sp)
                }
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
