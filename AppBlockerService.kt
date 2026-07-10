package com.example.ctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var blockedApps = listOf<String>()

    // Matches the names in your UI to the actual Android package codes
    private val packageMap = mapOf(
        "Google Chrome" to "com.android.chrome",
        "TikTok" to "com.zhiliaoapp.musically",
        "Instagram" to "com.instagram.android",
        "YouTube" to "com.google.android.youtube",
        "X (Twitter)" to "com.twitter.android",
        "Reddit" to "com.reddit.frontpage",
        "Facebook" to "com.facebook.katana",
        "Netflix" to "com.netflix.mediaclient"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appsToBlock = intent?.getStringArrayListExtra("BLOCKED_APPS") ?: return START_NOT_STICKY
        blockedApps = appsToBlock.mapNotNull { packageMap[it] }

        // Create a Foreground Notification (Required by Android so the blocker isn't killed)
        val channelId = "ctrl_blocker"
        val channel = NotificationChannel(channelId, "Ctrl App Blocker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ctrl Focus Session Active")
            .setContentText("Monitoring distracting apps...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Default safe icon
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

                if (blockedApps.contains(topPackage)) {
                    // INTERCEPT TRIGGERED! Force open the Ctrl Quiz Screen
                    val launchIntent = Intent(this@AppBlockerService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("NAVIGATE_TO", "intercept_input")
                    }
                    startActivity(launchIntent)
                }
                handler.postDelayed(this, 1000) // Check every 1 second
            }
        })
    }

    private fun getTopApp(): String {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 10000, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
