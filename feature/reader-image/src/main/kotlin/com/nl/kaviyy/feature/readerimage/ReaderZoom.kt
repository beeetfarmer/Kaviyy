package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Pinch to zoom, pan while zoomed, double tap to zoom in/out; single taps go to [zones].
 * At 1x, one-finger drags are left alone so the pager can turn pages.
 */
@Composable
internal fun ZoomBox(
    zones: TapZones,
    onZoomed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val notify by rememberUpdatedState(onZoomed)

    fun set(
        newScale: Float,
        newOffset: Offset,
    ) {
        val wasZoomed = scale > 1f
        scale = newScale.coerceIn(1f, MAX_ZOOM)
        offset = clampPan(newOffset, scale, size)
        if (wasZoomed != scale > 1f) notify(scale > 1f)
    }
    Box(
        modifier
            .pointerInput(zones) {
                size = this.size
                detectTapGestures(
                    onDoubleTap = { at ->
                        if (scale > 1f) {
                            set(1f, Offset.Zero)
                        } else {
                            // Zoom towards the tapped point.
                            val center = Offset(this.size.width / 2f, this.size.height / 2f)
                            set(DOUBLE_TAP_ZOOM, (center - at) * (DOUBLE_TAP_ZOOM - 1f))
                        }
                    },
                    onTap = { zones.onTap(it.x, this.size.width.toFloat()) },
                )
            }.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            set(scale * event.calculateZoom(), offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) { content() }
}

/** Keeps the zoomed content covering the viewport (graphicsLayer scales around the center). */
internal fun clampPan(
    offset: Offset,
    scale: Float,
    size: IntSize,
): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
