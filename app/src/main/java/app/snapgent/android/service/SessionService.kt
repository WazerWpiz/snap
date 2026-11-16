// SPDX-License-Identifier: GPL-3.0-or-later
// Foreground service opsional: menjaga proses (dan sesi bridge di dalamnya) tetap hidup
// saat layar mati. Diaktifkan dari Pengaturan > Layanan latar.
package app.snapgent.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.snapgent.android.MainActivity
import app.snapgent.android.R

class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snapgent)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.session_running))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    /** Android 15+ membatasi durasi dataSync; hentikan layanan dengan rapi ketika batas tercapai. */
    @RequiresApi(34)
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.session_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "snapgent_session"
        const val NOTIFICATION_ID = 1001

        /** Menyalakan atau mematikan layanan sesuai pengaturan. Panggil saat aplikasi di latar depan. */
        fun sync(context: Context, keepAlive: Boolean) {
            val intent = Intent(context, SessionService::class.java)
            if (keepAlive) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
