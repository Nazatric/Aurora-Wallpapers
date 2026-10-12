package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.ThemeMode
import com.auroro.wallpapers.core.design.AuroroTheme
import com.auroro.wallpapers.core.design.AmbientBackdrop

@Preview(name = "Settings · Material 3 Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun Material3SettingsPreview() {
    val settings = AppSettings(themeMode = ThemeMode.LIGHT, designTheme = DesignTheme.MATERIAL_3)
    PreviewFrame(settings)
}

@Preview(name = "Settings · Light Aero", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LightAeroSettingsPreview() {
    val settings = AppSettings(themeMode = ThemeMode.LIGHT, designTheme = DesignTheme.FRUTIGER_AERO)
    PreviewFrame(settings)
}

@Preview(name = "Settings · Dark Zen", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DarkZenSettingsPreview() {
    val settings = AppSettings(themeMode = ThemeMode.DARK, designTheme = DesignTheme.FRUTIGER_ZEN, amoled = true)
    PreviewFrame(settings)
}

@Composable
private fun PreviewFrame(settings: AppSettings) {
    AuroroTheme(settings) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackdrop(Modifier.fillMaxSize())
            SettingsScreen(
                settings = settings,
                onMenu = {},
                onSettings = {},
                onClearCache = {},
                onResetDiscovery = {},
                onAbout = {},
                onPrivacy = {},
                onLicenses = {},
                onSources = {},
                onRequestNotificationPermission = {},
            )
        }
    }
}
