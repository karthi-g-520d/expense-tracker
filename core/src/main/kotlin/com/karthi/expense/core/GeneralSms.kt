package com.karthi.expense.core

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

/**
 * Reads debit / credit SMS from any Indian bank, without a pattern per bank. Formats it was built
 * against (and tested with, see SmsParserTest):
 *
 *     SBI      Dear UPI user A/C X1234 debited by 100.0 on date 23Oct26 trf to RAVI Refno 412345678901
 *     HDFC     Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY On 23/10/26 Ref 412345678901
 *     Axis     INR 500.00 debited from A/c no. XX1234 on 23-10-26 ... UPI/P2M/412345678901/ZOMATO
 *     Kotak    Received Rs.250.00 in your Kotak Bank AC X3333 from ravi@oksbi on 14-10-25.UPI Ref 222
 *     PNB      A/c X0000 debited INR 125.00 Dt 01-01-26 12:00:00 to Swiggy thru UPI:000000000001
 *     BoB      Rs.500.00 Dr. from A/C XXXXXX1234 and Cr. to ravi@ybl. Ref:412345678901
 *     Canara   An amount of INR 500.00 has been DEBITED to your account XXX123 on 23/10/2026 ...
 *
 * Steps: find your account or card ("A/c XX1234", "Card ending 5678", "XXXXXX9722"), find the
 * amount (skipping balance and limit amounts), then decide debit or credit from the verb nearest
 * that account in the same clause. Verbs like "sent" or "received" can go either way ("Ravi has
 * received Rs 500 from your A/c"), so for those the word before your account decides: "from" is a
 * debit, "to" / "in" a credit.
 */
internal object GeneralSms {

    private val I = setOf(RegexOption.IGNORE_CASE)

    private const val AMT = """(\d[\d,]*(?:\.\d+)?)"""
    private const val CUR = """(?:(?<![A-Za-z])Rs[.:]?|(?<![A-Za-z])INR|₹)"""

    /** Not a completed debit or credit: OTPs, reminders, requests, offers, failures. */
    private val skip = Regex(
        listOf(
            """\bOTP\b""", """one[ -]time password""", """\bverification code\b""",
            """will be (?:auto[ -]?)?debited""", """to be debited""", """will be credited""", """\bscheduled\b""",
            """\bis due\b""", """\bdue date\b""", """(?:min(?:imum)?|total) (?:amount |amt )?due""",
            """payment request""", """collect request""", """has requested""", """\brequested (?:money|rs|inr)""",
            """\bfailed\b""", """\bdeclined\b""", """\bunsuccessful\b""",
            """pre-?approved""", """apply now""", """\bloan offer""",
            """statement (?:is|for)\b""", """mandate (?:created|registered|set)""", """standing instruction set""",
            """received today for processing""", """e-?voucher""",
        ).joinToString("|"),
        I,
    )

    // Amounts in another currency ("USD 11.80 spent"): we can't put them in rupees, so the SMS is skipped.
    private val foreign = Regex("""(?<![A-Za-z])(?:USD|EUR|GBP|AED|SGD|AUD|CAD|JPY|CHF|SAR|NPR)\s*\d|\$\s*\d""", I)

    private val currencyAmount = Regex("""(?:$CUR\s*\.?\s*)+$AMT""", I)

    // "debited by 100.0" (SBI leaves out "Rs"), "UPI debit:Rs.599.00".
    private val verbAmount = Regex("""(?:debited|credited|debit|credit)\s*(?:by|with|for|of)?\s*[:\-]?\s*(?:$CUR\s*\.?\s*)*$AMT""", I)

    private val balanceWord = Regex("""(?:bal|balance|\blimit|\blmt|\bavl|\bavbl|\baval|avlbl|\bamt\b)[\s.:\-]*$""", I)

    private val balance = Regex(
        """(?:bal(?:ance)?|avlbl amt)\s*(?:is|of)?\s*[:\-]?\s*(?:$CUR\s*\.?\s*)*$AMT""",
        I,
    )

