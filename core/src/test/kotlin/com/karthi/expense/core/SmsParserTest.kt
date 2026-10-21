package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SmsParserTest {

    @Test
    fun iciciDebit_isDebitEvenThoughPayeeIsCredited() {
        val t = SmsParser.parse(
            "ICICI Bank Acct XX597 debited for Rs 112.00 on 20-Oct-26; 25 dots creatio credited. " +
                "UPI:665940892846. Call 18002662 for dispute. SMS BLOCK 597 to 9215676766."
        )!!
        assertEquals(Bank.ICICI, t.bank)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(BigDecimal("112.00"), t.amount)
        assertEquals("597", t.account)
        assertEquals("25 dots creatio", t.counterparty)
        assertEquals(LocalDate.of(2026, 10, 20), t.date)
        assertEquals("665940892846", t.reference)
    }

    @Test
    fun iciciCredit() {
        val t = SmsParser.parse(
            "Dear Customer, Acct XX597 is credited with Rs 4.00 on 20-Oct-26 from NPCI BHIM. " +
                "UPI:104286021401-ICICI Bank."
        )!!
        assertEquals(Bank.ICICI, t.bank)
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals(BigDecimal("4.00"), t.amount)
        assertEquals("NPCI BHIM", t.counterparty)
        assertEquals(LocalDate.of(2026, 10, 20), t.date)
        assertEquals("104286021401", t.reference)
    }

    @Test
    fun iobDebit() {
        val t = SmsParser.parse(
            "Your a/c XXXXX35 debited for payee REDBUS for Rs. 1086.12 on 2026-10-20, " +
                "ref 201654309169.If not you, report to your bank immediately-IOB."
        )!!
        assertEquals(Bank.IOB, t.bank)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(BigDecimal("1086.12"), t.amount)
        assertEquals("35", t.account)
        assertEquals("REDBUS", t.counterparty)
        assertEquals(LocalDate.of(2026, 10, 20), t.date)
        assertEquals("201654309169", t.reference)
    }

    @Test
    fun iobCredit() {
        val t = SmsParser.parse(
            "Rs.35000.00 Credited to SB-xxx1035 AcBal:39595.87 CLRBal: 39595.87 [IMPS/62831 ] " +
                "SALAIYUR on 10-10-2026 18:06:20.IOB."
        )!!
        assertEquals(Bank.IOB, t.bank)
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals(BigDecimal("35000.00"), t.amount)
        assertEquals("1035", t.account)
        assertEquals("SALAIYUR", t.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), t.date)
        assertEquals("62831", t.reference)
        assertEquals(BigDecimal("39595.87"), t.balance)
    }

    @Test
    fun amountsWithCommasAreParsed() {
        val t = SmsParser.parse(
            "ICICI Bank Acct XX597 debited for Rs 1,25,000.00 on 01-Nov-26; ABC Realty credited. UPI:1."
        )!!
        assertEquals(BigDecimal("125000.00"), t.amount)
    }

    @Test
    fun nonTransactionSmsIsIgnored() {
        assertNull(SmsParser.parse("123456 is your OTP for ICICI Bank. Do not share it with anyone."))
        assertNull(SmsParser.parse("Get a pre-approved loan of Rs 5,00,000 credited instantly! T&C apply."))
    }

    @Test
    fun sameSmsGivesSameId() {
        val sms = "Dear Customer, Acct XX597 is credited with Rs 4.00 on 20-Oct-26 from NPCI BHIM. UPI:104286021401-ICICI Bank."
        assertEquals(SmsParser.parse(sms)!!.id, SmsParser.parse(sms)!!.id)
    }
}
