package com.karthi.expense.core

import java.math.BigDecimal

/**
 * Guesses which friend a UPI payment is from (or to), so a card can offer
 * "Kamalesh Clg paying you back ₹718 for COTTON HOUSE?" instead of the user looking it up.
 * Nothing here tags anything: the card shows the guess and the user confirms it.
 */
object PayerMatch {

    /** One person's line on the ledger: positive [change] means they owe you more, negative means less. */
    data class Entry(val id: String, val date: String, val label: String, val change: BigDecimal)

    /** Something a person still owes you (or you still hold of theirs), with what is left of it. */
    data class OpenItem(val id: String, val date: String, val label: String, val left: BigDecimal)

    /** What the card offers: who, how much of this payment is theirs, and what it clears. */
    data class Suggestion(
        val person: String,
        /** The person's share to put on this transaction. */
        val share: BigDecimal,
        /** Their open balance before this payment, always positive. */
        val open: BigDecimal,
        /** The single item this exact amount clears, if one matches. */
        val item: OpenItem?,
    ) {
        val clearsAll get() = share.compareTo(open) == 0
    }

    /** Words in contact names that say where you know someone from, not who they are. */
    private val ignored = setOf(
        "clg", "college", "school", "office", "work", "home", "bro", "anna", "sir", "madam", "mam",
        "nagar", "chennai", "bangalore", "bengaluru", "coimbatore", "madurai", "new", "old", "frnd", "friend",
        "oksbi", "okaxis", "okicici", "okhdfcbank", "ybl", "paytm", "upi", "ibl", "axl",
    )

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 && it !in ignored }

    /**
     * How well a contact name fits an SMS counterparty such as "Kamalesh N-pavikamalesh!oksbi"
     * (bank name, then the UPI id). 0 is no match, 1 is an exact word.
     */
    fun score(person: String, counterparty: String): Double {
        val theirs = words(counterparty)
        if (theirs.isEmpty()) return 0.0
        return words(person).maxOfOrNull { p -> theirs.maxOf { c -> wordScore(p, c) } } ?: 0.0
    }

    private fun wordScore(p: String, c: String): Double = when {
        p == c -> 1.0
        p.length < 4 || c.length < 4 -> 0.0
        // UPI ids glue names together: "pavikamalesh", "aakashraj12101".
        p.length >= 5 && c.contains(p) -> 0.9
        else -> 1.0 - distance(p, c).toDouble() / maxOf(p.length, c.length)
    }

    private const val THRESHOLD = 0.8

    /** The one person among [people] the counterparty most likely is; null when nobody fits or two fit equally. */
    fun bestPerson(counterparty: String?, people: Collection<String>): String? {
        if (counterparty.isNullOrBlank()) return null
        val ranked = people.map { it to score(it, counterparty) }
            .filter { it.second >= THRESHOLD }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        if (ranked.size > 1 && ranked[1].second == ranked[0].second) return null
        return ranked[0].first
    }

    /**
     * What a person still owes, item by item. A repayment of exactly one item's amount clears that
     * item (people usually pay back one thing at a time); any other repayment clears the oldest first.
     * Works the same for money you hold of theirs once the signs are flipped by the caller.
     */
    fun openItems(entries: List<Entry>): List<OpenItem> {
        val sorted = entries.sortedWith(compareBy({ it.date }, { it.id }))
        val items = sorted.filter { it.change.signum() > 0 }
            .map { OpenItem(it.id, it.date, it.label, it.change) }.toMutableList()
        val payments = sorted.filter { it.change.signum() < 0 }.map { it.change.negate() }
        val unmatched = mutableListOf<BigDecimal>()
        for (paid in payments) {
            val i = items.indexOfFirst { it.left.compareTo(paid) == 0 }
            if (i >= 0) items.removeAt(i) else unmatched += paid
        }
        for (paid in unmatched) {
            var rest = paid
            while (rest.signum() > 0 && items.isNotEmpty()) {
                val first = items[0]
                if (first.left <= rest) {
                    rest -= first.left
                    items.removeAt(0)
                } else {
                    items[0] = first.copy(left = first.left - rest)
                    rest = BigDecimal.ZERO
                }
            }
        }
        return items
    }

    /**
     * The suggestion for one card. [owing] maps each person to their ledger entries, already signed so
     * that positive means money flowing to you is expected from them (for a credit: what they owe you;
     * for a debit: what you hold of theirs).
     */
    fun suggest(counterparty: String?, amount: BigDecimal, owing: Map<String, List<Entry>>): Suggestion? {
        val open = owing.mapValues { (_, e) -> e.fold(BigDecimal.ZERO) { s, x -> s + x.change } }
            .filterValues { it >= BigDecimal("0.01") }
        val person = bestPerson(counterparty, open.keys) ?: return null
        val balance = open.getValue(person)
        val share = amount.min(balance)
        val item = openItems(owing.getValue(person)).lastOrNull { it.left.compareTo(amount) == 0 }
        return Suggestion(person, share, balance, item)
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
