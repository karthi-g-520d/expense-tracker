package com.karthi.expense.core

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns Indian bank transaction SMS into [ParsedTxn].
 *
 * ICICI and IOB formats seen on the owner's phone have exact patterns (so their ids never change);
 * every other bank goes through a general reader that looks for an amount, your account or card
 * ("A/c XX1234", "Card ending 5678") and the verb tied to that account.
 *
 * Debit vs credit is decided by the verb nearest your account, never by searching for the word
 * "credited": a debit SMS often also says "<payee> credited". Messages that aren't a completed
 * debit or credit (OTPs, offers, payment requests, "will be debited" reminders) return null.
 */
object SmsParser {

    private const val AMOUNT = """([\d,]+(?:\.\d+)?)"""
    private val opts = setOf(RegexOption.IGNORE_CASE)

    // ICICI Bank Acct XX597 debited for Rs 112.00 on 20-Oct-26; 25 dots creatio credited. UPI:665940892846. ...
    private val iciciDebit = Regex(
        """Acct\s+XX(\d+)\s+debited\s+for\s+Rs\.?\s*$AMOUNT\s+on\s+(\d{1,2}-[A-Za-z]{3}-\d{2});\s*(.+?)\s+credited\.?(?:.*?UPI:(\d+))?""",
        opts,
    )

    // Dear Customer, Acct XX597 is credited with Rs 4.00 on 20-Oct-26 from NPCI BHIM. UPI:104286021401-ICICI Bank.
    private val iciciCredit = Regex(
        """Acct\s+XX(\d+)\s+is\s+credited\s+with\s+Rs\.?\s*$AMOUNT\s+on\s+(\d{1,2}-[A-Za-z]{3}-\d{2})\s+from\s+(.+?)\.(?:.*?UPI:(\d+))?""",
        opts,
    )

    // Your a/c XXXXX35 debited for payee REDBUS for Rs. 1086.12 on 2026-10-20, ref 201654309169. ...-IOB.
    private val iobDebit = Regex(
        """a/c\s+X+(\d+)\s+debited\s+for\s+payee\s+(.+?)\s+for\s+Rs\.?\s*$AMOUNT\s+on\s+(\d{4}-\d{2}-\d{2})(?:.*?ref\s+(\d+))?""",
        opts,
    )

    // Rs.35000.00 Credited to SB-xxx1035 AcBal:39595.87 CLRBal: 39595.87 [IMPS/62831 ] SALAIYUR on 10-10-2026 18:06:20.IOB.
    private val iobCredit = Regex(
        """Rs\.?\s*$AMOUNT\s+Credited\s+to\s+SB-x+(\d+)\s+AcBal:\s*$AMOUNT.*?\[\s*\w+\s*/\s*(\w+)\s*]\s*(.*?)\s+on\s+(\d{2}-\d{2}-\d{4})""",
        opts,
    )

    private val iciciDate = DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH)
    private val iobDebitDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val iobCreditDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /**
     * @param sender the SMS address, e.g. "VM-HDFCBK-S"; tells the bank apart when the text doesn't.
     * @param received the day the SMS arrived, used when the text has no date of its own.
     */
    fun parse(body: String, sender: String? = null, received: LocalDate? = null): ParsedTxn? {
        // NFKC turns styled letters some banks use ("𝗌𝗉𝖾𝗇𝗍") back into plain ones.
        val text = Normalizer.normalize(body, Normalizer.Form.NFKC).replace(Regex("""\s+"""), " ").trim()
        val senderBank = Bank.fromSender(sender)
        parseKnown(text)?.let { if (senderBank == null || senderBank == it.bank) return it }
        return GeneralSms.parse(text, sender, senderBank, received)
    }

    private fun parseKnown(text: String): ParsedTxn? {
        iciciDebit.find(text)?.let { m ->
            val (acct, amt, date, payee) = m.destructured
            return ParsedTxn(
                bank = Bank.ICICI, type = TxnType.DEBIT, amount = amount(amt), account = acct,
                counterparty = payee.trim(), date = LocalDate.parse(titleCaseMonth(date), iciciDate),
                reference = m.groupValues[5].ifEmpty { null }, balance = null,
            )
        }
        iciciCredit.find(text)?.let { m ->
            val (acct, amt, date, from) = m.destructured
            return ParsedTxn(
                bank = Bank.ICICI, type = TxnType.CREDIT, amount = amount(amt), account = acct,
                counterparty = from.trim(), date = LocalDate.parse(titleCaseMonth(date), iciciDate),
                reference = m.groupValues[5].ifEmpty { null }, balance = null,
            )
        }
        iobDebit.find(text)?.let { m ->
            val (acct, payee, amt, date) = m.destructured
            return ParsedTxn(
                bank = Bank.IOB, type = TxnType.DEBIT, amount = amount(amt), account = acct,
                counterparty = payee.trim(), date = LocalDate.parse(date, iobDebitDate),
                reference = m.groupValues[5].ifEmpty { null }, balance = null,
            )
        }
        iobCredit.find(text)?.let { m ->
            val (amt, acct, bal, ref, from, date) = m.destructured
            return ParsedTxn(
                bank = Bank.IOB, type = TxnType.CREDIT, amount = amount(amt), account = acct,
                counterparty = from.trim().ifEmpty { null }, date = LocalDate.parse(date, iobCreditDate),
                reference = ref, balance = amount(bal),
            )
        }
        return null
    }

    internal fun amount(raw: String) = BigDecimal(raw.replace(",", "").trimEnd('.'))

    // "20-OCT-26" -> "20-Oct-26" so the formatter accepts any casing.
    private fun titleCaseMonth(date: String): String {
        val (d, mon, y) = date.split("-")
        return "$d-${mon.lowercase().replaceFirstChar { it.uppercase() }}-$y"
    }
}