    /** "A/c XX1234", "Acct XX597", "A/cX1234", "Card ending 5678", "SB-xxx1035", "A/C 2134***12345". */
    private val accountToken = Regex(
        """(?<![A-Za-z])(?:account|acct|a/c|ac|card|sb)\.?\s*(?:no\.?|number)?\s*(?:ending\s*(?:with|in)?)?\s*[:\-]?\s*([X*#.]*\d[\dX*#.]*|\d+[X*#]+)""",
        I,
    )

    /** A masked number with no "A/c" before it: "Sent Rs.51.00 from XXXXXX9722 to ...". */
    private val bareMaskedToken = Regex("""(?<![\w*])([X*]{2,}\d{3,})(?!\d)""", I)

    /** "your account" with no digits ("Your account has been debited with Rs 649.00"). */
    private val yourAccount = Regex("""\byour\s+(?:[\w&]+\s+){0,3}?(?:a/c|account|acct)\b""", I)

    private val debitVerbs = Regex(
        """\b(?:debited|debit(?!\s*card)|spent|withdrawn|withdrawal|deducted|purchase|charged|done\s+at)\b|\bdr\.(?=\s*(?:from|to)\b)""",
        I,
    )
    private val creditVerbs = Regex(
        """(?:credited|\bcredit(?!\s*card)\b|\bdeposited\b|\brefunded\b|\breversed\b)|\bcr\.(?=\s*(?:to|in|from)\b)""",
        I,
    )
    private val eitherVerbs = Regex("""\b(?:sent|received|recd|recvd|paid|transferred|added)\b""", I)
    private val creditByDefault = setOf("received", "recd", "recvd", "added")

    // The word right before "<Bank> A/c": "from your Kotak Bank AC" is a debit, "in your ... AC" a credit.
    private val prepositionBefore = Regex("""\b(from|to|in|into)\s+(?:your\s+|ur\s+)?(?:[\w&]+\s+){0,3}$""", I)

    private val referencePatterns = listOf(
        Regex("""UPI[\s:/-]*(?:Ref(?:erence)?\.?\s*(?:No|ID|number)?\.?)?\s*[:\-]?\s*(\d{6,})""", I),
        Regex("""UPI/[A-Z0-9]+/(\d{9,})/""", I),
        Regex("""(?:\bRRN|\bUTR|\bRef(?:erence)?|\bRefno|\bTxn|\bTransaction)\s*(?:no|id|number)?\.?\s*[:\-#]?\s*([A-Z0-9]*\d[A-Z0-9]{5,})""", I),
        Regex("""(?:IMPS|NEFT|RTGS)/(\d{6,})""", I),
    )

    /** Where the bank's footer starts ("Not you? Call ..."); payees are only looked for before it. */
    private val footer = Regex(
        """not you|if not|not u\?|not done by you|\bcall\s*\d|to report|to block|sms block|\bhelpline|\bdispute|fraud\?""",
        I,
    )

    private const val STOP =
        """(?=\s+(?:on|ref|refno|rrn|utr|upi|via|thru|through|avl|avbl|bal|dt|value|using|with|for|at|info|and|is|has)\b|\s*[;()\[\]]|\.(?:\s|$|rrn|ref|upi|avl|bal|not|imps|neft)|\s+\d{1,2}[-/]|$)"""
    private const val TARGET = """([^;()\[\]]+?)$STOP"""

    private val upiInfo = Regex("""UPI/[A-Z0-9]+/[A-Z]?\d+/\s*([^/.]+?)(?:/|$STOP)""", I)
    private val payee = Regex("""\b(?:to|at|towards|for payee|trf to|paid to|info[:\-]?)\s+(?:VPA\s+)?$TARGET""", I)
    private val payer = Regex(
        """\b(?:from|by|sent by|info[:\-]?)\s*:?\s+(?:an?\s+)?(?:a/c linked to\s+)?(?:VPA\s+)?(?!rs\b|rs\.|inr\b|₹|\d)$TARGET""",
        I,
    )

