package com.nl.kaviyy.feature.readerimage

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keep screen on, brightness override and immersive bars (hidden with the chrome); all undone on leave. */
@Composable
internal fun ReaderWindowEffects(
    keepScreenOn: Boolean,
    brightness: Float?,
    immersive: Boolean,
) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(brightness) {
        fun apply(value: Float) {
            window.attributes = window.attributes.apply { screenBrightness = value }
        }
        apply(brightness?.coerceIn(0.01f, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        onDispose { apply(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
    DisposableEffect(immersive) {
        val bars = WindowCompat.getInsetsController(window, view)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) {
            bars.hide(
                WindowInsetsCompat.Type.systemBars(),
            )
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
