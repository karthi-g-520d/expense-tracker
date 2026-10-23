package com.karthi.expense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Debit and credit SMS in the formats of banks across India (numbers and names changed). */
class BankSmsTest {

    private val today = LocalDate.of(2026, 10, 23)

    private data class Case(
        val sender: String?,
        val sms: String,
        val bank: Bank,
        val type: TxnType,
        val amount: String,
        val account: String,
        val date: LocalDate? = null,
        val reference: String? = null,
        val counterparty: String? = null,
        val balance: String? = null,
    )

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private val cases = listOf(
        // SBI
        Case(
            "JD-SBIUPI-S",
            "Dear UPI user A/C X4821 debited by 250.0 on date 21Oct26 trf to RAVI KUMAR Refno 629412345678. If not u? call 1800111109. -SBI",
            Bank.SBI, TxnType.DEBIT, "250.0", "4821", d(2026, 10, 21), "629412345678", "RAVI KUMAR",
        ),
        Case(
            "AD-SBIUPI-S",
            "Dear SBI UPI User, ur A/cX4821 credited by Rs500 on 20Oct26 by (Ref no 629398765432)",
            Bank.SBI, TxnType.CREDIT, "500", "4821", d(2026, 10, 20), "629398765432",
        ),
        Case(
            "VM-SBIINB-T",
            "Dear Customer, transaction number 1234 for Rs.383.00 by SBI Debit Card 7012 done at DMART on 13Oct26 at 21:38:26. Your updated available balance is Rs.12,450.10. If not done by you, forward this SMS to 7400165218",
            Bank.SBI, TxnType.DEBIT, "383.00", "7012", d(2026, 10, 13), balance = "12450.10",
        ),
        Case(
            "VM-SBICRD-S",
            "Rs.90.00 spent on your SBI Credit Card ending 5667 at SUPREMEGOURMET on 13/10/26. Trxn. not done by you? Report at https://sbicard.com/Dispute",
            Bank.SBI, TxnType.DEBIT, "90.00", "5667", d(2026, 10, 13), counterparty = "SUPREMEGOURMET",
        ),
        // Styled letters, as the SBI Card app sends them.
        Case(
            "VM-SBICRD-S",
            "Rs.90.00 𝗌𝗉𝖾𝗇𝗍 𝗈𝗇 𝗒𝗈𝗎𝗋 𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽 𝖾𝗇𝖽𝗂𝗇𝗀 5667 at SUPREMEGOURMET on 13/10/26.",
            Bank.SBI, TxnType.DEBIT, "90.00", "5667", d(2026, 10, 13),
        ),
        // HDFC
        Case(
            "VM-HDFCBK-S",
            "Sent Rs.1500.00 From HDFC Bank A/C *1234 To SWIGGY LIMITED On 22/10/26 Ref 629512345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
            Bank.HDFC, TxnType.DEBIT, "1500.00", "1234", d(2026, 10, 22), "629512345678", "SWIGGY LIMITED",
        ),
        Case(
            "AX-HDFCBK-S",
            "Update! INR 6,657.00 deposited in HDFC Bank A/c XX1234 on 30-SEP-26 for NEFT Cr-CITI0100000-ACME TECH.Avl bal INR 12,28,554.72. Cheque deposits in A/C are subject to clearing",
            Bank.HDFC, TxnType.CREDIT, "6657.00", "1234", d(2026, 9, 30), balance = "1228554.72",
        ),
        Case(
            "JM-HDFCBK-S",
            "Credit Alert! Rs.2000.00 credited to HDFC Bank A/c XX1234 on 21-10-26 from VPA ravi@okaxis (UPI 629612345678)",
            Bank.HDFC, TxnType.CREDIT, "2000.00", "1234", d(2026, 10, 21), "629612345678", "ravi@okaxis",
        ),
        Case(
            "VK-HDFCBK-S",
            "Spent Rs.849 From HDFC Bank Card x9876 At AMAZON On 2026-10-19:12:01:44 Bal Rs.20000",
            Bank.HDFC, TxnType.DEBIT, "849", "9876", d(2026, 10, 19), counterparty = "AMAZON",
        ),
        // Axis
        Case(
            "AD-AXISBK-S",
            "INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-10-2026 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank",
            Bank.AXIS, TxnType.DEBIT, "2000.00", "589034", d(2026, 10, 4), balance = "98919.81",
        ),
        Case(
            "VM-AXISBK-S",
            "INR 500.00 credited to A/c no. XX3456 on 18-10-26 at 10:00:00 IST. Info- UPI/P2A/629712345678/ANITHA S. Avl Bal- INR 1500.00 - Axis Bank",
            Bank.AXIS, TxnType.CREDIT, "500.00", "3456", d(2026, 10, 18), "629712345678", "ANITHA S",
        ),
        // Kotak
        Case(
            "AX-KOTAKB-S",
            "Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr2880@paytm on 14-10-26.UPI Ref 629812345678. Not you, https://kotak.com/KBANKT/Fraud",
            Bank.KOTAK, TxnType.DEBIT, "15.00", "1234", d(2026, 10, 14), "629812345678", "paytmqr2880@paytm",
        ),
        Case(
            "AX-KOTAKB-S",
            "Received Rs.250.00 in your Kotak Bank AC X3333 from john.doe@oksbi on 14-10-26.UPI Ref 629898765432. Not you, https://kotak.com/KBANKT/Fraud",
            Bank.KOTAK, TxnType.CREDIT, "250.00", "3333", d(2026, 10, 14), "629898765432", "john.doe@oksbi",
        ),
        Case(
            "JK-KOTAKD-S",
            "Sent Rs.51.00 from XXXXXX9722 to DINESH R on 20/10/2026. UPI ref no. 648604626824. Not you? Tap https://kotk.in/KOTAKD/Eg9Cu0 to report -Kotak",
            Bank.KOTAK, TxnType.DEBIT, "51.00", "9722", d(2026, 10, 20), "648604626824", "DINESH R",
        ),
        // PNB
        Case(
            "VM-PNBSMS-S",
            "A/c X0042 debited INR 125.00 Dt 01-10-26 12:00:00 to Swiggy thru UPI:629912345678..Bal INR 9000.00-PNB",
            Bank.PNB, TxnType.DEBIT, "125.00", "0042", d(2026, 10, 1), "629912345678", "Swiggy", "9000.00",
        ),
        Case(
            "VM-PNBSMS-S",
            "A/c X0042 credited INR 125.00 From Example Shop/UPI:629987654321. Bal INR 9125.00-PNB",
            Bank.PNB, TxnType.CREDIT, "125.00", "0042", today, "629987654321",
        ),
        // Bank of Baroda: the payee's "Cr." comes right after your account.
        Case(
            "AD-BOBTXN-S",
            "Rs.500.00 Dr. from A/C XXXXXX1234 and Cr. to ravi@ybl. Ref:630012345678. AvlBal:Rs1000.00(2026:10:22 10:00:00). Not you? Call 18005700-BOB",
            Bank.BOB, TxnType.DEBIT, "500.00", "1234", d(2026, 10, 22), "630012345678", balance = "1000.00",
        ),
        Case(
            "BP-BOBSMS-S",
            "Rs.2500.00 credited to A/c XX456789 via IMPS/518233445566 by JOHN DOE. AvlBal:Rs25000.00 -Bank of Baroda",
            Bank.BOB, TxnType.CREDIT, "2500.00", "456789", today, "518233445566", "JOHN DOE",
        ),
        // Canara: "DEBITED to your account" is still a debit.
        Case(
            "VM-CANBNK-S",
            "An amount of INR 500.00 has been DEBITED to your account XXX123 on 23/10/2026 towards UPI/DR/630112345678/RAVI. Total Avail.bal INR 1,000.00 - Canara Bank",
            Bank.CANARA, TxnType.DEBIT, "500.00", "123", d(2026, 10, 23), "630112345678", "RAVI",
        ),
        Case(
            "VM-CANBNK-S",
            "An amount of INR 13,30,614.75 has been credited to XXXX6785 on 02/10/2026 towards RTGS by Sender AXIS MUTUAL FUND, UTR UTIBR72025120200011461, Total Avail. Bal INR 2679815.88- Canara Bank",
            Bank.CANARA, TxnType.CREDIT, "1330614.75", "6785", d(2026, 10, 2),
        ),
        // Union Bank
        Case(
            "AD-UNIONB-S",
            "A/c *1234 Debited for Rs:300.00 on 20-10-2026 11:10:12 by Mob Bk ref no 630212345678 Avl Bal Rs:5000.00.If not you, Call 1800222243 -Union Bank of India",
            Bank.UNION, TxnType.DEBIT, "300.00", "1234", d(2026, 10, 20), "630212345678",
        ),
        // Indian Bank and Bank of India
        Case(
            "BZ-INDBNK-S",
            "Rs.2.00 credited to a/c *8175 on 07/10/2026 by a/c linked to VPA poweraccess.paytm3@axisbank (UPI Ref no 981408452805).Indian Bank",
            Bank.INDIAN, TxnType.CREDIT, "2.00", "8175", d(2026, 10, 7), "981408452805", "poweraccess.paytm3@axisbank",
        ),
        Case(
            null,
            "Rs.200.00 debited A/cXX5468 and credited to SAI MISAL via UPI Ref No 315439383341 on 23Oct26. Call 18001031906, if not done by you. -BOI",
            Bank.BOI, TxnType.DEBIT, "200.00", "5468", d(2026, 10, 23), "315439383341",
        ),
        Case(
            null,
            "BOI - Rs 15,000.00 Credited in your Ac XX5468 on 04-10-2026 By NEFTINWARD HDFCH00778553836/HDFC MUTUAL F .Avl Bal 18679.91",
            Bank.BOI, TxnType.CREDIT, "15000.00", "5468", d(2026, 10, 4), balance = "18679.91",
        ),
        // Central Bank, UCO, Punjab & Sind
        Case(
            "VM-CENTBK-S",
            "Rs. 5,000.00 credited to your A/c xxxxxx1234 on 03/10/2026 through NEFT By.NEXTBILLION TECHNOLOGY-CBoI",
            Bank.CENTRAL, TxnType.CREDIT, "5000.00", "1234", d(2026, 10, 3),
        ),
        Case(
            "AD-UCOBNK-S",
            "A/c XX1111 Credited with Rs.2,000.00 on 21-09-2026 by UCO-UPI.Avl Bal Rs.11111.11. Report Dispute https://spgrs.ucoonline.in -UCO Bank",
            Bank.UCO, TxnType.CREDIT, "2000.00", "1111", d(2026, 9, 21), balance = "11111.11",
        ),
        // IDFC, IndusInd, Yes
        Case(
            "VM-IDFCFB-S",
            "Your A/c XX4614 debited by Rs. 1,172.06 on 15/10/26; REDBUS credited. RRN 060649915527. Available balance Rs. 9,134.15. Team IDFC FIRST Bank",
            Bank.IDFC, TxnType.DEBIT, "1172.06", "4614", d(2026, 10, 15), "060649915527",
        ),
        Case(
            "VM-INDUSB-S",
            "A/C *XX1234 credited by Rs 25000.00 from xxxx.yyyy@ybl. RRN:510048508040. Avl Bal:105502.12. Not you? Call 18602677777 - IndusInd bank.",
            Bank.INDUSIND, TxnType.CREDIT, "25000.00", "1234", today, "510048508040", "xxxx.yyyy@ybl", "105502.12",
        ),
        Case(
            "VM-INDUSB-S",
            "Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/123456789012. Call 18602677777 to report issue-IndusInd Bank.",
            Bank.INDUSIND, TxnType.DEBIT, "6440", "1234", today, "123456789012",
        ),
        Case(
            "AD-YESBNK-S",
            "INR 404.36 spent on YES BANK Card X3349 @UPI_C N S FUEL PORT 22-10-2026 06:17:25 pm. Avl Lmt INR 211,476.24. SMS BLKCC 3349 to 9840909000 if not you",
            Bank.YES, TxnType.DEBIT, "404.36", "3349", d(2026, 10, 22),
        ),
        // Federal: "Ravi has received Rs from your A/c" is your debit.
        Case(
            "AD-FEDBNK-S",
            "Jerry Joseph has received Rs 6000.000 from your A/c XX3343 via NEFT on 22-10-2026 22:04:04. Ref no. FDRLM4175007432 - Federal Bank",
            Bank.FEDERAL, TxnType.DEBIT, "6000.000", "3343", d(2026, 10, 22), "FDRLM4175007432",
        ),
        Case(
            "AD-FEDBNK-S",
            "Rs 15000.00 credited to your A/c XX7890 via IMPS on 19OCT2026 16:20:15 IMPS Ref no 543210987654 Bal:Rs 42500.80 -Federal Bank",
            Bank.FEDERAL, TxnType.CREDIT, "15000.00", "7890", d(2026, 10, 19), "543210987654", balance = "42500.80",
        ),
        // South Indian Bank, TMB, Bandhan, AU
        Case(
            "AD-SIBSMS-S",
            "UPI debit:Rs.599.00 A/c X7477, 16-10-26 16:25:29 RRN: 565526068910 Bal:Rs.12345.89 Block A/c? Call18004251809 -South Indian Bank",
            Bank.SIB, TxnType.DEBIT, "599.00", "7477", d(2026, 10, 16), "565526068910", balance = "12345.89",
        ),
        Case(
            "VM-TMBANK-S",
            "Your A/c No.XXXX1111 is debited with Rs.487.21 on 09-10-2026 06:19 PM and UTIB A/c linked to testshop.offline@axl is credited (UPI Ref No.628200000001).Current AVBL bal is Rs.1234.73 - TMB",
            Bank.TMB, TxnType.DEBIT, "487.21", "1111", d(2026, 10, 9), "628200000001",
        ),
        Case(
            "AD-BANDHN-S",
            "INR 25,000.00 deposited to A/c XXXXXXXXXX1234 towards UPI/CR/C224513287910/JOHN DOE/u on 03-OCT-2026 . Clear Bal is INR 30,123.00 . Bandhan Bank.",
            Bank.BANDHAN, TxnType.CREDIT, "25000.00", "1234", d(2026, 10, 3), counterparty = "JOHN DOE", balance = "30123.00",
        ),
        Case(
            "VM-AUBANK-S",
            "Debited INR 500.00 from A/c 1234567890 on 15-OCT-2026. Bal INR 10000.00. -AU Bank",
            Bank.AU, TxnType.DEBIT, "500.00", "1234567890", d(2026, 10, 15), balance = "10000.00",
        ),
        // Payments banks
        Case(
            "AD-PAYTMB-S",
            "Your Paytm Payments Bank a/c **6343 is debited for Rs. 1,882.00 towards Chaayos. txn id 737961994.",
            Bank.PAYTM, TxnType.DEBIT, "1882.00", "6343", today, "737961994", "Chaayos",
        ),
        // Unknown bank from a business sender still counts.
        Case(
            "AD-XYZBNK-S",
            "Rs.750.00 debited from your A/c XX9911 on 22-10-26 towards UPI/DR/630312345678/ZOMATO. Avl Bal Rs.100.00",
            Bank.OTHER, TxnType.DEBIT, "750.00", "9911", d(2026, 10, 22), "630312345678", "ZOMATO",
        ),
        // ICICI formats the exact patterns don't cover.
        Case(
            "VM-ICICIT-S",
            "INR 500.00 spent using ICICI Bank Card XX5678 on 06-Oct-26 on Swiggy. Avl Limit: INR 1,50,000.00.",
            Bank.ICICI, TxnType.DEBIT, "500.00", "5678", d(2026, 10, 6),
        ),
        Case(
            "VM-ICICIB-S",
            "ICICI Bank Account XX566 credited:Rs. 18,832.00 on 28-Sep-26. Info INF*000169831922*SALARY. Available Balance is Rs. 28,076.14.",
            Bank.ICICI, TxnType.CREDIT, "18832.00", "566", d(2026, 9, 28), balance = "28076.14",
        ),
    )

