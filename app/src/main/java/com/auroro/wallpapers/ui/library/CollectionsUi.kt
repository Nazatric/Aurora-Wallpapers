package com.auroro.wallpapers.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.WallpaperArt
import com.auroro.wallpapers.ui.components.artAspect
import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.theme.auroroGloss

/** One saved collection: a 2x2 mosaic of its real cover artworks over a glossy aero card. */
@Composable
fun CollectionCard(
    name: String,
    count: Int,
    covers: List<Wallpaper>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .auroroGloss(
                shape = RoundedCornerShape(22.dp),
                base = AuroroPal.surfaceBright,
                rim = AuroroPal.glow,
            )
            .clickableCard(onClick),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(2) { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        repeat(2) { row ->
                            val cover = covers.getOrNull(col * 2 + row)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AuroroPal.surface),
                            ) {
                                if (cover != null) {
                                    AsyncImage(
                                        model = cover.thumbUrl.takeIf(String::isNotBlank) ?: cover.previewUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_image),
                                        contentDescription = null,
                                        tint = AuroroPal.textTertiary,
                                        modifier = Modifier.align(Alignment.Center).size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, color = AuroroPal.textPrimary)
                    Text(
                        text = stringResource(R.string.collection_count, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroroPal.textSecondary,
                    )
                }
            }
        }
    }
}

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** Inside one collection: the real saved wallpapers, nothing invented. */
@Composable
fun CollectionDetailPane(
    name: String,
    items: List<Wallpaper>,
    favoriteKeys: Set<String>,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, color = AuroroPal.textPrimary)
                Text(
                    text = stringResource(R.string.collection_count, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textSecondary,
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.collection_rename),
                    tint = AuroroPal.textSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.collection_delete),
                    tint = AuroroPal.textSecondary,
                )
            }
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.collection_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroroPal.textSecondary,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 118.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
            ) {
                items(items, key = { it.key }) { wallpaper ->
                    WallpaperArt(
                        wallpaper = wallpaper,
                        aspect = artAspect(wallpaper),
                        corner = 20.dp,
                        favorite = wallpaper.key in favoriteKeys,
                        onToggleFavorite = onToggleFavorite,
                        onClick = { onOpen(wallpaper) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Glass header strip reused by the add-to-collection sheet rows. */
@Composable
fun CollectionSheetRow(
    name: String,
    member: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .auroroGloss(
                shape = RoundedCornerShape(18.dp),
                base = if (member) AuroroPal.accent.copy(alpha = 0.35f) else AuroroPal.surfaceBright,
                rim = if (member) Color.White else AuroroPal.glow,
            )
            .clickableCard(onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = AuroroPal.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(if (member) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outlined),
            contentDescription = null,
            tint = if (member) AuroroPal.accent else AuroroPal.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Honest loading indicator for collection flows. */
@Composable
fun CollectionLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AuroroPal.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
    }
}
