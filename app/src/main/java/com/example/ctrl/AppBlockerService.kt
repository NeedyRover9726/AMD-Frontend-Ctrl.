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
    private var isBreakMode = false
    private var isUiProtected = false

    private var lastLaunchTime = 0L

    companion object {
        const val ACTION_QUIZ_LOCKDOWN = "com.example.ctrl.QUIZ_LOCKDOWN"
        const val EXTRA_LOCKDOWN_STATE = "lockdown_state"

        const val ACTION_PROTECT_UI = "com.example.ctrl.PROTECT_UI"
        const val EXTRA_PROTECTED_STATE = "protected_state"

        // FIXED: New Actions to prevent instant re-locking
        const val ACTION_FORCE_GRACE_PERIOD = "com.example.ctrl.FORCE_GRACE"
        const val ACTION_SYNC_BREAK_MODE = "com.example.ctrl.SYNC_BREAK"

        const val CHANNEL_ID = "ctrl_blocker_v2"
        const val ALERT_CHANNEL_ID = "ctrl_alerts"
    }

    private var ignoreInterceptUntil = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUIZ_LOCKDOWN -> {
                isQuizLockdown = intent.getBooleanExtra(EXTRA_LOCKDOWN_STATE, false)
            }
            ACTION_PROTECT_UI -> {
                isUiProtected = intent.getBooleanExtra(EXTRA_PROTECTED_STATE, false)
            }
            ACTION_FORCE_GRACE_PERIOD -> {
                // Ignore the UsageStatsManager lag for 4 seconds so the user can return home
                ignoreInterceptUntil = System.currentTimeMillis() + 4000L
            }
            ACTION_SYNC_BREAK_MODE -> {
                // Instantly syncs the break mode without waiting for the 1-second loop
                isBreakMode = true
            }
            else -> {
                val sm = SessionManager(this)
                val appsToBlock = intent?.getStringArrayListExtra("BLOCKED_APPS") ?: sm.blockedApps
                blockedApps = appsToBlock.toList()
                isBreakMode = sm.isBreakMode

                createNotificationChannels()
                startForeground(1, buildNotification(0f, "Starting session..."))

                if (!isRunning) {
                    isRunning = true
                    startMonitoring()
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(CHANNEL_ID, "Ctrl App Blocker", NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager?.createNotificationChannel(channel)

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

    private fun triggerAlert(title: String, msg: String) {
        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(System.currentTimeMillis().toInt(), alertNotification)
    }

    private fun startMonitoring() {
        var lastTickTime = System.currentTimeMillis()

        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                val now = System.currentTimeMillis()
                val delta = now - lastTickTime
                lastTickTime = now

                val sm = SessionManager(this@AppBlockerService)

                if (sm.isBreakMode && !isBreakMode) {
                    isBreakMode = true
                }

                // 1. BACKGROUND TIMER & PHASE LOGIC
                var elapsedMillis = (sm.elapsedMinutes * 60 * 1000).toLong()
                elapsedMillis += delta
                sm.elapsedMinutes = elapsedMillis / 60000f

                val targetMins = if (isBreakMode) sm.breakMinutes else sm.studyMinutes
                val targetMillis = targetMins * 60 * 1000L

                val progress = if (targetMillis > 0) elapsedMillis.toFloat() / targetMillis else 0f
                val remainingMillis = (targetMillis - elapsedMillis).coerceAtLeast(0)
                val remainingSecs = (remainingMillis / 1000).toInt()
                val hours = remainingSecs / 3600
                val mins = (remainingSecs % 3600) / 60
                val timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                val modeLabel = if (isBreakMode) "Break Time" else "Study Time"

                updateNotification(progress, "$modeLabel: $timeLabel")

                // 2. PHASE TRANSITION LOGIC
                if (targetMillis in 1..elapsedMillis) {
                    if (!isBreakMode) {
                        if (sm.breakMinutes > 0) {
                            isBreakMode = true
                            sm.isBreakMode = true
                            sm.elapsedMinutes = 0f
                            triggerAlert("Goal Reached! 100% Complete \uD83C\uDF89", "You finished your study session. Apps are unlocked for your break!")
                        } else {
                            sm.elapsedMinutes = 0f
                        }
                    } else {
                        isBreakMode = false
                        sm.isBreakMode = false
                        sm.elapsedMinutes = 0f
                        triggerAlert("Break Time is Up! ⏳", "Apps are locked again. Back to studying!")

                        if (!isQuizLockdown && !isUiProtected) {
                            val returnIntent = Intent(this@AppBlockerService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra("NAVIGATE_TO", "home")
                            }
                            startActivity(returnIntent)
                        }
                    }
                }

                // 3. APP BLOCKER INTERCEPT LOGIC
                val topPackage = getTopApp()
                val isTargetApp = blockedApps.contains(topPackage)
                val isIntercepting = (!isBreakMode && isTargetApp) || (isQuizLockdown && isForbiddenDuringQuiz(topPackage))

                // FIXED: Checks if we are safely within the 4-second grace period before intercepting
                if (isIntercepting && now > ignoreInterceptUntil) {
                    if (now - lastLaunchTime > 2000) {
                        lastLaunchTime = now
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