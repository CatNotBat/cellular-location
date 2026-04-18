package com.cellularlocation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cellularlocation.data.CellTowerRepository
import com.cellularlocation.data.LocationResolver
import com.cellularlocation.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Optional foreground service for background cell scanning.
 * Start with Context.startForegroundService(Intent(this, CellScanService::class.java)).
 */
class CellScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: CellTowerRepository
    private lateinit var resolver: LocationResolver

    override fun onCreate() {
        super.onCreate()
        repository = CellTowerRepository(this)
        resolver = LocationResolver(AppDatabase.getInstance(this))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Scanning cell towers…"))
        scope.launch {
            repository.observeCellTowers()
                .catch { /* swallow; service keeps running */ }
                .collect { towers ->
                    val location = resolver.resolve(towers)
                    val text = if (location != null) {
                        "%.4f, %.4f  ±${location.accuracyMeters.toInt()} m".format(
                            location.latitude, location.longitude
                        )
                    } else {
                        "${towers.size} tower(s) visible, resolving…"
                    }
                    updateNotification(text)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -----------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cell Scan",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cellular Location")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "cell_scan_channel"
        private const val NOTIFICATION_ID = 1
    }
}
