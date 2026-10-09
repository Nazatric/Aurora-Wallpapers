package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.DownloadQuality
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.data.SourceAvailability
import com.auroro.wallpapers.core.data.ThemeMode
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.design.ProviderAvailabilityLine
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.WallpaperSource

@Composable
fun SettingsScreen(
    settings: AppSettings,
    availability: Map<WallpaperSource, SourceAvailability>,
    onMenu: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onSaveAbyssKey: (String) -> Boolean,
    onOpenSource: (String) -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onSources: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var keyText by remember(settings.abyssApiKey) { mutableStateOf(settings.abyssApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var keySaved by remember(settings.abyssApiKey) { mutableStateOf(false) }
    var sourceStatusVisible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        HeaderBar("Settings", "Your device, your look, your files", onMenu = onMenu)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Appearance", "Aero glass, tuned for readable wallpaper browsing.")
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { GlassPill(it.label, settings.themeMode == it, onClick = { onSettings { s -> s.copy(themeMode = it) } }) }
                    }
                    Text("Accent", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentTheme.entries.forEach { GlassPill(it.label, settings.accent == it, onClick = { onSettings { s -> s.copy(accent = it) } }) }
                    }
                    SettingSwitch("AMOLED black", "Pure black backdrop for dark presentation.", settings.amoled, enabled = settings.themeMode != ThemeMode.LIGHT) {
                        onSettings { s -> s.copy(amoled = it) }
                    }
                    SettingSwitch("Reduce transparency", "Strengthen glass tints for contrast and readability.", settings.reduceTransparency) {
                        onSettings { s -> s.copy(reduceTransparency = it) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Image filters", "Aspect matching is based on reported pixel dimensions.")
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Aspect tolerance · ${(settings.aspectTolerance * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Text("Relative difference from the selected width : height ratio.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    Slider(
                        value = settings.aspectTolerance,
                        onValueChange = { value -> onSettings { s -> s.copy(aspectTolerance = value) } },
                        valueRange = 0.01f..0.05f,
                        steps = 3,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectMath.TOLERANCE_CHOICES.forEach { value ->
                            GlassPill("${(value * 100).toInt()}%", kotlin.math.abs(settings.aspectTolerance - value) < .001f, onClick = { onSettings { s -> s.copy(aspectTolerance = value) } })
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Image cache", "Temporary thumbnails only · original downloads are kept separately.")
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Disk cache limit · ${settings.cacheLimitMb} MB", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.CACHE_CHOICES_MB.forEach { mb ->
                            GlassPill("$mb MB", settings.cacheLimitMb == mb, onClick = { onSettings { s -> s.copy(cacheLimitMb = mb) } })
                        }
                    }
                    Text("The new limit is used the next time Auroro starts. Memory cache stays bounded. Clearing cache never deletes saved wallpapers.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    TextButton(onClick = onClearCache) { Icon(Icons.Rounded.DeleteOutline, null, tint = Aero.colors.accent); Text("  Clear image cache", color = Aero.colors.accent) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Downloads", "Full-size images are downloaded only when you ask.")
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("Default quality", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    DownloadQuality.entries.forEach { quality ->
                        SettingChoice(quality.label, quality.description, settings.downloadQuality == quality) { onSettings { s -> s.copy(downloadQuality = quality) } }
                    }
                    Text("Save location", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    SaveLocation.entries.forEach { location ->
                        SettingChoice(location.label, location.description, settings.saveLocation == location) { onSettings { s -> s.copy(saveLocation = location) } }
                    }
                    SettingSwitch("Set after download", "When you explicitly start a download, also apply it to both screens. Off by default.", settings.setAfterDownload) {
                        onSettings { s -> s.copy(setAfterDownload = it) }
                    }
                    SettingSwitch("Download progress notifications", "Show the download foreground notification's percentage when available.", settings.notifyProgress) {
                        if (it) onRequestNotificationPermission()
                        onSettings { s -> s.copy(notifyProgress = it) }
                    }
                    SettingSwitch("Download result notifications", "Show success and failure notifications.", settings.notifyResult) {
                        if (it) onRequestNotificationPermission()
                        onSettings { s -> s.copy(notifyResult = it) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Wallpaper sources", "Access methods allowed by each publisher.", trailing = {
                TextButton(onClick = { sourceStatusVisible = !sourceStatusVisible }) { Text(if (sourceStatusVisible) "Hide status" else "Show status", color = Aero.colors.accent) }
            })
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SettingSwitch("Wallhaven", "Official public API · safe-for-work results only.", settings.wallhavenEnabled) {
                        onSettings { s -> s.copy(wallhavenEnabled = it) }
                    }
                    SettingSwitch("Wallpaper Abyss", "Official Alpha Coders API · requires your own paid API key.", settings.abyssEnabled) {
                        onSettings { s -> s.copy(abyssEnabled = it) }
                    }
                    Text("Wallpaper Abyss API key", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Text("Stored encrypted on this device with AndroidKeyStore. It is sent only to api.alphacoders.com.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; keySaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Your Alpha Coders API key") },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Aero.colors.textPrimary, unfocusedTextColor = Aero.colors.textPrimary, focusedBorderColor = Aero.colors.accent, unfocusedBorderColor = Aero.colors.glassRimDark, focusedLabelColor = Aero.colors.accent, cursorColor = Aero.colors.accent),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showKey, onCheckedChange = { showKey = it })
                        Text("Show key", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (onSaveAbyssKey(keyText)) keySaved = true
                        }) { Text(if (keySaved) "Saved" else "Save key", color = Aero.colors.accent) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpenSource("https://api.alphacoders.com/api/instructions") }) { Text("Official API docs", color = Aero.colors.accent); Icon(Icons.Rounded.OpenInNew, null, tint = Aero.colors.accent) }
                        TextButton(onClick = { onOpenSource("https://api.alphacoders.com/") }) { Text("Get a key", color = Aero.colors.accent); Icon(Icons.Rounded.OpenInNew, null, tint = Aero.colors.accent) }
                    }
                    Spacer(Modifier.width(1.dp))
                    Text("Unsplash · browser handoff only", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Text("Unsplash's published API rules explicitly exclude wallpaper applications. Auroro makes no Unsplash API calls.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    TextButton(onClick = { onOpenSource("https://unsplash.com/?utm_source=auroro_wallpapers&utm_medium=referral") }) { Text("Browse Unsplash", color = Aero.colors.accent) }
                }
            }
            if (sourceStatusVisible) {
                WallpaperSource.entries.forEach { source -> availability[source]?.let { ProviderAvailabilityLine(source, it, onOpenSource) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("About Auroro", "Free, ad-free, open source.")
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 5.dp) {
                Column(Modifier.padding(12.dp)) {
                    AboutRow("About Auroro Wallpapers", Icons.Rounded.Info, onAbout)
                    AboutRow("Privacy & data practices", Icons.Rounded.Security, onPrivacy)
                    AboutRow("Open-source notices", Icons.Rounded.OpenInNew, onLicenses)
                    AboutRow("Source status", Icons.Rounded.Settings, onSources)
                }
            }
        }
        Spacer(Modifier.padding(bottom = 18.dp))
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (enabled) Aero.colors.textPrimary else Aero.colors.textTertiary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Aero.colors.accent)
        Text("  $title", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.OpenInNew, null, tint = Aero.colors.textTertiary)
    }
}