    fun parse(text: String, sender: String?, senderBank: Bank?, received: LocalDate?): ParsedTxn? {
        if (skip.containsMatchIn(text)) return null

        // A personal phone number isn't a bank, even if the text reads like one.
        val bank = senderBank ?: Bank.fromBody(text)
            ?: if (sender == null || Bank.isBusinessSender(sender)) Bank.OTHER else return null

        val acct = findAccount(text) ?: return null
        val amount = findAmount(text) ?: return null
        if (amount.value.signum() <= 0) return null
        foreign.find(text)?.let { if (it.range.first < amount.at) return null }

        val type = findType(text, acct.range) ?: return null
        val beforeFooter = text.substring(0, footer.find(text)?.range?.first ?: text.length)

        return ParsedTxn(
            bank = bank,
            type = type,
            amount = amount.value,
            account = acct.last,
            counterparty = counterparty(beforeFooter, type),
            date = DateFinder.find(text, received) ?: received ?: return null,
            reference = referencePatterns.firstNotNullOfOrNull { it.find(beforeFooter)?.groupValues?.get(1) },
            balance = balance.find(text)?.let { runCatching { SmsParser.amount(it.groupValues[1]) }.getOrNull() },
            accountFirst = acct.first,
        )
    }

    private class Account(val range: IntRange, val first: String, val last: String)

    private fun findAccount(text: String): Account? {
        val m = accountToken.findAll(text).firstOrNull { it.groupValues[1].count(Char::isDigit) >= 2 }
            ?: bareMaskedToken.find(text)
        if (m != null) {
            val token = m.groupValues[1].trimEnd('.')
            val last = token.takeLastWhile { it.isDigit() }
            val masked = token.any { !it.isDigit() }
            // Only an unmasked number or one shown as "2134***12345" tells us its first digits.
            val first = if (!masked) token else if (token.first().isDigit()) token.takeWhile { it.isDigit() } else ""
            return Account(m.range, first, last)
        }
        return yourAccount.find(text)?.let { Account(it.range, "", "") }
    }

    private class Amount(val value: BigDecimal, val at: Int)

    private fun findAmount(text: String): Amount? {
        val m = currencyAmount.findAll(text).firstOrNull { m ->
            !balanceWord.containsMatchIn(text.substring(maxOf(0, m.range.first - 16), m.range.first))
        } ?: verbAmount.find(text)
        return m?.let { runCatching { Amount(SmsParser.amount(it.groupValues[1]), it.range.first) }.getOrNull() }
    }

    private class Verb(val range: IntRange, val word: String, val type: TxnType?)

    private fun findType(text: String, acct: IntRange): TxnType? {
        val verbs = debitVerbs.findAll(text).map { Verb(it.range, it.value, TxnType.DEBIT) } +
            creditVerbs.findAll(text).map { Verb(it.range, it.value, TxnType.CREDIT) } +
            eitherVerbs.findAll(text).map { Verb(it.range, it.value.lowercase(), null) }
        val all = verbs.toList()
        if (all.isEmpty()) return null

        val clause = clauseAround(text, acct)
        val nearest = (all.filter { it.range.first in clause && it.range.last in clause }.ifEmpty { all })
            .minBy { distance(it.range, acct) }
        nearest.type?.let { return it }

        return when (prepositionBefore.find(text.substring(maxOf(0, acct.first - 40), acct.first))?.groupValues?.get(1)?.lowercase()) {
            "from" -> TxnType.DEBIT
            "to", "in", "into" -> TxnType.CREDIT
            else -> if (nearest.word in creditByDefault) TxnType.CREDIT else TxnType.DEBIT
        }
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }

