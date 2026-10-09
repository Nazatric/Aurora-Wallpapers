package com.auroro.wallpapers.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import coil3.request.crossfade
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.InlineError
import com.auroro.wallpapers.core.design.SkeletonBlock
import com.auroro.wallpapers.core.design.SourceDot
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource

/** Responsive masonry grid. Each image cell keeps the provider's real aspect ratio and full composition. */
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
    emptyMessage: String = "Wallpapers from the selected sources will appear here.",
    hasLeadingResult: Boolean = false,
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyStaggeredGridState()
    val lastKey = wallpapers.lastOrNull()?.key
    val firstKey = wallpapers.firstOrNull()?.key
    var lastAutoLoadKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state, firstKey, lastKey, loading, endReached) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val nearEnd = lastVisible >= state.layoutInfo.totalItemsCount - 4
                if (wallpapers.isNotEmpty() && !loading && !endReached && nearEnd && lastKey != null && lastAutoLoadKey != lastKey) {
                    lastAutoLoadKey = lastKey
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 148.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 7.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalItemSpacing = 9.dp,
    ) {
        if (headerContent != null) item(span = StaggeredGridItemSpan.FullLine) { headerContent() }

        if (wallpapers.isEmpty() && !hasLeadingResult && !initialLoadFinished) {
            items(4, key = { "skeleton-$it" }) { index ->
                SkeletonBlock(Modifier.fillMaxWidth().height(if (index % 2 == 0) 206.dp else 168.dp))
            }
        } else if (wallpapers.isEmpty() && !hasLeadingResult && initialLoadFinished) {
            item(span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    title = when {
                        pageError != null -> "Couldn't load wallpapers"
                        endReached -> emptyTitle
                        else -> "No matches on this page"
                    },
                    message = pageError ?: if (endReached) emptyMessage else "Continue to the next provider page to look for more matches.",
                    modifier = Modifier,
                    action = if (pageError != null) {
                        { androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") } }
                    } else if (!endReached) {
                        { androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Look further") } }
                    } else null,
                )
            }
        } else {
            if (wallpapers.isNotEmpty()) {
                items(wallpapers, key = { it.key }) { wallpaper ->
                    WallpaperTile(
                        wallpaper = wallpaper,
                        favorite = wallpaper.key in favoriteKeys,
                        onClick = { onOpen(wallpaper) },
                        onFavorite = { onFavorite(wallpaper) },
                    )
                }
            }
            if (loading) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Aero.colors.accent, strokeWidth = 2.dp)
                        Text("  Loading images…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                }
            }
            if (pageError != null) {
                item(span = StaggeredGridItemSpan.FullLine) { InlineError(pageError, onRetry = onRetry) }
            }
            if (!endReached && !loading && (wallpapers.isNotEmpty() || hasLeadingResult)) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Load more", color = Aero.colors.accent) }
                    }
                }
            }
        }
        if (footerContent != null) item(span = StaggeredGridItemSpan.FullLine) { footerContent() }
    }
}

