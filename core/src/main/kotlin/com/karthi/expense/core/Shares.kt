package com.karthi.expense.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The parts of a transaction that belong to other people, stored in one sheet cell as
 * "Arun:400;Bala:400".
 *
 * On a debit, a share is money spent for that person, so they owe it to you.
 * On a credit, a share is money that came from or for that person, which pays them back
 * or leaves their money with you. A negative share is a settlement: the difference you
 * chose to absorb (debit) or keep (credit).
 */
object Shares {

    fun parse(text: String?): Map<String, BigDecimal> {
        val result = LinkedHashMap<String, BigDecimal>()
        text.orEmpty().split(';').forEach { part ->
            val colon = part.lastIndexOf(':')
            if (colon <= 0) return@forEach
            val name = part.substring(0, colon).trim()
            val amount = part.substring(colon + 1).trim().toBigDecimalOrNull() ?: return@forEach
            if (name.isNotEmpty()) result[name] = (result[name] ?: BigDecimal.ZERO) + amount
        }
        return result
    }

    /** Drops blank names and zero amounts; null when nothing is left. */
    fun format(shares: Map<String, BigDecimal>): String? = shares
        .mapKeys { cleanName(it.key) }
        .filter { it.key.isNotEmpty() && it.value.signum() != 0 }
        .entries
        .joinToString(";") { "${it.key}:${it.value.stripTrailingZeros().toPlainString()}" }
        .ifEmpty { null }

    /** ':' and ';' are the separators, so they can't be part of a name. */
    fun cleanName(name: String) = name.replace(Regex("[:;]"), " ").trim().replace(Regex("\\s+"), " ")

    /** What is left for you after other people's shares. */
    fun myPart(amount: BigDecimal, shares: Map<String, BigDecimal>): BigDecimal =
        amount - shares.values.fold(BigDecimal.ZERO, BigDecimal::add)

    /** How one share moves that person's balance. Positive balance: they owe you. */
    fun balanceChange(type: TxnType, share: BigDecimal): BigDecimal =
        if (type == TxnType.CREDIT) share.negate() else share

    /**
     * Running balance per person over every transaction, matching names without regard to
     * case. Keys keep the first spelling seen.
     */
    fun balances(txns: List<Pair<TxnType, String?>>): Map<String, BigDecimal> {
        val display = LinkedHashMap<String, String>()
        val totals = LinkedHashMap<String, BigDecimal>()
        for ((type, text) in txns) {
            for ((name, share) in parse(text)) {
                val key = name.lowercase()
                display.putIfAbsent(key, name)
                totals[key] = (totals[key] ?: BigDecimal.ZERO) + balanceChange(type, share)
            }
        }
        return totals.entries.associate { display.getValue(it.key) to it.value }
    }

    /** Splits [amount] equally between you and [people]; any odd paisa stays with you. */
    fun splitEqually(amount: BigDecimal, people: Int): BigDecimal =
        amount.divide(BigDecimal(people + 1), 2, RoundingMode.DOWN)
}
