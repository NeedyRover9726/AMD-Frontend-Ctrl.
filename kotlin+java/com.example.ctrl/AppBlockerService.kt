package com.example.ctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var blockedApps = listOf<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIXED: We now safely receive the actual dynamic package names directly from the UI!
        val appsToBlock = intent?.getStringArrayListExtra("BLOCKED_APPS") ?: return START_NOT_STICKY
        blockedApps = appsToBlock.toList()

        val channelId = "ctrl_blocker"

        val channel = NotificationChannel(channelId, "Ctrl App Blocker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ctrl Focus Session Active")
            .setContentText("Monitoring distracting apps...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()

        startForeground(1, notification)
        isRunning = true
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                val topPackage = getTopApp()

                // If the app they opened is in their dynamic blocked list, INTERCEPT!
                if (blockedApps.contains(topPackage)) {
                    val launchIntent = Intent(this@AppBlockerService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("NAVIGATE_TO", "intercept_input")
                    }
                    startActivity(launchIntent)
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun getTopApp(): String {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 1000 * 60, time)
        var topPackage = ""
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topPackage = event.packageName ?: ""
            }
        }
        return topPackage
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
