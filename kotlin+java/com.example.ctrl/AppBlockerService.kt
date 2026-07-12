package com.example.ctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AppBlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var blockedApps = listOf<String>()

    private var isQuizLockdown = false
    private var isBreakMode = false // NEW: Tracks Break Phase

    private var lastLaunchTime = 0L

    companion object {
        const val ACTION_UPDATE_PROGRESS = "com.example.ctrl.UPDATE_PROGRESS"
        const val ACTION_QUIZ_LOCKDOWN = "com.example.ctrl.QUIZ_LOCKDOWN"
        const val ACTION_PHASE_CHANGE = "com.example.ctrl.PHASE_CHANGE" // NEW: Triggers Alert

        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_LOCKDOWN_STATE = "lockdown_state"
        const val EXTRA_IS_BREAK = "is_break"
        const val EXTRA_ALERT_TITLE = "alert_title"
        const val EXTRA_ALERT_MSG = "alert_msg"

        const val CHANNEL_ID = "ctrl_blocker_v2"
        const val ALERT_CHANNEL_ID = "ctrl_alerts" // NEW: High Priority Channel
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
            }
            ACTION_PHASE_CHANGE -> {
                // Triggered when hitting 100% (Switching between Study/Break loops)
                isBreakMode = intent.getBooleanExtra(EXTRA_IS_BREAK, false)
                val title = intent.getStringExtra(EXTRA_ALERT_TITLE) ?: ""
                val msg = intent.getStringExtra(EXTRA_ALERT_MSG) ?: ""

                val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .build()

                getSystemService(NotificationManager::class.java)?.notify(2, alertNotification)
            }
            else -> {
                val sm = SessionManager(this)
                val appsToBlock = intent?.getStringArrayListExtra("BLOCKED_APPS") ?: sm.blockedApps
                blockedApps = appsToBlock.toList()
                isBreakMode = sm.isBreakMode

                createNotificationChannels()
                startForeground(1, buildNotification(0f, "Starting session..."))
                isRunning = true
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Background Progress Channel (Silent)
        val channel = NotificationChannel(CHANNEL_ID, "Ctrl App Blocker", NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager?.createNotificationChannel(channel)

        // 100% Alerts Channel (Loud / Pop-up)
        val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Ctrl Session Alerts", NotificationManager.IMPORTANCE_HIGH)
        manager?.createNotificationChannel(alertChannel)
    }

    private fun buildNotification(progress: Float, text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isBreakMode) "Ctrl Break Active \uD83C\uDF89" else "Ctrl Focus Session Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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

                // FIXED: Only intercept if we are NOT in Break Mode (unless it's a quiz lockdown)
                val isTargetApp = blockedApps.contains(topPackage)
                val isIntercepting = (!isBreakMode && isTargetApp) || (isQuizLockdown && isForbiddenDuringQuiz(topPackage))

                if (isIntercepting) {
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

                handler.postDelayed(this, if (isQuizLockdown) 500 else 1000)
            }
        })
    }

    private fun isForbiddenDuringQuiz(pkg: String): Boolean {
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
