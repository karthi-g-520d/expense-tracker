package com.karthi.expense.data

/** Categories offered on each card. Edit this list to add your own. */
object Categories {
    val debit = listOf(
        "Food", "Groceries", "Rent", "Bills", "Fuel", "Travel", "Shopping",
        "Movie", "Health", "Investment", "Transfer", "Other",
    )
    val credit = listOf("Salary", "Refund", "Interest", "Transfer", "Other")

    fun forType(type: String) = if (type == "CREDIT") credit else debit
}
