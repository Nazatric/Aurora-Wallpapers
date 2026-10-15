package com.auroro.wallpapers.app

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * All application UI was removed on request. The core layers — providers, aggregator,
 * discovery engine, repositories, download/apply pipeline and the DI container — remain
 * intact. This activity is a placeholder launcher entry point so the app still builds,
 * installs and passes CI until a new UI is created.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
