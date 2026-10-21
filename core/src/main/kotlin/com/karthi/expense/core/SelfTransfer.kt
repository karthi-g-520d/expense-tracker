package com.karthi.expense.core

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Finds money moved between your own accounts: a debit in one bank and a credit of the same
 * amount in the other, close together in time. Neither side is spending or income.
 */
object SelfTransfer {

    data class Leg(
        val id: String,
        val bank: Bank,
        val type: TxnType,
        val amount: BigDecimal,
        val reference: String?,
        /** When the SMS arrived, in epoch millis. */
        val time: Long,
    )

    /** NEFT can take a few hours; IMPS and UPI land within seconds. */
    const val WINDOW_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Pairs of (debit id, credit id). A shared reference number wins; otherwise the credit
     * nearest in time with the same amount. Each leg is used at most once.
     */
    fun match(legs: List<Leg>): List<Pair<String, String>> {
        val credits = legs.filter { it.type == TxnType.CREDIT }.toMutableList()
        val pairs = mutableListOf<Pair<String, String>>()
        for (debit in legs.filter { it.type == TxnType.DEBIT }.sortedBy { it.time }) {
            val candidates = credits.filter {
                it.bank != debit.bank && it.amount.compareTo(debit.amount) == 0 &&
                    abs(it.time - debit.time) <= WINDOW_MILLIS
            }
            val credit = candidates.firstOrNull { debit.reference != null && it.reference == debit.reference }
                ?: candidates.minByOrNull { abs(it.time - debit.time) }
                ?: continue
            credits.remove(credit)
            pairs += debit.id to credit.id
        }
        return pairs
    }
}
