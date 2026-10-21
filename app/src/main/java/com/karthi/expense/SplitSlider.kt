package com.karthi.expense

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.karthi.expense.core.SplitBar
import kotlinx.coroutines.delay
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

/** iOS system colours, one per part; they repeat after eight. */
private val PartColors = listOf(
    Color(0xFF007AFF), Color(0xFFFF9500), Color(0xFF34C759), Color(0xFFAF52DE),
    Color(0xFFFF2D55), Color(0xFF30B0C7), Color(0xFFFFCC00), Color(0xFF5856D6),
)

fun partColor(index: Int) = PartColors[index % PartColors.size]

// Normal drag moves in ₹10 steps; holding still for a moment zooms in to ₹1 steps, like the
// camera's zoom dial. A quick flick zooms back out.
private val CoarseStep = BigDecimal.TEN
private val FineStep = BigDecimal.ONE
private const val ZOOM = 6f
private const val HOLD_TO_ZOOM_MS = 280L
private const val FAST_PX_PER_MS = 1.2f

private fun BigDecimal.rupees() = "₹" + stripTrailingZeros().toPlainString()

/**
 * One bar from 0 to [total], coloured per part, with a handle between each pair of parts.
 * Dragging a handle moves the boundary between its two neighbours; a dark capsule pops up
 * above it with a moving ruler and both amounts. Tapping a part's label lets you type it exactly.
 */
@Composable
fun SplitSlider(
    total: BigDecimal,
    labels: List<String>,
    amounts: List<BigDecimal>,
    onChange: (List<BigDecimal>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (amounts.size < 2 || total.signum() <= 0) return
    val view = LocalView.current
    val density = LocalDensity.current
    val latestAmounts by rememberUpdatedState(amounts)
    val latestOnChange by rememberUpdatedState(onChange)

    var widthPx by remember { mutableIntStateOf(1) }
    var active by remember { mutableIntStateOf(-1) }
    var zoomed by remember { mutableStateOf(false) }
    var lastMoveAt by remember { mutableLongStateOf(0L) }
    var typing by remember { mutableIntStateOf(-1) }
    val zoom by animateFloatAsState(if (zoomed) 1f else 0f, fluidSpring(), label = "zoom")

    // Holding the handle still for a moment zooms in.
    LaunchedEffect(active, lastMoveAt) {
        if (active < 0) return@LaunchedEffect
        delay(HOLD_TO_ZOOM_MS)
        if (active >= 0 && !zoomed) {
            zoomed = true
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val cuts = SplitBar.cuts(amounts)
    fun xOf(value: BigDecimal) = (value.toFloat() / total.toFloat()) * widthPx

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(40.dp).zIndex(1f)) {
            // The bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                    .pointerInput(total) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val now = SplitBar.cuts(latestAmounts)
                            val grab = 28.dp.toPx()
                            val index = now.indices.minByOrNull { abs(xOf(now[it]) - down.position.x) }
                                ?.takeIf { abs(xOf(now[it]) - down.position.x) <= grab } ?: return@awaitEachGesture
                            down.consume()
                            active = index
                            zoomed = false
                            lastMoveAt = System.currentTimeMillis()
                            var raw = now[index]
                            var lastTime = down.uptimeMillis
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dx = change.position.x - change.previousPosition.x
                                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1)
                                lastTime = change.uptimeMillis
                                if (dx == 0f) continue
                                if (abs(dx) / dt > FAST_PX_PER_MS) zoomed = false
                                lastMoveAt = System.currentTimeMillis()
                                val perPx = total.toDouble() / widthPx / (if (zoomed) ZOOM else 1f)
                                raw += BigDecimal(dx * perPx)
                                val current = SplitBar.cuts(latestAmounts)
                                val moved = SplitBar.move(current, index, raw, total, if (zoomed) FineStep else CoarseStep)
                                if (moved[index].compareTo(current[index]) != 0) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    latestOnChange(SplitBar.amounts(moved, total))
                                }
                                // Don't let the raw position run past the ends, or it takes a while to come back.
                                raw = raw.max(current.getOrNull(index - 1) ?: BigDecimal.ZERO).min(current.getOrNull(index + 1) ?: total)
                                change.consume()
                            }
                            active = -1
                            zoomed = false
                        }
                    },
            ) {
                amounts.forEachIndexed { i, a ->
                    val weight = a.toFloat() / total.toFloat()
                    if (weight > 0f) {
                        Box(
                            Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(partColor(i).copy(alpha = if (active < 0 || active == i || active + 1 == i) 0.9f else 0.45f)),
                        )
                    }
                }
            }

            // Handles: white pills that grow while held.
            cuts.forEachIndexed { i, c ->
                val held = active == i
                val grow by animateFloatAsState(if (held) 1f else 0f, fluidSpring(), label = "handle")
                val handleWidth = 6.dp + 4.dp * grow
                val handleHeight = 36.dp + 4.dp * grow
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((xOf(c) - handleWidth.toPx() / 2).roundToInt(), 0) }
                        .size(handleWidth, handleHeight)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }

            // The capsule above the held handle.
            val shown = active in cuts.indices
            val anchorX = if (shown) xOf(cuts[active]) else 0f
            val capsuleWidth = with(density) { 188.dp.toPx() }
            // Called by its full name: inside this Column the ColumnScope overload would be picked.
            androidx.compose.animation.AnimatedVisibility(
                visible = shown,
                enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                    fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit = scaleOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium), targetScale = 0.4f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                    fadeOut(spring(stiffness = Spring.StiffnessMedium)),
                modifier = Modifier
                    .offset {
                        val x = (anchorX - capsuleWidth / 2).coerceIn(0f, (widthPx - capsuleWidth).coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), (-68).dp.roundToPx())
                    },
            ) {
                val i = active.coerceIn(0, (amounts.size - 2).coerceAtLeast(0))
                ZoomCapsule(
                    left = labels.getOrElse(i) { "" } to amounts.getOrElse(i) { BigDecimal.ZERO },
                    right = labels.getOrElse(i + 1) { "" } to amounts.getOrElse(i + 1) { BigDecimal.ZERO },
                    value = cuts.getOrElse(i) { BigDecimal.ZERO },
                    zoom = zoom,
                )
            }
        }

        // Labels: tap one to type its amount.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            labels.forEachIndexed { i, label ->
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { typing = i }.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(partColor(i)))
                    Text(
                        "  $label ${amounts.getOrElse(i) { BigDecimal.ZERO }.rupees()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (typing in amounts.indices) {
        var text by remember(typing) { mutableStateOf(amounts[typing].stripTrailingZeros().toPlainString()) }
        AlertDialog(
            onDismissRequest = { typing = -1 },
            title = { Text(labels.getOrElse(typing) { "" }) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("\\d*\\.?\\d{0,2}"))) text = v },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("The part next to it changes to keep the total ${total.rupees()}") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toBigDecimalOrNull()?.let { onChange(SplitBar.setAmount(amounts, typing, it)) }
                    typing = -1
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { typing = -1 }) { Text("Cancel") } },
        )
    }
}

