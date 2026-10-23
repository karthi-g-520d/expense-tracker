package com.karthi.expense.core

/**
 * One of your own accounts or cards, as set in Settings: the bank and the digits its SMS show.
 * Empty [digits] means every account in that bank.
 */
data class MyAccount(val bank: Bank, val digits: String = "", val fromStart: Boolean = false) {
    /** "HDFC ••1234", "SBI 2010••", or just "Kotak" for any account. */
    val label: String
        get() = when {
            digits.isEmpty() -> bank.label
            fromStart -> "${bank.label} $digits••"
            else -> "${bank.label} ••$digits"
        }
}

object MyAccounts {

    /**
     * Whether a parsed SMS is about one of your accounts. With nothing set up yet, every bank SMS is
     * kept, so the app works right after install.
     */
    fun isMine(accounts: List<MyAccount>, txn: ParsedTxn): Boolean =
        accounts.isEmpty() || accounts.any { it.bank == txn.bank && matches(it, txn.account, txn.accountFirst) }

    private fun matches(a: MyAccount, last: String, first: String): Boolean {
        if (a.digits.isEmpty()) return true
        return if (a.fromStart) {
            first.isNotEmpty() && (first.startsWith(a.digits) || (first.length >= 2 && a.digits.startsWith(first)))
        } else {
            // Banks mask to different lengths: "1035" in Settings matches an SMS showing "XXXXX35" and vice versa.
            last.isNotEmpty() && (last.endsWith(a.digits) || (last.length >= 2 && a.digits.endsWith(last)))
        }
    }

    /** Same account, allowing for different masking ("35" and "1035"). Unknown digits never match. */
    fun sameDigits(a: String, b: String): Boolean =
        a.isNotEmpty() && b.isNotEmpty() && (a.endsWith(b) || b.endsWith(a))

    /**
     * Accounts found in transactions already on the phone, used once to fill in Settings after the
     * update that added it. Within a bank, "35" and "1035" are one account; the longer is kept.
     */
    fun fromHistory(seen: List<Pair<String, String>>): List<MyAccount> {
        val result = mutableListOf<MyAccount>()
        for ((bankName, digits) in seen.sortedByDescending { it.second.length }) {
            val bank = Bank.entries.firstOrNull { it.name == bankName } ?: continue
            if (digits.isEmpty()) continue
            if (result.any { it.bank == bank && sameDigits(it.digits, digits) }) continue
            result += MyAccount(bank, digits)
        }
        return result.sortedWith(compareBy({ it.bank.label }, { it.digits }))
    }
}
