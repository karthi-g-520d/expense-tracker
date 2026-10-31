package com.karthi.expense.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karthi.expense.core.CategoryParts
import com.karthi.expense.core.ParsedTxn
import java.math.BigDecimal

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
    /** Your part split across categories, e.g. "Food:300;Movie:500"; null when it's one category. See [com.karthi.expense.core.CategoryParts]. */
    val parts: String? = null,
) {
    /** The rows this transaction becomes in the sheet: one, or one per category part (plus other people's shares). */
    fun sheetRows(): List<PendingTxn> =
        CategoryParts.rows(BigDecimal(amount), category, parts, shares, Categories.NOT_MINE).map { r ->
            if (r.suffix.isEmpty()) this
            else copy(
                id = id + r.suffix,
                amount = r.amount.toPlainString(),
                category = r.category,
                shares = r.shares,
                parts = null,
                // The bank balance belongs to the transaction once, not to each part.
                balance = if (r.suffix == "#1") balance else null,
            )
        }

    companion object {
        /** Sheet row ids of a split transaction carry a "#..." suffix; this gives back the transaction's id. */
        fun baseId(rowId: String) = rowId.substringBefore('#')

        /** Added by hand (cash, a missed SMS), not read from an SMS. */
        fun isManual(id: String) = id.startsWith("MANUAL-")

        fun manual(type: String, amount: BigDecimal, counterparty: String?, date: java.time.LocalDate, bank: String, account: String): PendingTxn {
            val now = System.currentTimeMillis()
            return PendingTxn(
                id = "MANUAL-$now",
                bank = bank,
                type = type,
                amount = amount.stripTrailingZeros().toPlainString(),
                account = account,
                counterparty = counterparty?.trim()?.ifEmpty { null },
                date = date.toString(),
                reference = null,
                balance = null,
                smsTime = now,
            )
        }

        /** Shown where the bank name would be, and written to the sheet's bank column. */
        const val MANUAL_BANK = "Cash"

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
