package com.auroro.wallpapers.ui.crop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.CropMath
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroPal
import kotlin.math.max

private const val MAX_USER_ZOOM = 4f

/**
 * Crop editor for "Set wallpaper". The frame mirrors the device screen aspect, the image
 * starts cover-fitted, drag/pinch move the visible region, a rule-of-thirds grid guides the
 * framing, and Rotate steps the image 90° clockwise (swapping the working dimensions so the
 * same [CropMath.visibleRect] maths stays exact). The Set-on row picks Home / Lock / Both
 * with icon cards; Continue hands crop + rotation + target to the applier, which re-derives
 * everything on the full-resolution original — what you frame here is what gets applied.
 */
@Composable
fun CropScreen(
    wallpaper: Wallpaper,
    onBack: () -> Unit,
    onConfirm: (crop: NormalizedCrop?, rotationDegrees: Int, target: ApplyTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    var userScale by remember(wallpaper.key) { mutableFloatStateOf(1f) }
    var offsetX by remember(wallpaper.key) { mutableFloatStateOf(0f) }
    var offsetY by remember(wallpaper.key) { mutableFloatStateOf(0f) }
    var rotation by remember(wallpaper.key) { mutableIntStateOf(0) }
    var target by rememberSaveable { mutableStateOf(ApplyTarget.BOTH) }
    var imageW by remember(wallpaper.key) {
        mutableIntStateOf(if (wallpaper.hasKnownDimensions) wallpaper.width else 0)
    }
    var imageH by remember(wallpaper.key) {
        mutableIntStateOf(if (wallpaper.hasKnownDimensions) wallpaper.height else 0)
    }
    var frameWPx by remember { mutableFloatStateOf(0f) }
    var frameHPx by remember { mutableFloatStateOf(0f) }

    val swapped = rotation == 90 || rotation == 270
    val effW = if (swapped) imageH else imageW
    val effH = if (swapped) imageW else imageH

    Box(modifier = modifier.fillMaxSize().background(AuroroPal.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.detail_back),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Text(text = stringResource(R.string.crop_title), style = MaterialTheme.typography.titleMedium)
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val areaW = constraints.maxWidth.toFloat()
                val areaH = constraints.maxHeight.toFloat()
                val container = windowInfo.containerSize
                val screenAspect = if (container.width > 0 && container.height > 0) {
                    container.width.toFloat() / container.height.toFloat()
                } else {
                    9f / 19.5f
                }
                val frameW: Float
                val frameH: Float
                if (areaW / areaH > screenAspect) {
                    frameH = areaH * 0.94f
                    frameW = frameH * screenAspect
                } else {
                    frameW = areaW * 0.94f
                    frameH = frameW / screenAspect
                }
                SideEffect {
                    frameWPx = frameW
                    frameHPx = frameH
                }
                val frameWdp = with(density) { frameW.toDp() }
                val frameHdp = with(density) { frameH.toDp() }
                val shape = RoundedCornerShape(20.dp)
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Box(
                        modifier = Modifier
                            .size(frameWdp, frameHdp)
                            .clip(shape)
                            .background(Color.Black)
                            .pointerInput(effW, effH, frameW, frameH) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    userScale = (userScale * zoom).coerceIn(1f, MAX_USER_ZOOM)
                                    if (effW > 0 && effH > 0) {
                                        val base = max(frameW / effW, frameH / effH)
                                        val clamped = CropMath.clampOffset(
                                            effW,
                                            effH,
                                            frameW,
                                            frameH,
                                            base * userScale,
                                            offsetX + pan.x,
                                            offsetY + pan.y,
                                        )
                                        offsetX = clamped.first
                                        offsetY = clamped.second
                                    }
                                }
                            },
                    ) {
                        // Rotated rendering: coil cover-fits at s0 = max(fw/iw, fh/ih); a 90/270
                        // rotation needs s1 = max(fw/ih, fh/iw) to keep covering, so the layer
                        // scale carries the s1/s0 compensation exactly.
                        val coverComp = if (swapped && imageW > 0 && imageH > 0) {
                            val s0 = max(frameW / imageW, frameH / imageH)
                            val s1 = max(frameW / imageH, frameH / imageW)
                            if (s0 > 0f) s1 / s0 else 1f
                        } else {
                            1f
                        }
                        AsyncImage(
                            model = wallpaper.previewUrl,
                            contentDescription = stringResource(R.string.wallpaper_cd, wallpaperDescription(wallpaper)),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    val drawable = state.result.image
                                    val w = drawable.width
                                    val h = drawable.height
                                    if (w > 0 && h > 0 && imageW <= 0) {
                                        imageW = w
                                        imageH = h
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotation.toFloat()
                                    scaleX = userScale * coverComp
                                    scaleY = userScale * coverComp
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                        )
                        // Rule-of-thirds grid.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val line = Color.White.copy(alpha = 0.28f)
                            val stroke = 2f
                            for (f in listOf(1f / 3f, 2f / 3f)) {
                                drawLine(
                                    color = line,
                                    start = Offset(size.width * f, 0f),
                                    end = Offset(size.width * f, size.height),
                                    strokeWidth = stroke,
                                )
                                drawLine(
                                    color = line,
                                    start = Offset(0f, size.height * f),
                                    end = Offset(size.width, size.height * f),
                                    strokeWidth = stroke,
                                )
                            }
                        }
                    }
                    // Rotate: top-right of the frame, out of the way of the framing.
                    IconButton(
                        onClick = {
                            rotation = (rotation + 90) % 360
                            userScale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rotate_right),
                            contentDescription = stringResource(R.string.crop_rotate),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.crop_set_on),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroPal.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TargetCard(
                        icon = R.drawable.ic_home_outlined,
                        label = stringResource(R.string.set_on_home),
                        selected = target == ApplyTarget.HOME,
                        onClick = { target = ApplyTarget.HOME },
                        modifier = Modifier.weight(1f),
                    )
                    TargetCard(
                        icon = R.drawable.ic_lock_outlined,
                        label = stringResource(R.string.set_on_lock),
                        selected = target == ApplyTarget.LOCK,
                        onClick = { target = ApplyTarget.LOCK },
                        modifier = Modifier.weight(1f),
                    )
                    TargetCard(
                        icon = R.drawable.ic_smartphone_outlined,
                        label = stringResource(R.string.set_on_both),
                        selected = target == ApplyTarget.BOTH,
                        onClick = { target = ApplyTarget.BOTH },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (userScale > 1.01f || offsetX != 0f || offsetY != 0f || rotation != 0) {
                        TextButton(onClick = {
                            userScale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            rotation = 0
                        }) {
                            Text(stringResource(R.string.crop_reset), color = AuroroPal.textSecondary)
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                    Button(
                        onClick = {
                            val crop = if (effW > 0 && effH > 0 && frameWPx > 0f && frameHPx > 0f) {
                                CropMath.visibleRect(
                                    effW,
                                    effH,
                                    frameWPx,
                                    frameHPx,
                                    userScale,
                                    offsetX,
                                    offsetY,
                                )
                            } else {
                                null
                            }
                            onConfirm(crop, rotation, target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuroroPal.accent),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f, fill = false).height(46.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.crop_confirm),
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetCard(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AuroroPal.accent.copy(alpha = 0.20f) else AuroroPal.surface)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) AuroroPal.accent else AuroroPal.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AuroroPal.textPrimary else AuroroPal.textTertiary,
            maxLines = 1,
        )
    }
}