    /**
     * The part of the SMS around your account, split at ";", " and " and sentence ends, so the
     * payee's "credited" in "A/c XX1 debited; RAVI credited" or "Dr. from A/C XX1 and Cr. to ravi@ybl"
     * isn't read as yours. Abbreviations like "Dr. from" or "Rs. 500" don't end a sentence.
     */
    private fun clauseAround(text: String, acct: IntRange): IntRange {
        val cuts = Regex(""";|\s+and\s+|\.\s+(?=[A-Z])""").findAll(text).map { it.range }.toList()
        val start = cuts.filter { it.last < acct.first }.maxOfOrNull { it.last + 1 } ?: 0
        val end = cuts.filter { it.first > acct.last }.minOfOrNull { it.first - 1 } ?: text.lastIndex
        return start..end
    }

    private fun counterparty(text: String, type: TxnType): String? {
        val candidates = sequence {
            upiInfo.find(text)?.let { yield(it.groupValues[1]) }
            val re = if (type == TxnType.DEBIT) payee else payer
            yieldAll(re.findAll(text).map { it.groupValues[1] })
        }
        return candidates
            .map { it.trim().trim('.', ',', '-', ':', ' ') }
            .firstOrNull { c ->
                c.length >= 2 && !c.all { it.isDigit() || it == ' ' } &&
                    !Regex("""^(?:your|ur|the|a/c|ac|acct|account|card|bank)\b""", I).containsMatchIn(c) &&
                    !accountToken.matches(c)
            }
            ?.take(40)
            ?.trim()
    }
}

/** Finds the transaction date in the text, in the formats Indian banks use. */
internal object DateFinder {

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val dayMonthNameYear = Regex("""(?<![\w])(\d{1,2})(?:st|nd|rd|th)?[-/ .]?([A-Za-z]{3,9})[-/ .,]*'?(\d{4}|\d{2})(?!\d)""")
    private val monthNameDayYear = Regex("""(?<![\w])([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)""")
    private val yearMonthDay = Regex("""(?<![\d.])(\d{4})[-/:](\d{1,2})[-/:](\d{1,2})(?![\d])""")
    private val dayMonthYear = Regex("""(?<![\d/.\-:])(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?![\d/\-:]|\.\d)""")

    fun find(text: String, received: LocalDate?): LocalDate? {
        val found = mutableListOf<Pair<Int, LocalDate>>()
        dayMonthNameYear.findAll(text).forEach { m ->
            val (d, mon, y) = m.destructured
            date(y, month(mon), d)?.let { found += m.range.first to it }
        }
        monthNameDayYear.findAll(text).forEach { m ->
            val (mon, d, y) = m.destructured
            date(y, month(mon), d)?.let { found += m.range.first to it }
        }
        yearMonthDay.findAll(text).forEach { m ->
            val (y, mon, d) = m.destructured
            date(y, mon.toInt(), d)?.let { found += m.range.first to it }
        }
        dayMonthYear.findAll(text).forEach { m ->
            val (d, mon, y) = m.destructured
            date(y, mon.toInt(), d)?.let { found += m.range.first to it }
        }
        // A "date" far from when the SMS arrived is more likely a misread number.
        return found.sortedBy { it.first }.map { it.second }
            .firstOrNull { received == null || abs(it.toEpochDay() - received.toEpochDay()) <= 62 }
    }

    private fun month(name: String): Int? {
        val n = name.lowercase()
        if (n.length > 3 && months.none { n.startsWith(it) && fullMonth(it).startsWith(n) }) return null
        return months.indexOf(n.take(3)).takeIf { it >= 0 }?.plus(1)
    }

    // "sept" and "june" are fine; "marks" or "decide" aren't months.
    private fun fullMonth(abbr: String) = when (abbr) {
        "jan" -> "january"; "feb" -> "february"; "mar" -> "march"; "apr" -> "april"; "may" -> "may"
        "jun" -> "june"; "jul" -> "july"; "aug" -> "august"; "sep" -> "september"; "oct" -> "october"
        "nov" -> "november"; else -> "december"
    }

    private fun date(year: String, month: Int?, day: String): LocalDate? {
        if (month == null) return null
        val y = year.toInt().let { if (year.length == 2) 2000 + it else it }
        if (y !in 2000..2099) return null
        return runCatching { LocalDate.of(y, month, day.toInt()) }.getOrNull()
    }
}
