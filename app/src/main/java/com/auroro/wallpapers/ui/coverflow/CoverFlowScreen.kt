package com.auroro.wallpapers.ui.coverflow

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
import com.auroro.wallpapers.ui.components.rememberBounceScale
import com.auroro.wallpapers.ui.components.resolutionLabel
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroBackground
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary
import com.auroro.wallpapers.ui.theme.rememberBounceScale
import kotlin.math.abs

/**
 * Cover-flow browser opened by the pill bar's magic button: the current discovery mix as a
 * rotating carousel. Side pages tilt away on the Y axis and shrink/fade with distance while
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
    val pagerState = rememberPagerState(pageCount = { items.size })
    val current = pagerState.currentPage.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val currentItem = items.getOrNull(current)

    // A fresh shuffle replaces the list: start the carousel back at the first card so the
    // new mix is what you see, not a stale page index.
    LaunchedEffect(items) {
        if (items.isNotEmpty() && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(AuroroBackground),
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
                        color = AuroroTextTertiary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.coverflow_close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            when {
                items.isEmpty() && loading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AuroroAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(14.dp))
                        Text(
                            text = stringResource(R.string.coverflow_fetching),
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroroTextSecondary,
                        )
                    }
                }

                items.isEmpty() -> Box(
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
                            color = AuroroTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Button(
                            onClick = onShuffle,
                            colors = ButtonDefaults.buttonColors(containerColor = AuroroAccent),
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
                    val wallpaper = items[page]
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
                            text = buildList {
                                wallpaper.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                                resolutionLabel(wallpaper)?.let { add(it) }
                                add(wallpaper.source.displayName)
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroroTextSecondary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.size(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onOpen(wallpaper) },
                                colors = ButtonDefaults.buttonColors(containerColor = AuroroAccent),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(stringResource(R.string.coverflow_open))
                            }
                            TextButton(onClick = onShuffle, shape = RoundedCornerShape(50)) {
                                Text(
                                    stringResource(R.string.coverflow_shuffle),
                                    color = AuroroTextSecondary,
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
            .background(AuroroSurface, shape)
            .clickable(role = Role.Button, onClick = { onOpen(wallpaper) }),
    ) {
        AsyncImage(
            model = wallpaper.previewUrl,
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
