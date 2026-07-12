package com.example.ctrl

import android.content.Intent
import android.os.Bundle
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