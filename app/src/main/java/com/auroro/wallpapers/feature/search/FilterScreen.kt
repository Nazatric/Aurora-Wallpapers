package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.WallhavenQuery
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlin.math.roundToInt

@Composable
fun FilterScreen(
    initial: WallpaperFilter,
    query: String,
    onBack: () -> Unit,
    onApply: (WallpaperFilter) -> Unit,
    aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
    enabledSources: Set<WallpaperSource> = WallpaperSource.integrated.toSet(),
) {
    var draft by remember(initial, query, enabledSources) { mutableStateOf(initial) }
    var customW by remember(initial) { mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.w?.toString().orEmpty()) }
    var customH by remember(initial) { mutableStateOf((initial.aspect as? AspectFilter.Custom)?.ratio?.h?.toString().orEmpty()) }
    var minShort by remember(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.shortEdge?.toString().orEmpty()) }
    var minLong by remember(initial) { mutableStateOf((initial.resolution as? ResolutionFilter.Custom)?.longEdge?.toString().orEmpty()) }
    var invalidAspect by remember(initial) { mutableStateOf(false) }
    var invalidResolution by remember(initial) { mutableStateOf(false) }

    val activeSources = (draft.sources.ifEmpty { enabledSources }).intersect(enabledSources)
    val onlyWallhaven = activeSources == setOf(WallpaperSource.WALLHAVEN)
    val sortChoices = buildList {
        if (activeSources.isNotEmpty()) add(SortOption.SOURCE_DEFAULT)
        if (query.isNotBlank() && activeSources.any { it == WallpaperSource.WALLHAVEN || it == WallpaperSource.ART_INSTITUTE }) {
            add(SortOption.RELEVANCE)
        }
        if (query.isBlank() && activeSources.any { it == WallpaperSource.WALLHAVEN || it == WallpaperSource.NASA_APOD }) {
            add(SortOption.NEWEST)
        }
        if (onlyWallhaven) addAll(listOf(SortOption.POPULAR, SortOption.RANDOM))
    }.distinct()

    fun chooseSource(source: WallpaperSource?) {
        if (source != null && source !in enabledSources) return
        draft = SearchSourceSelection.select(draft, source?.let(::setOf).orEmpty(), query)
    }

    fun setCategory(category: WallhavenCategory?) {
        if (WallpaperSource.WALLHAVEN !in enabledSources) return
        draft = SearchSourceSelection.select(draft, setOf(WallpaperSource.WALLHAVEN), query)
        draft = draft.copy(
            wallhavenCategories = when {
                category == null -> emptySet()
                category in draft.wallhavenCategories -> draft.wallhavenCategories - category
                else -> draft.wallhavenCategories + category
            },
        )
    }

    fun reset() {
        draft = WallpaperFilter.Default
        customW = ""
        customH = ""
        minShort = ""
        minLong = ""
        invalidAspect = false
        invalidResolution = false
    }

    Column(Modifier.fillMaxSize()) {
        HeaderBar("Filters", "Source-aware · measured image dimensions", onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            FilterBlock("Catalogues", "Each catalogue keeps its own results and ranking.") {
                SourceChoice("All enabled", draft.sources.isEmpty(), enabledSources.isNotEmpty()) { chooseSource(null) }
                WallpaperSource.integrated.forEach { source ->
                    SourceChoice(source.displayName, draft.sources == setOf(source), source in enabledSources) {
                        chooseSource(source)
                    }
                }
            }

            FilterBlock("Orientation", "Uses the provider's original pixel dimensions; unknown dimensions are not guessed.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Orientation.entries.forEach { option ->
                        SelectableTile(
                            label = option.label,
                            selected = draft.orientation == option,
                            onClick = { draft = draft.copy(orientation = option) },
                        )
                    }
                }
            }

            FilterBlock("Aspect ratio", "Match a composition rather than a cropped card.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableTile("Any", draft.aspect == AspectFilter.Any) { draft = draft.copy(aspect = AspectFilter.Any) }
                    AspectPreset.entries.forEach { preset ->
                        SelectableTile(preset.label, draft.aspect == AspectFilter.Preset(preset)) {
                            draft = draft.copy(aspect = AspectFilter.Preset(preset))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Width", customW, { customW = it; invalidAspect = false }, Modifier.weight(1f), decimal = true)
                    Text(":", color = Aero.colors.textSecondary)
                    NumberField("Height", customH, { customH = it; invalidAspect = false }, Modifier.weight(1f), decimal = true)
                    TextButton(onClick = {
                        val ratio = AspectMath.parseCustom("$customW:$customH")
                        if (ratio == null) invalidAspect = true else {
                            invalidAspect = false
                            draft = draft.copy(aspect = AspectFilter.Custom(ratio))
                        }
                    }) { Text("Set") }
                }
                if (invalidAspect) HelperText("Enter positive width and height values, up to 100 each.")
                if (draft.aspect != AspectFilter.Any) HelperText("Tolerance ${(aspectTolerance * 100).roundToInt()}% · checked against source dimensions")
            }

            FilterBlock("Minimum resolution", "Both edges are verified locally against the image metadata.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableTile("Any", draft.resolution == ResolutionFilter.Any) { draft = draft.copy(resolution = ResolutionFilter.Any) }
                    ResolutionPreset.entries.forEach { preset ->
                        SelectableTile(preset.label, draft.resolution == ResolutionFilter.Preset(preset)) {
                            draft = draft.copy(resolution = ResolutionFilter.Preset(preset))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Short edge", minShort, { minShort = it; invalidResolution = false }, Modifier.weight(1f))
                    NumberField("Long edge", minLong, { minLong = it; invalidResolution = false }, Modifier.weight(1f))
                    TextButton(onClick = {
                        val short = minShort.toIntOrNull()
                        val long = minLong.toIntOrNull()
                        if (short == null || long == null || short !in 1..32_000 || long !in short..32_000) invalidResolution = true else {
                            invalidResolution = false
                            draft = draft.copy(resolution = ResolutionFilter.Custom(short, long))
                        }
                    }) { Text("Set") }
                }
                if (invalidResolution) HelperText("Use whole-pixel edges from 1 to 32,000; the long edge cannot be shorter.")
            }

            FilterBlock("Order", "Provider rankings are never blended into a made-up global score.") {
                if (sortChoices.isEmpty()) HelperText("Turn on at least one catalogue in Sources.")
                sortChoices.forEach { option ->
                    SourceChoice(option.label, draft.sort == option, true) { draft = draft.copy(sort = option) }
                }
            }

            if (onlyWallhaven) {
                FilterBlock("Wallhaven categories", "Uses Wallhaven's own category parameter.") {
                    WallhavenCategory.entries.forEach { category ->
                        SourceChoice(category.label, category in draft.wallhavenCategories, true) {
                            setCategory(category)
                        }
                    }
                    SourceChoice("All categories", draft.wallhavenCategories.isEmpty(), true) { setCategory(null) }
                }
                FilterBlock("Dominant colour", "Only Wallhaven reports a colour palette.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectableTile("Any", draft.colorHex == null) { draft = draft.copy(colorHex = null) }
                        WallhavenQuery.PALETTE.take(18).forEach { value ->
                            val colour = remember(value) { Color(android.graphics.Color.parseColor("#$value")) }
                            Surface(
                                onClick = { draft = draft.copy(colorHex = if (draft.colorHex.equals(value, true)) null else value) },
                                modifier = Modifier.size(38.dp).semantics { selected = draft.colorHex.equals(value, true) },
                                shape = CircleShape,
                                color = colour,
                                border = BorderStroke(if (draft.colorHex.equals(value, true)) 3.dp else 1.dp, Aero.colors.glassRimDark.copy(alpha = .35f)),
                            ) {
                                if (draft.colorHex.equals(value, true)) androidx.compose.material3.Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.padding(9.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(color = Aero.colors.surfaceSolid.copy(alpha = .96f), shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = ::reset) { Text("Reset") }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Aero.colors.accent, contentColor = Aero.colors.onAccent),
                ) { Text("Show results") }
            }
        }
    }
}

@Composable
private fun FilterBlock(title: String, note: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
        if (note != null) HelperText(note)
        content()
    }
}

