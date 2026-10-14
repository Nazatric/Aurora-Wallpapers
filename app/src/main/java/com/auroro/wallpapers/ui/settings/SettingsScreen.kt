package com.auroro.wallpapers.ui.settings

import com.auroro.wallpapers.ui.theme.AuroroPal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.FeedLayout
import com.auroro.wallpapers.core.data.GlassQuality
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.data.ThemeMode
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.ui.components.AuroraChip
import com.auroro.wallpapers.ui.components.formatBytes
import kotlin.math.roundToInt

/**
 * Slice-4 settings: grouped cards in the app's dark Material language. Every interactive row
 * writes a real [AppSettings] field consumed by the data layer; rows whose visual effect lands
 * in later slices are disabled with an honest note.
 */
@Composable
fun SettingsScreen(
    state: SettingsViewModel.UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClearCaches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.set_header),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp),
        )
        if (settings == null) {
            Spacer(Modifier.height(40.dp))
            return@Column
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_appearance)) {
            // Theme mode: Light / Dark / Follow system — drives the real colour scheme.
            SettingsRowTitle(androidx.compose.ui.res.stringResource(R.string.set_theme_mode))
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    AuroraChip(
                        label = mode.label,
                        active = settings.themeMode == mode,
                        onClick = { onUpdate { it.copy(themeMode = mode) } },
                    )
                }
            }
            // Design language: live palette presets (the glass rendering itself lands later).
            SettingsRowTitle(androidx.compose.ui.res.stringResource(R.string.set_design_theme))
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DesignTheme.entries.forEach { design ->
                    DesignThemeCard(
                        design = design,
                        selected = settings.designTheme == design,
                        onClick = { onUpdate { it.copy(designTheme = design) } },
                    )
                }
            }
            // Accent hue recolours brand, chips, buttons and indicators everywhere.
            SettingsRowTitle(androidx.compose.ui.res.stringResource(R.string.set_accent))
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                AccentTheme.entries.forEach { accent ->
                    AuroraChip(
                        label = accent.label,
                        active = settings.accent == accent,
                        onClick = { onUpdate { it.copy(accent = accent) } },
                    )
                }
            }
            // Font scale: applied through LocalDensity in the theme, so every screen obeys it.
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.set_font_scale,
                        (settings.fontScale * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = settings.fontScale,
                    onValueChange = { v ->
                        val stepped = (v / 0.05f).roundToInt() * 0.05f
                        onUpdate { it.copy(fontScale = stepped.coerceIn(0.85f, 1.3f)) }
                    },
                    valueRange = 0.85f..1.3f,
                    steps = 8,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = AuroroPal.accent,
                        activeTrackColor = AuroroPal.accent,
                    ),
                )
            }
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_typeface),
                value = settings.fontChoice.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
            )
            // Liquid Glass rendering tier: real Haze tiers, honest cost labels.
            SettingsRowTitle(androidx.compose.ui.res.stringResource(R.string.set_glass))
            Text(
                text = settings.glassQuality.description,
                style = MaterialTheme.typography.labelSmall,
                color = AuroroPal.textTertiary,
                modifier = Modifier.padding(start = 18.dp),
            )
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                GlassQuality.entries.forEach { quality ->
                    AuroraChip(
                        label = quality.label,
                        active = settings.glassQuality == quality,
                        onClick = { onUpdate { it.copy(glassQuality = quality) } },
                    )
                }
            }
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_amoled),
                subtitle = androidx.compose.ui.res.stringResource(R.string.set_amoled_sub),
                checked = settings.amoled,
                onCheckedChange = { v -> onUpdate { it.copy(amoled = v) } },
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_contrast),
                subtitle = androidx.compose.ui.res.stringResource(R.string.set_contrast_sub),
                checked = settings.increaseContrast,
                onCheckedChange = { v -> onUpdate { it.copy(increaseContrast = v) } },
                last = true,
            )
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_discovery)) {
            // Feed arrangement on Home: stacked cards, two-column grid or compact list.
            SettingsRowTitle(androidx.compose.ui.res.stringResource(R.string.set_feed_layout))
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                FeedLayout.entries.forEach { layout ->
                    AuroraChip(
                        label = layout.label,
                        active = settings.feedLayout == layout,
                        onClick = { onUpdate { it.copy(feedLayout = layout) } },
                        icon = when (layout) {
                            FeedLayout.CARDS -> R.drawable.ic_view_stream
                            FeedLayout.GRID -> R.drawable.ic_view_module
                            FeedLayout.LIST -> R.drawable.ic_view_list
                        },
                    )
                }
            }
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_rotate),
                subtitle = androidx.compose.ui.res.stringResource(R.string.set_rotate_sub),
                checked = settings.rotateDiscoveryHourly,
                onCheckedChange = { v -> onUpdate { it.copy(rotateDiscoveryHourly = v) } },
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_avoid),
                subtitle = androidx.compose.ui.res.stringResource(R.string.set_avoid_sub),
                checked = settings.avoidRecentlySeen,
                onCheckedChange = { v -> onUpdate { it.copy(avoidRecentlySeen = v) } },
            )
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.set_repeat, settings.repeatAfterHours),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.set_repeat_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textTertiary,
                )
                Slider(
                    value = settings.repeatAfterHours.toFloat(),
                    onValueChange = { v ->
                        val hours = (v / 6f).roundToInt() * 6
                        onUpdate { it.copy(repeatAfterHours = hours.coerceIn(6, 72)) }
                    },
                    valueRange = 6f..72f,
                    steps = 10,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = AuroroPal.accent,
                        activeTrackColor = AuroroPal.accent,
                    ),
                )
            }
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_wallhaven),
                checked = settings.wallhavenEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(wallhavenEnabled = v) } },
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_nasa),
                checked = settings.nasaApodEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(nasaApodEnabled = v) } },
                last = true,
            )
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_downloads)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.set_save_location),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp),
            )
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SaveLocation.entries.forEach { location ->
                    AuroraChip(
                        label = location.label,
                        active = settings.saveLocation == location,
                        onClick = { onUpdate { it.copy(saveLocation = location) } },
                    )
                }
            }
            Text(
                text = settings.saveLocation.description,
                style = MaterialTheme.typography.labelSmall,
                color = AuroroPal.textTertiary,
                modifier = Modifier.padding(start = 18.dp),
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_set_after),
                subtitle = androidx.compose.ui.res.stringResource(R.string.set_set_after_sub),
                checked = settings.setAfterDownload,
                onCheckedChange = { v -> onUpdate { it.copy(setAfterDownload = v) } },
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_notify_progress),
                checked = settings.notifyProgress,
                onCheckedChange = { v -> onUpdate { it.copy(notifyProgress = v) } },
            )
            SwitchRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_notify_result),
                checked = settings.notifyResult,
                onCheckedChange = { v -> onUpdate { it.copy(notifyResult = v) } },
                last = true,
            )
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_storage)) {
            state.storage?.let { info -> StorageRow(info) }
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.set_cache_limit),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp),
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.set_cache_sub),
                style = MaterialTheme.typography.labelSmall,
                color = AuroroPal.textTertiary,
                modifier = Modifier.padding(start = 18.dp),
            )
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                AppSettings.CACHE_CHOICES_MB.forEach { mb ->
                    AuroraChip(
                        label = "$mb MB",
                        active = settings.cacheLimitMb == mb,
                        onClick = { onUpdate { it.copy(cacheLimitMb = mb) } },
                    )
                }
            }
            Button(
                onClick = onClearCaches,
                colors = ButtonDefaults.buttonColors(containerColor = AuroroPal.surfaceBright),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.set_clear_cache),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_about)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.set_version,
                        state.versionName ?: "—",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.set_about_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SettingsRowTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun DesignThemeCard(design: DesignTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AuroroPal.accent.copy(alpha = 0.16f) else AuroroPal.surfaceBright)
            .clickable(role = Role.RadioButton, onClickLabel = design.label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = design.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) AuroroPal.textPrimary else AuroroPal.textSecondary,
            )
            Text(text = design.description, style = MaterialTheme.typography.labelSmall, color = AuroroPal.textTertiary)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(AuroroPal.accent),
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AuroroPal.accent,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AuroroPal.surface,
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun ColumnScope.SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = if (last) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = AuroroPal.textTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AuroroPal.accent,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun ColumnScope.ComingSoonRow(
    title: String,
    value: String,
    note: String,
    last: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = if (last) 10.dp else 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AuroroPal.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = AuroroPal.textTertiary)
        }
        Text(text = note, style = MaterialTheme.typography.labelSmall, color = AuroroPal.textTertiary)
    }
}

@Composable
private fun ColumnScope.ChipFlow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun StorageRow(info: StorageInfo) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(
            text = androidx.compose.ui.res.stringResource(
                R.string.set_storage_line,
                info.savedCount,
                formatBytes(info.savedBytes),
                formatBytes(info.deviceFreeBytes),
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
