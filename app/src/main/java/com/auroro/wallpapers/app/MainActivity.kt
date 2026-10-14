package com.auroro.wallpapers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auroro.wallpapers.ui.theme.AuroroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AuroroApp).container
        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            AuroroTheme(settings = settings) {
                AuroroShell()
            }
        }
    }
}
