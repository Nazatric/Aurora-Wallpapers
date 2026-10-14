package com.auroro.wallpapers.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.theme.LocalGlassQuality
import com.auroro.wallpapers.ui.theme.auroroGlass
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The shell destinations around the centred magic action. */
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
 * Floating Material-Expressive pill navigation. The selection indicator is a single sliding
 * pill (measured tab bounds + bouncy spring — the pattern from M3's NavigationBar primary
 * indicator), icons crossfade filled/outlined, and the centre action keeps its press spring.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun BottomPillBar(
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    onTabDoubleTap: (ShellTab) -> Unit = {},
    onCenterAction: () -> Unit,
    @StringRes centerActionLabel: Int,
    @DrawableRes centerActionIcon: Int,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(34.dp)
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .auroroGlass(
                shape = pillShape,
                quality = LocalGlassQuality.current,
                fallback = AuroroPal.surface,
                tint = AuroroPal.surface,
            ),
        shape = pillShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        val bounds = remember { mutableStateMapOf<ShellTab, Rect>() }
        val active = bounds[selected]
        val density = LocalDensity.current
        val indicatorX = remember { Animatable(0f) }
        val indicatorW = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(active) {
            val target = active ?: return@LaunchedEffect
            if (!placed) {
                placed = true
                indicatorX.snapTo(target.left)
                indicatorW.snapTo(target.width)
            } else {
                launch { indicatorX.animateTo(target.left, AuroroMotion.BouncyFloat) }
                launch { indicatorW.animateTo(target.width, AuroroMotion.BouncyFloat) }
            }
        }
        // Indicator and tabs share ONE padded box: measured tab bounds are in the Row's
        // coordinate space, so the sliding pill must live inside the same padding or it
        // renders offset from the icon it highlights.
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (active != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(indicatorX.value.roundToInt(), active.top.roundToInt())
                        }
                        .width(with(density) { indicatorW.value.toDp() })
                        .height(with(density) { active.height.toDp() })
                        .background(AuroroPal.accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp)),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PillItem(ShellTab.HOME, selected, onSelect, onTabDoubleTap) { bounds[ShellTab.HOME] = it }
                PillItem(ShellTab.SEARCH, selected, onSelect, onTabDoubleTap) { bounds[ShellTab.SEARCH] = it }
                CenterAction(
                    onClick = onCenterAction,
                    label = stringResource(centerActionLabel),
                    icon = centerActionIcon,
                )
                PillItem(ShellTab.LIBRARY, selected, onSelect, onTabDoubleTap) { bounds[ShellTab.LIBRARY] = it }
                PillItem(ShellTab.SETTINGS, selected, onSelect, onTabDoubleTap) { bounds[ShellTab.SETTINGS] = it }
            }
        }
    }
}

@Composable
private fun PillItem(
    tab: ShellTab,
    selected: ShellTab,
    onSelect: (ShellTab) -> Unit,
    onTabDoubleTap: (ShellTab) -> Unit,
    onPosition: (Rect) -> Unit,
) {
    val active = tab == selected
    val label = stringResource(tab.labelRes)
    val iconTint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else AuroroPal.textTertiary,
        animationSpec = AuroroMotion.ColorCalm,
        label = "pillIconTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.1f else 1f,
        animationSpec = AuroroMotion.BouncyFloat,
        label = "pillIconScale",
    )
    val labelColor by animateColorAsState(
        targetValue = if (active) AuroroPal.textSecondary else AuroroPal.textTertiary,
        animationSpec = AuroroMotion.ColorCalm,
        label = "pillLabel",
    )
    Column(
        modifier = Modifier
            .onGloballyPositioned { onPosition(it.boundsInParent()) }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                role = Role.Tab,
                onClickLabel = label,
                onClick = { onSelect(tab) },
                onDoubleClick = { onTabDoubleTap(tab) },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = active, animationSpec = tween(200), label = "pillIcon") { activeNow ->
                Icon(
                    painter = painterResource(if (activeNow) tab.iconFilled else tab.iconOutlined),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

@Composable
private fun CenterAction(onClick: () -> Unit, label: String, @DrawableRes icon: Int) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = AuroroMotion.BouncyFloat,
        label = "centerPress",
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(AuroroPal.accent, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
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
