package com.gitpush.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitpush.app.data.GhEmail
import com.gitpush.app.data.GhEvent
import com.gitpush.app.data.GhGist
import com.gitpush.app.data.GhKey
import com.gitpush.app.data.GhOrg
import com.gitpush.app.data.GhUser
import com.gitpush.app.data.GhUserLite
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================
// ============ EDIT PROFIL PUBLIK (PATCH /user) ==============
// ============================================================

@Composable
fun EditProfileDialog(
    user: GhUser,
    onDismiss: () -> Unit,
    onSaved: (GhUser) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(user.name) }
    var bio by remember { mutableStateOf(user.bio ?: "") }
    var company by remember { mutableStateOf(user.company ?: "") }
    var location by remember { mutableStateOf(user.location ?: "") }
    var blog by remember { mutableStateOf(user.blog ?: "") }
    var email by remember { mutableStateOf(user.email ?: "") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Edit profil publik", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bio, onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = company, onValueChange = { company = it },
                    label = { Text("Perusahaan") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Lokasi") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = blog, onValueChange = { blog = it },
                    label = { Text("Situs web") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email publik") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val fresh = withContext(Dispatchers.IO) {
                                GitHubApi.updateProfile(
                                    Store.token.value, name.trim(), email.trim(), blog.trim(),
                                    company.trim(), location.trim(), bio.trim()
                                )
                            }
                            Store.user.value = fresh
                            onSaved(fresh)
                        } catch (e: Exception) {
                            err = GitHubApi.humanError(e)
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("Simpan profil") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

// ============================================================
// ============ EMAIL (list / tambah / hapus) ==================
// ============================================================

@Composable
fun EmailsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<GhEmail>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var newEmail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val load: () -> Unit = {
        scope.launch {
            err = null
            try {
                list = withContext(Dispatchers.IO) { GitHubApi.fetchEmails(Store.token.value) }
            } catch (e: Exception) { err = GitHubApi.humanError(e) }
        }
    }
    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email akun", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                when {
                    list == null -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                    err != null -> Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    else -> Column(
                        Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                    ) {
                        list!!.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.email, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (e.primary) Chip("primer", GreenPrimary)
                                        if (e.verified) Chip("terverifikasi", BlueAccent)
                                        if (!e.primary && !e.verified) Chip("sekunder", GrayMuted)
                                    }
                                }
                                if (!e.primary) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { GitHubApi.deleteEmail(Store.token.value, e.email) }
                                                load()
                                            } catch (ex: Exception) {
                                                Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(ex)}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus email", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newEmail, onValueChange = { newEmail = it },
                        label = { Text("Tambah email baru") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        enabled = newEmail.contains('@') && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { GitHubApi.addEmail(Store.token.value, newEmail) }
                                    newEmail = ""
                                    load()
                                    Toast.makeText(ctx, "Email ditambahkan ✓", Toast.LENGTH_SHORT).show()
                                } catch (ex: Exception) {
                                    Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(ex)}", Toast.LENGTH_SHORT).show()
                                } finally { busy = false }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah", tint = GreenPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(5.dp)) {
        Text(
            text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
    Spacer(Modifier.width(4.dp))
}

// ============================================================
// ============ KUNCI SSH (list / tambah / hapus) ==============
// ============================================================

