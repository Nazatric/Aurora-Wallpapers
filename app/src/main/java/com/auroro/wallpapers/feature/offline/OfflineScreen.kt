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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.OfflineRow
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
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
    storageInfo: StorageInfo?,
    onRequestStorageInfo: ((StorageInfo) -> Unit) -> Unit,
    onMenu: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (OfflineRow) -> Unit,
    onClearHistory: () -> Unit,
    onApply: (Wallpaper) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(OfflineTab.SAVED) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var localStorage by remember { mutableStateOf(storageInfo) }
    LaunchedEffect(storageInfo) { localStorage = storageInfo }
    LaunchedEffect(rows.size, selectedTab) {
        if (selectedTab == OfflineTab.SAVED) onRequestStorageInfo { localStorage = it }
    }

    when (selectedTab) {
        OfflineTab.SAVED -> {
            val completed = rows.filter { it.download.status == DownloadStatus.COMPLETED.name }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(164.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalItemSpacing = 12.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    HeaderBar("Downloads", "Original files saved on this device", onMenu = onMenu)
                }
                item(span = StaggeredGridItemSpan.FullLine) { OfflineTabs(selectedTab) { selectedTab = it } }
                if (localStorage != null) item(span = StaggeredGridItemSpan.FullLine) { StorageSummary(localStorage!!) }
                if (completed.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        EmptyState(
                            "No downloads yet",
                            "Download a wallpaper to save its original file here. Cached previews are separate and aren't listed. Use Queue to follow active or interrupted saves.",
                            icon = Icons.Rounded.Download,
                        )
                    }
                } else {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SectionTitle("Saved wallpapers", "${completed.count { it.fileExists }} files available offline")
                    }
                    items(completed, key = { it.download.wallpaperKey }) { row ->
                        SavedWallpaperCard(row, onOpen, onDelete, onApply)
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(18.dp)) }
            }
        }

        OfflineTab.QUEUE -> {
            val active = rows.filter { it.download.status == DownloadStatus.RUNNING.name || it.download.status == DownloadStatus.QUEUED.name }
            val failed = rows.filter { it.download.status == DownloadStatus.FAILED.name || it.download.status == DownloadStatus.CANCELED.name }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HeaderBar("Download queue", "Active and interrupted original saves", onMenu = onMenu) }
                item { OfflineTabs(selectedTab) { selectedTab = it } }
                if (active.isEmpty() && failed.isEmpty()) {
                    item {
                        EmptyState(
                            "Queue is clear",
                            "Active, waiting and interrupted downloads appear here. Saved originals are in Downloads.",
                            icon = Icons.Rounded.Download,
                        )
                    }
                }
                if (active.isNotEmpty()) {
                    item { SectionTitle("In progress", "${active.size} active download${if (active.size == 1) "" else "s"}") }
                    columnItems(active, key = { "active-${it.download.wallpaperKey}" }) { row ->
                        DownloadRow(row, onOpen, onCancel, onRetry, onDelete)
                    }
                }
                if (failed.isNotEmpty()) {
                    item { SectionTitle("Needs attention", "Interrupted or unavailable files") }
                    columnItems(failed, key = { "failed-${it.download.wallpaperKey}" }) { row ->
                        DownloadRow(row, onOpen, onCancel, onRetry, onDelete)
                    }
                }
            }
        }

        OfflineTab.HISTORY -> {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HeaderBar("History", "Recent wallpaper details · stored locally", onMenu = onMenu, trailing = {
                        if (history.isNotEmpty()) {
                            GlassIconButton(onClick = { confirmClearHistory = true }, description = "Clear history", icon = Icons.Rounded.DeleteOutline)
                        }
                    })
                }
                item { OfflineTabs(selectedTab) { selectedTab = it } }
                item { SectionTitle("Recently viewed", "Most recent first") }
                if (history.isEmpty()) {
                    item { EmptyState("No history yet", "Open a wallpaper to keep its source details in your local history.", icon = Icons.Rounded.History) }
                }
                columnItems(history, key = { it.key }) { wallpaper ->
                    HistoryRow(wallpaper, onOpen = { onOpen(wallpaper) })
                }
            }
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            "Clear viewing history?",
            "This removes recent-view metadata. Favorites, collections and downloaded files are not changed.",
            { confirmClearHistory = false },
            { confirmClearHistory = false; onClearHistory() },
            confirmText = "Clear history",
        )
    }
}

private enum class OfflineTab(val label: String) { SAVED("Downloaded"), QUEUE("Queue"), HISTORY("History") }

@Composable
private fun OfflineTabs(selected: OfflineTab, onSelect: (OfflineTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OfflineTab.entries.forEach { tab -> GlassPill(tab.label, selected == tab, onClick = { onSelect(tab) }) }
    }
}

