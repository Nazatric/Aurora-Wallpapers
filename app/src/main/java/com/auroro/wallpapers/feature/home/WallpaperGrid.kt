package com.auroro.wallpapers.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.InlineError
import com.auroro.wallpapers.core.design.SkeletonBlock
import com.auroro.wallpapers.core.model.Wallpaper

/** Responsive masonry grid. Each image keeps its provider-reported dimensions and full composition. */
@Composable
fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    loading: Boolean,
    initialLoadFinished: Boolean,
    endReached: Boolean,
    pageError: String?,
    onOpen: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Nothing here yet",
    emptyMessage: String = "Wallpapers from the selected sources will appear here.",
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyStaggeredGridState()
    val firstKey = wallpapers.firstOrNull()?.key
    val lastKey = wallpapers.lastOrNull()?.key
    var lastAutoLoadKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state, firstKey, lastKey, loading, endReached) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val totalItems = state.layoutInfo.totalItemsCount
                val nearEnd = totalItems > 0 && lastVisible >= totalItems - 4
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
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 5.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        if (headerContent != null) item(span = StaggeredGridItemSpan.FullLine) { headerContent() }

        if (wallpapers.isEmpty() && !initialLoadFinished) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Aero.colors.accent, strokeWidth = 2.dp)
                    Text("  Loading wallpapers…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
            }
            items(4, key = { "skeleton-$it" }) { index ->
                SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(if (index % 2 == 0) 0.78f else 0.92f))
            }
        } else if (wallpapers.isEmpty() && initialLoadFinished) {
            item(span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    title = when {
                        pageError != null -> "Couldn't load wallpapers"
                        endReached -> emptyTitle
                        else -> "No matches on this page"
                    },
                    message = pageError ?: if (endReached) emptyMessage else "Continue to the next provider page to look for more matches.",
                    action = if (pageError != null) {
                        { androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") } }
                    } else if (!endReached) {
                        { androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Look further") } }
                    } else null,
                )
            }
        } else {
            items(wallpapers, key = { it.key }) { wallpaper ->
                WallpaperTile(wallpaper = wallpaper, onClick = { onOpen(wallpaper) })
            }
            if (loading) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Aero.colors.accent, strokeWidth = 2.dp)
                        Text("  Loading images…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                }
            }
            if (pageError != null) {
                item(span = StaggeredGridItemSpan.FullLine) { InlineError(pageError, onRetry = onRetry) }
            }
            if (!endReached && !loading && wallpapers.isNotEmpty()) {
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

/** A quiet, image-only browsing tile. Provider, licence and favorite actions stay on the detail page. */
@Composable
fun WallpaperTile(wallpaper: Wallpaper, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val metadataRatio = wallpaper.takeIf { it.hasKnownDimensions }
        ?.aspectRatio?.takeIf { it.isFinite() && it > 0f }
    var ratio by remember(wallpaper.key, wallpaper.width, wallpaper.height) { mutableFloatStateOf(metadataRatio ?: 1f) }
    var imageUrl by remember(wallpaper.key, wallpaper.thumbUrl, wallpaper.previewUrl) { mutableStateOf(wallpaper.thumbUrl) }
    var imageLoaded by remember(wallpaper.key, wallpaper.thumbUrl, wallpaper.previewUrl) { mutableStateOf(false) }
    var imageFailed by remember(wallpaper.key, wallpaper.thumbUrl, wallpaper.previewUrl) { mutableStateOf(false) }
    val description = buildString {
        append("Wallpaper")
        if (wallpaper.hasKnownDimensions) append(", ${wallpaper.width} by ${wallpaper.height}")
        wallpaper.title?.takeIf(String::isNotBlank)?.let { append(", ").append(it) }
    }
    val shape = RoundedCornerShape(15.dp)

    Box(
        modifier.fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(Aero.colors.surfaceSolid)
            .border(0.8.dp, Aero.colors.glassRimLight.copy(alpha = if (Aero.colors.isDark) 0.28f else 0.64f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (!imageLoaded && !imageFailed) SkeletonBlock(Modifier.fillMaxSize())
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCacheKey(imageUrl)
                .diskCacheKey(imageUrl)
                .crossfade(140)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                imageLoaded = true
                if (metadataRatio == null) {
                    val image = state.result.image
                    if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                }
            },
            onError = {
                imageLoaded = false
                if (imageUrl != wallpaper.previewUrl) imageUrl = wallpaper.previewUrl else imageFailed = true
            },
        )
        if (imageFailed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Icon(Icons.Rounded.BrokenImage, null, tint = Aero.colors.textTertiary, modifier = Modifier.size(22.dp))
                Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Clean image preview for local downloads and history; captions and file actions live outside it. */
@Composable
fun OfflineTile(
    wallpaper: Wallpaper?,
    localUri: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    respectAspectRatio: Boolean = true,
) {
    val context = LocalContext.current
    val metadataRatio = wallpaper?.takeIf { it.hasKnownDimensions }
        ?.aspectRatio?.takeIf { it.isFinite() && it > 0f }
    var ratio by remember(localUri, wallpaper?.key, wallpaper?.width, wallpaper?.height, respectAspectRatio) {
        mutableFloatStateOf(metadataRatio ?: 1f)
    }
    var imageData by remember(localUri, wallpaper?.key, wallpaper?.thumbUrl, wallpaper?.previewUrl) {
        mutableStateOf(localUri ?: wallpaper?.thumbUrl)
    }
    var imageLoaded by remember(localUri, wallpaper?.key, wallpaper?.thumbUrl, wallpaper?.previewUrl) { mutableStateOf(false) }
    var imageFailed by remember(localUri, wallpaper?.key, wallpaper?.thumbUrl, wallpaper?.previewUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    val description = wallpaper?.let {
        if (it.hasKnownDimensions) "Saved wallpaper, ${it.width} by ${it.height}" else "Saved wallpaper"
    } ?: "Saved wallpaper preview"
    Box(
        modifier
            .then(if (respectAspectRatio) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier)
            .clip(shape)
            .background(Aero.colors.surfaceSolid)
            .border(0.8.dp, Aero.colors.glassRimLight.copy(alpha = if (Aero.colors.isDark) 0.28f else 0.64f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (imageData != null && !imageLoaded && !imageFailed) SkeletonBlock(Modifier.fillMaxSize())
        if (imageData != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .memoryCacheKey(if (localUri != null) "local:$localUri" else imageData)
                    .diskCacheKey(if (localUri != null) localUri else imageData)
                    .crossfade(130)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    imageLoaded = true
                    if (respectAspectRatio && metadataRatio == null) {
                        val image = state.result.image
                        if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                    }
                },
                onError = {
                    imageLoaded = false
                    val fallback = wallpaper?.previewUrl
                    if (localUri == null && fallback != null && imageData != fallback) imageData = fallback else imageFailed = true
                },
            )
        }
        if (imageFailed || imageData == null) {
            Icon(Icons.Rounded.BrokenImage, null, tint = Aero.colors.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}
