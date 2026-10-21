package com.karthi.expense.data

import com.karthi.expense.core.Shares
import java.math.BigDecimal

/** Categories offered on each card. Edit these lists to add your own. */
object Categories {
    val debit = listOf(
        "Food", "Groceries", "Rent", "Bills", "Fuel", "Travel", "Shopping",
        "Movie", "Health", "Investment", SELF_TRANSFER, "Transfer", "Other",
    )
    val credit = listOf("Salary", "Refund", "Interest", SELF_TRANSFER, "Transfer", "Other")

    /** Money moved between your own IOB and ICICI accounts: neither spend nor income. */
    const val SELF_TRANSFER = "Self transfer"

    /** Set automatically when the whole amount belongs to other people (see Shares). */
    const val NOT_MINE = "Not mine"

    /** Rows written by Settle: the leftover of a person's balance, counted as your own. */
    const val SETTLED = "Settled"

    private val icons = mapOf(
        "Food" to "🍔", "Groceries" to "🛒", "Rent" to "🏠", "Bills" to "💡", "Fuel" to "⛽",
        "Travel" to "🚌", "Shopping" to "🛍️", "Movie" to "🎬", "Health" to "💊",
        "Investment" to "📈", "Transfer" to "🔁", "Other" to "📦",
        "Salary" to "💼", "Refund" to "↩️", "Interest" to "🏦", NOT_MINE to "🤝", SETTLED to "✅",
        SELF_TRANSFER to "🔄",
    )

    /** Your own part of a transaction; zero for a self transfer, so it stays out of totals. */
    fun myAmount(amount: BigDecimal, shares: String?, category: String?): BigDecimal =
        if (category == SELF_TRANSFER) BigDecimal.ZERO else Shares.myPart(amount, Shares.parse(shares))

    fun forType(type: String) = if (type == "CREDIT") credit else debit

    /** "🍔 Food" for known categories, the plain name otherwise. */
    fun label(category: String) = icons[category]?.let { "$it $category" } ?: category
}
