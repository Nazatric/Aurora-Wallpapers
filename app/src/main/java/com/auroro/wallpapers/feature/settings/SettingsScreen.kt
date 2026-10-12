package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.AppearancePreset
import com.auroro.wallpapers.core.data.FontChoice
import com.auroro.wallpapers.core.data.GlassQuality
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.HeaderBar
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
    var fontScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
    var tolerance by remember(settings.aspectTolerance) { mutableFloatStateOf(settings.aspectTolerance) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderBar("Settings", "Make Auroro yours", onMenu = onMenu) }

        item {
            SettingsGroup("Appearance", "Image-first surfaces, with glass where it helps.") {
                Text("Colour and atmosphere", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                ChoiceGrid(AppearancePreset.entries.map { it to it.label }, settings.appearance) { value ->
                    onSettings { it.copy(appearance = value) }
                }
                Text(settings.appearance.description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                Text("Accent", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                ChoiceGrid(AccentTheme.entries.map { it to it.label }, settings.accent) { value -> onSettings { it.copy(accent = value) } }
                Text("Typeface", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                ChoiceGrid(FontChoice.entries.map { it to it.label }, settings.fontChoice) { value -> onSettings { it.copy(fontChoice = value) } }
                Text("Text size · ${(fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textSecondary)
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    onValueChangeFinished = { onSettings { it.copy(fontScale = fontScale) } },
                    valueRange = 0.85f..1.30f,
                    steps = 8,
                )
                Text("Glass rendering", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                ChoiceGrid(GlassQuality.entries.map { it to it.label }, settings.glassQuality) { value -> onSettings { it.copy(glassQuality = value) } }
                Text(settings.glassQuality.description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                SettingSwitch("Reduce transparency", "Use the accessibility fallback with less live blur.", settings.reduceTransparency) { value -> onSettings { it.copy(reduceTransparency = value) } }
                SettingSwitch("Increase contrast", "Strengthen borders and control separation.", settings.increaseContrast) { value -> onSettings { it.copy(increaseContrast = value) } }
                if (settings.appearance == AppearancePreset.DARK_AERO || settings.appearance == AppearancePreset.SYSTEM) {
                    SettingSwitch("Black background", "Use deeper blacks in a dark appearance.", settings.amoled) { value -> onSettings { it.copy(amoled = value) } }
                }
            }
        }

        item {
            SettingsGroup("For You", "Wallhaven and public-domain Art Institute searches, plus the dated view-only APOD archive.") {
                Text(
                    "Search-query results need source-reported dimensions and a 720 px minimum short edge; portrait and 1080p are not forced. APOD does not report pixel dimensions, so those view-only entries remain unknown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aero.colors.textTertiary,
                )
                SettingSwitch("Rotate hourly", "Reuse a stable mix during the hour instead of polling repeatedly.", settings.rotateDiscoveryHourly) { value -> onSettings { it.copy(rotateDiscoveryHourly = value) } }
                SettingSwitch("Avoid recently shown", "Skip recent recipe and image IDs when possible; new results are not guaranteed.", settings.avoidRecentlySeen) { value -> onSettings { it.copy(avoidRecentlySeen = value) } }
                if (settings.avoidRecentlySeen) {
                    Text("Repeat window", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                    ChoiceGrid(listOf(12 to "12 hours", 24 to "24 hours", 72 to "3 days", 168 to "7 days"), settings.repeatAfterHours) { hours ->
                        onSettings { it.copy(repeatAfterHours = hours) }
                    }
                }
                Text("Preferred styles", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                Text("These preferences bias the source-backed mix; they do not invent category results.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                MultiChoiceGrid(DiscoveryStyle.entries.map { it.id to it.label }, settings.preferredDiscoveryStyleIds) { id ->
                    onSettings { current -> current.copy(preferredDiscoveryStyleIds = if (id in current.preferredDiscoveryStyleIds) current.preferredDiscoveryStyleIds - id else current.preferredDiscoveryStyleIds + id) }
                }
                Text("Exclude from Auto", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                MultiChoiceGrid(DiscoveryStyle.entries.map { it.id to it.label }, settings.hiddenDiscoveryStyleIds) { id ->
                    onSettings { current ->
                        val hidden = if (id in current.hiddenDiscoveryStyleIds) current.hiddenDiscoveryStyleIds - id else current.hiddenDiscoveryStyleIds + id
                        current.copy(hiddenDiscoveryStyleIds = hidden, preferredDiscoveryStyleIds = current.preferredDiscoveryStyleIds - id)
                    }
                }
                TextButton(onClick = onResetDiscovery, modifier = Modifier.align(Alignment.End)) { Text("Reset discovery preferences") }
            }
        }

        item {
            SettingsGroup("Image matching", "Filters use source-reported dimensions; unknown dimensions stay unknown.") {
                Text("Aspect-ratio tolerance · ${(tolerance * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                Slider(
                    value = tolerance,
                    onValueChange = { tolerance = it },
                    onValueChangeFinished = { onSettings { it.copy(aspectTolerance = tolerance) } },
                    valueRange = 0.01f..0.05f,
                    steps = 3,
                )
                Text("The selected tolerance is relative to each exact ratio preset.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
            }
        }

        item {
            SettingsGroup("Downloads", "Setting a wallpaper and saving a file remain separate actions.") {
                Text("Save originals to", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary)
                SaveLocation.entries.forEach { location ->
                    ChoiceRow(
                        label = location.label,
                        detail = location.description,
                        selected = settings.saveLocation == location,
                    ) { onSettings { it.copy(saveLocation = location) } }
                }
                SettingSwitch("Progress notifications", "Show progress while an original is saving.", settings.notifyProgress) { value ->
                    onSettings { it.copy(notifyProgress = value) }
                    if (value) onRequestNotificationPermission()
                }
                SettingSwitch("Result notifications", "Notify when a save completes or fails.", settings.notifyResult) { value ->
                    onSettings { it.copy(notifyResult = value) }
                    if (value) onRequestNotificationPermission()
                }
            }
        }

        item {
            SettingsGroup("Image cache", "The disposable preview cache never removes saved files.") {
                Text("Maximum cache · ${settings.cacheLimitMb} MB", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                ChoiceGrid(AppSettings.CACHE_CHOICES_MB.map { it to "$it MB" }, settings.cacheLimitMb) { value -> onSettings { it.copy(cacheLimitMb = value) } }
                Text("The new limit is applied the next time Auroro starts.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                TextButton(onClick = onClearCache, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                    Text("  Clear cached images")
                }
            }
        }

        item {
            SettingsGroup("Sources and information", "Provider terms, privacy and attribution.") {
                SettingAction("Wallpaper sources", "Wallhaven · NASA APOD · Art Institute · Reddit status", Icons.Rounded.Tune, onSources)
                SettingAction("Privacy", "On-device history, settings and saved files", Icons.Rounded.Policy, onPrivacy)
                SettingAction("Third-party notices", "Libraries, image rights and attribution", Icons.Rounded.Info, onLicenses)
                SettingAction("About Auroro", "Version, product details and acknowledgements", Icons.Rounded.Info, onAbout)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Aero.colors.surfaceSolid.copy(alpha = if (Aero.colors.isDark) .94f else .97f),
            border = BorderStroke(1.dp, Aero.colors.glassRimDark.copy(alpha = .13f)),
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { content() }
        }
    }
}

@Composable
private fun <T> ChoiceGrid(
    values: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { (value, label) ->
            val active = value == selected
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.semantics { this.selected = active },
                shape = RoundedCornerShape(13.dp),
                color = if (active) Aero.colors.accent.copy(alpha = if (Aero.colors.isDark) .20f else .11f) else Aero.colors.surfaceSolid.copy(alpha = .58f),
                border = BorderStroke(1.dp, if (active) Aero.colors.accent.copy(alpha = .38f) else Aero.colors.glassRimDark.copy(alpha = .15f)),
            ) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (active) Aero.colors.accent else Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun MultiChoiceGrid(values: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { (value, label) ->
            val active = value in selected
            Surface(
                onClick = { onToggle(value) },
                modifier = Modifier.semantics { this.selected = active },
                shape = RoundedCornerShape(13.dp),
                color = if (active) Aero.colors.accent.copy(alpha = if (Aero.colors.isDark) .20f else .11f) else Aero.colors.surfaceSolid.copy(alpha = .58f),
                border = BorderStroke(1.dp, if (active) Aero.colors.accent.copy(alpha = .38f) else Aero.colors.glassRimDark.copy(alpha = .15f)),
            ) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (active) Aero.colors.accent else Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Aero.colors.accent.copy(alpha = if (Aero.colors.isDark) .16f else .08f) else Aero.colors.surfaceSolid.copy(alpha = .42f),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 6.dp, end = 6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Aero.colors.surfaceSolid.copy(alpha = .48f),
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Aero.colors.accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            }
        }
    }
}
