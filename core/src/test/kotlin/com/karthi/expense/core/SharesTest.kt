package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SharesTest {

    @Test
    fun parseAndFormat_roundTrip() {
        val shares = Shares.parse("Arun:400; Bala:400.50")
        assertEquals(mapOf("Arun" to BigDecimal("400"), "Bala" to BigDecimal("400.50")), shares)
        assertEquals("Arun:400;Bala:400.5", Shares.format(shares))
    }

    @Test
    fun parse_ignoresJunkAndMergesRepeats() {
        assertEquals(mapOf("Arun" to BigDecimal("150")), Shares.parse("Arun:100;oops;:5;Arun:50;Bala:x"))
        assertEquals(emptyMap<String, BigDecimal>(), Shares.parse(null))
    }

    @Test
    fun format_dropsZeroAndBlank_andCleansSeparators() {
        assertNull(Shares.format(mapOf("Arun" to BigDecimal.ZERO, " " to BigDecimal.ONE)))
        assertEquals("A B:10", Shares.format(mapOf("A;B" to BigDecimal.TEN)))
    }

    @Test
    fun splitBill_myShareAndWhatFriendsOwe() {
        // Paid 1200 for three, friends owe 400 each.
        val shares = mapOf("Arun" to BigDecimal("400"), "Bala" to BigDecimal("400"))
        assertEquals(BigDecimal("400"), Shares.myPart(BigDecimal("1200"), shares))
        val balances = Shares.balances(listOf(TxnType.DEBIT to Shares.format(shares)))
        assertEquals(BigDecimal("400"), balances["Arun"])
    }

    @Test
    fun repaymentShort_leavesSmallBalance_thenSettleClearsIt() {
        val balances = Shares.balances(
            listOf(
                TxnType.DEBIT to "Arun:400",
                TxnType.CREDIT to "arun:350", // paid back by UPI, different case
                TxnType.DEBIT to "Arun:-50", // settled: absorbed as my own expense
            )
        )
        assertEquals(BigDecimal.ZERO.compareTo(balances.getValue("Arun")), 0)
    }

    @Test
    fun moneyGivenToSpendForThem_tracksWhatIsLeft() {
        // Got 5000 from Bala, spent 4500 for him: 500 of his is still with me (negative).
        val balances = Shares.balances(listOf(TxnType.CREDIT to "Bala:5000", TxnType.DEBIT to "Bala:4500"))
        assertEquals(BigDecimal("-500"), balances["Bala"])
        // Overspent to 5100: he owes me 100.
        val over = Shares.balances(listOf(TxnType.CREDIT to "Bala:5000", TxnType.DEBIT to "Bala:5100"))
        assertEquals(BigDecimal("100"), over["Bala"])
    }

    @Test
    fun splitEqually_keepsOddPaisaWithMe() {
        assertEquals(BigDecimal("333.33"), Shares.splitEqually(BigDecimal("1000"), 2))
    }
}
