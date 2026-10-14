package com.auroro.wallpapers.ui.library

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.AuroraChip
import com.auroro.wallpapers.ui.components.WallpaperArt
import com.auroro.wallpapers.ui.components.artAspect
import com.auroro.wallpapers.ui.components.formatBytes
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

/**
 * Slice-4 library: favorites and downloads in one place, same card language as Home/Search.
 * Download rows mirror the queue truthfully — status, progress bytes, honest error text and
 * only the actions that apply to that status.
 */
@Composable
fun LibraryScreen(
    state: LibraryViewModel.UiState,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var segment by remember { mutableStateOf(LibrarySegment.FAVORITES) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(16.dp, 1.dp))
            AuroraChip(
                label = stringResource(R.string.lib_favorites, state.favorites.size),
                active = segment == LibrarySegment.FAVORITES,
                onClick = { segment = LibrarySegment.FAVORITES },
            )
            AuroraChip(
                label = stringResource(R.string.lib_downloads, state.downloads.size),
                active = segment == LibrarySegment.DOWNLOADS,
                onClick = { segment = LibrarySegment.DOWNLOADS },
            )
            Spacer(Modifier.size(16.dp, 1.dp))
        }
        when (segment) {
            LibrarySegment.FAVORITES -> FavoritesGrid(
                favorites = state.favorites,
                favoriteKeys = state.favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onOpen = onOpen,
            )

            LibrarySegment.DOWNLOADS -> DownloadsList(
                rows = state.downloads,
                storageLine = state.storage?.let { info ->
                    stringResource(
                        R.string.lib_storage_line,
                        info.savedCount,
                        formatBytes(info.savedBytes),
                        formatBytes(info.deviceFreeBytes),
                    )
                },
                onCancel = onCancel,
                onRetry = onRetry,
                onDelete = onDelete,
                onOpen = onOpen,
            )
        }
    }
}

private enum class LibrarySegment { FAVORITES, DOWNLOADS }

@Composable
private fun FavoritesGrid(
    favorites: List<Wallpaper>,
    favoriteKeys: Set<String>,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
) {
    if (favorites.isEmpty()) {
        LibraryEmptyState(
            title = stringResource(R.string.lib_fav_empty_title),
            body = stringResource(R.string.lib_fav_empty_body),
        )
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 126.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        staggeredItems(favorites, key = { it.key }) { wallpaper ->
            WallpaperArt(
                wallpaper = wallpaper,
                aspect = artAspect(wallpaper),
                corner = 18.dp,
                favorite = wallpaper.key in favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onClick = { onOpen(wallpaper) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DownloadsList(
    rows: List<DownloadRow>,
    storageLine: String?,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (Wallpaper) -> Unit,
) {
    if (rows.isEmpty()) {
        LibraryEmptyState(
            title = stringResource(R.string.lib_dl_empty_title),
            body = stringResource(R.string.lib_dl_empty_body),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        storageLine?.let { line ->
            item(key = "storage") {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroTextTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
        }
        items(rows, key = { it.entity.wallpaperKey }) { row ->
            DownloadRowItem(
                row = row,
                onCancel = onCancel,
                onRetry = onRetry,
                onDelete = onDelete,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun DownloadRowItem(
    row: DownloadRow,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (Wallpaper) -> Unit,
) {
    val entity = row.entity
    val wallpaper = row.wallpaper
    val status = runCatching { DownloadStatus.valueOf(entity.status) }.getOrDefault(DownloadStatus.FAILED)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AuroroSurfaceBright,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (wallpaper != null) {
                    AsyncImage(
                        model = wallpaper.previewUrl,
                        contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        tint = AuroroTextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wallpaper?.title?.takeIf { it.isNotBlank() }
                        ?: entity.fileName
                        ?: stringResource(R.string.lib_dl_unknown),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = when (status) {
                        DownloadStatus.QUEUED -> stringResource(R.string.dl_queued)
                        DownloadStatus.RUNNING -> if (entity.totalBytes > 0) {
                            stringResource(
                                R.string.dl_running_progress,
                                formatBytes(entity.bytesDownloaded),
                                formatBytes(entity.totalBytes),
                            )
                        } else {
                            stringResource(R.string.dl_running)
                        }

                        DownloadStatus.COMPLETED -> buildList {
                            add(stringResource(R.string.dl_saved))
                            entity.savedSizeBytes?.let { add(formatBytes(it)) }
                            entity.savedWidth?.let { w ->
                                entity.savedHeight?.let { h -> add("$w×$h") }
                            }
                        }.joinToString(" · ")

                        DownloadStatus.FAILED -> entity.errorMessage
                            ?: stringResource(R.string.dl_failed)

                        DownloadStatus.CANCELED -> stringResource(R.string.dl_canceled)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        AuroroTextSecondary
                    },
                    maxLines = 2,
                )
                if (status == DownloadStatus.RUNNING) {
                    Spacer(Modifier.size(6.dp))
                    if (entity.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (entity.bytesDownloaded.toFloat() / entity.totalBytes).coerceIn(0f, 1f) },
                            color = AuroroAccent,
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = AuroroAccent,
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING -> TextButton(onClick = { onCancel(entity.wallpaperKey) }) {
                        Text(stringResource(R.string.dl_cancel), color = AuroroTextSecondary)
                    }

                    DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                        TextButton(onClick = { onRetry(entity.wallpaperKey) }) {
                            Text(stringResource(R.string.dl_retry), color = AuroroAccent)
                        }
                        TextButton(onClick = { onDelete(entity.wallpaperKey) }) {
                            Text(stringResource(R.string.dl_delete), color = AuroroTextTertiary)
                        }
                    }

                    DownloadStatus.COMPLETED -> {
                        wallpaper?.let { w ->
                            TextButton(onClick = { onOpen(w) }) {
                                Text(stringResource(R.string.dl_open), color = AuroroAccent)
                            }
                        }
                        TextButton(onClick = { onDelete(entity.wallpaperKey) }) {
                            Text(stringResource(R.string.dl_delete), color = AuroroTextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = AuroroSurfaceBright) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_outlined),
                contentDescription = null,
                tint = AuroroAccent,
                modifier = Modifier.padding(20.dp).size(28.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = AuroroTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
    }
}
