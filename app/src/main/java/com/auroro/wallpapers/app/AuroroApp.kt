package com.auroro.wallpapers.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader

class AuroroApp : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }

    override fun newImageLoader(context: Context): ImageLoader = container.imageLoader

    override fun onCreate() {
        super.onCreate()
        // Creating the channel is cheap and lets WorkManager post truthful foreground progress.
        container.notifier.ensureChannel()
    }
}
