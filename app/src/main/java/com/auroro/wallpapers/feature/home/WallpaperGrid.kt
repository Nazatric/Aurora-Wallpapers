package com.auroro.wallpapers.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.SkeletonBlock
import com.auroro.wallpapers.core.design.SourceDot
import com.auroro.wallpapers.core.model.Wallpaper

/** Responsive masonry gallery; images are always provider thumbnails and Coil sizes requests to each cell. */
@Composable
fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    initialLoadFinished: Boolean,
    endReached: Boolean,
    pageError: String?,
    onOpen: (Wallpaper) -> Unit,
    onFavorite: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Nothing here yet",
    emptyMessage: String = "Wallpapers from available sources will appear here.",
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyStaggeredGridState()
    val key = wallpapers.lastOrNull()?.key
    LaunchedEffect(state, key, loading, endReached) {
        androidx.compose.runtime.snapshotFlow {
            val info = state.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (wallpapers.isNotEmpty() && !loading && !endReached && lastVisible >= wallpapers.size - 4) onLoadMore()
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalItemSpacing = 11.dp,
    ) {
        if (headerContent != null) item(span = StaggeredGridItemSpan.FullLine) { headerContent() }

        if (wallpapers.isEmpty() && !initialLoadFinished) {
            items(6, key = { "skeleton-$it" }) { index ->
                SkeletonBlock(Modifier.fillMaxWidth().height(if (index % 3 == 0) 226.dp else 184.dp))
            }
        } else if (wallpapers.isEmpty() && initialLoadFinished) {
            item(span = StaggeredGridItemSpan.FullLine) {
                EmptyFeedState(
                    title = when {
                        pageError != null -> "Couldn't load wallpapers"
                        endReached -> emptyTitle
                        else -> "Still looking"
                    },
                    message = pageError ?: if (endReached) emptyMessage else "This filter matched no images on the first page. Continue searching the next provider pages.",
                    loading = loading,
                    canContinue = !endReached && pageError == null,
                    onContinue = onLoadMore,
                    onRetry = onRetry,
                )
            }
        } else {
            items(wallpapers.size, key = { wallpapers[it].key }) { index ->
                val w = wallpapers[index]
                WallpaperTile(
                    wallpaper = w,
                    favorite = w.key in favoriteKeys,
                    onClick = { onOpen(w) },
                    onFavorite = { onFavorite(w) },
                )
            }
            if (loading) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Aero.colors.accent, strokeWidth = 2.dp)
                        Text("  Finding more…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                }
            }
            if (pageError != null) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pageError, style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Retry", tint = Aero.colors.accent) }
                        }
                    }
                }
            }
            if (!endReached && !loading && wallpapers.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Load more", color = Aero.colors.accent) }
                    }
                }
            }
        }
        if (footerContent != null) item(span = StaggeredGridItemSpan.FullLine) { footerContent() }
    }
}

@Composable
private fun EmptyFeedState(title: String, message: String, loading: Boolean, canContinue: Boolean, onContinue: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = .3f), Aero.colors.emerald.copy(alpha = .12f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Aero.colors.accentLight, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Aero.colors.accent)
        else if (canContinue) androidx.compose.material3.Button(onClick = onContinue) { Text("Look further") }
        else if (title.contains("load", true)) androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun WallpaperTile(wallpaper: Wallpaper, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ratio = remember(wallpaper.width, wallpaper.height) {
        if (wallpaper.hasKnownDimensions) (wallpaper.width.toFloat() / wallpaper.height).coerceIn(0.68f, 1.18f)
        else 0.83f
    }
    val scale by animateFloatAsState(if (favorite) 1.12f else 1f, spring(dampingRatio = 0.52f, stiffness = 550f), label = "favorite-scale")
    var imageFailed by remember(wallpaper.thumbUrl) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF164666), Color(0xFF08283A))))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(wallpaper.thumbUrl)
                .memoryCacheKey("${wallpaper.key}:thumb")
                .diskCacheKey(wallpaper.thumbUrl)
                .crossfade(180)
                .build(),
            contentDescription = "${wallpaper.source.displayName} wallpaper, ${wallpaper.width} by ${wallpaper.height}${wallpaper.category?.let { ", $it" } ?: ""}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { imageFailed = true },
        )
        if (imageFailed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(22.dp))
                Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.69f)))))
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { SourceDot(wallpaper.source) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) Color(0xFFFF8FA0) else Color.White,
                    modifier = Modifier.size(20.dp).scale(scale),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(
                wallpaper.category ?: wallpaper.tags.firstOrNull()?.name ?: "${wallpaper.width} × ${wallpaper.height}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (wallpaper.creatorName != null) {
                Spacer(Modifier.height(2.dp))
                Text("by ${wallpaper.creatorName}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .77f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun OfflineTile(
    wallpaper: Wallpaper?,
    localUri: String?,
    title: String,
    subtitle: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageFailed by remember(localUri, wallpaper?.thumbUrl) { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().aspectRatio(.79f).clip(RoundedCornerShape(19.dp)).background(Brush.verticalGradient(listOf(Color(0xFF164666), Color(0xFF08283A))))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (localUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(localUri).memoryCacheKey("local:$localUri").crossfade(160).build(),
                contentDescription = wallpaper?.let { "Saved ${it.source.displayName} wallpaper, ${it.width} by ${it.height}" } ?: "Saved wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else if (wallpaper != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(wallpaper.thumbUrl).memoryCacheKey("${wallpaper.key}:thumb").crossfade(160).build(),
                contentDescription = "${wallpaper.source.displayName} wallpaper preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        }
        if (imageFailed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(22.dp))
                Text("Image unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFavorite != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .36f)), contentAlignment = Alignment.Center) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
                    Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) Color(0xFFFF8FA0) else Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}
