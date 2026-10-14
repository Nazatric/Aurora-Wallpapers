package com.auroro.wallpapers.ui.crop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.CropMath
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.wallpaperDescription
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroBackground
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary
import kotlin.math.max

private const val MAX_USER_ZOOM = 4f

/**
 * Slice-3 crop editor for "Set wallpaper": the frame mirrors the device screen's aspect
 * ratio, the image starts cover-fitted (exactly what [CropMath.centerCrop] would take) and
 * drag/pinch move the visible region. On confirm the same [CropMath.visibleRect] maths the
 * applier uses converts scale + pan into a [NormalizedCrop], so WYSIWYG holds: the framing
 * you see here is the framing WallpaperManager receives.
 */
@Composable
fun CropScreen(
    wallpaper: Wallpaper,
    target: ApplyTarget,
    onBack: () -> Unit,
    onConfirm: (NormalizedCrop?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    var userScale by remember(wallpaper.key) { mutableFloatStateOf(1f) }
    var offsetX by remember(wallpaper.key) { mutableFloatStateOf(0f) }
    var offsetY by remember(wallpaper.key) { mutableFloatStateOf(0f) }
    var imageW by remember(wallpaper.key) {
        mutableIntStateOf(if (wallpaper.hasKnownDimensions) wallpaper.width else 0)
    }
    var imageH by remember(wallpaper.key) {
        mutableIntStateOf(if (wallpaper.hasKnownDimensions) wallpaper.height else 0)
    }
    // Frame size in pixels, hoisted out of BoxWithConstraints so the confirm button can
    // run the exact same visibleRect maths the gesture clamping uses.
    var frameWPx by remember { mutableFloatStateOf(0f) }
    var frameHPx by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize().background(AuroroBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.detail_back),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(text = stringResource(R.string.crop_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = target.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroroTextTertiary,
                    )
                }
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
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(frameWdp, frameHdp)
                        .clip(shape)
                        .background(Color.Black)
                        .pointerInput(imageW, imageH, frameW, frameH) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userScale = (userScale * zoom).coerceIn(1f, MAX_USER_ZOOM)
                                if (imageW > 0 && imageH > 0) {
                                    val base = max(frameW / imageW, frameH / imageH)
                                    val clamped = CropMath.clampOffset(
                                        imageW,
                                        imageH,
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
                                scaleX = userScale
                                scaleY = userScale
                                translationX = offsetX
                                translationY = offsetY
                            },
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroroTextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (userScale > 1.01f || offsetX != 0f || offsetY != 0f) {
                        TextButton(onClick = {
                            userScale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }) {
                            Text(stringResource(R.string.crop_reset), color = AuroroTextSecondary)
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                    Button(
                        onClick = {
                            val crop = if (imageW > 0 && imageH > 0 && frameWPx > 0f && frameHPx > 0f) {
                                CropMath.visibleRect(
                                    imageW,
                                    imageH,
                                    frameWPx,
                                    frameHPx,
                                    userScale,
                                    offsetX,
                                    offsetY,
                                )
                            } else {
                                // Dimensions never resolved: let the applier center-crop the
                                // original itself rather than guessing a rectangle.
                                null
                            }
                            onConfirm(crop)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuroroAccent),
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
