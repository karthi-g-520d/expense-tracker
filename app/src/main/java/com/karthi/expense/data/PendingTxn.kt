package com.karthi.expense.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karthi.expense.core.ParsedTxn

/**
 * A transaction read from SMS and kept on the phone until it is synced to the sheet.
 * Synced rows are kept (with [syncedAt] set) so the same SMS is never imported twice.
 */
@Entity(tableName = "pending_txn")
data class PendingTxn(
    @PrimaryKey val id: String,
    val bank: String,
    val type: String,
    /** Decimal string, e.g. "1086.12", to avoid floating point rounding. */
    val amount: String,
    val account: String,
    val counterparty: String?,
    /** ISO date, e.g. "2026-10-20". */
    val date: String,
    val reference: String?,
    val balance: String?,
    /** When the SMS arrived, in epoch millis. */
    val smsTime: Long,
    val category: String? = null,
    val reason: String? = null,
    val syncedAt: Long? = null,
    /** Other people's parts of this transaction, e.g. "Arun:400;Bala:400". See [com.karthi.expense.core.Shares]. */
    val shares: String? = null,
) {
    companion object {
        fun from(t: ParsedTxn, smsTime: Long) = PendingTxn(
            id = t.id,
            bank = t.bank.name,
            type = t.type.name,
            amount = t.amount.toPlainString(),
            account = t.account,
            counterparty = t.counterparty,
            date = t.date.toString(),
            reference = t.reference,
            balance = t.balance?.toPlainString(),
            smsTime = smsTime,
        )
    }
}
