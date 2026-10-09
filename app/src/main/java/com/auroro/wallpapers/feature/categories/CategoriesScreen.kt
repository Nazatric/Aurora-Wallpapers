package com.auroro.wallpapers.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.HdrStrong
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar

private data class Shortcut(val title: String, val query: String, val summary: String, val icon: ImageVector, val color: Color)
private val categories = listOf(
    Shortcut("Ocean", "ocean water", "Blue horizons, waves and sea light", Icons.Rounded.Water, Color(0xFF58DDF3)),
    Shortcut("Nature", "nature forest", "Woodland, leaves and open green", Icons.Rounded.Forest, Color(0xFF75DEAA)),
    Shortcut("Sky", "sky clouds", "Clouds, sunlight and wide-open skies", Icons.Rounded.Cloud, Color(0xFF9CCEFF)),
    Shortcut("Mountains", "mountains landscape", "Ridgelines, peaks and quiet valleys", Icons.Rounded.Landscape, Color(0xFFFFC979)),
    Shortcut("Space", "space stars", "Stars, galaxies and distant light", Icons.Rounded.NightsStay, Color(0xFFC7B6FF)),
    Shortcut("Abstract", "abstract", "Shapes, colour and visual rhythm", Icons.Rounded.HdrStrong, Color(0xFFFF9DBD)),
    Shortcut("Architecture", "architecture", "Buildings and designed spaces", Icons.Rounded.Explore, Color(0xFFB8E0D2)),
)

@Composable
fun CategoriesScreen(onMenu: () -> Unit, onSearch: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderBar("Categories", "Useful search shortcuts · results stay live", onMenu = onMenu) }
        item { Text("These are search phrases, not provider categories. Tapping one runs the phrase as a real source search.", Modifier.padding(horizontal = 3.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
        items(categories, key = { it.title }) { item ->
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { onSearch(item.query) }) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(item.color.copy(alpha = .68f), item.color.copy(alpha = .17f)))), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                        Text(item.summary, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                    GlassIconButton(onClick = { onSearch(item.query) }, description = "Search ${item.title}", icon = Icons.Rounded.Search, tint = item.color)
                }
            }
        }
    }
}
