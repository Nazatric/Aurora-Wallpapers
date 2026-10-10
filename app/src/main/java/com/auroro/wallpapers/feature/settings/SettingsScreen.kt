package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.AppearancePreset
import com.auroro.wallpapers.core.data.FontChoice
import com.auroro.wallpapers.core.data.GlassQuality
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.DiscoveryStyle

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onMenu: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onResetDiscovery: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onSources: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeaderBar("Settings", onMenu = onMenu)
        }
        item { SectionTitle("Appearance") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("Complete appearance", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppearancePreset.entries.forEach { preset ->
                            GlassPill(preset.label, settings.appearance == preset, onClick = { onSettings { it.copy(appearance = preset) } })
                        }
                    }
                    Text(settings.appearance.description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    Text("Typeface", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    ChoiceRow(FontChoice.entries.map { it to it.label }, settings.fontChoice) { value -> onSettings { it.copy(fontChoice = value) } }
                    Text("Text size · ${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textSecondary)
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { value -> onSettings { it.copy(fontScale = value) } },
                        valueRange = 0.85f..1.30f,
                        steps = 8,
                    )
                    Text(
                        "A clear, accessible wallpaper gallery",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Aero.colors.textPrimary,
                    )
                    Text("Glass rendering", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    ChoiceRow(GlassQuality.entries.map { it to it.label }, settings.glassQuality) { value -> onSettings { it.copy(glassQuality = value) } }
                    Text(settings.glassQuality.description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    SettingSwitch("Reduce transparency", "Use Haze's reduced-blur accessibility mode and clearer fallback panels.", settings.reduceTransparency) { value -> onSettings { it.copy(reduceTransparency = value) } }
                    SettingSwitch("Increase contrast", "Strengthen glass borders and separation around controls.", settings.increaseContrast) { value -> onSettings { it.copy(increaseContrast = value) } }
                    if (settings.appearance == AppearancePreset.DARK_AERO || settings.appearance == AppearancePreset.SYSTEM) {
                        SettingSwitch("Black background", "Use deeper blacks with Dark Aero or System dark mode.", settings.amoled) { value -> onSettings { it.copy(amoled = value) } }
                    }
                }
            }
        }
        item { SectionTitle("Discovery", "For You uses SFW Wallhaven searches and local repeat-avoidance") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SettingSwitch(
                        "Rotate For You hourly",
                        "A stable style and query are selected for each hour; the timer does not poll the provider repeatedly.",
                        settings.rotateDiscoveryHourly,
                    ) { value -> onSettings { it.copy(rotateDiscoveryHourly = value) } }
                    SettingSwitch(
                        "Avoid recently shown wallpapers",
                        "Previously loaded identifiers are ranked lower; this cannot guarantee a completely new result set.",
                        settings.avoidRecentlySeen,
                    ) { value -> onSettings { it.copy(avoidRecentlySeen = value) } }
                    if (settings.avoidRecentlySeen) {
                        Text("Allow repeats after", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textSecondary)
                        ChoiceRow(
                            listOf(12 to "12 h", 24 to "24 h", 72 to "3 days", 168 to "7 days"),
                            settings.repeatAfterHours,
                        ) { value -> onSettings { it.copy(repeatAfterHours = value) } }
                    }
                    Text("Preferred styles", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Text("When any are selected, Auto rotates only through those styles.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DiscoveryStyle.entries.forEach { style ->
                            val selected = style.id in settings.preferredDiscoveryStyleIds
                            GlassPill(
                                style.label,
                                selected,
                                selectionRole = Role.Checkbox,
                                onClick = { onSettings { it.copy(preferredDiscoveryStyleIds = if (selected) it.preferredDiscoveryStyleIds - style.id else it.preferredDiscoveryStyleIds + style.id) } },
                            )
                        }
                    }
                    Text("Exclude from Auto", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DiscoveryStyle.entries.forEach { style ->
                            val selected = style.id in settings.hiddenDiscoveryStyleIds
                            GlassPill(
                                style.label,
                                selected,
                                selectionRole = Role.Checkbox,
                                onClick = { onSettings { it.copy(
                                    hiddenDiscoveryStyleIds = if (selected) it.hiddenDiscoveryStyleIds - style.id else it.hiddenDiscoveryStyleIds + style.id,
                                    preferredDiscoveryStyleIds = if (selected) it.preferredDiscoveryStyleIds else it.preferredDiscoveryStyleIds - style.id,
                                ) } },
                            )
                        }
                    }
                    SettingSwitch(
                        "Reduce potentially explicit results",
                        "Wallhaven stays SFW and Openverse uses its safe API filter. Local checks only inspect unmistakable metadata markers; they cannot inspect pixels or certify safety.",
                        settings.reducePotentiallyExplicitContent,
                    ) { value -> onSettings { it.copy(reducePotentiallyExplicitContent = value) } }
                    TextButton(onClick = onResetDiscovery) { Text("Reset discovery preferences and cache history") }
                }
            }
        }
        item { SectionTitle("Image matching", "Relative aspect tolerance") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Aspect ratio", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    ChoiceRow(
                        AppSettingsAspectChoices,
                        settings.aspectTolerance,
                    ) { value -> onSettings { it.copy(aspectTolerance = value) } }
                    Text(
                        "Filters use the image's reported pixel dimensions; tolerance is relative to the selected ratio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aero.colors.textSecondary,
                    )
                }
            }
        }
        item { SectionTitle("Downloads") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Save originals to", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    SaveLocation.entries.forEach { location ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = settings.saveLocation == location,
                                    role = Role.RadioButton,
                                    onClick = { onSettings { it.copy(saveLocation = location) } },
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.saveLocation == location,
                                onClick = null,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(location.label, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
                                Text(location.description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                            }
                        }
                    }
                    SettingSwitch("Download progress notifications", "Show progress while an original is saving.", settings.notifyProgress) { value ->
                        onSettings { it.copy(notifyProgress = value) }
                        if (value) onRequestNotificationPermission()
                    }
                    SettingSwitch("Download result notifications", "Notify when a save finishes or fails.", settings.notifyResult) { value ->
                        onSettings { it.copy(notifyResult = value) }
                        if (value) onRequestNotificationPermission()
                    }
                }
            }
        }
        item { SectionTitle("Image cache") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Maximum disposable image cache", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    ChoiceRow(AppSettings.CACHE_CHOICES_MB.map { it to "$it MB" }, settings.cacheLimitMb) { value ->
                        onSettings { it.copy(cacheLimitMb = value) }
                    }
                    Text("The new limit applies the next time Auroro starts. Saved downloads are separate and aren't removed when this cache is cleared.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    TextButton(onClick = onClearCache) {
                        androidx.compose.material3.Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                        Text("  Clear cached images")
                    }
                }
            }
        }
        item { SectionTitle("Sources & information") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingAction("Wallpaper sources", "Wallhaven and Openverse", Icons.Rounded.Tune, onSources)
                SettingAction("Privacy", "Local history, settings and saved files", Icons.Rounded.Policy, onPrivacy)
                SettingAction("Third-party notices", "Libraries, licences and attribution", Icons.Rounded.Info, onLicenses)
                SettingAction("About Auroro", "Made using Openverse; not endorsed or certified by Openverse.", Icons.Rounded.Info, onAbout)
            }
        }
        item {
            Text(
                "Search results link to their original publishers. Openverse does not verify every licence claim; review the work's source page before reuse.",
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Aero.colors.textTertiary,
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

private val AppSettingsAspectChoices = listOf(0.01f to "1%", 0.02f to "2%", 0.03f to "3%", 0.05f to "5%")

@Composable
private fun <T> ChoiceRow(values: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEach { (value, label) ->
            GlassPill(label, selected == value, onClick = { onSelect(value) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Aero.colors.accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
