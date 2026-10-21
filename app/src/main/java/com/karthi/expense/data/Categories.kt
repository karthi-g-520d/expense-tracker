package com.karthi.expense.data

/** Categories offered on each card. Edit these lists to add your own. */
object Categories {
    val debit = listOf(
        "Food", "Groceries", "Rent", "Bills", "Fuel", "Travel", "Shopping",
        "Movie", "Health", "Investment", "Transfer", "Other",
    )
    val credit = listOf("Salary", "Refund", "Interest", "Transfer", "Other")

    private val icons = mapOf(
        "Food" to "🍔", "Groceries" to "🛒", "Rent" to "🏠", "Bills" to "💡", "Fuel" to "⛽",
        "Travel" to "🚌", "Shopping" to "🛍️", "Movie" to "🎬", "Health" to "💊",
        "Investment" to "📈", "Transfer" to "🔁", "Other" to "📦",
        "Salary" to "💼", "Refund" to "↩️", "Interest" to "🏦",
    )

    fun forType(type: String) = if (type == "CREDIT") credit else debit

    /** "🍔 Food" for known categories, the plain name otherwise. */
    fun label(category: String) = icons[category]?.let { "$it $category" } ?: category
}
