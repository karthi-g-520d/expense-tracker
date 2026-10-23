package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SelfTransferTest {

    private val hour = 60L * 60 * 1000

    private fun leg(id: String, bank: Bank, type: TxnType, amount: String, time: Long, ref: String? = null, account: String = "") =
        SelfTransfer.Leg(id, bank, type, BigDecimal(amount), ref, time, account)

    @Test
    fun matchesTwoAccountsInTheSameBank_butNotOneAccountMaskedTwoWays() {
        val legs = listOf(
            leg("d", Bank.SBI, TxnType.DEBIT, "2000", 0, account = "4821"),
            leg("sameAccount", Bank.SBI, TxnType.CREDIT, "2000", 60_000, account = "21"),
            leg("other", Bank.SBI, TxnType.CREDIT, "2000", 120_000, account = "7700"),
        )
        assertEquals(listOf("d" to "other"), SelfTransfer.match(legs))
    }

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
