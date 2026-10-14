package com.auroro.wallpapers.ui.coverflow

import com.auroro.wallpapers.ui.theme.AuroroPal
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.artAspect
import com.auroro.wallpapers.ui.components.wallpaperMeta
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.LocalGlassQuality
import com.auroro.wallpapers.ui.theme.auroroGlass
import com.auroro.wallpapers.ui.theme.auroroHazeSource
import com.auroro.wallpapers.ui.theme.rememberBounceScale
import kotlin.math.abs

private enum class CoverOrientation { ALL, PORTRAIT, LANDSCAPE }

/**
 * Cover-flow browser opened by the pill bar's magic button: the current discovery mix as a
 * rotating carousel with an icon-only All / Portrait / Landscape orientation filter. Side pages tilt away on the Y axis and shrink/fade with distance while
 * the centre card sits flat — pure graphicsLayer math, no bitmaps, cheap on the GPU.
 *
 * Cards fill their frame with [ContentScale.Crop]: a browsing carousel shows a full-bleed
 * crop (never letterboxed bars), while the detail screen keeps the honest whole-image view.
 */
@Composable
fun CoverFlowScreen(
    items: List<Wallpaper>,
    loading: Boolean,
    favoriteKeys: Set<String>,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onShuffle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    var orientation by rememberSaveable { mutableStateOf(CoverOrientation.ALL) }
    val visibleItems = remember(items, orientation) {
        when (orientation) {
            CoverOrientation.ALL -> items
            CoverOrientation.PORTRAIT -> items.filter { it.hasKnownDimensions && it.height > it.width }
            CoverOrientation.LANDSCAPE -> items.filter { it.hasKnownDimensions && it.width > it.height }
        }
    }
    val pagerState = rememberPagerState(pageCount = { visibleItems.size })
    val current = pagerState.currentPage.coerceIn(0, (visibleItems.size - 1).coerceAtLeast(0))
    val currentItem = visibleItems.getOrNull(current)

    // A fresh shuffle replaces the list: start the carousel back at the first card so the
    // new mix is what you see, not a stale page index.
    LaunchedEffect(items) {
        if (items.isNotEmpty() && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }
    LaunchedEffect(orientation) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    Box(
        modifier = modifier.fillMaxSize().background(AuroroPal.background)
            .auroroHazeSource(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(text = stringResource(R.string.coverflow_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.coverflow_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroroPal.textTertiary,
                    )
                }
                OrientationToggle(
                    selected = orientation,
                    onSelect = { orientation = it },
                )
                Spacer(Modifier.size(6.dp))
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.coverflow_close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            when {
                visibleItems.isEmpty() && loading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AuroroPal.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(14.dp))
                        Text(
                            text = stringResource(R.string.coverflow_fetching),
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroroPal.textSecondary,
                        )
                    }
                }

                visibleItems.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.coverflow_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.coverflow_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroroPal.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Button(
                            onClick = onShuffle,
                            colors = ButtonDefaults.buttonColors(containerColor = AuroroPal.accent),
                        ) {
                            Text(stringResource(R.string.coverflow_shuffle))
                        }
                    }
                }

                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 30.dp),
                    pageSpacing = (-6).dp,
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    val wallpaper = visibleItems[page]
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val distance = abs(offset).coerceIn(0f, 1f)
                    val density = LocalDensity.current
                    CoverFlowCard(
                        wallpaper = wallpaper,
                        favorite = wallpaper.key in favoriteKeys,
                        onToggleFavorite = onToggleFavorite,
                        onOpen = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                rotationY = offset.coerceIn(-1f, 1f) * 38f
                                cameraDistance = 16f * density.density
                                val scale = lerp(0.78f, 1f, 1f - distance)
                                scaleX = scale
                                scaleY = scale
                                alpha = lerp(0.38f, 1f, 1f - distance)
                            },
                    )
                }
            }
            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    fadeIn(tween(350, easing = AuroroMotion.EmphasizedDecelerate)) +
                        slideInVertically(tween(350, easing = AuroroMotion.EmphasizedDecelerate)) { it / 3 } togetherWith
                        fadeOut(tween(180, easing = AuroroMotion.EmphasizedAccelerate))
                },
                label = "coverflowCaption",
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp),
            ) { wallpaper ->
                if (wallpaper != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = wallpaper.title?.takeIf { it.isNotBlank() }
                                ?: wallpaperDescription(wallpaper),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = listOf(wallpaperMeta(wallpaper), wallpaper.source.displayName)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroroPal.textSecondary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.size(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onOpen(wallpaper) },
                                colors = ButtonDefaults.buttonColors(containerColor = AuroroPal.accent),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(stringResource(R.string.coverflow_open))
                            }
                            TextButton(onClick = onShuffle, shape = RoundedCornerShape(50)) {
                                Text(
                                    stringResource(R.string.coverflow_shuffle),
                                    color = AuroroPal.textSecondary,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(84.dp))
                }
            }
        }
    }
}

/** Icon-only segmented orientation filter — same pill language as the navigation bar. */
@Composable
private fun OrientationToggle(
    selected: CoverOrientation,
    onSelect: (CoverOrientation) -> Unit,
) {
    Row(
        modifier = Modifier
            .auroroGlass(
                shape = RoundedCornerShape(50),
                quality = LocalGlassQuality.current,
                fallback = AuroroPal.surfaceBright,
                tint = AuroroPal.surface,
            )
            .clip(RoundedCornerShape(50))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(CoverOrientation.ALL, R.drawable.ic_aspect_ratio, R.string.coverflow_orient_all, selected, onSelect)
        ToggleButton(CoverOrientation.PORTRAIT, R.drawable.ic_crop_portrait, R.string.coverflow_orient_portrait, selected, onSelect)
        ToggleButton(CoverOrientation.LANDSCAPE, R.drawable.ic_crop_landscape, R.string.coverflow_orient_landscape, selected, onSelect)
    }
}

@Composable
private fun ToggleButton(
    value: CoverOrientation,
    icon: Int,
    labelRes: Int,
    selected: CoverOrientation,
    onSelect: (CoverOrientation) -> Unit,
) {
    val active = value == selected
    val label = stringResource(labelRes)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) AuroroPal.accent.copy(alpha = 0.22f) else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label) { onSelect(value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) AuroroPal.accent else AuroroPal.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CoverFlowCard(
    wallpaper: Wallpaper,
    favorite: Boolean,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val heartScale = rememberBounceScale(favorite)
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .aspectRatio(artAspect(wallpaper).coerceIn(0.46f, 2.1f))
            .clip(shape)
            .background(AuroroPal.surface, shape)
            .clickable(role = Role.Button, onClick = { onOpen(wallpaper) }),
    ) {
        AsyncImage(
            // Fullscreen showcase: the carousel earns the original bytes, cards don't.
            model = wallpaper.originalUrl,
            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = { onToggleFavorite(wallpaper) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).graphicsLayer {
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
