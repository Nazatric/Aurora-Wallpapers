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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar

@Composable
fun CategoriesScreen(onMenu: () -> Unit, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HeaderBar("Explore topics", "${SEARCH_TOPICS.size} live search shortcuts", onMenu = onMenu)
        Text(
            "Each topic runs its shown phrase against enabled catalogues. These are search shortcuts, not provider categories.",
            Modifier.padding(start = 19.dp, end = 19.dp, top = 2.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Aero.colors.textSecondary,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(SEARCH_TOPICS, key = { it.query }) { topic ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Search ${topic.title}, phrase ${topic.query}"
                    },
                    shape = RoundedCornerShape(22.dp),
                    onClick = { onSearch(topic.query) },
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 136.dp).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(topic.accent.copy(alpha = if (Aero.colors.isDark) 0.19f else 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(topic.icon, null, tint = topic.accent, modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.Search, null, tint = Aero.colors.textTertiary, modifier = Modifier.size(19.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                            Text(
                                topic.query,
                                style = MaterialTheme.typography.bodySmall,
                                color = Aero.colors.textSecondary,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
