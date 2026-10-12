package com.gitpush.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GhException
import com.gitpush.app.data.GitHubApi
import com.gitpush.app.data.Prefs
import com.gitpush.app.ui.AuthScreen
import com.gitpush.app.ui.BlueAccent
import com.gitpush.app.ui.BrandLogo
import com.gitpush.app.ui.EditorScreen
import com.gitpush.app.ui.GitPushTheme
import com.gitpush.app.ui.GreenPrimary
import com.gitpush.app.ui.GrayMuted
import com.gitpush.app.ui.HomeScreen
import com.gitpush.app.ui.NotificationsScreen
import com.gitpush.app.ui.OperationOverlay
import com.gitpush.app.ui.ProfileScreen
import com.gitpush.app.ui.RepoScreen
import com.gitpush.app.ui.Screen
import com.gitpush.app.ui.SettingsScreen
import com.gitpush.app.ui.Store
import com.gitpush.app.ui.UploadTray
import com.gitpush.app.ui.ViewerScreen
import com.gitpush.app.ui.cardBorder
import com.gitpush.app.ui.cardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        Store.init(prefs)
        Store.attach(this) // utk notifikasi progres transfer di status bar
        Store.token.value = prefs.token
        // Android 13+: izin notifikasi diperlukan agar progress bar transfer
        // (unggah/unduh) tampil di status bar — diminta sekali di awal.
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
            } catch (_: Exception) { }
        }
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
            // Logo brand — identik dengan ikon launcher
            BrandLogo(glowSize = 128, logoSize = 96)
            Spacer(Modifier.height(14.dp))
            Text("GitPush", fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, letterSpacing = (-0.5).sp)
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
                    "settings" -> SettingsScreen()
                    else -> ProfileScreen()
                }
            }
            // Chip unggahan global (ala aplikasi cloud) — tampil di layar mana pun
            // saat ada unggahan berjalan / hasilnya belum ditutup. Ketuk → detail.
            UploadTray(Modifier.align(Alignment.BottomCenter))
            // Dialog progres GLOBAL — semua proses panjang (unduh, ZIP, pindah, hapus, dll)
            // tampil di sini, di atas layar mana pun.
            OperationOverlay()
        }
    }
}

@Composable
private fun BottomBar() {
    val tab = Store.tab.value
    val shape = RoundedCornerShape(26.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = cardBorder(),
        modifier = Modifier
            .navigationBarsPadding() // jarak dari bilah gestur/sistem
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .cardShadow(shape)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            NavItem(
                Icons.Filled.Home, "Beranda",
                selected = tab == "home" && Store.stack.isEmpty(),
                modifier = Modifier.weight(1f)
            ) { Store.gotoTab("home") }
            NavItem(
                Icons.Filled.Notifications, "Notifikasi",
                selected = tab == "notifs" && Store.stack.isEmpty(),
                modifier = Modifier.weight(1f)
            ) { Store.gotoTab("notifs") }
            NavItem(
                Icons.Filled.Settings, "Pengaturan",
                selected = tab == "settings" && Store.stack.isEmpty(),
                modifier = Modifier.weight(1f)
            ) { Store.gotoTab("settings") }
            NavItem(
                Icons.Filled.Person, "Profil",
                selected = tab == "profile" && Store.stack.isEmpty(),
                modifier = Modifier.weight(1f)
            ) { Store.gotoTab("profile") }
        }
    }
}

/** Butir navigasi modern: pil indikator di belakang ikon + label kecil. */
@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        BadgedBox(badge = {
            val n = Store.unread.value
            if (label == "Notifikasi" && n > 0) {
                Badge(containerColor = BlueAccent) {
                    Text(if (n > 9) "9+" else n.toString(), fontSize = 10.sp)
                }
            }
        }) {
            Box(
                Modifier
                    .size(if (selected) 36.dp else 30.dp)
                    .background(
                        if (selected) GreenPrimary.copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) GreenPrimary else GrayMuted,
                    modifier = Modifier.size(if (selected) 22.dp else 21.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else GrayMuted
        )
    }
}
