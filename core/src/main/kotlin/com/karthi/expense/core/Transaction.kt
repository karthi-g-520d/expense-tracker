package com.karthi.expense.core

import java.math.BigDecimal
import java.time.LocalDate

enum class TxnType { DEBIT, CREDIT }

/** A debit or credit pulled out of a bank SMS, before the user tags it. */
data class ParsedTxn(
    val bank: Bank,
    val type: TxnType,
    val amount: BigDecimal,
    /** Last digits of the user's own account (or card), e.g. "597"; empty when the SMS doesn't show them. */
    val account: String,
    /** Payee for a debit, sender for a credit. */
    val counterparty: String?,
    val date: LocalDate,
    /** UPI / IMPS / bank reference number, used to avoid duplicates. */
    val reference: String?,
    /** Available balance after the transaction, when the SMS includes it. */
    val balance: BigDecimal?,
    /** Leading digits, for SMS that show the start of the number ("20XXXXX1234" -> "20"); usually empty. */
    val accountFirst: String = "",
) {
    /** Stable id so the same SMS is never stored or synced twice. */
    val id: String
        get() = "$bank-$type-${reference ?: "$account-$date-$amount"}"
}
