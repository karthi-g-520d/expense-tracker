package com.karthi.expense

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** The scrolling content that glass bars blur. Set once in MainScreen. */
val LocalHazeState = staticCompositionLocalOf { HazeState() }

/** Springs tuned to settle quickly without visible bounce: fluid, not playful. */
fun <T> fluidSpring() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

/**
 * Frosted glass: blurs whatever scrolls behind it, with a light tint and a thin bright edge.
 * Falls back to a translucent fill on phones older than Android 12.
 */
@Composable
fun Modifier.glass(shape: Shape, floating: Boolean = true): Modifier {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme.surface
    val style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tint = HazeTint(base.copy(alpha = if (dark) 0.55f else 0.62f)),
        blurRadius = 24.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(base.copy(alpha = 0.94f)),
    )
    return this
        .then(
            if (floating) Modifier.shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
            else Modifier
        )
        .clip(shape)
        .hazeChild(LocalHazeState.current, style)
        .then(if (floating) Modifier.border(0.75.dp, glassEdge(dark), shape) else Modifier)
}

/**
 * Card with a soft, wide shadow and a hairline edge, iOS inset-grouped style.
 * Solid on the plain background; slightly see-through over a wallpaper so its colours glow through.
 */
@Composable
fun Modifier.softCard(shape: Shape = MaterialTheme.shapes.large, highlight: Color = Color.Transparent): Modifier {
    val dark = isSystemInDarkTheme()
    val seeThrough = Backdrop.image != null
    return this
        .shadow(
            // A shadow would show through a see-through card as a grey smudge.
            elevation = if (dark || seeThrough) 0.dp else 10.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.05f),
            spotColor = Color.Black.copy(alpha = 0.10f),
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (seeThrough) 0.78f else 1f))
        .border(0.5.dp, glassEdge(dark), shape)
        .then(if (highlight.alpha > 0f) Modifier.border(1.5.dp, highlight, shape) else Modifier)
}

// Brighter at the top, like light catching the rim of glass.
private fun glassEdge(dark: Boolean) = Brush.verticalGradient(
    if (dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
    else listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.06f))
)

/**
 * A sideways-scrolling row of chips: chips fade out as they slide into the card's side padding,
 * and a swipe that runs past the end of the row stays with the row instead of switching tabs.
 * Put the side padding after this modifier, inside the scroll.
 */
fun Modifier.chipRow(state: ScrollState, fade: Dp = 16.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = fade.toPx()
        // Each fade eases in over the first few pixels of scroll instead of popping on.
        val left = 1f - (state.value / w).coerceIn(0f, 1f)
        val right = 1f - ((state.maxValue - state.value) / w).coerceIn(0f, 1f)
        if (left < 1f) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = left), Color.Black), startX = 0f, endX = w),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right < 1f) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = right)), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
    .nestedScroll(KeepSideways)
    .horizontalScroll(state)

private object KeepSideways : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

/** Shrinks slightly while pressed and springs back on release. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, fluidSpring(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The one strong vibration in the app, for sync: a firm heavy tick on success,
 * a double buzz on failure.
 */
fun Context.syncHaptic(success: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    } ?: return
    val effect = when {
        !success -> VibrationEffect.createWaveform(longArrayOf(0, 45, 70, 45), -1)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    vibrator.vibrate(effect)
}
