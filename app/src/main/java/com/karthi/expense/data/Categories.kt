package com.karthi.expense.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.karthi.expense.core.Shares
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** A tag offered on cards: its name (what goes in the sheet) and an optional emoji. */
data class Category(val name: String, val icon: String = "")

/**
 * Categories offered on each card, editable in Settings and saved on the phone. Spending and
 * income have separate lists. Renaming or removing one leaves rows already in the sheet as they are.
 */
object Categories {

    /** Money moved between your own IOB and ICICI accounts: neither spend nor income. */
    const val SELF_TRANSFER = "Self transfer"

    /** Set automatically when the whole amount belongs to other people (see Shares). */
    const val NOT_MINE = "Not mine"

    /** Rows written by Settle: the leftover of a person's balance, counted as your own. */
    const val SETTLED = "Settled"

    private val defaultDebit = listOf(
        Category("Food", "🍔"), Category("Groceries", "🛒"), Category("Rent", "🏠"), Category("Bills", "💡"),
        Category("Fuel", "⛽"), Category("Travel", "🚌"), Category("Shopping", "🛍️"), Category("Movie", "🎬"),
        Category("Health", "💊"), Category("Investment", "📈"), Category(SELF_TRANSFER, "🔄"),
        Category("Transfer", "🔁"), Category("Other", "📦"),
    )
    private val defaultCredit = listOf(
        Category("Salary", "💼"), Category("Refund", "↩️"), Category("Interest", "🏦"),
        Category(SELF_TRANSFER, "🔄"), Category("Transfer", "🔁"), Category("Other", "📦"),
    )

    // Icons for names that aren't in either list any more, so old rows keep their emoji.
    private val builtInIcons = (defaultDebit + defaultCredit).associate { it.name to it.icon } +
        mapOf(NOT_MINE to "🤝", SETTLED to "✅")

    /** Compose state, so cards and Settings update as soon as a list changes. */
    val debit: SnapshotStateList<Category> = mutableStateListOf<Category>().apply { addAll(defaultDebit) }
    val credit: SnapshotStateList<Category> = mutableStateListOf<Category>().apply { addAll(defaultCredit) }

    private var prefs: android.content.SharedPreferences? = null

    /** Loads your saved lists. Call once before showing any screen. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("categories", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { saved ->
            runCatching {
                val json = JSONObject(saved)
                debit.replaceWith(read(json.getJSONArray("debit")))
                credit.replaceWith(read(json.getJSONArray("credit")))
            }
        }
    }

    /** Your own part of a transaction; zero for a self transfer, so it stays out of totals. */
    fun myAmount(amount: BigDecimal, shares: String?, category: String?): BigDecimal =
        if (category == SELF_TRANSFER) BigDecimal.ZERO else Shares.myPart(amount, Shares.parse(shares))

    fun forType(type: String): SnapshotStateList<Category> = if (type == "CREDIT") credit else debit

    /** "🍔 Food" when the category has an emoji, the plain name otherwise. */
    fun label(category: String): String {
        val icon = (debit + credit).firstOrNull { it.name == category && it.icon.isNotEmpty() }?.icon
            ?: builtInIcons[category]
        return if (icon.isNullOrEmpty()) category else "$icon $category"
    }

    /** Names are trimmed and can't repeat within a list (ignoring case). Returns false if refused. */
    fun add(type: String, name: String, icon: String): Boolean {
        val list = forType(type)
        val clean = Shares.cleanName(name)
        if (clean.isEmpty() || list.any { it.name.equals(clean, ignoreCase = true) }) return false
        // New ones go before "Other", which reads best last.
        val other = list.indexOfFirst { it.name == "Other" }
        list.add(if (other >= 0) other else list.size, Category(clean, icon.trim()))
        save()
        return true
    }

    fun update(type: String, index: Int, name: String, icon: String): Boolean {
        val list = forType(type)
        val clean = Shares.cleanName(name)
        if (clean.isEmpty() || list.withIndex().any { (i, c) -> i != index && c.name.equals(clean, ignoreCase = true) }) return false
        list[index] = Category(clean, icon.trim())
        save()
        return true
    }

    fun remove(type: String, index: Int) {
        forType(type).removeAt(index)
        save()
    }

    fun move(type: String, index: Int, by: Int) {
        val list = forType(type)
        val to = index + by
        if (to !in list.indices) return
        list.add(to, list.removeAt(index))
        save()
    }

    fun resetToDefaults() {
        debit.replaceWith(defaultDebit)
        credit.replaceWith(defaultCredit)
        save()
    }

    private fun save() {
        val json = JSONObject().put("debit", write(debit)).put("credit", write(credit))
        prefs?.edit()?.putString(KEY, json.toString())?.apply()
    }

    private fun read(arr: JSONArray) = (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Category(o.getString("name"), o.optString("icon"))
    }

    private fun write(list: List<Category>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("name", it.name).put("icon", it.icon)) }
    }

    private fun SnapshotStateList<Category>.replaceWith(items: List<Category>) {
        clear()
        addAll(items)
    }

    private const val KEY = "lists"
}
