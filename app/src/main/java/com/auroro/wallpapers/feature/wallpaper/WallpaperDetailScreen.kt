package com.auroro.wallpapers.feature.wallpaper

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.app.DetailUiState
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.SourceGlyph
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.Format
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.feature.home.WallpaperTile
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun WallpaperDetailScreen(
    state: DetailUiState?,
    favorite: Boolean,
    download: DownloadEntity?,
    localPreviewUri: String?,
    relatedLoading: Boolean,
    canSetWallpaper: Boolean,
    setUnavailableReason: String?,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: (Wallpaper) -> Unit,
    onApply: (ApplyTarget) -> Unit,
    onShare: (Wallpaper) -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenRelated: (Wallpaper) -> Unit,
    onAddToCollection: (Wallpaper) -> Unit,
    onTag: (String) -> Unit,
) {
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Aero.colors.accent)
                Text("Loading wallpaper details…", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary)
                TextButton(onClick = onBack) { Text("Back to results", color = Aero.colors.accent) }
            }
        }
        return
    }

    val wallpaper = state.wallpaper
    val context = LocalContext.current
    val heroHazeState = rememberHazeState()
    var showInfo by remember(wallpaper.key) { mutableStateOf(false) }
    var showApply by remember(wallpaper.key) { mutableStateOf(false) }
    var previewLoaded by remember(wallpaper.key, localPreviewUri, wallpaper.previewUrl) { mutableStateOf(false) }
    var previewFailed by remember(wallpaper.key, localPreviewUri, wallpaper.previewUrl) { mutableStateOf(false) }
    val isSaved = download?.status == DownloadStatus.COMPLETED.name && localPreviewUri != null
    val isDownloading = download?.status == DownloadStatus.RUNNING.name || download?.status == DownloadStatus.QUEUED.name
    val hasOriginalForApply = wallpaper.downloadAllowed || localPreviewUri != null
    val canApplyThisWallpaper = canSetWallpaper && wallpaper.setWallpaperAllowed && hasOriginalForApply
    val heroHeight = when {
        wallpaper.aspectRatio < 0.82f -> 410.dp
        wallpaper.aspectRatio > 1.55f -> 285.dp
        else -> 340.dp
    }
    val attributionLine = when (wallpaper.source) {
        WallpaperSource.OPENVERSE -> wallpaper.attribution?.takeIf(String::isNotBlank)
            ?: wallpaper.creatorName?.let { "Creator · $it" }
        WallpaperSource.WALLHAVEN -> wallpaper.creatorName?.let { "Uploader · $it" }
        WallpaperSource.ARCHIVED -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "hero-${wallpaper.key}") {
            Box(
                Modifier.fillMaxWidth().height(heroHeight)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (Aero.colors.isDark) Color(0xFF050C11) else Color(0xFFE7EFF0)),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(localPreviewUri ?: wallpaper.previewUrl)
                        .memoryCacheKey(if (localPreviewUri != null) "local:$localPreviewUri" else wallpaper.previewUrl)
                        .diskCacheKey(if (localPreviewUri != null) localPreviewUri else wallpaper.previewUrl)
                        .crossfade(180)
                        .build(),
                    contentDescription = if (wallpaper.hasKnownDimensions) {
                        "Full wallpaper preview, ${wallpaper.width} by ${wallpaper.height}"
                    } else {
                        "Full wallpaper preview"
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().hazeSource(heroHazeState),
                    onSuccess = { previewLoaded = true },
                    onError = { previewFailed = true },
                )
                if (!previewLoaded && !previewFailed) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(24.dp),
                        color = Aero.colors.accent,
                        strokeWidth = 2.dp,
                    )
                }
                if (previewFailed) {
                    Column(
                        Modifier.align(Alignment.Center).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = Aero.colors.accent, modifier = Modifier.size(28.dp))
                        Text("Preview unavailable", style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                        TextButton(onClick = { onOpenSource(wallpaper.pageUrl) }) {
                            Text("Open source page", color = Aero.colors.accent)
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(86.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent)),
                    ),
                )
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    GlassIconButton(
                        onClick = onBack,
                        description = "Back to wallpapers",
                        icon = Icons.Rounded.ArrowBack,
                        hazeState = heroHazeState,
                    )
                }
            }
        }

        item(key = "details-${wallpaper.key}") {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 4.dp) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            (wallpaper.title ?: wallpaper.category)?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SourceGlyph(wallpaper.source, small = true)
                                Text(wallpaper.source.displayName, style = MaterialTheme.typography.labelMedium, color = Aero.colors.textSecondary)
                                Text("·", color = Aero.colors.textTertiary)
                                Text(
                                    "${Format.dimensions(wallpaper.width, wallpaper.height)} · ${AspectMath.describe(wallpaper.width, wallpaper.height)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Aero.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ActionPill(
                            if (favorite) "Favorited" else "Favorite",
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            favorite,
                            onFavorite,
                            if (favorite) Color(0xFFFF8FA0) else Aero.colors.accent,
                        )
                        ActionPill(
                            when {
                                isSaved -> "Saved"
                                isDownloading -> "Downloading"
                                else -> "Download original"
                            },
                            if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                            isSaved,
                            { onDownload(wallpaper) },
                            if (wallpaper.downloadAllowed) Aero.colors.accent else Aero.colors.textTertiary,
                            enabled = wallpaper.downloadAllowed && !isSaved && !isDownloading,
                        )
                        ActionPill(
                            "Set wallpaper",
                            Icons.Rounded.Wallpaper,
                            false,
                            { showApply = true },
                            if (canApplyThisWallpaper) Aero.colors.accent else Aero.colors.textTertiary,
                            enabled = canApplyThisWallpaper,
                        )
                        ActionPill("Collection", Icons.Rounded.BookmarkAdd, false, { onAddToCollection(wallpaper) }, Aero.colors.accent)
                        ActionPill("Share", Icons.Rounded.Share, false, { onShare(wallpaper) }, Aero.colors.accent)
                    }

                    if (isDownloading) {
                        val determinate = (download?.totalBytes ?: 0L) > 0L
                        val progress = if (determinate) {
                            ((download?.bytesDownloaded ?: 0L).toFloat() / (download?.totalBytes ?: 1L)).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Aero.colors.accent,
                            trackColor = Aero.colors.accent.copy(alpha = 0.16f),
                        )
                        Text(
                            if (determinate) "Saving original · ${(progress * 100).toInt()}%" else "Saving original…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aero.colors.textSecondary,
                        )
                    }
                    if (download?.status == DownloadStatus.FAILED.name) {
                        Text(download.errorMessage ?: "The original could not be downloaded.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                    }
                    if (!wallpaper.downloadAllowed) {
                        val explanation = if (wallpaper.source == WallpaperSource.ARCHIVED) {
                            "This saved record is from a source Auroro no longer integrates with."
                        } else {
                            "A direct original download isn't enabled for this reported licence or file type. Review the source terms."
                        }
                        Text(explanation, style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                    }
                    if (wallpaper.downloadAllowed && !wallpaper.setWallpaperAllowed) {
                        Text("This licence does not allow adapting the image for wallpaper setting.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                    }
                    if (wallpaper.setWallpaperAllowed && !canSetWallpaper && setUnavailableReason != null) {
                        Text(setUnavailableReason, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    }
                    if (wallpaper.setWallpaperAllowed && !hasOriginalForApply) {
                        Text("A saved original is required before this wallpaper can be set.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    }

                    attributionLine?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (wallpaper.source == WallpaperSource.OPENVERSE) {
                        wallpaper.licenseCode?.let { code ->
                            Text(
                                "Licence · $code${wallpaper.licenseVersion?.let { " $it" }.orEmpty()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Aero.colors.textSecondary,
                            )
                        }
                        Text("Made using Openverse; not endorsed or certified by Openverse.", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Image details", style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showInfo = !showInfo }) {
                            Text(if (showInfo) "Hide" else "More", color = Aero.colors.accent)
                        }
                    }
                    AnimatedVisibility(showInfo) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoCell("Original dimensions", Format.dimensions(wallpaper.width, wallpaper.height), Modifier.weight(1f))
                                InfoCell("Aspect ratio", AspectMath.describe(wallpaper.width, wallpaper.height), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoCell("File type", Format.mimeLabel(wallpaper.mimeType), Modifier.weight(1f))
                                InfoCell("File size", Format.fileSize(wallpaper.fileSizeBytes), Modifier.weight(1f))
                            }
                            if (wallpaper.creatorName != null) {
                                Text(
                                    when (wallpaper.source) {
                                        WallpaperSource.WALLHAVEN -> "Uploader · ${wallpaper.creatorName}"
                                        WallpaperSource.OPENVERSE -> "Creator · ${wallpaper.creatorName}"
                                        WallpaperSource.ARCHIVED -> "Creator · ${wallpaper.creatorName}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Aero.colors.textSecondary,
                                )
                            }
                            wallpaper.creatorUrl?.takeIf(UrlPolicy::isAllowedForBrowsing)?.let { creatorUrl ->
                                TextButton(onClick = { onOpenSource(creatorUrl) }) {
                                    Text("View creator page", color = Aero.colors.accent)
                                    Spacer(Modifier.width(5.dp))
                                    Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
                                }
                            }
                            if (wallpaper.source == WallpaperSource.OPENVERSE) {
                                wallpaper.attribution?.let { attribution ->
                                    Text(attribution, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                                }
                                wallpaper.licenseCode?.let { code ->
                                    Text(
                                        "Licence · $code${wallpaper.licenseVersion?.let { " $it" }.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Aero.colors.textSecondary,
                                    )
                                }
                                wallpaper.licenseUrl?.takeIf(UrlPolicy::isAllowedForBrowsing)?.let { licenseUrl ->
                                    TextButton(onClick = { onOpenSource(licenseUrl) }) {
                                        Text("Read licence terms", color = Aero.colors.accent)
                                        Spacer(Modifier.width(5.dp))
                                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
                                    }
                                }
                                wallpaper.providerName?.let { Text("Indexed provider · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
                                wallpaper.catalogSource?.takeIf { it != wallpaper.providerName }?.let {
                                    Text("Catalogue source · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                                }
                                Text(
                                    "Openverse indexes third-party works; confirm the licence on the original page before reuse.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Aero.colors.textTertiary,
                                )
                            } else if (wallpaper.source == WallpaperSource.WALLHAVEN) {
                                Text(
                                    "Wallhaven reports uploader details, not necessarily the original creator or image rights. Check the source page before reuse.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Aero.colors.textTertiary,
                                )
                            }
                            wallpaper.createdAt?.let { Text("Added · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
                            wallpaper.colors.takeIf { it.isNotEmpty() }?.let {
                                Text("Reported colours · ${it.joinToString("  ")}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                            }
                            if (wallpaper.tags.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    wallpaper.tags.take(12).forEach { tag ->
                                        GlassPill(tag.name, false, selectionRole = null, onClick = { onTag(tag.name) })
                                    }
                                }
                            }
                            wallpaper.originSourceUrl?.takeIf(UrlPolicy::isAllowedForBrowsing)?.let { origin ->
                                SourceLink("Open source-provided link", onClick = { onOpenSource(origin) })
                            }
                            SourceLink("View source page", onClick = { onOpenSource(wallpaper.pageUrl) })
                        }
                    }
                }
            }
        }

        if (state.error != null) {
            item(key = "detail-error-${wallpaper.key}") {
                Text(state.error, modifier = Modifier.padding(horizontal = 5.dp), style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
            }
        }
        if (state.related.isNotEmpty() || relatedLoading) {
            item(key = "related-${wallpaper.key}") {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 3.dp) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Related wallpapers", style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                            Text("Only recommendations returned by this source are shown.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                        }
                        if (relatedLoading) CircularProgressIndicator(Modifier.padding(8.dp).size(18.dp), strokeWidth = 2.dp, color = Aero.colors.accent)
                        if (state.related.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                state.related.take(6).forEach { item ->
                                    WallpaperTile(item, onClick = { onOpenRelated(item) }, modifier = Modifier.width(126.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApply) {
        ApplyTargetDialog(
            enabled = canSetWallpaper,
            unavailableReason = setUnavailableReason,
            onDismiss = { showApply = false },
            onSelect = { target -> showApply = false; onApply(target) },
        )
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) tint.copy(alpha = 0.16f) else Aero.colors.glassTint.copy(alpha = if (Aero.colors.isDark) 0.64f else 0.72f),
        border = BorderStroke(0.8.dp, tint.copy(alpha = if (enabled) 0.34f else 0.14f)),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 8.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = if (enabled) tint else Aero.colors.textTertiary, modifier = Modifier.size(17.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) Aero.colors.textPrimary else Aero.colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InfoCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Aero.colors.glassTint.copy(alpha = if (Aero.colors.isDark) 0.64f else 0.72f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = Aero.colors.accent)
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
    }
}

@Composable
fun ApplyTargetDialog(enabled: Boolean, unavailableReason: String?, onDismiss: () -> Unit, onSelect: (ApplyTarget) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Wallpaper, null, tint = Aero.colors.accent) },
        title = { Text("Set wallpaper", color = Aero.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Auroro uses the saved original, downloading it if needed, then applies your chosen crop through Android's wallpaper service.", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary)
                if (!enabled) Text(unavailableReason ?: "Changing wallpaper is unavailable on this device.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                ApplyTarget.entries.forEach { target ->
                    TextButton(enabled = enabled, onClick = { onSelect(target) }) { Text(target.label, color = Aero.colors.accent) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Aero.colors.textSecondary) } },
        containerColor = Aero.colors.surfaceSolid,
        iconContentColor = Aero.colors.accent,
        titleContentColor = Aero.colors.textPrimary,
    )
}

fun shareWallpaper(context: android.content.Context, w: Wallpaper) {
    val text = buildString {
        val attribution = w.attribution?.takeIf(String::isNotBlank)
        if (attribution != null) {
            append(attribution)
        } else {
            when (w.source) {
                WallpaperSource.OPENVERSE -> append(w.creatorName?.let { "Creator: $it · Openverse" } ?: "Openverse image")
                WallpaperSource.WALLHAVEN -> append(w.creatorName?.let { "Uploaded by $it · Wallhaven" } ?: "Wallhaven wallpaper")
                WallpaperSource.ARCHIVED -> append("Saved wallpaper")
            }
        }
        append("\nSource: ").append(w.pageUrl)
        w.licenseCode?.let { code ->
            append("\nLicence: ").append(code)
            w.licenseVersion?.let { append(' ').append(it) }
            w.licenseUrl?.takeIf(UrlPolicy::isAllowedForBrowsing)?.let { append("\nLicence terms: ").append(it) }
        }
        if (w.source == WallpaperSource.OPENVERSE) append("\nMade using Openverse; not endorsed or certified by Openverse.")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Wallpaper from ${w.source.displayName}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share wallpaper"))
}
