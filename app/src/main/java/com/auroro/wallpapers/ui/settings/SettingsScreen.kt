package com.auroro.wallpapers.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.core.model.DiscoveryStyle
import com.auroro.wallpapers.ui.components.AuroraChip
import com.auroro.wallpapers.ui.components.formatBytes
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary
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
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_theme_mode),
                value = settings.themeMode.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_design_theme),
                value = settings.designTheme.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_glass),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_accent),
                value = settings.accent.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_typeface),
                value = settings.fontChoice.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_glass),
                value = settings.glassQuality.label,
                note = androidx.compose.ui.res.stringResource(R.string.soon_glass),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_amoled),
                value = if (settings.amoled) onLabel() else offLabel(),
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
            )
            ComingSoonRow(
                title = androidx.compose.ui.res.stringResource(R.string.set_contrast),
                value = if (settings.increaseContrast) onLabel() else offLabel(),
                note = androidx.compose.ui.res.stringResource(R.string.soon_theming),
                last = true,
            )
        }
        SettingsCard(title = androidx.compose.ui.res.stringResource(R.string.section_discovery)) {
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
                    color = AuroroTextTertiary,
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
                        thumbColor = AuroroAccent,
                        activeTrackColor = AuroroAccent,
                    ),
                )
            }
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.set_hidden),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 18.dp, top = 6.dp),
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.set_hidden_sub),
                style = MaterialTheme.typography.labelSmall,
                color = AuroroTextTertiary,
                modifier = Modifier.padding(start = 18.dp),
            )
            ChipFlow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                DiscoveryStyle.entries.forEach { style ->
                    AuroraChip(
                        label = style.label,
                        active = style.id in settings.hiddenDiscoveryStyleIds,
                        onClick = {
                            onUpdate { s ->
                                val hidden = if (style.id in s.hiddenDiscoveryStyleIds) {
                                    s.hiddenDiscoveryStyleIds - style.id
                                } else {
                                    s.hiddenDiscoveryStyleIds + style.id
                                }
                                s.copy(hiddenDiscoveryStyleIds = hidden)
                            }
                        },
                    )
                }
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
                color = AuroroTextTertiary,
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
                color = AuroroTextTertiary,
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
                colors = ButtonDefaults.buttonColors(containerColor = AuroroSurfaceBright),
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
                    color = AuroroTextTertiary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun onLabel(): String = androidx.compose.ui.res.stringResource(R.string.set_on)

@Composable
private fun offLabel(): String = androidx.compose.ui.res.stringResource(R.string.set_off)

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = AuroroAccent,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Surface(shape = RoundedCornerShape(24.dp), color = AuroroSurface) {
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
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = AuroroTextTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AuroroAccent,
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
                color = AuroroTextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = AuroroTextTertiary)
        }
        Text(text = note, style = MaterialTheme.typography.labelSmall, color = AuroroTextTertiary)
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
