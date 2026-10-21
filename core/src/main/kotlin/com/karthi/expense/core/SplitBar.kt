package com.karthi.expense.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The maths behind the split slider: one bar from 0 to the total, with a handle between each
 * pair of neighbouring parts. Handles are stored as their positions ("cuts") along the bar.
 */
object SplitBar {

    /** Positions of the handles for these part amounts, e.g. [300, 500, 400] -> [300, 800]. */
    fun cuts(amounts: List<BigDecimal>): List<BigDecimal> =
        amounts.dropLast(1).runningFold(BigDecimal.ZERO) { sum, a -> sum + a }.drop(1)

    /** Part amounts between the handles; the inverse of [cuts]. */
    fun amounts(cuts: List<BigDecimal>, total: BigDecimal): List<BigDecimal> =
        (listOf(BigDecimal.ZERO) + cuts + total).zipWithNext { a, b -> b - a }

    /**
     * Moves handle [index] to [value], snapped to [step] and kept between its neighbours (or
     * the ends of the bar), so no part goes below zero. The ends themselves are always reachable.
     */
    fun move(cuts: List<BigDecimal>, index: Int, value: BigDecimal, total: BigDecimal, step: BigDecimal): List<BigDecimal> {
        val low = cuts.getOrNull(index - 1) ?: BigDecimal.ZERO
        val high = cuts.getOrNull(index + 1) ?: total
        val half = step.divide(BigDecimal(2))
        val snapped = when {
            // Near a neighbour, land exactly on it even if it isn't a round number.
            (value - high).abs() < half -> high
            (value - low).abs() < half -> low
            else -> value.divide(step, 0, RoundingMode.HALF_UP).multiply(step)
        }
        val clamped = snapped.max(low).min(high)
        return cuts.toMutableList().also { it[index] = clamped }
    }

    /**
     * Sets part [index] to an exact typed amount. The next part (or the previous one, for the
     * last) gives or takes the difference, so the total stays the same; capped at what they share.
     */
    fun setAmount(amounts: List<BigDecimal>, index: Int, value: BigDecimal): List<BigDecimal> {
        if (amounts.size < 2) return amounts
        val other = if (index < amounts.lastIndex) index + 1 else index - 1
        val pool = amounts[index] + amounts[other]
        val v = value.max(BigDecimal.ZERO).min(pool)
        return amounts.toMutableList().also {
            it[index] = v
            it[other] = pool - v
        }
    }

    /** Equal parts, with the odd paisa going to the first part. */
    fun equal(total: BigDecimal, parts: Int): List<BigDecimal> {
        val each = total.divide(BigDecimal(parts), 2, RoundingMode.DOWN)
        val first = total - each.multiply(BigDecimal(parts - 1))
        return listOf(first) + List(parts - 1) { each }
    }

    /**
     * Fits existing parts to a new total, keeping their proportions (used when your own part
     * changes because someone's share changed). The last part absorbs rounding.
     */
    fun rescale(amounts: List<BigDecimal>, total: BigDecimal): List<BigDecimal> {
        val old = amounts.fold(BigDecimal.ZERO, BigDecimal::add)
        if (old.signum() == 0) return equal(total, amounts.size)
        val scaled = amounts.dropLast(1).map { it.multiply(total).divide(old, 2, RoundingMode.HALF_UP) }
        return scaled + (total - scaled.fold(BigDecimal.ZERO, BigDecimal::add))
    }
}
