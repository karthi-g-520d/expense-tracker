package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SelfTransferTest {

    private val hour = 60L * 60 * 1000

    private fun leg(id: String, bank: Bank, type: TxnType, amount: String, time: Long, ref: String? = null) =
        SelfTransfer.Leg(id, bank, type, BigDecimal(amount), ref, time)

    @Test
    fun matchesDebitInOneBankWithCreditInTheOther() {
        val legs = listOf(
            leg("d", Bank.IOB, TxnType.DEBIT, "5000", 0),
            leg("c", Bank.ICICI, TxnType.CREDIT, "5000.00", 2 * 60_000),
        )
        assertEquals(listOf("d" to "c"), SelfTransfer.match(legs))
    }

    @Test
    fun ignoresSameBankDifferentAmountAndTooLate() {
        val legs = listOf(
            leg("d", Bank.IOB, TxnType.DEBIT, "5000", 0),
            leg("sameBank", Bank.IOB, TxnType.CREDIT, "5000", hour),
            leg("otherAmount", Bank.ICICI, TxnType.CREDIT, "4999", hour),
            leg("late", Bank.ICICI, TxnType.CREDIT, "5000", 25 * hour),
        )
        assertEquals(emptyList<Pair<String, String>>(), SelfTransfer.match(legs))
    }

    @Test
    fun prefersSharedReference_thenNearestTime_andUsesEachLegOnce() {
        val legs = listOf(
            leg("d1", Bank.ICICI, TxnType.DEBIT, "1000", 0, ref = "123"),
            leg("d2", Bank.ICICI, TxnType.DEBIT, "1000", hour),
            leg("near", Bank.IOB, TxnType.CREDIT, "1000", 60_000),
            leg("sameRef", Bank.IOB, TxnType.CREDIT, "1000", 3 * hour, ref = "123"),
        )
        assertEquals(listOf("d1" to "sameRef", "d2" to "near"), SelfTransfer.match(legs))
    }
}
