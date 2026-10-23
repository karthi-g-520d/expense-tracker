package com.karthi.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private val Segment = 132.dp
private val TabHeight = 42.dp

/** How much bigger the text looks through the droplet. */
private const val LENS_ZOOM = 1.14f

/**
 * Floating glass capsule whose highlight is a drop of liquid: it stretches out the faster it
 * travels, thins as it stretches, and wobbles like jelly as it comes to rest. The tab text under
 * the drop is magnified and tinted, as if seen through it.
 *
 * [position] is the pager position (0 = first tab, 1 = second, fractions in between).
 */
@Composable
fun TabPill(position: () -> Float, labels: List<String>, onSelect: (Int) -> Unit) {
    val stretch = rememberJelly(position)
    val primary = MaterialTheme.colorScheme.primary
    val rim = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.85f)

    // Read only while drawing, so the drop moves without recomposing anything.
    fun DrawScope.drop(): RoundRect {
        val seg = Segment.toPx()
        val s = stretch.value
        val cx = seg * (position() + 0.5f)
        val w = seg * s
        // Keeps roughly the same amount of liquid: longer means thinner, squashed means taller.
        val h = (size.height / sqrt(s)).coerceAtMost(size.height * 1.12f)
        // Squishes against the ends of the capsule instead of poking out.
        val left = (cx - w / 2).coerceAtLeast(0f)
        val right = (cx + w / 2).coerceAtMost(size.width)
        val top = (size.height - h) / 2
        return RoundRect(Rect(left, top, right, top + h), CornerRadius(h / 2))
    }
    fun DrawScope.dropPath() = Path().apply { addRoundRect(drop()) }

    Box(Modifier.glass(CircleShape).padding(5.dp)) {
        Box(
            Modifier
                .width(Segment * labels.size)
                .height(TabHeight)
                .drawBehind {
                    val d = drop()
                    val r = d.topLeftCornerRadius
                    drawRoundRect(primary.copy(alpha = 0.16f), Offset(d.left, d.top), Size(d.width, d.height), r)
                    // A bright rim, stronger on top, like light caught in the drop's edge.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(rim, rim.copy(alpha = 0f)), startY = d.top, endY = d.bottom),
                        Offset(d.left, d.top),
                        Size(d.width, d.height),
                        r,
                        style = Stroke(1.dp.toPx()),
                    )
                }
        )

        // Plain labels, with the part under the drop cut out so only the magnified copy shows there.
        Row(Modifier.drawWithContent { clipPath(dropPath(), ClipOp.Difference) { this@drawWithContent.drawContent() } }) {
            labels.forEachIndexed { i, label ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .width(Segment)
                        .height(TabHeight)
                        .pressScale(interaction, 0.94f)
                        .clickable(interaction, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // The same labels, enlarged around the drop's centre and tinted: the lens.
        Box(Modifier.matchParentSize().drawWithContent { clipPath(dropPath()) { this@drawWithContent.drawContent() } }) {
            Row(
                Modifier.graphicsLayer {
                    val total = Segment.toPx() * labels.size
                    scaleX = LENS_ZOOM
                    scaleY = LENS_ZOOM
                    transformOrigin = TransformOrigin((Segment.toPx() * (position() + 0.5f)) / total, 0.5f)
                }
            ) {
                labels.forEach { label ->
                    Box(Modifier.width(Segment).height(TabHeight), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = primary)
                    }
                }
            }
        }
    }
}

/**
 * How stretched the drop is (1 = round). Follows the drop's speed through an underdamped spring,
 * so it lengthens while moving and overshoots into a few soft wobbles when it stops.
 * Runs frame by frame only while something is moving.
 */
@Composable
private fun rememberJelly(position: () -> Float): State<Float> {
    val stretch = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        var last = position()
        while (true) {
            snapshotFlow { position() }.first { it != last }
            var s = stretch.floatValue
            var velocity = 0f
            var speed = 0f
            var still = 0
            var prev = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - prev) / 1e9f).coerceIn(0.001f, 0.032f)
                prev = now
                val p = position()
                val moved = abs(p - last)
                last = p
                // Tabs per second, smoothed so a jittery finger doesn't make it flutter.
                speed += (moved / dt - speed) * min(1f, dt * 18f)
                val target = 1f + min(speed * 0.14f, 0.42f)
                // Damping well under 1 gives the jelly wobble; stiffness sets how quick it is.
                val k = 380f
                val c = 2f * 0.36f * sqrt(k)
                velocity += (-(s - target) * k - c * velocity) * dt
                s += velocity * dt
                stretch.floatValue = s
                still = if (moved == 0f) still + 1 else 0
                if (still > 3 && abs(s - 1f) < 0.002f && abs(velocity) < 0.02f) {
                    stretch.floatValue = 1f
                    break
                }
            }
        }
    }
    return stretch
}
