package com.auroro.wallpapers.feature.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.CropMath
import com.auroro.wallpapers.core.data.download.LocalFiles
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.LocalAeroHazeState
import com.auroro.wallpapers.core.model.Wallpaper
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun CropScreen(
    wallpaper: Wallpaper?,
    target: ApplyTarget,
    download: DownloadEntity?,
    onBack: () -> Unit,
    onEnsureDownload: (Wallpaper) -> Unit,
    onRetry: (String) -> Unit,
    onApply: (String, ApplyTarget, NormalizedCrop) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var fileExists by remember(download?.localUri) { mutableStateOf(false) }
    val canSetWallpaper = wallpaper?.setWallpaperAllowed == true
    LaunchedEffect(download?.localUri, download?.status) {
        fileExists = withContext(Dispatchers.IO) {
            download?.status == DownloadStatus.COMPLETED.name && LocalFiles.exists(context, download.localUri)
        }
    }
    LaunchedEffect(wallpaper?.key, fileExists, download?.status) {
        if (!fileExists && wallpaper != null && wallpaper.downloadAllowed && wallpaper.setWallpaperAllowed &&
            download?.status != DownloadStatus.RUNNING.name && download?.status != DownloadStatus.QUEUED.name &&
            download?.status != DownloadStatus.FAILED.name && download?.status != DownloadStatus.CANCELED.name
        ) {
            onEnsureDownload(wallpaper)
        }
    }

    val localUri = download?.localUri?.takeIf { fileExists }
    val imageUri = localUri ?: wallpaper?.previewUrl ?: wallpaper?.thumbUrl
    val width = download?.savedWidth?.takeIf { it > 0 } ?: wallpaper?.width?.coerceAtLeast(1) ?: 1
    val height = download?.savedHeight?.takeIf { it > 0 } ?: wallpaper?.height?.coerceAtLeast(1) ?: 1
    var scale by remember(wallpaper?.key) { mutableFloatStateOf(1f) }
    var offsetX by remember(wallpaper?.key) { mutableFloatStateOf(0f) }
    var offsetY by remember(wallpaper?.key) { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val hazeState = LocalAeroHazeState.current
    val crop by remember(width, height, viewportW, viewportH, scale, offsetX, offsetY) {
        derivedStateOf {
            if (viewportW <= 0f || viewportH <= 0f) NormalizedCrop(0f, 0f, 1f, 1f)
            else CropMath.visibleRect(width, height, viewportW, viewportH, scale, offsetX, offsetY)
        }
    }
    val lightSurfaces = !Aero.colors.isDark
    val edgeScrim = if (lightSurfaces) Color.White else Color.Black
    val isPreparingOriginal = localUri == null && canSetWallpaper && wallpaper?.downloadAllowed == true &&
        download?.status != DownloadStatus.FAILED.name && download?.status != DownloadStatus.CANCELED.name
    val resetCrop = {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (imageUri != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                LaunchedEffect(viewportWidthPx, viewportHeightPx) {
                    viewportW = viewportWidthPx
                    viewportH = viewportHeightPx
                }
                val baseScale = max(viewportWidthPx / width, viewportHeightPx / height)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUri)
                        .memoryCacheKey(if (localUri != null) "crop:$localUri" else "crop-preview:${wallpaper?.key}")
                        .diskCacheKey(if (localUri != null) localUri else imageUri)
                        .crossfade(170)
                        .build(),
                    contentDescription = "Full-screen crop preview of ${wallpaper?.source?.displayName ?: "saved"} wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .then(hazeState?.let { Modifier.hazeSource(it) } ?: Modifier)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                        .pointerInput(imageUri, width, height, viewportWidthPx, viewportHeightPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                val (x, y) = CropMath.clampOffset(
                                    width,
                                    height,
                                    viewportWidthPx,
                                    viewportHeightPx,
                                    baseScale * nextScale,
                                    offsetX + pan.x,
                                    offsetY + pan.y,
                                )
                                scale = nextScale
                                offsetX = x
                                offsetY = y
                            }
                        },
                )
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Aero.colors.accentDeep, Color.Black, Aero.colors.emerald.copy(alpha = 0.55f))),
                ),
            )
        }

        // Edge fades preserve status/navigation affordances while leaving the image genuinely full-bleed.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(168.dp)
                .background(Brush.verticalGradient(listOf(edgeScrim.copy(alpha = 0.82f), edgeScrim.copy(alpha = 0f)))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(292.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, edgeScrim.copy(alpha = 0.76f)))),
        )

        GlassPanel(
            modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBack, description = "Back", icon = Icons.Rounded.ArrowBack)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Preview & crop", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                    Text(
                        "${target.label} · drag to pan, pinch to zoom",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aero.colors.textSecondary,
                    )
                }
                if (isPreparingOriginal) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Aero.colors.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        GlassPanel(
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = 12.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zoom", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                        Text(
                            "${width} × $height px${if (localUri == null) " · original needed to set" else " · saved original"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Aero.colors.textSecondary,
                        )
                    }
                    Text("${java.lang.String.format(locale, "%.1f", scale)}×", style = MaterialTheme.typography.labelMedium, color = Aero.colors.accent)
                    TextButton(onClick = resetCrop) { Text("Reset", color = Aero.colors.textSecondary) }
                }
                Slider(
                    value = scale,
                    onValueChange = { value ->
                        val base = max(viewportW / width, viewportH / height)
                        val (x, y) = CropMath.clampOffset(width, height, viewportW, viewportH, base * value, offsetX, offsetY)
                        scale = value
                        offsetX = x
                        offsetY = y
                    },
                    valueRange = 1f..4f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Aero.colors.accent,
                        activeTrackColor = Aero.colors.accent,
                    ),
                )
                when {
                    wallpaper?.setWallpaperAllowed == false -> Text(
                        "This licence does not allow an adapted crop. Wallpaper setting is disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aero.colors.warning,
                    )
                    wallpaper?.downloadAllowed == false -> Text(
                        "Auroro cannot download this original under its reported licence or file type.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aero.colors.warning,
                    )
                    localUri != null -> Unit
                    download?.status == DownloadStatus.FAILED.name || download?.status == DownloadStatus.CANCELED.name -> {
                        Text(
                            if (download.status == DownloadStatus.CANCELED.name) "Original download canceled" else "Couldn't prepare the original image",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (download.status == DownloadStatus.CANCELED.name) Aero.colors.textPrimary else Aero.colors.error,
                        )
                        Text(
                            if (download.status == DownloadStatus.CANCELED.name) "Retry when you're ready to continue."
                            else download.errorMessage ?: "Download failed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aero.colors.textSecondary,
                        )
                        TextButton(onClick = { onRetry(download.wallpaperKey) }) { Text("Retry download", color = Aero.colors.accent) }
                    }
                    else -> {
                        Text(
                            when (download?.status) {
                                DownloadStatus.RUNNING.name -> "Saving the full-resolution original…"
                                DownloadStatus.QUEUED.name -> "Original queued · preparing the preview…"
                                else -> "Preparing the original image…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Aero.colors.textSecondary,
                        )
                        download?.takeIf { it.status == DownloadStatus.RUNNING.name && it.totalBytes > 0 }?.let { progress ->
                            Text(
                                "${(progress.bytesDownloaded * 100 / progress.totalBytes).coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Aero.colors.accent,
                            )
                        }
                    }
                }
                Button(
                    onClick = { localUri?.let { onApply(it, target, crop) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSetWallpaper && localUri != null,
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            !canSetWallpaper -> "Setting unavailable"
                            wallpaper?.downloadAllowed == false && localUri == null -> "Original unavailable"
                            localUri != null -> "Set ${target.label}"
                            else -> "Preparing original…"
                        },
                    )
                }
            }
        }
    }
}
