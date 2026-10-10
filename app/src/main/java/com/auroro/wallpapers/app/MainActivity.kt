package com.auroro.wallpapers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auroro.wallpapers.core.design.AuroroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as AuroroApp).container
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(app))
            val settings by vm.settings.collectAsState()
            AuroroTheme(settings = settings) {
                AuroroRoot(vm, intent.getStringExtra(EXTRA_ROUTE))
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "auroro_route"
    }
}
