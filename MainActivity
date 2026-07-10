package com.example.ctrl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the Blocker Service triggers the app, it will tell us to go to "intercept_input"
        val startDest = intent.getStringExtra("NAVIGATE_TO") ?: "splash"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090616) // Raw hex prevents crashing
                ) {
                    CtrlApp(startDestination = startDest)
                }
            }
        }
    }
}
