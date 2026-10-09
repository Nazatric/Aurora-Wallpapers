package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.OpenverseCategory
import com.auroro.wallpapers.core.model.OpenverseLicense
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlin.math.max
import kotlin.math.min

@Composable
fun FilterScreen(
    initial: WallpaperFilter,
    query: String,
    onBack: () -> Unit,
    onApply: (WallpaperFilter) -> Unit,
    aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
    enabledSources: Set<WallpaperSource> = WallpaperSource.integrated.toSet(),
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var customW by remember(initial) { mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.w?.toString().orEmpty()) }
    var customH by remember(initial) { mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.h?.toString().orEmpty()) }
    var minShort by remember(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.shortEdge?.toString().orEmpty()) }
    var minLong by remember(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.longEdge?.toString().orEmpty()) }
    var invalidAspectInput by remember(initial) { mutableStateOf(false) }
    var invalidResolutionInput by remember(initial) { mutableStateOf(false) }
    val tagKeywordConflict = query.isNotBlank() && !draft.openverseTag.isNullOrBlank()
    val colors = com.auroro.wallpapers.core.data.WallhavenQuery.PALETTE

    fun chooseSource(source: WallpaperSource?) {
        if (source != null && source !in enabledSources) return
        draft = when (source) {
            null -> draft.copy(
                sources = emptySet(),
                wallhavenCategories = emptySet(),
                colorHex = null,
                openverseTag = null,
                openverseCategory = null,
                openverseLicense = null,
            )
            WallpaperSource.WALLHAVEN -> draft.copy(
                sources = setOf(WallpaperSource.WALLHAVEN),
                openverseTag = null,
                openverseCategory = null,
                openverseLicense = null,
            )
            WallpaperSource.OPENVERSE -> draft.copy(
                sources = setOf(WallpaperSource.OPENVERSE),
                sort = SortOption.RELEVANCE,
                wallhavenCategories = emptySet(),
                colorHex = null,
            )
            WallpaperSource.ARCHIVED -> draft
        }
    }

    fun toggleWallhavenCategory(category: WallhavenCategory?) {
        if (WallpaperSource.WALLHAVEN !in enabledSources) return
        chooseSource(WallpaperSource.WALLHAVEN)
        if (category == null) {
            draft = draft.copy(wallhavenCategories = emptySet())
        } else {
            val selected = draft.wallhavenCategories.toMutableSet()
            if (!selected.add(category)) selected.remove(category)
            draft = draft.copy(wallhavenCategories = selected)
        }
    }

    fun chooseOpenverseFilter(transform: (WallpaperFilter) -> WallpaperFilter) {
        if (WallpaperSource.OPENVERSE !in enabledSources) return
        chooseSource(WallpaperSource.OPENVERSE)
        draft = transform(draft)
    }

    fun resetDraft() {
        draft = WallpaperFilter.Default
        customW = ""
        customH = ""
        minShort = ""
        minLong = ""
        invalidAspectInput = false
        invalidResolutionInput = false
    }

    val effectiveSources = (draft.sources.ifEmpty { enabledSources }).intersect(enabledSources)
    val isOnlyOpenverse = effectiveSources == setOf(WallpaperSource.OPENVERSE)
    val sortChoices = when {
        effectiveSources.isEmpty() || isOnlyOpenverse -> listOf(SortOption.RELEVANCE)
        else -> SortOption.entries
    }

    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = "Filters",
            subtitle = "Actual dimensions · source-aware filters",
            onBack = onBack,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilterSection("Sources", if (enabledSources.isEmpty()) "Enable a catalogue in Sources to search." else "All uses enabled catalogues.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlassPill("All", draft.sources.isEmpty(), enabled = enabledSources.isNotEmpty(), onClick = { chooseSource(null) })
                    GlassPill("Wallhaven", draft.sources == setOf(WallpaperSource.WALLHAVEN), enabled = WallpaperSource.WALLHAVEN in enabledSources, onClick = { chooseSource(WallpaperSource.WALLHAVEN) })
                    GlassPill("Openverse", draft.sources == setOf(WallpaperSource.OPENVERSE), enabled = WallpaperSource.OPENVERSE in enabledSources, onClick = { chooseSource(WallpaperSource.OPENVERSE) })
                }
            }

            FilterSection("Aspect ratio", "Exact ratio presets or a custom width-to-height match.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlassPill("Any", draft.aspect == AspectFilter.Any, onClick = { draft = draft.copy(aspect = AspectFilter.Any) })
                    AspectPreset.entries.forEach { preset ->
                        GlassPill(preset.label, draft.aspect == AspectFilter.Preset(preset), onClick = {
                            draft = draft.copy(aspect = AspectFilter.Preset(preset))
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Width", customW, { customW = it; invalidAspectInput = false }, Modifier.weight(1f), decimal = true)
                    Text(":", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textSecondary)
                    NumberField("Height", customH, { customH = it; invalidAspectInput = false }, Modifier.weight(1f), decimal = true)
                    TextButton(onClick = {
                        val ratio = AspectMath.parseCustom("$customW:$customH")
                        if (ratio == null) {
                            invalidAspectInput = true
                        } else {
                            invalidAspectInput = false
                            draft = draft.copy(aspect = AspectFilter.Custom(ratio))
                        }
                    }) { Text("Use") }
                }
                if (invalidAspectInput) Text("Enter positive width and height values (up to 100 each).", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                if (draft.aspect is AspectFilter.Custom) Text("Selected · ${draft.aspect.label}", style = MaterialTheme.typography.labelSmall, color = Aero.colors.success)
                Text(
                    "Relative tolerance: ${(aspectTolerance * 100).toInt()}%. Openverse can prefilter portrait or landscape only when the full tolerance range fits; actual dimensions decide the ratio match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aero.colors.textTertiary,
                )
            }

            FilterSection("Minimum resolution", "Short and long edges work for portrait and landscape images.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlassPill("Any", draft.resolution == ResolutionFilter.Any, onClick = { draft = draft.copy(resolution = ResolutionFilter.Any) })
                    ResolutionPreset.entries.forEach { preset ->
                        GlassPill(preset.label, draft.resolution == ResolutionFilter.Preset(preset), onClick = {
                            draft = draft.copy(resolution = ResolutionFilter.Preset(preset))
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Short edge px", minShort, { minShort = it; invalidResolutionInput = false }, Modifier.weight(1f))
                    NumberField("Long edge px", minLong, { minLong = it; invalidResolutionInput = false }, Modifier.weight(1f))
                    TextButton(onClick = {
                        val first = minShort.toIntOrNull()
                        val second = minLong.toIntOrNull()
                        if (first == null || second == null || first !in 1..20_000 || second !in 1..20_000) {
                            invalidResolutionInput = true
                        } else {
                            invalidResolutionInput = false
                            draft = draft.copy(resolution = ResolutionFilter.Custom(min(first, second), max(first, second)))
                        }
                    }) { Text("Use") }
                }
                if (invalidResolutionInput) Text("Enter both edge values between 1 and 20,000 pixels.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                if (draft.resolution is ResolutionFilter.Custom) Text("Selected · ${draft.resolution.label}", style = MaterialTheme.typography.labelSmall, color = Aero.colors.success)
                Text("Openverse wallpaper results have a 720 px minimum short edge even when the optional resolution filter is Any.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
            }

            FilterSection("Orientation", "Portrait and landscape use original dimensions. Edges within 5% of the longer side count as square.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Orientation.entries.forEach { orientation ->
                        GlassPill(orientation.label, draft.orientation == orientation, onClick = { draft = draft.copy(orientation = orientation) })
                    }
                }
            }

            if (effectiveSources.isNotEmpty()) {
                FilterSection("Sort", if (isOnlyOpenverse) "Openverse supports relevance order." else "New, popular and random ordering applies to Wallhaven only.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        sortChoices.forEach { sort -> GlassPill(sort.label, draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }) }
                    }
                    if (!isOnlyOpenverse && WallpaperSource.OPENVERSE in effectiveSources && draft.sort != SortOption.RELEVANCE) {
                        Text("Openverse remains relevance-ranked; sources are not given a made-up global ranking.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    }
                }
            }

            if (WallpaperSource.WALLHAVEN in enabledSources && (draft.sources.isEmpty() || draft.sources.contains(WallpaperSource.WALLHAVEN))) {
                FilterSection("Wallhaven categories", "Category and colour values come from Wallhaven metadata.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        GlassPill("All", draft.wallhavenCategories.isEmpty(), onClick = { toggleWallhavenCategory(null) })
                        WallhavenCategory.entries.forEach { category ->
                            GlassPill(
                                category.label,
                                category in draft.wallhavenCategories,
                                selectionRole = Role.Checkbox,
                                onClick = { toggleWallhavenCategory(category) },
                            )
                        }
                    }
                    Text("Dominant colour", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    ColorPalette(colors, draft.colorHex) { selected ->
                        chooseSource(WallpaperSource.WALLHAVEN)
                        draft = draft.copy(colorHex = selected)
                    }
                }
            }

            if (WallpaperSource.OPENVERSE in enabledSources && (draft.sources.isEmpty() || draft.sources.contains(WallpaperSource.OPENVERSE))) {
                FilterSection("Openverse metadata", "Tags, categories and licences are returned by the image API.") {
                    OutlinedTextField(
                        value = draft.openverseTag.orEmpty(),
                        onValueChange = { value -> chooseOpenverseFilter { it.copy(openverseTag = value.take(200).takeIf(String::isNotBlank)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Openverse tag") },
                        placeholder = { Text("For example: aurora") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                    if (tagKeywordConflict) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Openverse tag-only searches cannot be combined with a keyword.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Aero.colors.warning,
                            )
                            TextButton(onClick = { onApply(draft) }) {
                                Text("Clear keyword and apply tag", color = Aero.colors.accent)
                            }
                        }
                    }
                    Text("Category", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        GlassPill("Any", draft.openverseCategory == null, onClick = { chooseOpenverseFilter { it.copy(openverseCategory = null) } })
                        OpenverseCategory.entries.forEach { category ->
                            GlassPill(category.label, draft.openverseCategory == category, onClick = {
                                chooseOpenverseFilter { it.copy(openverseCategory = category) }
                            })
                        }
                    }
                    Text("Licence", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        GlassPill("Any", draft.openverseLicense == null, onClick = { chooseOpenverseFilter { it.copy(openverseLicense = null) } })
                        OpenverseLicense.entries.forEach { licence ->
                            GlassPill(licence.label, draft.openverseLicense == licence, onClick = {
                                chooseOpenverseFilter { it.copy(openverseLicense = licence) }
                            })
                        }
                    }
                    Text("Openverse aspect filters select orientation, not exact ratios; size bands are coarse. Real dimensions are checked locally. Licence groups follow the API; sampling licences are browse-only here. Check source terms before reuse.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        GlassPanel(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            shape = RoundedCornerShape(18.dp),
            elevation = 2.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${draft.activeCount} active", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = ::resetDraft) { Text("Reset") }
                Button(onClick = { onApply(draft) }, enabled = !tagKeywordConflict) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
        }
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || decimal && it == '.' }.take(8)) },
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ColorDot(null, selected == null, onSelect)
        colors.forEach { ColorDot(it, selected == it, onSelect) }
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
        Modifier.size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton) { onSelect(if (selected) null else hex) }
            .semantics { contentDescription = if (hex == null) "Any dominant colour" else "Dominant colour #$hex" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 2.dp else 1.dp, if (selected) Aero.colors.accent else Aero.colors.glassRimLight.copy(alpha = .58f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected && hex != null) Icon(Icons.Rounded.Check, null, tint = if (hex == "ffffff") Color.Black else Color.White, modifier = Modifier.size(17.dp))
            else if (hex == null) Icon(Icons.Rounded.ColorLens, null, tint = Aero.colors.accent, modifier = Modifier.size(17.dp))
        }
    }
}
