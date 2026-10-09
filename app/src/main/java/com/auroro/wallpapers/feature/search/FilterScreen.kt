package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.CustomRatio
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource

@Composable
fun FilterScreen(initial: WallpaperFilter, query: String, onBack: () -> Unit, onApply: (WallpaperFilter) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var customW by rememberSaveable(initial) {
        mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.w?.toString().orEmpty())
    }
    var customH by rememberSaveable(initial) {
        mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.h?.toString().orEmpty())
    }
    var minW by rememberSaveable(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.minWidth?.toString().orEmpty()) }
    var minH by rememberSaveable(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.minHeight?.toString().orEmpty()) }
    val showWallhaven = draft.sources.isEmpty() || WallpaperSource.WALLHAVEN in draft.sources
    val colors = listOf(
        "660000", "990000", "cc0000", "cc3333", "ea4c88", "993399", "663399", "333399", "0066cc", "0099cc",
        "66cccc", "77cc33", "669900", "336600", "666600", "999900", "cccc33", "ffff00", "ffcc33", "ff9900",
        "ff6600", "cc6633", "996633", "663300", "000000", "999999", "cccccc", "ffffff", "424153",
    )

    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = "Filters",
            subtitle = "Applied to real source results",
            onBack = onBack,
            trailing = {
                TextButton(onClick = {
                    draft = WallpaperFilter.Default
                    customW = ""
                    customH = ""
                    minW = ""
                    minH = ""
                }) { Text("Reset", color = Aero.colors.accent) }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Aspect ratio", "Measured from width ÷ height. Portrait 9:16 is 0.5625.")
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AspectPreset.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                GlassPill(preset.label, draft.aspect == AspectFilter.Preset(preset), onClick = { draft = draft.copy(aspect = AspectFilter.Preset(preset)) })
                            }
                            if (row.size < 3) Spacer(Modifier.weight(1f))
                        }
                    }
                    GlassPill("Any ratio", draft.aspect == AspectFilter.Any, onClick = { draft = draft.copy(aspect = AspectFilter.Any) })
                    SectionTitle("Custom ratio", "Enter width : height (for example 9 : 20). Ratios are orientation-aware.")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField("Width", customW, { customW = it }, Modifier.weight(1f), decimal = true)
                        Text(":", style = MaterialTheme.typography.titleLarge, color = Aero.colors.textSecondary)
                        NumberField("Height", customH, { customH = it }, Modifier.weight(1f), decimal = true)
                        TextButton(onClick = {
                            AspectMath.parseCustom("$customW:$customH")?.let { draft = draft.copy(aspect = AspectFilter.Custom(it)) }
                        }) { Text("Use", color = Aero.colors.accent) }
                    }
                    if (draft.aspect is AspectFilter.Custom) Text("Selected · ${draft.aspect.label}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.success)
                    Text("Matching tolerance: 3% of the target ratio. Change it in Settings.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Minimum resolution", "4K / 8K uses the short and long edges, regardless of orientation.")
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill("Any", draft.resolution == ResolutionFilter.Any, onClick = { draft = draft.copy(resolution = ResolutionFilter.Any) })
                        ResolutionPreset.entries.forEach { preset ->
                            GlassPill(preset.label, draft.resolution == ResolutionFilter.Preset(preset), onClick = { draft = draft.copy(resolution = ResolutionFilter.Preset(preset)) })
                        }
                    }
                    SectionTitle("Custom minimum", "Width and height are literal; for portrait, enter the portrait dimensions.")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField("Width px", minW, { minW = it }, Modifier.weight(1f))
                        NumberField("Height px", minH, { minH = it }, Modifier.weight(1f))
                        TextButton(onClick = {
                            val w = minW.toIntOrNull()
                            val h = minH.toIntOrNull()
                            if (w != null && h != null && w in 1..20000 && h in 1..20000) draft = draft.copy(resolution = ResolutionFilter.Custom(w, h))
                        }) { Text("Use", color = Aero.colors.accent) }
                    }
                    if (draft.resolution is ResolutionFilter.Custom) Text("Selected · ${draft.resolution.label}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.success)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Orientation", "Checked against actual pixel dimensions.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orientation.entries.forEach { item -> GlassPill(item.label, draft.orientation == item, onClick = { draft = draft.copy(orientation = item) }) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Sources", "Choose one or more. Unavailable providers are explained in the results.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill("All available", draft.sources.isEmpty(), onClick = { draft = draft.copy(sources = emptySet()) })
                    WallpaperSource.entries.forEach { source ->
                        GlassPill(source.displayName, source in draft.sources, onClick = {
                            val next = draft.sources.toMutableSet().apply { if (!add(source)) remove(source) }
                            draft = draft.copy(sources = next)
                        })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Sort", "Each provider uses its own documented ranking; scores aren't mixed.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val availableSorts = if (query.isBlank()) SortOption.entries.filter { it != SortOption.RELEVANCE } else SortOption.entries.toList()
                    availableSorts.forEach { sort -> GlassPill(sort.label, draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }) }
                }
                Text("Popular and random are available on Wallhaven. Wallpaper Abyss supports newest and search relevance only; unsupported sources are shown as skipped.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
            }

            if (showWallhaven) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Wallhaven categories", "Provider category codes are applied on the server.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill("All", draft.wallhavenCategories.isEmpty(), onClick = { draft = draft.copy(wallhavenCategories = emptySet()) })
                        WallhavenCategory.entries.forEach { category ->
                            GlassPill(category.label, category in draft.wallhavenCategories, onClick = {
                                val next = draft.wallhavenCategories.toMutableSet().apply { if (!add(category)) remove(category) }
                                draft = draft.copy(wallhavenCategories = next)
                            })
                        }
                    }
                    Text("Tags are searched from the Search field. Wallhaven supports ordinary, +required and -excluded tags.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Dominant color", "Uses Wallhaven's published palette. This filter is unavailable on other providers.")
                    ColorPalette(colors, draft.colorHex) { draft = draft.copy(colorHex = it) }
                }
            } else {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), elevation = 3.dp) {
                    Text("Wallhaven categories and color filtering are hidden because Wallhaven isn't selected.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        GlassPanel(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            shape = RoundedCornerShape(22.dp),
            elevation = 13.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${draft.activeCount} active", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Cancel", color = Aero.colors.textSecondary) }
                Spacer(Modifier.width(6.dp))
                Button(onClick = { onApply(draft) }) { Text("Apply filters") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || decimal && it == '.' }.take(7)) },
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Aero.colors.textPrimary,
            unfocusedTextColor = Aero.colors.textPrimary,
            focusedBorderColor = Aero.colors.accent,
            unfocusedBorderColor = Aero.colors.glassRimDark,
            focusedLabelColor = Aero.colors.accent,
            unfocusedLabelColor = Aero.colors.textTertiary,
            cursorColor = Aero.colors.accent,
        ),
    )
}

