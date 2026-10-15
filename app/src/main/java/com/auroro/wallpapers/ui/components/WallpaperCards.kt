package com.auroro.wallpapers.ui.components

import com.auroro.wallpapers.ui.theme.AuroroPal
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.util.LruCache
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.rememberBounceScale

/**
 * Cards show the WHOLE artwork: the front layer is [ContentScale.Fit] inside the true-aspect
 * container, so a 1920x3970 portrait is never cropped to a square. Behind it the same image
 * is drawn cropped and heavily dimmed, which turns any residual letterbox area (metadata
 * disagreeing with the delivered frame) into a seamless ambient edge instead of hard bars.
 * Decoding is capped at [CARD_DECODE_MAX_PX] — above any phone panel's pixel count, so cards
 * stay pixel-perfect while a 4K bitmap never costs full memory in a scrolling feed.
 */
fun artAspect(wallpaper: Wallpaper): Float =
    wallpaper.aspectRatio.takeIf { wallpaper.hasKnownDimensions && it.isFinite() && it > 0f }
        ?.coerceIn(0.4f, 2.6f) ?: 1f

/**
 * Card meta line: real provider tags first (the useful "abstract", "oled"-style descriptors),
 * then the honest resolution tag. Wallhaven's `category` is "general" for ~90% of uploads,
 * so it only appears as a last resort.
 */
fun wallpaperMeta(wallpaper: Wallpaper): String = buildList {
    wallpaper.tags.take(2).forEach { tag -> add(tag.name) }
    resolutionLabel(wallpaper)?.let { add(it) }
    if (isEmpty()) {
        wallpaper.category?.takeIf { it.isNotBlank() }?.let { add(it) } ?: add(wallpaper.source.displayName)
    }
}.joinToString(" · ").ifBlank { wallpaper.source.displayName }

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

/** Feed decode cap: above any phone panel, below 4K memory cost. Detail keeps the original. */
private const val CARD_DECODE_MAX_PX = 1920

/**
 * Decoded aspects survive composition: lazy feeds recycle items, and re-measuring on every
 * re-entry made grid columns reflow mid-scroll (the "cards all over the place" lag). Once a
 * bitmap has reported its true ratio it is remembered process-wide, so a recycled card is born
 * at its final size and never animates the layout again.
 */
object AspectCache {
    private val cache = LruCache<String, Float>(512)
    fun get(key: String): Float? = cache.get(key)
    fun put(key: String, aspect: Float) { cache.put(key, aspect) }
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
                    val dotWidth by animateDpAsState(
                        targetValue = if (index == pagerState.currentPage) 18.dp else 4.dp,
                        animationSpec = AuroroMotion.BouncyDp,
                        label = "heroDot",
                    )
                    Box(
                        modifier = Modifier
                            .size(dotWidth, 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    AuroroPal.accent
                                } else {
                                    AuroroPal.textTertiary.copy(alpha = 0.5f)
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
fun WallpaperArt(
    wallpaper: Wallpaper,
    aspect: Float,
    corner: androidx.compose.ui.unit.Dp,
    favorite: Boolean,
    onToggleFavorite: (Wallpaper) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Called once per key when the image request fails permanently, so feeds can skip it. */
    onImageFailed: ((Wallpaper) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(corner)
    val heartScale = rememberBounceScale(favorite)
    // Born at the cached true aspect when known, else metadata; the first decode may still
    // correct it once, but never with an animation — animating aspect reflows every column of
    // a staggered grid and reads as lag. Cards stay static where the user left them.
    var measuredAspect by remember(wallpaper.key) {
        mutableFloatStateOf(AspectCache.get(wallpaper.key) ?: aspect)
    }
    val liveAspect = measuredAspect
    var reportedFailures by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    // Feeds load the provider's sized THUMB, never the multi-MB original: that is what thumbs
    // exist for, and it is the difference between an instant card and a black rectangle.
    val feedUrl = wallpaper.thumbUrl.takeIf(String::isNotBlank) ?: wallpaper.previewUrl
    val request = remember(feedUrl) {
        ImageRequest.Builder(context)
            .data(feedUrl)
            .size(CARD_DECODE_MAX_PX, CARD_DECODE_MAX_PX)
            .build()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioSafe(liveAspect)
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
            .background(AuroroPal.surface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // Ambient fill: same art cropped + dimmed, so empty ratios read as depth, not bars.
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f },
        )
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
            contentScale = ContentScale.Fit,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        val w = state.result.image.width
                        val h = state.result.image.height
                        if (w > 0 && h > 0) {
                            val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.35f, 2.8f)
                            measuredAspect = ratio
                            AspectCache.put(wallpaper.key, ratio)
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        if (wallpaper.key !in reportedFailures) {
                            reportedFailures = reportedFailures + wallpaper.key
                            onImageFailed?.invoke(wallpaper)
                        }
                    }
                    else -> Unit
                }
            },
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
            Text(
            text = wallpaper.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.untitled_wallpaper),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
            val meta = wallpaperMeta(wallpaper)
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = { onToggleFavorite(wallpaper) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 6.dp)
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                },
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
