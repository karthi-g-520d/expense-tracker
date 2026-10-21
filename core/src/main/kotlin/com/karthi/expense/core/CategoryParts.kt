package com.karthi.expense.core

import java.math.BigDecimal

/**
 * One spend split across several of your categories, stored like shares: "Food:300;Movie:500".
 * The parts cover your own part only; other people's shares stay separate.
 */
object CategoryParts {

    /** One sheet row of a transaction: [suffix] is added to the transaction id ("" when not split). */
    data class Row(val suffix: String, val amount: BigDecimal, val category: String?, val shares: String?)

    /**
     * A transaction with two or more parts becomes one row per category, plus one row holding
     * other people's shares under [peopleCategory], so totals per category and per person both
     * add up. The last part absorbs any rounding so the rows always sum to [amount].
     */
    fun rows(amount: BigDecimal, category: String?, parts: String?, shares: String?, peopleCategory: String): List<Row> {
        val split = Shares.parse(parts).entries.toList()
        if (split.size < 2) return listOf(Row("", amount, category, shares))

        val others = Shares.parse(shares)
        val mine = Shares.myPart(amount, others)
        val beforeLast = split.dropLast(1).fold(BigDecimal.ZERO) { sum, e -> sum + e.value }
        val rows = split.mapIndexed { i, (name, value) ->
            Row("#${i + 1}", if (i == split.lastIndex) mine - beforeLast else value, name, null)
        }.toMutableList()
        if (others.isNotEmpty()) rows += Row("#people", amount - mine, peopleCategory, shares)
        return rows
    }
}
