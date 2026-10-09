package com.auroro.wallpapers.feature.wallpaper

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.auroro.wallpapers.core.data.download.WallpaperCapabilities
import com.auroro.wallpapers.feature.home.WallpaperTile
import com.auroro.wallpapers.core.network.UrlPolicy

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Aero.colors.accent) }
        return
    }
    val wallpaper = state.wallpaper
    val context = LocalContext.current
    var showInfo by remember(wallpaper.key) { mutableStateOf(false) }
    var showApply by remember(wallpaper.key) { mutableStateOf(false) }
    var previewFailed by remember(wallpaper.key, localPreviewUri) { mutableStateOf(false) }
    val isSaved = download?.status == DownloadStatus.COMPLETED.name && localPreviewUri != null
    val hasOriginalForApply = wallpaper.downloadAllowed || localPreviewUri != null
    val canApplyThisWallpaper = canSetWallpaper && wallpaper.setWallpaperAllowed && hasOriginalForApply

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF020912))) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(localPreviewUri ?: wallpaper.previewUrl)
                    .memoryCacheKey(if (localPreviewUri != null) "local:$localPreviewUri" else "${wallpaper.key}:detail")
                    .diskCacheKey(if (localPreviewUri != null) localPreviewUri else wallpaper.previewUrl)
                    .crossfade(220)
                    .build(),
                contentDescription = "Wallpaper preview from ${wallpaper.source.displayName}, ${wallpaper.width} by ${wallpaper.height}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onError = { previewFailed = true },
            )
            if (previewFailed) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = Aero.colors.accent, modifier = Modifier.size(28.dp))
                    Text("Preview unavailable", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    TextButton(onClick = { onOpenSource(wallpaper.pageUrl) }) { Text("Open the source page", color = Aero.colors.accentLight) }
                }
            }
            Box(Modifier.fillMaxWidth().height(136.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .62f), Color.Transparent))))
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBack, description = "Close wallpaper preview", icon = Icons.Rounded.ArrowBack, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(wallpaper.source.displayName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("${wallpaper.width} × ${wallpaper.height} · ${AspectMath.describe(wallpaper.width, wallpaper.height)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .78f))
                }
                GlassIconButton(onClick = { onShare(wallpaper) }, description = "Share wallpaper source", icon = Icons.Rounded.Share, tint = Color.White)
                Spacer(Modifier.width(7.dp))
                GlassIconButton(onClick = onFavorite, description = if (favorite) "Remove from favorites" else "Add to favorites", icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tint = if (favorite) Color(0xFFFF93A4) else Color.White, selected = favorite)
            }

            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 17.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceGlyph(wallpaper.source)
                    Spacer(Modifier.width(8.dp))
                    Text(wallpaper.category ?: wallpaper.tags.firstOrNull()?.name ?: "Wallpaper", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                val contributorLabel = when (wallpaper.source) {
                    WallpaperSource.OPENVERSE -> wallpaper.creatorName?.let { "Creator · $it" }
                    WallpaperSource.WALLHAVEN -> wallpaper.creatorName?.let { "Uploader · $it" }
                    WallpaperSource.ARCHIVED -> null
                }
                contributorLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .82f)) }
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp).animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            elevation = 12.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionPill("Favorite", Icons.Rounded.Favorite, favorite, onFavorite, if (favorite) Color(0xFFFF93A4) else Aero.colors.accent)
                    ActionPill(
                        if (isSaved) "Saved" else "Download",
                        if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                        isSaved,
                        { onDownload(wallpaper) },
                        if (wallpaper.downloadAllowed) Aero.colors.accent else Aero.colors.textTertiary,
                        enabled = wallpaper.downloadAllowed,
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
                if (download?.status == DownloadStatus.RUNNING.name || download?.status == DownloadStatus.QUEUED.name) {
                    val determinate = download.totalBytes > 0
                    val progress = if (determinate) (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) else 0f
                    androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Aero.colors.accent, trackColor = Aero.colors.accent.copy(alpha = .17f))
                    Text(if (determinate) "Downloading original · ${(progress * 100).toInt()}%" else "Downloading original…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
                if (download?.status == DownloadStatus.FAILED.name) {
                    Text(download.errorMessage ?: "Download failed.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                }
                if (!wallpaper.downloadAllowed) {
                    val message = if (wallpaper.source == WallpaperSource.ARCHIVED) {
                        "This saved item is from a source no longer integrated; Auroro won't fetch it again."
                    } else {
                        "Direct download is unavailable because this licence or file format is not verified here. Review the source page."
                    }
                    Text(message, style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                }
                when (wallpaper.source) {
                    WallpaperSource.OPENVERSE -> {
                        val credit = wallpaper.attribution?.takeIf(String::isNotBlank) ?: buildString {
                            wallpaper.creatorName?.let { append("Creator: ").append(it) }
                            wallpaper.licenseCode?.let { code ->
                                if (isNotEmpty()) append(" · ")
                                append("Licence: ").append(code)
                                wallpaper.licenseVersion?.let { append(' ').append(it) }
                            }
                        }
                        if (credit.isNotBlank()) Text(credit, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("Made using Openverse; not endorsed or certified by Openverse.", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                    }
                    WallpaperSource.WALLHAVEN -> Text(
                        "Uploader names aren't necessarily creators. Image rights remain with the original owner; review the source page before reuse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aero.colors.textTertiary,
                    )
                    WallpaperSource.ARCHIVED -> Unit
                }
                if (wallpaper.downloadAllowed && !wallpaper.setWallpaperAllowed) {
                    Text("This licence does not allow adaptations; Auroro won't crop or set this image as wallpaper.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                }
                if (wallpaper.setWallpaperAllowed && !canSetWallpaper && setUnavailableReason != null) {
                    Text(setUnavailableReason, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                }
                if (wallpaper.setWallpaperAllowed && !hasOriginalForApply) {
                    Text("A saved original is required to set this image; a new download isn't available.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showInfo) "Less about this wallpaper" else "About this wallpaper", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showInfo = !showInfo }) { Text(if (showInfo) "Close" else "Details", color = Aero.colors.accent) }
                    Icon(Icons.Rounded.Info, null, tint = Aero.colors.accent, modifier = Modifier.size(17.dp))
                }
                AnimatedVisibility(showInfo) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCell("Original size", Format.dimensions(wallpaper.width, wallpaper.height), Modifier.weight(1f))
                            InfoCell("Aspect ratio", AspectMath.describe(wallpaper.width, wallpaper.height), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCell("File type", Format.mimeLabel(wallpaper.mimeType), Modifier.weight(1f))
                            InfoCell("File size", Format.fileSize(wallpaper.fileSizeBytes), Modifier.weight(1f))
                        }
                        if (wallpaper.creatorName != null) Text("Creator · ${wallpaper.creatorName}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                        wallpaper.creatorUrl?.takeIf { UrlPolicy.isAllowedForBrowsing(it) }?.let { creatorUrl ->
                            TextButton(onClick = { onOpenSource(creatorUrl) }) {
                                Text("View creator profile", color = Aero.colors.accent)
                                Spacer(Modifier.width(5.dp)); Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
                            }
                        }
                        if (wallpaper.source == WallpaperSource.OPENVERSE) {
                            wallpaper.attribution?.let { attribution ->
                                Text(attribution, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                            }
                            wallpaper.licenseCode?.let { code ->
                                val licence = buildString {
                                    append(code)
                                    wallpaper.licenseVersion?.let { append(" ").append(it) }
                                }
                                Text("Licence · $licence", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                            }
                            wallpaper.licenseUrl?.takeIf(UrlPolicy::isAllowedForBrowsing)?.let { licenceUrl ->
                                TextButton(onClick = { onOpenSource(licenceUrl) }) {
                                    Text("Read licence terms", color = Aero.colors.accent)
                                    Spacer(Modifier.width(5.dp)); Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
                                }
                            }
                            wallpaper.providerName?.let { Text("Indexed provider · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
                            wallpaper.catalogSource?.takeIf { it != wallpaper.providerName }?.let {
                                Text("Catalogue source · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                            }
                            Text("Openverse indexes third-party works; metadata and licence claims may need confirmation on the source page.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                        }
                        wallpaper.createdAt?.let { Text("Added · $it", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
                        wallpaper.colors.takeIf { it.isNotEmpty() }?.let { Text("Colors · ${it.joinToString("  ")}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary) }
                        if (wallpaper.tags.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                wallpaper.tags.take(12).forEach { tag -> GlassPill(tag.name, false, onClick = { onTag(tag.name) }) }
                            }
                        }
                        if (wallpaper.originSourceUrl != null && UrlPolicy.isAllowedForBrowsing(wallpaper.originSourceUrl)) {
                            TextButton(onClick = { onOpenSource(wallpaper.originSourceUrl) }) { Text("Open source-provided link", color = Aero.colors.accent); Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent) }
                        }
                        TextButton(onClick = { onOpenSource(wallpaper.pageUrl) }) {
                            Text("View source page", color = Aero.colors.accent)
                            Spacer(Modifier.width(5.dp)); Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Aero.colors.accent)
                        }
                    }
                }
            }
        }

        if (state.related.isNotEmpty() || relatedLoading) {
            GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(20.dp), elevation = 4.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text("Related wallpapers", style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                    Text("Only recommendations provided by the source are shown.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    if (relatedLoading) CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp, color = Aero.colors.accent)
                    if (state.related.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            state.related.take(6).forEach { item ->
                                WallpaperTile(item, false, { onOpenRelated(item) }, {}, Modifier.width(126.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
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
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, tint: Color, enabled: Boolean = true) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) tint.copy(alpha = .19f) else Aero.colors.glassTint.copy(alpha = .46f),
        border = androidx.compose.foundation.BorderStroke(.8.dp, tint.copy(alpha = if (enabled) .35f else .14f)),
        modifier = Modifier.height(42.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (enabled) tint else Aero.colors.textTertiary, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) Aero.colors.textPrimary else Aero.colors.textTertiary)
        }
    }
}

@Composable
private fun InfoCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Aero.colors.glassTint.copy(alpha = .38f)).padding(9.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("Auroro uses the saved original, downloading it if needed, then applies this crop with Android's wallpaper service.", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary)
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
