package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PayerMatchTest {

    private val people = listOf("Sakthivel Bethal Nagar Chennai", "Akash Clg", "Kamalesh Clg")

    private fun e(id: String, label: String, change: String, date: String = "2026-10-31") =
        PayerMatch.Entry(id, date, label, BigDecimal(change))

    @Test
    fun matchesUpiNamesToContacts() {
        assertEquals("Kamalesh Clg", PayerMatch.bestPerson("Kamalesh N-pavikamalesh!oksbi", people))
        assertEquals("Akash Clg", PayerMatch.bestPerson("AAKASH RAJ R-aakashraj12101.r", people))
        assertEquals("Sakthivel Bethal Nagar Chennai", PayerMatch.bestPerson("SIVARAMASAKTHIVEL G-sivarama", people))
    }

    @Test
    fun noMatchForStrangersOrPlaceWords() {
        assertNull(PayerMatch.bestPerson("SWIGGY-swiggy@icici", people))
        assertNull(PayerMatch.bestPerson("CHENNAI METRO RAIL", people))
        assertNull(PayerMatch.bestPerson("Clg Canteen", people))
        assertNull(PayerMatch.bestPerson(null, people))
    }

    @Test
    fun twoEqualMatches_noGuess() {
        assertNull(PayerMatch.bestPerson("Kamalesh N", listOf("Kamalesh Clg", "Kamalesh Office")))
    }

    @Test
    fun exactAmountPicksThatItem() {
        val kamalesh = listOf(e("1", "V VIJAYA LAKSHM", "70"), e("2", "COTTON HOUSE", "718"), e("3", "METROPOLITAN TR", "13"))
        val s = PayerMatch.suggest("Kamalesh N-pavikamalesh!oksbi", BigDecimal("718"), mapOf("Kamalesh Clg" to kamalesh))!!
        assertEquals("Kamalesh Clg", s.person)
        assertEquals(BigDecimal("718"), s.share)
        assertEquals("COTTON HOUSE", s.item?.label)
        assertEquals(BigDecimal("801"), s.open)
    }

    @Test
    fun otherAmountIsPartial_andNeverMoreThanOwed() {
        val owing = mapOf("Sakthivel Bethal Nagar Chennai" to listOf(e("1", "Dinner", "1427.14")))
        val part = PayerMatch.suggest("SIVARAMASAKTHIVEL G-sivarama", BigDecimal("100"), owing)!!
        assertEquals(BigDecimal("100"), part.share)
        assertNull(part.item)
        val more = PayerMatch.suggest("SIVARAMASAKTHIVEL G-sivarama", BigDecimal("2000"), owing)!!
        assertEquals(BigDecimal("1427.14"), more.share)
        assertTrue(more.clearsAll)
    }

    @Test
    fun settledPeopleAreNotSuggested() {
        val owing = mapOf("Kamalesh Clg" to listOf(e("1", "COTTON HOUSE", "718"), e("2", "Kamalesh N", "-718")))
        assertNull(PayerMatch.suggest("Kamalesh N-pavikamalesh!oksbi", BigDecimal("718"), owing))
    }

    @Test
    fun openItems_exactRepaymentClearsThatItem_restOldestFirst() {
        val items = PayerMatch.openItems(
            listOf(
                e("a", "Lunch", "70", "2026-10-01"),
                e("b", "COTTON HOUSE", "718", "2026-10-02"),
                e("c", "Bus", "13", "2026-10-03"),
                e("d", "paid", "-718", "2026-10-04"),
                e("e", "paid", "-50", "2026-10-05"),
            )
        )
        assertEquals(listOf("Lunch" to BigDecimal("20"), "Bus" to BigDecimal("13")), items.map { it.label to it.left })
    }
}