@Composable
fun KeysDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<GhKey>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val load: () -> Unit = {
        scope.launch {
            err = null
            try {
                list = withContext(Dispatchers.IO) { GitHubApi.fetchKeys(Store.token.value) }
            } catch (e: Exception) { err = GitHubApi.humanError(e) }
        }
    }
    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kunci SSH", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "Kunci SSH dipakai untuk autentikasi git dari komputer Anda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                when {
                    list == null -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                    err != null -> Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    list!!.isEmpty() -> Text(
                        "Belum ada kunci SSH terdaftar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                    )
                    else -> Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        list!!.forEach { k ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Tag, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(k.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        k.key.take(28) + "…",
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { GitHubApi.deleteKey(Store.token.value, k.id) }
                                            load()
                                        } catch (ex: Exception) {
                                            Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(ex)}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus kunci", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = GreenPrimary)
                    Spacer(Modifier.width(5.dp))
                    Text("Tambah kunci SSH")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )

    if (showAdd) {
        AddKeyDialog(
            onDismiss = { showAdd = false },
            onAdded = { showAdd = false; load() }
        )
    }
}

@Composable
private fun AddKeyDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("GitPush") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Tambah kunci SSH", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Judul") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Kunci (ssh-ed25519 AAAA…)") },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = key.isNotBlank() && title.isNotBlank() && !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { GitHubApi.addKey(Store.token.value, title, key) }
                            Toast.makeText(ctx, "Kunci SSH ditambahkan ✓", Toast.LENGTH_SHORT).show()
                            onAdded()
                        } catch (e: Exception) {
                            err = GitHubApi.humanError(e)
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Tambah")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

// ============================================================
// ============ GIST — catatan cepat (list/buat/hapus/lihat) ===
// ============================================================

@Composable
fun GistsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<GhGist>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var viewContent by remember { mutableStateOf<Pair<String, String>?>(null) } // nama → isi

    val load: () -> Unit = {
        scope.launch {
            err = null
            try {
                list = withContext(Dispatchers.IO) { GitHubApi.fetchGists(Store.token.value) }
            } catch (e: Exception) { err = GitHubApi.humanError(e) }
        }
    }
    LaunchedEffect(Unit) { load() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                    Column(Modifier.weight(1f)) {
                        Text("Gist saya", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Catatan & potongan kode di gist.github.com", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Buat gist", tint = GreenPrimary)
                    }
                }
                when {
                    list == null -> Loading()
                    err != null -> ErrorCard(err!!) { load() }
                    list!!.isEmpty() -> EmptyState(
                        Icons.Filled.Edit,
                        "Belum ada gist",
                        "Tekan ikon + untuk membuat catatan cepat pertama"
                    )
                    else -> ResponsiveBox {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list!!, key = { it.id }) { g ->
                                androidx.compose.material3.Card(onClick = {
                                    scope.launch {
                                        try {
                                            val content = withContext(Dispatchers.IO) {
                                                GitHubApi.fetchGistContent(Store.token.value, g.id)
                                            }
                                            viewContent = (g.firstFileName ?: "gist") to content
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(e)}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Edit, contentDescription = null, tint = FilePalette.Doc, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                g.description?.ifBlank { g.firstFileName ?: "(tanpa judul)" }
                                                    ?: g.firstFileName ?: "(tanpa judul)",
                                                fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) { GitHubApi.deleteGist(Store.token.value, g.id) }
                                                        load()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(e)}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Hapus gist", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "${g.fileCount} file • ${timeAgo(g.updatedAt)} • ${if (g.isPublic) "publik" else "rahasia"}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
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

    if (showCreate) {
        CreateGistDialog(onDismiss = { showCreate = false }, onCreated = { showCreate = false; load() })
    }

    viewContent?.let { (fname, content) ->
        AlertDialog(
            onDismissRequest = { viewContent = null },
            title = { Text(fname, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        content.take(4000),
                        fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 16.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 320.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("gist", content))
                    Toast.makeText(ctx, "Isi gist disalin", Toast.LENGTH_SHORT).show()
                }) { Text("Salin") }
            },
            dismissButton = { TextButton(onClick = { viewContent = null }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun CreateGistDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var fileName by remember { mutableStateOf("catatan.md") }
    var desc by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Gist baru", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = fileName, onValueChange = { fileName = it },
                    label = { Text("Nama file") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Deskripsi (opsional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("Isi catatan") },
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isPublic) "Publik" else "Rahasia", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isPublic) "Terlihat di pencarian & profil" else "Hanya via tautan langsung",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = !busy)
                }
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.isNotBlank() && !busy,
                onClick = {
                    busy = true; err = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                GitHubApi.createGist(Store.token.value, fileName.trim(), content, desc.trim(), isPublic)
                            }
                            Toast.makeText(ctx, "Gist dibuat ✓", Toast.LENGTH_SHORT).show()
                            onCreated()
                        } catch (e: Exception) {
                            err = GitHubApi.humanError(e)
                        } finally { busy = false }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Buat gist")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

// ============================================================
// ============ FOLLOWERS / FOLLOWING + tombol ikuti ==========
// ============================================================

@Composable
fun UsersListDialog(
    title: String,
    fetcher: suspend () -> List<GhUserLite>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<GhUserLite>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var followed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        try {
            val l = withContext(Dispatchers.IO) { fetcher() }
            list = l
        } catch (e: Exception) { err = GitHubApi.humanError(e) }
        // Tandai yang sudah diikuti (bagi yang berupa following)
        runCatching {
            val f = withContext(Dispatchers.IO) { GitHubApi.fetchFollowing(Store.token.value, Store.user.value?.login ?: "") }
            followed = f.map { it.login }.toSet()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            when {
                list == null && err == null -> Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                err != null -> Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                list!!.isEmpty() -> Text("Kosong.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    list!!.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(u.avatarUrl, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(u.login, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            val isFollowed = u.login in followed
                            TextButton(onClick = {
                                val target = !isFollowed
                                followed = if (target) followed + u.login else followed - u.login
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { GitHubApi.setFollowing(Store.token.value, u.login, target) }
                                    } catch (e: Exception) {
                                        followed = if (target) followed - u.login else followed + u.login
                                        Toast.makeText(ctx, "Gagal: ${GitHubApi.humanError(e)}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Text(if (isFollowed) "Berhenti" else "Ikuti", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ============================================================
// ============ ORGANISASI =====================================
// ============================================================

@Composable
fun OrgsDialog(onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<GhOrg>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            list = withContext(Dispatchers.IO) { GitHubApi.fetchOrgs(Store.token.value) }
        } catch (e: Exception) { err = GitHubApi.humanError(e) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organisasi", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            when {
                list == null && err == null -> Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                err != null -> Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                list!!.isEmpty() -> Text("Anda belum tergabung di organisasi mana pun.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                else -> Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    list!!.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(o.avatarUrl, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.login, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                if (!o.description.isNullOrBlank()) {
                                    Text(
                                        o.description!!,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ============================================================
// ============ UMPAN AKTIVITAS (events) =======================
// ============================================================

private fun eventIcon(type: String): ImageVector = when (type) {
    "PushEvent" -> Icons.Filled.Upload
    "CreateEvent" -> Icons.Filled.Add
    "DeleteEvent" -> Icons.Filled.Close
    "WatchEvent" -> Icons.Filled.Star
    "ForkEvent" -> Icons.Filled.SwapVert
    "IssuesEvent", "IssueCommentEvent" -> Icons.Filled.Tag
    "PullRequestEvent", "PullRequestReviewEvent" -> Icons.Filled.SwapVert
    "ReleaseEvent" -> Icons.Filled.Tag
    "MemberEvent" -> Icons.Filled.Group
    "PublicEvent" -> Icons.Filled.Public
    else -> Icons.Filled.Event
}

/** Daftar aktivitas terbaru pengguna — dipakai ProfileScreen. */
@Composable
fun EventsList(events: List<GhEvent>) {
    Column {
        events.take(12).forEach { ev ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    eventIcon(ev.type),
                    contentDescription = ev.type,
                    tint = GreenPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ev.detail,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        ev.repo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(timeAgo(ev.createdAt), color = GrayMuted, fontSize = 10.sp)
            }
        }
    }
}
