package com.auroro.wallpapers.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurface
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

/** The five shell destinations; only Home is implemented in slice 1. */
enum class ShellTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconFilled: Int,
    @param:DrawableRes val iconOutlined: Int,
) {
    HOME(R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_outlined),
    SEARCH(R.string.nav_search, R.drawable.ic_search, R.drawable.ic_search_outlined),
    LIBRARY(R.string.nav_library, R.drawable.ic_bookmark, R.drawable.ic_bookmark_outlined),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings_outlined),
}

/**
 * Floating Material-Expressive style pill navigation: four labelled destinations around a
 * centred accent action. Kept deliberately dumb — the shell owns tab state.
 */
@Composable
fun BottomPillBar(
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    onCenterAction: () -> Unit,
    @param:StringRes centerActionLabel: Int,
    @param:DrawableRes centerActionIcon: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(34.dp),
        color = AuroroSurface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PillItem(ShellTab.HOME, selected, onSelect)
            PillItem(ShellTab.SEARCH, selected, onSelect)
            CenterAction(
                onClick = onCenterAction,
                label = stringResource(centerActionLabel),
                icon = centerActionIcon,
            )
            PillItem(ShellTab.LIBRARY, selected, onSelect)
            PillItem(ShellTab.SETTINGS, selected, onSelect)
        }
    }
}

@Composable
private fun PillItem(tab: ShellTab, selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    val active = tab == selected
    val label = stringResource(tab.labelRes)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClickLabel = label) { onSelect(tab) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .then(
                    if (active) {
                        Modifier.background(AuroroAccent.copy(alpha = 0.22f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (active) tab.iconFilled else tab.iconOutlined),
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else AuroroTextTertiary,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AuroroTextSecondary.copy(alpha = 1f) else AuroroTextTertiary,
        )
    }
}

@Composable
private fun CenterAction(onClick: () -> Unit, label: String, @param:DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(AuroroAccent, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}
