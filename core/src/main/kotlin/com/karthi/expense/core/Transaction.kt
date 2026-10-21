package com.karthi.expense.core

import java.math.BigDecimal
import java.time.LocalDate

enum class Bank { ICICI, IOB }

enum class TxnType { DEBIT, CREDIT }

/** A debit or credit pulled out of a bank SMS, before the user tags it. */
data class ParsedTxn(
    val bank: Bank,
    val type: TxnType,
    val amount: BigDecimal,
    /** Last digits of the user's own account, e.g. "597". */
    val account: String,
    /** Payee for a debit, sender for a credit. */
    val counterparty: String?,
    val date: LocalDate,
    /** UPI / IMPS / bank reference number, used to avoid duplicates. */
    val reference: String?,
    /** Available balance after the transaction, when the SMS includes it. */
    val balance: BigDecimal?,
) {
    /** Stable id so the same SMS is never stored or synced twice. */
    val id: String
        get() = "$bank-$type-${reference ?: "$account-$date-$amount"}"
}