/**
 * Dark capsule like the iPhone's Dynamic Island: both neighbouring amounts on top and a ruler
 * that slides under a fixed centre line. When zoomed in, the ticks spread out and step by ₹1.
 */
@Composable
private fun ZoomCapsule(left: Pair<String, BigDecimal>, right: Pair<String, BigDecimal>, value: BigDecimal, zoom: Float) {
    Column(
        Modifier
            .width(188.dp)
            .height(60.dp)
            .shadow(12.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${left.first} ${left.second.rupees()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (zoom > 0.5f) "₹1" else "₹10",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFCC00),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Text(
                "${right.second.rupees()} ${right.first}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Canvas(Modifier.fillMaxSize().padding(top = 6.dp)) {
            // Spacing per ₹1 grows as the zoom animates in.
            val perRupee = 1.2.dp.toPx() + (7.dp.toPx() - 1.2.dp.toPx()) * zoom
            val unit = if (zoom > 0.5f) 1 else 10
            val v = value.toFloat()
            val half = size.width / 2
            val span = (half / perRupee).toInt() + unit
            val first = ((v - span) / unit).toInt() * unit
            var tick = first
            while (tick <= v + span) {
                val x = half + (tick - v) * perRupee
                if (x in 0f..size.width) {
                    val major = tick % (unit * 10) == 0
                    val h = if (major) size.height else size.height * 0.5f
                    // Ticks fade towards the edges, like the camera dial.
                    val fade = 1f - abs(x - half) / half
                    drawLine(
                        Color.White.copy(alpha = 0.25f + 0.55f * fade),
                        Offset(x, size.height - h),
                        Offset(x, size.height),
                        strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                tick += unit
            }
            drawLine(Color(0xFFFFCC00), Offset(half, 0f), Offset(half, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}