@Composable
private fun StorageSummary(info: StorageInfo) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saved files", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textTertiary)
                Text("${info.savedCount} · ${Format.fileSize(info.savedBytes)}", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Free space", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                Text(Format.fileSize(info.deviceFreeBytes), style = MaterialTheme.typography.titleSmall, color = Aero.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SavedWallpaperCard(
    row: OfflineRow,
    onOpen: (Wallpaper) -> Unit,
    onDelete: (OfflineRow) -> Unit,
    onApply: (Wallpaper) -> Unit,
) {
    val wallpaper = row.wallpaper
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OfflineTile(
            wallpaper = wallpaper,
            localUri = row.download.localUri.takeIf { row.fileExists },
            onClick = wallpaper?.let { saved -> { onOpen(saved) } },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 3.dp, end = 4.dp)) {
                Text(
                    if (row.fileExists) "Saved original" else "File missing",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.fileExists) Aero.colors.textPrimary else Aero.colors.warning,
                    maxLines = 1,
                )
                Text(
                    "${Format.fileSize(row.download.savedSizeBytes)} · ${Format.dimensions(row.download.savedWidth ?: wallpaper?.width ?: 0, row.download.savedHeight ?: wallpaper?.height ?: 0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Aero.colors.textSecondary,
                    maxLines = 1,
                )
            }
            if (row.fileExists && wallpaper?.setWallpaperAllowed == true) {
                GlassIconButton(onClick = { onApply(wallpaper) }, description = "Set as wallpaper", icon = Icons.Rounded.Wallpaper)
            }
            GlassIconButton(onClick = { onDelete(row) }, description = "Delete saved file", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.error)
        }
    }
}

@Composable
private fun DownloadRow(
    row: OfflineRow,
    onOpen: (Wallpaper) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (OfflineRow) -> Unit,
) {
    val download = row.download
    val wallpaper = row.wallpaper
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wallpaper != null) {
                OfflineTile(
                    wallpaper = wallpaper,
                    localUri = download.localUri.takeIf { row.fileExists },
                    onClick = { onOpen(wallpaper) },
                    modifier = Modifier.size(72.dp),
                    respectAspectRatio = false,
                )
            } else {
                Box(
                    Modifier.size(64.dp).background(
                        Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = .2f), Aero.colors.emerald.copy(alpha = .12f))),
                        RoundedCornerShape(14.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Download, null, tint = Aero.colors.accent) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (download.status) {
                        DownloadStatus.QUEUED.name -> "Queued original"
                        DownloadStatus.RUNNING.name -> "Saving original"
                        DownloadStatus.FAILED.name -> "Download failed"
                        DownloadStatus.CANCELED.name -> "Download canceled"
                        else -> "Wallpaper file"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Aero.colors.textPrimary,
                    maxLines = 1,
                )
                when (download.status) {
                    DownloadStatus.QUEUED.name -> Text("Waiting to start", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    DownloadStatus.RUNNING.name -> {
                        val progress = if (download.totalBytes > 0) (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Aero.colors.accent, trackColor = Aero.colors.accent.copy(alpha = .15f))
                        Text(
                            if (download.totalBytes > 0) "${Format.fileSize(download.bytesDownloaded)} / ${Format.fileSize(download.totalBytes)}"
                            else "${Format.fileSize(download.bytesDownloaded)} downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aero.colors.textSecondary,
                        )
                    }
                    DownloadStatus.FAILED.name -> Text(download.errorMessage ?: "The source could not provide this file.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.error)
                    DownloadStatus.CANCELED.name -> Text("You can retry this save.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary)
                    else -> Text("${Format.fileSize(download.savedSizeBytes)} · ${Format.dimensions(download.savedWidth ?: 0, download.savedHeight ?: 0)}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
                if (download.status == DownloadStatus.COMPLETED.name && !row.fileExists) {
                    Text("The file was moved or deleted.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.warning)
                }
            }
            when {
                download.status == DownloadStatus.RUNNING.name || download.status == DownloadStatus.QUEUED.name ->
                    GlassIconButton(onClick = { onCancel(download.wallpaperKey) }, description = "Cancel download", icon = Icons.Rounded.DeleteOutline)
                download.status == DownloadStatus.FAILED.name || download.status == DownloadStatus.CANCELED.name ||
                    download.status == DownloadStatus.COMPLETED.name && !row.fileExists && wallpaper?.downloadAllowed == true ->
                    GlassIconButton(onClick = { onRetry(download.wallpaperKey) }, description = "Retry download", icon = Icons.Rounded.Refresh, tint = Aero.colors.accent)
                else -> GlassIconButton(onClick = { onDelete(row) }, description = "Delete saved file", icon = Icons.Rounded.DeleteOutline, tint = Aero.colors.error)
            }
        }
    }
}

@Composable
private fun HistoryRow(wallpaper: Wallpaper, onOpen: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OfflineTile(
                wallpaper = wallpaper,
                localUri = null,
                onClick = onOpen,
                modifier = Modifier.size(74.dp),
                respectAspectRatio = false,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    wallpaper.title?.takeIf(String::isNotBlank) ?: "Wallpaper details",
                    style = MaterialTheme.typography.titleSmall,
                    color = Aero.colors.textPrimary,
                    maxLines = 1,
                )
                Text("Open for image dimensions, source and attribution", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 2)
            }
        }
    }
}
