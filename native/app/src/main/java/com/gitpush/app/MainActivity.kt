package com.gitpush.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhException
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.Prefs
import com.gitpush.app.ui.AuthScreen
import com.gitpush.app.ui.BlueAccent
import com.gitpush.app.ui.EditorScreen
import com.gitpush.app.ui.GitPushTheme
import com.gitpush.app.ui.GreenPrimary
import com.gitpush.app.ui.HomeScreen
import com.gitpush.app.ui.NotificationsScreen
import com.gitpush.app.ui.ProfileScreen
import com.gitpush.app.ui.RepoScreen
import com.gitpush.app.ui.Screen
import com.gitpush.app.ui.SettingsScreen
import com.gitpush.app.ui.Store
import com.gitpush.app.ui.UploadScreen
import com.gitpush.app.ui.ViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        Store.init(prefs)
        Store.token.value = prefs.token
        setContent {
            GitPushTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GitPushApp()
                }
            }
        }
    }
}

@Composable
fun GitPushApp() {
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(650)
        if (Store.token.value.isNotEmpty()) {
            try {
                Store.user.value = withContext(Dispatchers.IO) {
                    GitHubApi.fetchUser(Store.token.value)
                }
            } catch (e: Exception) {
                if ((e as? GhException)?.code == 401) Store.signOut()
            }
        }
        booted = true
    }
    if (!booted) {
        Splash()
    } else if (Store.token.value.isEmpty()) {
        AuthScreen()
    } else {
        MainScaffold()
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Upload,
                    contentDescription = "Logo GitPush",
                    tint = GreenPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("GitPush", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Kelola GitHub dari HP — native Android",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = GreenPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun MainScaffold() {
    LaunchedEffect(Unit) {
        runCatching {
            val list = withContext(Dispatchers.IO) {
                GitHubApi.fetchNotifications(Store.token.value, all = false)
            }
            Store.unread.value = list.count { it.unread }
        }
    }
    BackHandler(enabled = Store.stack.isNotEmpty()) { Store.pop() }
    val overlay = Store.stack.lastOrNull()
    val tab = Store.tab.value
    Scaffold(
        bottomBar = { BottomBar() },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (overlay) {
                is Screen.Repo -> RepoScreen(overlay)
                is Screen.Viewer -> ViewerScreen(overlay)
                is Screen.Editor -> EditorScreen(overlay)
                null -> when (tab) {
                    "home" -> HomeScreen()
                    "notifs" -> NotificationsScreen()
                    "upload" -> UploadScreen()
                    "settings" -> SettingsScreen()
                    else -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
private fun BottomBar() {
    val tab = Store.tab.value
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = tab == "home" && Store.stack.isEmpty(),
            onClick = { Store.gotoTab("home") },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Beranda") },
            label = { Text("Beranda", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = tab == "notifs" && Store.stack.isEmpty(),
            onClick = { Store.gotoTab("notifs") },
            icon = {
                BadgedBox(badge = {
                    val n = Store.unread.value
                    if (n > 0) {
                        Badge(containerColor = BlueAccent) {
                            Text(if (n > 9) "9+" else n.toString(), fontSize = 10.sp)
                        }
                    }
                }) { Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi") }
            },
            label = { Text("Notifikasi", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = tab == "upload" && Store.stack.isEmpty(),
            onClick = { Store.gotoTab("upload") },
            icon = { Icon(Icons.Filled.Upload, contentDescription = "Unggah", tint = GreenPrimary) },
            label = { Text("Unggah", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = tab == "settings" && Store.stack.isEmpty(),
            onClick = { Store.gotoTab("settings") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Pengaturan") },
            label = { Text("Pengaturan", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = tab == "profile" && Store.stack.isEmpty(),
            onClick = { Store.gotoTab("profile") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profil") },
            label = { Text("Profil", fontSize = 11.sp) }
        )
    }
}
