package com.gitpush.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gitpush.app.ui.Store
import com.gitpush.app.ui.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service "cermin progres": menampilkan progress bar di STATUS BAR
 * (notifikasi) selama proses transfer berlangsung — unggah maupun unduh/ZIP
 * (permintaan user untuk finishing versi 1.0).
 *
 * Service ini TIDAK menjalankan transfer. Ia hanya membaca state global yang
 * sudah ada (UploadManager utk unggah, Store.operation utk unduh/ZIP/proses
 * panjang lain) lalu menyegarkan notifikasi tiap ±700 ms. Tidak ada proses
 * aktif → service menghentikan dirinya sendiri (notifikasi ikut hilang).
 */
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updater: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        // Satu loop saja — bila service sudah jalan dan dipicu lagi, batalkan loop lama
        updater?.cancel()
        updater = scope.launch {
            while (isActive) {
                if (!UploadManager.active && !Store.operation.running) break
                try {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, buildNotification())
                } catch (_: Exception) { }
                delay(700)
            }
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Proses transfer", NotificationManager.IMPORTANCE_LOW)
        )
        // Tipe dataSync (minSdk 29 selalu memakai versi 3-arg): transfer data pengguna
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun buildNotification(): Notification {
        val up = UploadManager.active
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (up) R.drawable.ic_stat_upload else R.drawable.ic_stat_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (up) {
            val sent = UploadManager.progressBytes()
            val total = UploadManager.bytesTotal
            val speed = UploadManager.speedBps
            val pct = if (total > 0) ((sent * 100 / total).toInt().coerceIn(0, 100)) else 0
            b.setContentTitle(
                when (UploadManager.phase) {
                    "commit" -> "Menyimpan commit…"
                    "prepare" -> "Menyiapkan unggahan…"
                    else -> "Mengunggah ${UploadManager.filesDone}/${UploadManager.filesTotal} file"
                }
            ).setContentText(buildString {
                if (total > 0) {
                    append("${fmt(sent)} / ${fmt(total)}")
                    if (speed > 0) append(" • ${fmt(speed)}/dtk")
                } else append(UploadManager.stage.ifBlank { "Memproses…" })
            }).setSubText("${UploadManager.repoFull}@${UploadManager.branch}")
                .setProgress(100, pct, total <= 0)
        } else {
            val op = Store.operation
            val pct = if (op.total > 0) ((op.done * 100 / op.total).toInt().coerceIn(0, 100)) else 0
            b.setContentTitle(op.title.ifBlank { "Memproses…" })
                .setContentText(
                    when {
                        op.total > 0 && op.unit == "bytes" -> "${fmt(op.done)} / ${fmt(op.total)}"
                        op.detail.isNotBlank() -> op.detail
                        else -> "Sedang berjalan…"
                    }
                )
                .setProgress(100, pct, op.total <= 0)
        }
        return b.build()
    }

    private fun contentIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Format ringkas utk notifikasi: 192 KB, 4.2 MB, 1.10 GB. */
    private fun fmt(n: Long): String = when {
        n >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", n / 1073741824.0)
        n >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", n / 1048576.0)
        n >= 1024L -> "${n / 1024} KB"
        else -> "$n B"
    }

    companion object {
        private const val CHANNEL_ID = "transfer"
        const val NOTIF_ID = 20251

        /** Mulai (atau segarkan) service notifikasi progres — aman dipanggil kapan pun. */
        fun start(context: Context?) {
            val c = context ?: return
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    c, Intent(c, TransferService::class.java)
                )
            } catch (_: Exception) {
                // Android 12+ menolak start FGS dari background — abaikan;
                // proses transfer tetap berjalan, hanya notifikasi yang tidak tampil.
            }
        }
    }
}
