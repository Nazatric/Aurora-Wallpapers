package com.auroro.wallpapers.ui

import com.auroro.wallpapers.ui.theme.AuroroPal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.components.ShellTab

/** Honest stand-in for destinations that arrive in later slices. */
@Composable
fun PlaceholderScreen(tab: ShellTab, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(tab.iconOutlined),
            contentDescription = null,
            tint = AuroroPal.textTertiary,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.tab_placeholder, stringResource(tab.labelRes)),
            style = MaterialTheme.typography.bodySmall,
            color = AuroroPal.textSecondary,
        )
    }
}
