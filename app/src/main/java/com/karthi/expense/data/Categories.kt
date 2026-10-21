package com.karthi.expense.data

/** Categories offered on each card. Edit these lists to add your own. */
object Categories {
    val debit = listOf(
        "Food", "Groceries", "Rent", "Bills", "Fuel", "Travel", "Shopping",
        "Movie", "Health", "Investment", "Transfer", "Other", NOT_MINE,
    )
    val credit = listOf("Salary", "Refund", "Interest", "Transfer", "Other", NOT_MINE)

    /**
     * Money that only passes through the account: a friend sends some and it's spent for them.
     * Kept in the sheet but left out of spent and received totals.
     */
    const val NOT_MINE = "Not mine"

    private val icons = mapOf(
        "Food" to "🍔", "Groceries" to "🛒", "Rent" to "🏠", "Bills" to "💡", "Fuel" to "⛽",
        "Travel" to "🚌", "Shopping" to "🛍️", "Movie" to "🎬", "Health" to "💊",
        "Investment" to "📈", "Transfer" to "🔁", "Other" to "📦",
        "Salary" to "💼", "Refund" to "↩️", "Interest" to "🏦", NOT_MINE to "🤝",
    )

    fun forType(type: String) = if (type == "CREDIT") credit else debit

    /** "🍔 Food" for known categories, the plain name otherwise. */
    fun label(category: String) = icons[category]?.let { "$it $category" } ?: category
}