    @Test
    fun readsEachBanksFormat() {
        // Checks every case before failing, so one run lists all the formats that broke.
        val problems = cases.mapNotNull { c ->
            val t = SmsParser.parse(c.sms, c.sender, today) ?: return@mapNotNull "not parsed: ${c.sms}"
            val wrong = listOfNotNull(
                "bank ${t.bank}".takeIf { t.bank != c.bank },
                "type ${t.type}".takeIf { t.type != c.type },
                "amount ${t.amount}".takeIf { t.amount.compareTo(BigDecimal(c.amount)) != 0 },
                "account ${t.account}".takeIf { t.account != c.account },
                "date ${t.date}".takeIf { t.date != (c.date ?: today) },
                "reference ${t.reference}".takeIf { c.reference != null && t.reference != c.reference },
                "counterparty ${t.counterparty}".takeIf { c.counterparty != null && t.counterparty != c.counterparty },
                "balance ${t.balance}".takeIf { c.balance != null && t.balance?.compareTo(BigDecimal(c.balance)) != 0 },
            )
            if (wrong.isEmpty()) null else "${wrong.joinToString()} for: ${c.sms}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun ignoresMessagesThatAreNotADoneTransaction() {
        val notTxns = listOf(
            "123456 is your OTP for txn of Rs 500.00 on HDFC Bank Card XX1234. Do not share.",
            "Your account will be debited with Rs 649.00 on 03-Nov-26 towards Netflix for AutoPay, RRN 421723106963-ICICI Bank.",
            "Payment request of INR 500.00 from merchant@upi on your A/c XX1234. Ignore if already paid.",
            "Your Yes Bank Credit Card XX3349 payment of INR 10,000 is due by 25-10-2026",
            "Dear Customer, you are eligible for a pre-approved loan of Rs.31800.00. Amount will be credited instantly. Apply now!",
            "Your Credit Card statement is ready. Min due: 1,559.00. Total due: INR 96379.00. Pay by 10-Nov.",
            "Txn of Rs 250.00 from A/c XX1234 failed. Any amount debited will be refunded in 3 days.",
            "USD 11.80 spent using ICICI Bank Card XX7004 on 03-Oct-26 on JetBrains. Avl Limit: INR 17,95,899.53.",
            "Get 30% off on your next SWIGGY order! Use code SWI30. Valid till midnight.",
            "Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/26 04:10 AM. - IndusInd Bank",
        )
        for (sms in notTxns) assertNull("should be ignored: $sms", SmsParser.parse(sms, "VM-HDFCBK-S", today))
    }

    @Test
    fun ignoresBankLookingTextFromAPhoneNumber() {
        assertNull(SmsParser.parse("Rs.500 debited from A/c X1234 on 13Oct26. Avl Bal Rs.1000", "+919876543210", today))
    }

    @Test
    fun noDateInTextUsesTheDayItArrived() {
        val t = SmsParser.parse("Rs.500 debited from A/c X1234. Avl Bal Rs.1000 -SBI", null, today)!!
        assertEquals(today, t.date)
    }

    @Test
    fun bankFromSenderOrSignature() {
        assertEquals(Bank.HDFC, Bank.fromSender("VM-HDFCBK-S"))
        assertEquals(Bank.SBI, Bank.fromSender("JD-SBIUPI"))
        assertEquals(Bank.IOB, Bank.fromSender("AD-IOBCHN-S"))
        assertNull(Bank.fromSender("+919876543210"))
        assertEquals(Bank.CENTRAL, Bank.fromBody("Rs 5 credited to your A/c x1234 - Central Bank of India"))
        assertEquals(Bank.BOI, Bank.fromBody("Rs 5 credited to A/c XX12 by NEFT from HDFC MUTUAL. -Bank of India"))
        assertEquals(Bank.IOB, Bank.fromBody("Rs 5 debited -Indian Overseas Bank"))
        assertEquals(Bank.INDIAN, Bank.fromBody("Rs 5 debited.Indian Bank"))
    }

    @Test
    fun firstDigitsAreKeptWhenTheSmsShowsThem() {
        val t = SmsParser.parse(
            "Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/123456789012.",
            "VM-INDUSB-S", today,
        )!!
        assertEquals("20", t.accountFirst)
        assertEquals("1234", t.account)
    }

    @Test
    fun myAccountsFilter() {
        val hdfc = SmsParser.parse("Sent Rs.10.00 From HDFC Bank A/C *1234 To X On 22/10/26 Ref 629512345678", "VM-HDFCBK-S", today)!!
        val indus = SmsParser.parse("Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/123456789012.", "VM-INDUSB-S", today)!!

        assertTrue(MyAccounts.isMine(emptyList(), hdfc))
        assertTrue(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC, "1234")), hdfc))
        assertTrue(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC, "34")), hdfc))
        assertTrue(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC, "001234")), hdfc))
        assertTrue(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC)), hdfc))
        assertFalse(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC, "9999")), hdfc))
        assertFalse(MyAccounts.isMine(listOf(MyAccount(Bank.SBI, "1234")), hdfc))

        assertTrue(MyAccounts.isMine(listOf(MyAccount(Bank.INDUSIND, "20", fromStart = true)), indus))
        assertFalse(MyAccounts.isMine(listOf(MyAccount(Bank.INDUSIND, "30", fromStart = true)), indus))
        // HDFC's SMS doesn't show first digits, so a first-digits entry can't claim it.
        assertFalse(MyAccounts.isMine(listOf(MyAccount(Bank.HDFC, "50", fromStart = true)), hdfc))
    }

    @Test
    fun accountsFromHistoryMergeDifferentMasking() {
        val accounts = MyAccounts.fromHistory(listOf("IOB" to "35", "IOB" to "1035", "ICICI" to "597", "ICICI" to "597"))
        assertEquals(listOf(MyAccount(Bank.ICICI, "597"), MyAccount(Bank.IOB, "1035")), accounts)
    }
}
