package com.auroro.wallpapers.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.OfflineRow
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Format
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.collections.ConfirmDialog
import com.auroro.wallpapers.feature.home.OfflineTile

@Composable
fun OfflineScreen(
    rows: List<OfflineRow>,
    history: List<Wallpaper>,
    favoriteKeys: Set<String>,
    storageInfo: StorageInfo?,
    onRequestStorageInfo: ((StorageInfo) -> Unit) -> Unit,
    onMenu: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (OfflineRow) -> Unit,
    onClearHistory: () -> Unit,
    onFavorite: (Wallpaper) -> Unit,
    onApply: (Wallpaper) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(OfflineTab.SAVED) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var localStorage by remember { mutableStateOf(storageInfo) }
    LaunchedEffect(storageInfo) { localStorage = storageInfo }
    LaunchedEffect(rows.size, selectedTab) { if (selectedTab == OfflineTab.SAVED) onRequestStorageInfo { localStorage = it } }

    when (selectedTab) {
        OfflineTab.SAVED -> {
            val active = rows.filter { it.download.status == DownloadStatus.RUNNING.name || it.download.status == DownloadStatus.QUEUED.name }
            val failed = rows.filter { it.download.status == DownloadStatus.FAILED.name || it.download.status == DownloadStatus.CANCELED.name }
            val completed = rows.filter { it.download.status == DownloadStatus.COMPLETED.name }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(158.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 11.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    HeaderBar("Offline", "Files saved on this device", onMenu = onMenu)
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflineTab.entries.forEach { tab -> GlassPill(tab.label, selectedTab == tab, onClick = { selectedTab = tab }) }
                    }
                }
                if (localStorage != null) item(span = StaggeredGridItemSpan.FullLine) { StorageSummary(localStorage!!) }
                if (rows.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        EmptyState(
                            "No downloads yet",
                            "Download a wallpaper to save its original file on this device. Cached previews aren't listed here.",
                            icon = Icons.Rounded.Download,
                            action = { Text("Browse wallpapers from Home or Search.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.accent) },
                        )
                    }
                }
                if (active.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionTitle("In progress", "${active.size} active download${if (active.size == 1) "" else "s"}") }
                    items(active, key = { "active-${it.download.wallpaperKey}" }, span = StaggeredGridItemSpan.FullLine) { row ->
                        DownloadRow(row, onOpen, onCancel, onRetry, onDelete)
                    }
                }
                if (failed.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionTitle("Needs attention", "Interrupted or unavailable files") }
                    items(failed, key = { "failed-${it.download.wallpaperKey}" }, span = StaggeredGridItemSpan.FullLine) { row ->
                        DownloadRow(row, onOpen, onCancel, onRetry, onDelete)
                    }
                }
                if (completed.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { SectionTitle("Saved wallpapers", "${completed.count { it.fileExists }} files available offline") }
                    items(completed, key = { it.download.wallpaperKey }) { row ->
                        val w = row.wallpaper
                        val title = w?.category ?: w?.source?.displayName ?: row.download.fileName ?: "Saved wallpaper"
                        val caption = if (row.fileExists) "${Format.fileSize(row.download.savedSizeBytes)} · ${row.download.savedWidth ?: "?"} × ${row.download.savedHeight ?: "?"}" else "File missing"
                        Box {
                            OfflineTile(
                                wallpaper = w,
                                localUri = row.download.localUri.takeIf { row.fileExists },
                                title = title,
                                subtitle = caption,
                                favorite = w?.key?.let { it in favoriteKeys } ?: false,
                                onClick = { if (w != null) onOpen(w) },
                                onFavorite = if (w != null) ({ onFavorite(w) }) else null,
                            )
                            if (row.fileExists && w != null && w.setWallpaperAllowed) {
                                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                                    GlassIconButton(onClick = { onApply(w) }, description = "Set as wallpaper", icon = Icons.Rounded.Wallpaper, tint = Aero.colors.accent)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(5.dp)) {
                                GlassIconButton(onClick = { onDelete(row) }, description = "Delete saved file", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.error)
                            }
                        }
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(18.dp)) }
            }
        }

        OfflineTab.HISTORY -> {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HeaderBar("Offline", "Downloads and recent views", onMenu = onMenu, trailing = {
                        if (history.isNotEmpty()) GlassIconButton(onClick = { confirmClearHistory = true }, description = "Clear history", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.error)
                    })
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OfflineTab.entries.forEach { tab -> GlassPill(tab.label, selectedTab == tab, onClick = { selectedTab = tab }) } } }
                item { SectionTitle("Recently viewed", "Stored locally · most recent first") }
                if (history.isEmpty()) item { EmptyState("No history yet", "Open a wallpaper to keep its source and details in your recent history.", icon = Icons.Rounded.History) }
                columnItems(history, key = { it.key }) { w ->
                    HistoryRow(w, favorite = w.key in favoriteKeys, onOpen = { onOpen(w) }, onFavorite = { onFavorite(w) })
                }
            }
        }
    }

    if (confirmClearHistory) ConfirmDialog("Clear viewing history?", "This removes recent-view metadata. Favorites, collections and downloaded files are not changed.", { confirmClearHistory = false }, { confirmClearHistory = false; onClearHistory() })
}

