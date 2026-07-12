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
import android.util.Log

class AppBlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var blockedApps = listOf<String>()
    private var isQuizLockdown = false
    private var lastLaunchTime = 0L

    companion object {
        const val ACTION_UPDATE_PROGRESS = "com.example.ctrl.UPDATE_PROGRESS"
        const val ACTION_QUIZ_LOCKDOWN = "com.example.ctrl.QUIZ_LOCKDOWN"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_LOCKDOWN_STATE = "lockdown_state"
        const val CHANNEL_ID = "ctrl_blocker"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val timeRemaining = intent.getStringExtra(EXTRA_TIME_REMAINING) ?: "Calculating..."
                updateNotification(progress, timeRemaining)
            }
            ACTION_QUIZ_LOCKDOWN -> {
                isQuizLockdown = intent.getBooleanExtra(EXTRA_LOCKDOWN_STATE, false)
                Log.d("AppBlocker", "Quiz Lockdown set to: $isQuizLockdown")
            }
            else -> {
                val appsToBlock = intent?.getStringArrayListExtra("BLOCKED_APPS") ?: SessionManager(this).blockedApps
                blockedApps = appsToBlock.toList()
                createNotificationChannel()
                startForeground(1, buildNotification(0f, "Starting session..."))
                isRunning = true
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Ctrl App Blocker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(progress: Float, text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ctrl Focus Session Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Float, timeRemaining: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, buildNotification(progress, "$timeRemaining remaining"))
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                val topPackage = getTopApp()

                // INTERCEPT LOGIC
                if (blockedApps.contains(topPackage) || (isQuizLockdown && isForbiddenDuringQuiz(topPackage))) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLaunchTime > 2000) {
                        lastLaunchTime = currentTime
                        val launchIntent = Intent(this@AppBlockerService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("NAVIGATE_TO", if (isQuizLockdown) "quiz" else "intercept_input")
                        }
                        startActivity(launchIntent)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun isForbiddenDuringQuiz(pkg: String): Boolean {
        // Forbidden apps during quiz: Home launchers, Recent Apps (SystemUI), and other apps.
        // We allow the app's own package.
        val myPackage = packageName
        return pkg != myPackage && pkg.isNotEmpty()
    }

    private fun getTopApp(): String {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 2000, time)
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
