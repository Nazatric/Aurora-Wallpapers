package com.auroro.wallpapers.app

import android.app.Application
import androidx.work.Configuration

class AuroroApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
}