private enum class OfflineTab(val label: String) { SAVED("Downloads"), HISTORY("History") }

@Composable
private fun StorageSummary(info: StorageInfo) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auroro files", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textTertiary)
                Text("${info.savedCount} · ${Format.fileSize(info.savedBytes)}", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Device free", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                Text(Format.fileSize(info.deviceFreeBytes), style = MaterialTheme.typography.titleSmall, color = Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DownloadRow(row: OfflineRow, onOpen: (Wallpaper) -> Unit, onCancel: (String) -> Unit, onRetry: (String) -> Unit, onDelete: (OfflineRow) -> Unit) {
    val d = row.download
    val w = row.wallpaper
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), elevation = 5.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (w != null) OfflineTile(w, d.localUri.takeIf { row.fileExists }, w.category ?: w.source.displayName, Format.dimensions(w.width, w.height), false, { onOpen(w) }, modifier = Modifier.size(76.dp), respectAspectRatio = false)
            else Box(Modifier.size(68.dp).background(Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = .22f), Aero.colors.emerald.copy(alpha = .12f))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Download, null, tint = Aero.colors.accent) }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(w?.source?.displayName ?: d.fileName ?: "Wallpaper", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textPrimary)
                when (d.status) {
                    DownloadStatus.QUEUED.name -> Text("Queued…", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    DownloadStatus.RUNNING.name -> {
                        val progress = if (d.totalBytes > 0) (d.bytesDownloaded.toFloat() / d.totalBytes).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Aero.colors.accent, trackColor = Aero.colors.accent.copy(alpha = .15f))
                        Text(if (d.totalBytes > 0) "${Format.fileSize(d.bytesDownloaded)} / ${Format.fileSize(d.totalBytes)}" else "${Format.fileSize(d.bytesDownloaded)} downloaded", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                    DownloadStatus.FAILED.name -> Text(d.errorMessage ?: "Download failed", style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                    DownloadStatus.CANCELED.name -> Text("Canceled", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    else -> Text("${Format.fileSize(d.savedSizeBytes)} · ${d.savedWidth ?: "?"} × ${d.savedHeight ?: "?"}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
                if (d.status == DownloadStatus.COMPLETED.name && !row.fileExists) Text("The file was moved or deleted.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
            }
            when {
                d.status == DownloadStatus.RUNNING.name || d.status == DownloadStatus.QUEUED.name ->
                    GlassIconButton(onClick = { onCancel(d.wallpaperKey) }, description = "Cancel download", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.textSecondary)
                d.status == DownloadStatus.FAILED.name || d.status == DownloadStatus.CANCELED.name ||
                    d.status == DownloadStatus.COMPLETED.name && !row.fileExists && w?.downloadAllowed == true ->
                    GlassIconButton(onClick = { onRetry(d.wallpaperKey) }, description = "Retry download", icon = Icons.Rounded.Refresh, tint = Aero.colors.accent)
                else -> GlassIconButton(onClick = { onDelete(row) }, description = "Delete downloaded file", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.error)
            }
        }
    }
}

@Composable
private fun HistoryRow(w: Wallpaper, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    com.auroro.wallpapers.core.design.GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OfflineTile(w, null, w.category ?: w.source.displayName, Format.dimensions(w.width, w.height), favorite, onOpen, modifier = Modifier.size(76.dp), respectAspectRatio = false)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(w.source.displayName, style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                Text("${w.width} × ${w.height} · ${Format.fileSize(w.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            }
            GlassIconButton(onClick = onFavorite, description = if (favorite) "Remove from favorites" else "Add to favorites", icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tint = if (favorite) Color(0xFFFF8FA0) else Aero.colors.textTertiary)
        }
    }
}
