package com.auroro.wallpapers.ui.detail

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.resolutionLabel
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

/**
 * Slice-2 fullscreen preview: the artwork is shown whole (ContentScale.Fit on black) so no
 * orientation is ever cropped or misrepresented. Actions honour the data-layer rights gates:
 * `downloadAllowed` controls saving the original, `setWallpaperAllowed` controls applying it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    wallpaper: Wallpaper,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onApply: (ApplyTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember(wallpaper.key) { mutableStateOf(false) }
    var showApply by remember(wallpaper.key) { mutableStateOf(false) }
    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = wallpaper.previewUrl,
            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(icon = R.drawable.ic_arrow_back, label = stringResource(R.string.detail_back), onClick = onBack)
            Spacer(Modifier.weight(1f))
            CircleButton(
                icon = if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_outlined,
                label = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                tint = if (favorite) Color(0xFFFF6B81) else Color.White,
                onClick = onToggleFavorite,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DetailAction(
                icon = R.drawable.ic_info_outlined,
                label = stringResource(R.string.detail_info),
                emphasized = false,
                onClick = { showInfo = true },
            )
            DetailAction(
                icon = R.drawable.ic_download,
                label = stringResource(R.string.detail_download),
                emphasized = true,
                onClick = onDownload,
            )
            DetailAction(
                icon = R.drawable.ic_stat_auroro,
                label = stringResource(R.string.detail_set_wallpaper),
                emphasized = false,
                onClick = { showApply = true },
            )
        }
    }

    if (showInfo) {
        InfoSheet(wallpaper = wallpaper, onDismiss = { showInfo = false })
    }
    if (showApply) {
        ModalBottomSheet(onDismissRequest = { showApply = false }, containerColor = AuroroSurface) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(text = stringResource(R.string.apply_sheet_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.apply_sheet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroroTextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                ApplyTarget.entries.forEach { target ->
                    TextButton(
                        onClick = {
                            showApply = false
                            onApply(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun CircleButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(AuroroSurface.copy(alpha = 0.72f), CircleShape),
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
private fun DetailAction(icon: Int, label: String, emphasized: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    if (emphasized) AuroroAccent else AuroroSurface.copy(alpha = 0.78f),
                    CircleShape,
                ),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(23.dp),
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroroSurface) {
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
                    color = AuroroTextTertiary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    wallpaper.tags.forEach { tag ->
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroroTextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AuroroSurfaceBright)
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
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AuroroTextTertiary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = AuroroTextSecondary)
    }
}