@Composable
private fun SourceChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { this.selected = selected },
        shape = RoundedCornerShape(15.dp),
        color = if (selected) Aero.colors.accent.copy(alpha = if (Aero.colors.isDark) .20f else .11f) else Aero.colors.surfaceSolid.copy(alpha = .72f),
        border = BorderStroke(1.dp, if (selected) Aero.colors.accent.copy(alpha = .36f) else Aero.colors.glassRimDark.copy(alpha = .15f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (enabled) Aero.colors.textPrimary else Aero.colors.textTertiary)
        }
    }
}

@Composable
private fun SelectableTile(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Aero.colors.accent.copy(alpha = if (Aero.colors.isDark) .2f else .12f) else Aero.colors.surfaceSolid.copy(alpha = .78f),
        border = BorderStroke(1.dp, if (selected) Aero.colors.accent.copy(alpha = .42f) else Aero.colors.glassRimDark.copy(alpha = .14f)),
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (selected) Aero.colors.accent else Aero.colors.textSecondary)
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { char -> char.isDigit() || decimal && char == '.' }.take(6)) },
        modifier = modifier,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Aero.colors.accent,
            unfocusedBorderColor = Aero.colors.glassRimDark.copy(alpha = .3f),
            focusedTextColor = Aero.colors.textPrimary,
            unfocusedTextColor = Aero.colors.textPrimary,
            focusedLabelColor = Aero.colors.accent,
            unfocusedLabelColor = Aero.colors.textTertiary,
        ),
    )
}

@Composable
private fun HelperText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
}