@Composable
fun WallpaperTile(wallpaper: Wallpaper, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val metadataRatio = if (wallpaper.hasKnownDimensions && wallpaper.aspectRatio.isFinite() && wallpaper.aspectRatio > 0f) wallpaper.aspectRatio else null
    var ratio by remember(wallpaper.key, wallpaper.width, wallpaper.height) { mutableFloatStateOf(metadataRatio ?: 1f) }
    val scale by animateFloatAsState(if (favorite) 1.08f else 1f, spring(dampingRatio = 0.64f, stiffness = 600f), label = "favorite-scale")
    var imageUrl by remember(wallpaper.key, wallpaper.thumbUrl, wallpaper.previewUrl) { mutableStateOf(wallpaper.thumbUrl) }
    var imageFailed by remember(wallpaper.key, wallpaper.thumbUrl, wallpaper.previewUrl) { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(15.dp))
            .background(Aero.colors.surfaceSolid)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCacheKey(imageUrl)
                .diskCacheKey(imageUrl)
                .crossfade(140)
                .build(),
            contentDescription = buildString {
                append("${wallpaper.source.displayName} wallpaper, ${wallpaper.width} by ${wallpaper.height}")
                wallpaper.title?.let { append(", $it") }
            },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                if (metadataRatio == null) {
                    val image = state.result.image
                    if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                }
            },
            onError = {
                if (imageUrl != wallpaper.previewUrl) imageUrl = wallpaper.previewUrl else imageFailed = true
            },
        )
        if (imageFailed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(21.dp))
                Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .86f))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.56f)))))
        Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { SourceDot(wallpaper.source) }
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) Color(0xFFFFA3AD) else Color.White,
                    modifier = Modifier.size(19.dp).scale(scale),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp)) {
            val label = wallpaper.title ?: wallpaper.category ?: wallpaper.tags.firstOrNull()?.name
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val contributorLabel = when (wallpaper.source) {
                WallpaperSource.OPENVERSE -> buildString {
                    wallpaper.licenseCode?.let { code ->
                        append(compactLicenseLabel(code))
                        wallpaper.licenseVersion?.let { append(' ').append(it) }
                    }
                    wallpaper.creatorName?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }.ifBlank { wallpaper.attribution.orEmpty() }.takeIf(String::isNotBlank)
                WallpaperSource.WALLHAVEN -> wallpaper.creatorName?.let { "Uploader: $it" }
                WallpaperSource.ARCHIVED -> null
            }
            if (contributorLabel != null) {
                Spacer(Modifier.height(1.dp))
                Text(contributorLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("${wallpaper.width} × ${wallpaper.height}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .82f), maxLines = 1)
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
    respectAspectRatio: Boolean = true,
) {
    val context = LocalContext.current
    val metadataRatio = wallpaper?.takeIf { it.hasKnownDimensions }
        ?.aspectRatio?.takeIf { it.isFinite() && it > 0f }
    var ratio by remember(localUri, wallpaper?.key, wallpaper?.width, wallpaper?.height, respectAspectRatio) {
        mutableFloatStateOf(metadataRatio ?: 0.82f)
    }
    var imageData by remember(localUri, wallpaper?.key, wallpaper?.thumbUrl, wallpaper?.previewUrl) { mutableStateOf(localUri ?: wallpaper?.thumbUrl) }
    var imageFailed by remember(localUri, wallpaper?.key, wallpaper?.thumbUrl, wallpaper?.previewUrl) { mutableStateOf(false) }
    Box(
        modifier
            .then(if (respectAspectRatio) Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(0.01f)) else Modifier)
            .clip(RoundedCornerShape(15.dp))
            .background(Aero.colors.surfaceSolid)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (imageData != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .memoryCacheKey(if (localUri != null) "local:$localUri" else imageData)
                    .diskCacheKey(if (localUri != null) localUri else imageData)
                    .crossfade(130)
                    .build(),
                contentDescription = wallpaper?.let { "${it.source.displayName} wallpaper, ${it.width} by ${it.height}" } ?: title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    if (respectAspectRatio && metadataRatio == null) {
                        val image = state.result.image
                        if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                    }
                },
                onError = {
                    val fallback = wallpaper?.previewUrl
                    if (localUri == null && fallback != null && imageData != fallback) imageData = fallback else imageFailed = true
                },
            )
        }
        if (imageFailed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(21.dp))
                Text("Image unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .86f))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .73f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(9.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFavorite != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                    Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) Color(0xFFFFA3AD) else Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun compactLicenseLabel(code: String): String {
    val normalized = code.lowercase(java.util.Locale.US)
    return when {
        normalized == "cc0" -> "CC0"
        normalized == "pdm" -> "PDM"
        normalized in setOf("by", "by-sa", "by-nd", "by-nc", "by-nc-sa", "by-nc-nd", "sampling+", "nc-sampling+") ->
            "CC ${normalized.uppercase(java.util.Locale.US)}"
        else -> normalized.uppercase(java.util.Locale.US)
    }
}
