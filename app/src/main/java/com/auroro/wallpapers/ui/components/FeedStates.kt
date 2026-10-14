package com.auroro.wallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

/** First-load skeleton: calm static blocks plus one honest progress indicator. */
@Composable
fun FeedLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(300.dp)
                .clip(RoundedCornerShape(26.dp)).background(AuroroSurface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AuroroAccent, strokeWidth = 2.5.dp)
        }
        repeat(2) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(22.dp)).background(AuroroSurface),
            )
        }
        Text(
            text = stringResource(R.string.loading_feed),
            style = MaterialTheme.typography.labelSmall,
            color = AuroroTextTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Nothing came back at all: either a provider failure (retryable) or a genuinely empty mix. */
@Composable
fun FeedEmptyState(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(if (error != null) R.drawable.ic_cloud_off else R.drawable.ic_image),
            contentDescription = null,
            tint = AuroroTextTertiary,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = stringResource(if (error != null) R.string.error_title else R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = error ?: stringResource(R.string.empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AuroroTextSecondary,
        )
        if (error != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Footer under the last card: continuation progress, a partial-failure notice, or a true end. */
@Composable
fun FeedFooterState(
    loadingMore: Boolean,
    notice: String?,
    endReached: Boolean,
    onRetryMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(color = AuroroAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.loading_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroTextSecondary,
                )
            }
        } else if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = AuroroTextSecondary,
            )
            TextButton(onClick = onRetryMore) { Text(stringResource(R.string.retry)) }
        } else if (endReached) {
            Text(
                text = stringResource(R.string.end_feed),
                style = MaterialTheme.typography.labelSmall,
                color = AuroroTextTertiary,
            )
        }
    }
}
