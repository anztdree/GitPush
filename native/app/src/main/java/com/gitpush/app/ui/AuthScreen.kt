package com.gitpush.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitpush.app.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(42.dp))
        // ===== Logo gradien + gloro lembut =====
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(118.dp).background(
                    Brush.radialGradient(listOf(GreenPrimary.copy(alpha = 0.22f), Color.Transparent))
                )
            )
            Box(
                Modifier.size(92.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF1F6F33), GreenDeep, GreenGlow)),
                    RoundedCornerShape(28.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Upload,
                    contentDescription = "Logo GitPush",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("GitPush", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, letterSpacing = (-0.6).sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "File manager + penyimpanan awan di atas repository GitHub",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(20.dp))
        // ===== 3 sorotan fitur utama =====
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            FeatureTile(Icons.Filled.CloudUpload, "Upload massal", "banyak file → 1 commit", Modifier.weight(1f))
            FeatureTile(Icons.Filled.Lock, "100% aman", "token hanya di HP", Modifier.weight(1f))
            FeatureTile(Icons.Filled.FolderZip, "Download", "file, folder & repo", Modifier.weight(1f))
        }
        Spacer(Modifier.height(26.dp))
        Column(Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text("Personal Access Token") },
                placeholder = { Text("ghp_••••••••••••••••") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(
                            if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (show) "Sembunyikan token" else "Tampilkan token"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val u = withContext(Dispatchers.IO) {
                                GitHubApi.fetchUser(token.trim())
                            }
                            Store.prefs?.token = token.trim()
                            Store.token.value = token.trim()
                            Store.user.value = u
                            Toast.makeText(ctx, "Halo, ${u.login}!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            error = GitHubApi.humanError(e)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = token.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = GreenDeep, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text("Masuk ke GitPush", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/settings/tokens/new?scopes=repo&description=GitPush")
                            )
                        )
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                Spacer(Modifier.size(6.dp))
                Text("Buat Personal Access Token (scope repo)", color = GreenPrimary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Text(
                "Token disimpan hanya di perangkat Anda dan dikirim langsung ke api.github.com — tanpa server perantara.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Tile sorotan fitur di layar masuk: ikon berwarna + judul + keterangan kecil. */
@Composable
private fun FeatureTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(36.dp).background(GreenPrimary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
            Text(
                subtitle,
                color = GrayMuted,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2
            )
        }
    }
}