@Composable
private fun ColorPalette(colors: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorDot(null, selected == null, onSelect)
            colors.take(15).forEach { ColorDot(it, selected == it, onSelect) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.drop(15).forEach { ColorDot(it, selected == it, onSelect) }
        }
    }
}

@Composable
private fun ColorDot(hex: String?, selected: Boolean, onSelect: (String?) -> Unit) {
    val color = when (hex?.lowercase()) {
        "ffffff" -> Color.White
        null -> Aero.colors.surfaceSolid
        else -> runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Aero.colors.textSecondary)
    }
    Box(
        Modifier.size(34.dp).shadow(if (selected) 7.dp else 1.dp, CircleShape).clip(CircleShape)
            .background(color).semantics { contentDescription = if (hex == null) "Any dominant color" else "Dominant color #$hex" }
            .then(if (selected) Modifier.background(Color.Transparent, CircleShape) else Modifier)
            .clickableColor { onSelect(if (selected) null else hex) }
            .border(1.dp, if (selected) Aero.colors.accent else Aero.colors.glassRimLight.copy(alpha = .52f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) androidx.compose.material3.Icon(Icons.Rounded.Check, null, tint = if (hex == "ffffff") Color.Black else Color.White, modifier = Modifier.size(18.dp))
        else if (hex == null) androidx.compose.material3.Icon(Icons.Rounded.ColorLens, null, tint = Aero.colors.accent, modifier = Modifier.size(17.dp))
    }
}

private fun Modifier.clickableColor(onClick: () -> Unit) = this.clickable(onClick = onClick)
