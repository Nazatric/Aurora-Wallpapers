package com.auroro.wallpapers.ui.detail

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.components.resolutionLabel
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.rememberBounceScale

/**
 * Slice-2 fullscreen preview: the artwork is shown whole (ContentScale.Fit on black) so no
 * orientation is ever cropped or misrepresented. Actions honour the data-layer rights gates:
 * `downloadAllowed` controls saving the original, `setWallpaperAllowed` controls applying it.
 * On HDR-capable displays the window switches to HDR colour mode while this screen is open
 * (Ultra HDR sources render with real headroom; SDR sources are unaffected), and the Set
 * action goes straight to the crop editor, which now owns the Home / Lock / Both choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    wallpaper: Wallpaper,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareSuffix = stringResource(R.string.share_suffix)
    var showInfo by remember(wallpaper.key) { mutableStateOf(false) }
    var chromeVisible by remember(wallpaper.key) { mutableStateOf(false) }
    val heartScale = rememberBounceScale(favorite)
    LaunchedEffect(wallpaper.key) { chromeVisible = true }
    // HDR: ask the window for HDR headroom only when the display actually supports it.
    DisposableEffect(wallpaper.key) {
        val window = (context as? Activity)?.window
        @Suppress("DEPRECATION")
        val display = (context as? Activity)?.windowManager?.defaultDisplay
        val hdrCapable = display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        if (window != null && hdrCapable) window.colorMode = ActivityInfo.COLOR_MODE_HDR
        onDispose {
            if (window != null && hdrCapable) window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
    }
    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = wallpaper.originalUrl,
            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(450, easing = AuroroMotion.EmphasizedDecelerate)) +
                slideInVertically(tween(450, easing = AuroroMotion.EmphasizedDecelerate)) { -it / 3 },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(icon = R.drawable.ic_arrow_back, label = stringResource(R.string.detail_back), onClick = onBack)
                Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(450, easing = AuroroMotion.EmphasizedDecelerate)) +
                slideInVertically(tween(450, easing = AuroroMotion.EmphasizedDecelerate)) { it / 3 },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(bottom = 20.dp, top = 40.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetailAction(
                        icon = if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_outlined,
                        label = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (favorite) Color(0xFFFF6B81) else null,
                        emphasized = false,
                        onClick = onToggleFavorite,
                        iconModifier = Modifier.graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        },
                    )
                    DetailAction(
                        icon = R.drawable.ic_share,
                        label = stringResource(R.string.detail_share),
                        emphasized = false,
                        onClick = {
                            val title = wallpaper.title?.takeIf { it.isNotBlank() }
                            val text = buildString {
                                if (title != null) {
                                    append(title)
                                    append(" — ")
                                }
                                append(shareSuffix)
                                append(wallpaper.pageUrl)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        null,
                                    ),
                                )
                            }
                        },
                    )
                    DetailAction(
                        icon = R.drawable.ic_download,
                        label = stringResource(R.string.detail_download),
                        emphasized = true,
                        onClick = onDownload,
                    )
                    DetailAction(
                        icon = R.drawable.ic_info_outlined,
                        label = stringResource(R.string.detail_info),
                        emphasized = false,
                        onClick = { showInfo = true },
                    )
                }
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = AuroroPal.accent),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stat_auroro),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.detail_set_wallpaper))
                }
            }
        }
    }

    if (showInfo) {
        InfoSheet(wallpaper = wallpaper, onDismiss = { showInfo = false })
    }
}

@Composable
private fun CircleButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp).clip(CircleShape).background(AuroroPal.surface.copy(alpha = 0.72f), CircleShape),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun DetailAction(
    icon: Int,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
    iconModifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = onClick,
            modifier = iconModifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (emphasized) AuroroPal.accent else AuroroPal.surface.copy(alpha = 0.78f),
                    CircleShape,
                ),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint ?: if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(wallpaper: Wallpaper, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroroPal.surface) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = wallpaper.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.info_untitled),
                style = MaterialTheme.typography.titleMedium,
            )
            InfoRow(stringResource(R.string.info_source), wallpaper.source.displayName)
            InfoRow(
                stringResource(R.string.info_dimensions),
                if (wallpaper.hasKnownDimensions) {
                    "${wallpaper.width} × ${wallpaper.height}" +
                        (resolutionLabel(wallpaper)?.let { " · $it" } ?: "")
                } else {
                    stringResource(R.string.info_unknown)
                },
            )
            wallpaper.category?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.info_category), it) }
            wallpaper.creatorName?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.info_creator), it) }
            wallpaper.attribution?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.info_rights), it) }
            wallpaper.licenseCode?.takeIf { it.isNotBlank() }?.let { code ->
                InfoRow(stringResource(R.string.info_licence), code)
            }
            if (wallpaper.tags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.info_tags),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textTertiary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    wallpaper.tags.forEach { tag ->
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroroPal.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AuroroPal.surfaceBright)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                wallpaper.pageUrl.toUri(),
                            ),
                        )
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.info_open_original))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AuroroPal.textTertiary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = AuroroPal.textSecondary)
    }
}
