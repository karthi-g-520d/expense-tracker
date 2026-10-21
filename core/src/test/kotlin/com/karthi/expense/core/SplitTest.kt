package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SplitTest {

    private fun d(vararg v: String) = v.map { BigDecimal(it) }

    @Test
    fun cutsAndAmounts_roundTrip() {
        val cuts = SplitBar.cuts(d("300", "500", "400"))
        assertEquals(d("300", "800"), cuts)
        assertEquals(d("300", "500", "400"), SplitBar.amounts(cuts, BigDecimal("1200")))
    }

    @Test
    fun move_snapsToStep_andStaysBetweenNeighbours() {
        val cuts = d("300", "800")
        val total = BigDecimal("1200")
        assertEquals(d("350", "800"), SplitBar.move(cuts, 0, BigDecimal("347.2"), total, BigDecimal.TEN))
        assertEquals(d("347", "800"), SplitBar.move(cuts, 0, BigDecimal("347.2"), total, BigDecimal.ONE))
        // Can't pass the next handle or the end of the bar.
        assertEquals(d("800", "800"), SplitBar.move(cuts, 0, BigDecimal("950"), total, BigDecimal.TEN))
        assertEquals(d("300", "1200"), SplitBar.move(cuts, 1, BigDecimal("1300"), total, BigDecimal.TEN))
        // The end is reachable even when the total isn't a multiple of the step.
        assertEquals(d("300", "1205"), SplitBar.move(d("300", "800"), 1, BigDecimal("1204"), BigDecimal("1205"), BigDecimal.TEN))
    }

    @Test
    fun equalAndRescale_alwaysAddUp() {
        assertEquals(d("333.34", "333.33", "333.33"), SplitBar.equal(BigDecimal("1000"), 3))
        assertEquals(d("200.00", "300.00"), SplitBar.rescale(d("400", "600"), BigDecimal("500")))
    }

    @Test
    fun setAmount_neighbourAbsorbsDifference() {
        assertEquals(d("437.50", "362.50", "400"), SplitBar.setAmount(d("300", "500", "400"), 0, BigDecimal("437.50")))
        assertEquals(d("300", "0", "900"), SplitBar.setAmount(d("300", "500", "400"), 2, BigDecimal("2000")))
    }

    @Test
    fun categoryRows_onePerPart_plusPeople() {
        val rows = CategoryParts.rows(BigDecimal("1200"), "Food", "Food:300;Movie:500", "Arun:400", "Not mine")
        assertEquals(
            listOf(
                CategoryParts.Row("#1", BigDecimal("300"), "Food", null),
                CategoryParts.Row("#2", BigDecimal("500"), "Movie", null),
                CategoryParts.Row("#people", BigDecimal("400"), "Not mine", "Arun:400"),
            ),
            rows,
        )
    }

    @Test
    fun categoryRows_singleCategoryStaysOneRow_andLastPartAbsorbsDrift() {
        assertEquals(
            listOf(CategoryParts.Row("", BigDecimal("100"), "Food", null)),
            CategoryParts.rows(BigDecimal("100"), "Food", null, null, "Not mine"),
        )
        val rows = CategoryParts.rows(BigDecimal("1000"), "Food", "Food:300;Movie:600", null, "Not mine")
        assertEquals(BigDecimal("700"), rows[1].amount)
    }
}
