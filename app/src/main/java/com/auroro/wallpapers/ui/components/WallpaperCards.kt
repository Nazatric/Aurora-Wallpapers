package com.auroro.wallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

/**
 * Container aspect follows the provider's real dimensions; only extreme ratios are trimmed
 * by the clamp and images are drawn with [ContentScale.Fit], so artwork is always shown whole
 * in its true orientation — landscape never becomes a portrait crop and vice versa.
 */
private fun artAspect(wallpaper: Wallpaper): Float =
    wallpaper.aspectRatio.takeIf { wallpaper.hasKnownDimensions && it.isFinite() && it > 0f }
        ?.coerceIn(0.5f, 2.4f) ?: 1f

/** Honest resolution tag derived from real pixel dimensions; never invented. */
fun resolutionLabel(wallpaper: Wallpaper): String? {
    if (!wallpaper.hasKnownDimensions) return null
    return when {
        wallpaper.width >= 3840 || wallpaper.height >= 2160 -> "4K"
        wallpaper.width >= 2560 || wallpaper.height >= 1440 -> "QHD"
        wallpaper.width >= 1920 || wallpaper.height >= 1080 -> "FHD"
        wallpaper.height >= 720 || wallpaper.width >= 720 -> "HD"
        else -> "${wallpaper.width}×${wallpaper.height}"
    }
}

fun wallpaperDescription(wallpaper: Wallpaper): String =
    if (wallpaper.hasKnownDimensions) {
        "${wallpaper.width} by ${wallpaper.height}"
    } else {
        wallpaper.source.displayName
    }

@Composable
fun HeroPager(
    items: List<Wallpaper>,
    favoriteKeys: Set<String>,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 14.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) { page ->
            WallpaperArt(
                wallpaper = items[page],
                aspect = artAspect(items[page]),
                corner = 26.dp,
                favorite = items[page].key in favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onClick = { onOpen(items[page]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(items.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 18.dp else 4.dp, 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    AuroroAccent
                                } else {
                                    AuroroTextTertiary.copy(alpha = 0.5f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    favorite: Boolean,
    onToggleFavorite: (Wallpaper) -> Unit,
    onClick: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    WallpaperArt(
        wallpaper = wallpaper,
        aspect = artAspect(wallpaper),
        corner = 22.dp,
        favorite = favorite,
        onToggleFavorite = onToggleFavorite,
        onClick = { onClick(wallpaper) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun WallpaperArt(
    wallpaper: Wallpaper,
    aspect: Float,
    corner: androidx.compose.ui.unit.Dp,
    favorite: Boolean,
    onToggleFavorite: (Wallpaper) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioSafe(aspect)
            .clip(shape)
            .background(AuroroSurface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(
            model = wallpaper.thumbUrl,
            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xB3050508),
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 60.dp, bottom = 14.dp),
        ) {
            wallpaper.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            val meta = buildList {
                wallpaper.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                resolutionLabel(wallpaper)?.let { add(it) }
                if (isEmpty()) add(wallpaper.source.displayName)
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = { onToggleFavorite(wallpaper) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_outlined,
                ),
                contentDescription = stringResource(
                    if (favorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                tint = if (favorite) Color(0xFFFF6B81) else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** Aspect-ratio box that degrades to a fixed height when the ratio is unusable. */
private fun Modifier.aspectRatioSafe(ratio: Float): Modifier =
    if (ratio.isFinite() && ratio > 0f) aspectRatio(ratio) else height(260.dp)
