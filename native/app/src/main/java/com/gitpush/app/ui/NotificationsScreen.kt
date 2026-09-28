package com.gitpush.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhNotification
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun notifIcon(type: String): ImageVector = when (type) {
    "PullRequest" -> Icons.Filled.AccountTree
    "Issue" -> Icons.Filled.BugReport
    "Release" -> Icons.Filled.Tag
    "CheckSuite", "CheckRun", "WorkflowRun" -> Icons.Filled.PlayArrow
    "Discussion" -> Icons.Filled.Comment
    "SecurityAlert", "DependabotAlert" -> Icons.Filled.Security
    else -> Icons.Filled.Notifications
}

private fun notifColor(type: String): Color = when (type) {
    "PullRequest" -> GreenPrimary
    "Issue" -> BlueAccent
    "Release" -> PurpleAccent
    "SecurityAlert", "DependabotAlert" -> RedDanger
    else -> GrayMuted
}

private fun reasonId(reason: String): String = when (reason) {
    "assign" -> "Ditugaskan"
    "author" -> "Anda penulis"
    "comment" -> "Komentar"
    "ci_activity" -> "Aktivitas CI"
    "manual" -> "Langganan"
    "mention" -> "Disebut"
    "review_requested" -> "Minta review"
    "security_alert" -> "Peringatan keamanan"
    "state_change" -> "Perubahan status"
    "team_mention" -> "Tim disebut"
    "subscribe" -> "Berlangganan"
    else -> reason
}

@Composable
fun NotificationsScreen() {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<GhNotification>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filterUnread by remember { mutableStateOf(false) }
    var busyAll by remember { mutableStateOf(false) }

    val load: () -> Unit = {
        scope.launch {
            error = null
            try {
                val list = withContext(Dispatchers.IO) {
                    GitHubApi.fetchNotifications(Store.token.value, all = true)
                }
                items = list
                Store.unread.value = list.count { it.unread }
            } catch (e: Exception) {
                error = GitHubApi.humanError(e)
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val markRead: (GhNotification) -> Unit = { n ->
        scope.launch {
            items = items?.map { if (it.id == n.id) it.copy(unread = false) else it }
            Store.unread.value = items?.count { it.unread } ?: 0
            try {
                withContext(Dispatchers.IO) { GitHubApi.markThreadRead(Store.token.value, n.id) }
            } catch (e: Exception) {
                load()
            }
        }
    }

    val markAll: () -> Unit = {
        scope.launch {
            busyAll = true
            try {
                withContext(Dispatchers.IO) { GitHubApi.markAllRead(Store.token.value) }
                items = items?.map { it.copy(unread = false) }
                Store.unread.value = 0
            } catch (e: Exception) {
                load()
            } finally {
                busyAll = false
            }
        }
    }

    val list = items
    val filtered = if (list == null) emptyList() else if (filterUnread) list.filter { it.unread } else list

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notifikasi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "${Store.unread.value} belum dibaca",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Segarkan")
            }
            IconButton(onClick = { if (!busyAll) markAll() }, enabled = (Store.unread.value > 0)) {
                Icon(Icons.Filled.DoneAll, contentDescription = "Tandai semua dibaca")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !filterUnread, onClick = { filterUnread = false }, label = { Text("Semua") })
            FilterChip(
                selected = filterUnread,
                onClick = { filterUnread = true },
                label = { Text("Belum dibaca (${Store.unread.value})") }
            )
        }
        when {
            list == null -> Loading()
            error != null -> ErrorCard(error!!) { load() }
            filtered.isEmpty() -> EmptyState(
                Icons.Filled.Notifications,
                if (filterUnread) "Semua sudah dibaca ✓" else "Tidak ada notifikasi",
                if (filterUnread) "Anda sudah membaca semua notifikasi"
                    else "Notifikasi dari repository yang Anda ikuti akan muncul di sini"
            )
            else -> ResponsiveBox {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { n ->
                        NotifCard(n = n, onRead = { if (n.unread) markRead(n) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(n: GhNotification, onRead: () -> Unit) {
    Card(onClick = onRead) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp)) {
                if (n.unread) {
                    Box(
                        Modifier.fillMaxSize().background(BlueAccent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Icon(
                notifIcon(n.subjectType),
                contentDescription = n.subjectType,
                tint = notifColor(n.subjectType),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    n.subjectTitle,
                    fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    n.repoFullName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            reasonId(n.reason),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(timeAgo(n.updatedAt), color = GrayMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
