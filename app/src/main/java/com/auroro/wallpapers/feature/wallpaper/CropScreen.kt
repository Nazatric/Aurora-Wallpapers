package com.auroro.wallpapers.feature.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.CropMath
import com.auroro.wallpapers.core.data.download.DownloadStatus
import com.auroro.wallpapers.core.data.download.LocalFiles
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.model.Wallpaper
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
    var fileExists by remember(download?.localUri) { mutableStateOf(false) }
    LaunchedEffect(download?.localUri, download?.status) {
        fileExists = withContext(Dispatchers.IO) {
            download?.status == DownloadStatus.COMPLETED.name && LocalFiles.exists(context, download.localUri)
        }
    }
    LaunchedEffect(wallpaper?.key, fileExists, download?.status) {
        if (!fileExists && wallpaper != null && download?.status != DownloadStatus.RUNNING.name && download?.status != DownloadStatus.QUEUED.name) {
            onEnsureDownload(wallpaper)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onBack, description = "Back", icon = Icons.Rounded.ArrowBack)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Preview & crop", style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary)
                Text("${target.label} · drag to pan, pinch to zoom", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            }
            GlassIconButton(onClick = { }, description = "Crop preview", icon = Icons.Rounded.CenterFocusStrong, tint = Aero.colors.accent)
        }

        val localUri = download?.localUri?.takeIf { fileExists }
        if (localUri == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when (download?.status) {
                    DownloadStatus.FAILED.name -> {
                        Text("Couldn't prepare the original image", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                        Text(download.errorMessage ?: "Download failed.", Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                        TextButton(onClick = { wallpaper?.let { onRetry(it.key) } }) { Text("Retry download", color = Aero.colors.accent) }
                    }
                    else -> {
                        CircularProgressIndicator(color = Aero.colors.accent)
                        Text("Preparing the original image…", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary)
                        download?.takeIf { it.totalBytes > 0 }?.let { progress ->
                            Text("${(progress.bytesDownloaded * 100 / progress.totalBytes).coerceIn(0, 100)}%", style = MaterialTheme.typography.labelMedium, color = Aero.colors.accent)
                        }
                    }
                }
            }
        } else {
            var scale by remember(localUri) { mutableFloatStateOf(1f) }
            var offsetX by remember(localUri) { mutableFloatStateOf(0f) }
            var offsetY by remember(localUri) { mutableFloatStateOf(0f) }
            val width = download?.savedWidth?.takeIf { it > 0 } ?: wallpaper?.width?.coerceAtLeast(1) ?: 1
            val height = download?.savedHeight?.takeIf { it > 0 } ?: wallpaper?.height?.coerceAtLeast(1) ?: 1
            val density = LocalDensity.current
            var viewportW by remember { mutableFloatStateOf(0f) }
            var viewportH by remember { mutableFloatStateOf(0f) }
            val crop by remember(width, height, viewportW, viewportH, scale, offsetX, offsetY) {
                derivedStateOf {
                    if (viewportW <= 0 || viewportH <= 0) NormalizedCrop(0f, 0f, 1f, 1f)
                    else CropMath.visibleRect(width, height, viewportW, viewportH, scale, offsetX, offsetY)
                }
            }

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black),
            ) {
                val pxW = with(density) { maxWidth.toPx() }
                val pxH = with(density) { maxHeight.toPx() }
                LaunchedEffect(pxW, pxH) { viewportW = pxW; viewportH = pxH }
                val baseScale = max(pxW / width, pxH / height)
                AsyncImage(
                    model = ImageRequest.Builder(context).data(localUri).memoryCacheKey("crop:$localUri").crossfade(170).build(),
                    contentDescription = "Full-screen crop preview of ${wallpaper?.source?.displayName ?: "saved"} wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                        .pointerInput(width, height, pxW, pxH) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                val (x, y) = CropMath.clampOffset(width, height, pxW, pxH, baseScale * nextScale, offsetX + pan.x, offsetY + pan.y)
                                scale = nextScale
                                offsetX = x
                                offsetY = y
                            }
                        },
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Transparent, Color.Black.copy(alpha = .1f))))
                    .clip(RoundedCornerShape(24.dp)))
                Row(Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = .48f)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.RemoveRedEye, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${width} × $height", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(22.dp), elevation = 11.dp) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zoom", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text("${"%.1f".format(scale)}×", style = MaterialTheme.typography.labelMedium, color = Aero.colors.accent)
                        TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) { Text("Reset", color = Aero.colors.textSecondary) }
                    }
                    Slider(
                        value = scale,
                        onValueChange = { value ->
                            val b = max(viewportW / width, viewportH / height)
                            val (x, y) = CropMath.clampOffset(width, height, viewportW, viewportH, b * value, offsetX, offsetY)
                            scale = value; offsetX = x; offsetY = y
                        },
                        valueRange = 1f..4f,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Aero.colors.accent, activeTrackColor = Aero.colors.accent),
                    )
                    Button(
                        onClick = { onApply(localUri, target, crop) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set ${target.label}")
                    }
                }
            }
        }
    }
}
