package com.auroro.wallpapers.core.data

import android.content.Context
import java.security.KeyStore

/** Removes the encrypted credential left by the previous provider setup during the source migration. */
object LegacyDataCleanup {
    fun run(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences("provider_secrets", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .takeIf { it.containsAlias("auroro_provider_secret_v1") }
                ?.deleteEntry("auroro_provider_secret_v1")
        }
    }
}
