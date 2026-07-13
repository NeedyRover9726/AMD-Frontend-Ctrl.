package com.example.ctrl

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ctrl.ui.theme.BgMidnight
import com.example.ctrl.ui.theme.CtrlTheme

class MainActivity : ComponentActivity() {
    private var onNewIntentReceived: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIXED: Prevents Android from violently pushing the app to the background
        // and showing the system lock screen when stopLockTask() is called.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val startDest = intent.getStringExtra("NAVIGATE_TO")

        setContent {
            CtrlTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgMidnight
                ) {
                    CtrlApp(
                        initialNavigateTo = startDest,
                        registerIntentListener = { listener ->
                            onNewIntentReceived = listener
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("NAVIGATE_TO")?.let { dest ->
            onNewIntentReceived?.invoke(dest)
        }
    }
}