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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar

@Composable
fun CategoriesScreen(onMenu: () -> Unit, onSearch: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HeaderBar("Search topics", "Live searches from enabled sources", onMenu = onMenu)
        }
        item {
            Text(
                "Each shortcut runs the shown phrase as a real search; these are not provider categories.",
                Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Aero.colors.textSecondary,
            )
        }
        items(SEARCH_TOPICS, key = { it.query }) { topic ->
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = { onSearch(topic.query) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(topic.accent.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(topic.icon, null, tint = topic.accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(topic.title, style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                        Text("Search · ${topic.query}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                    GlassIconButton(onClick = { onSearch(topic.query) }, description = "Search ${topic.title}", icon = Icons.Rounded.Search, tint = topic.accent)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
