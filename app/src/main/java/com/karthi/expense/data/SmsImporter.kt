package com.karthi.expense.data

import android.content.Context
import android.provider.Telephony
import com.karthi.expense.core.Bank
import com.karthi.expense.core.SelfTransfer
import com.karthi.expense.core.SmsParser
import com.karthi.expense.core.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads inbox SMS that arrived after the last import, keeps the ones [SmsParser]
 * recognises as ICICI / IOB debits or credits, and stores them in [PendingTxnDao].
 */
class SmsImporter(private val context: Context, private val dao: PendingTxnDao) {

    private val prefs = context.getSharedPreferences("sms_import", Context.MODE_PRIVATE)

    /** Returns how many new transactions were stored. Needs READ_SMS. */
    suspend fun importNew(): Int = withContext(Dispatchers.IO) {
        val since = prefs.getLong(KEY_LAST_SMS_TIME, -1L).takeIf { it >= 0 } ?: startOfThisMonth()
        var newest = since
        val found = mutableListOf<PendingTxn>()

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE, Telephony.Sms.BODY),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            while (c.moveToNext()) {
                val time = c.getLong(dateCol)
                newest = maxOf(newest, time)
                val body = c.getString(bodyCol) ?: continue
                SmsParser.parse(body)?.let { found += PendingTxn.from(it, time) }
            }
        }

        val added = dao.insertAll(found).count { it != -1L }
        // Saved only after the insert succeeds, so a crash mid-import re-reads the same SMS.
        prefs.edit().putLong(KEY_LAST_SMS_TIME, newest).apply()
        tagSelfTransfers()
        added
    }

    /**
     * Tags untagged pairs that look like money moved between your own IOB and ICICI accounts
     * (see [SelfTransfer]). You can still change the tag on either card.
     */
    private suspend fun tagSelfTransfers() {
        val untagged = dao.untaggedUnsynced().associateBy { it.id }
        val legs = untagged.values.map {
            SelfTransfer.Leg(it.id, Bank.valueOf(it.bank), TxnType.valueOf(it.type), BigDecimal(it.amount), it.reference, it.smsTime)
        }
        for ((debitId, creditId) in SelfTransfer.match(legs)) {
            val debit = untagged.getValue(debitId)
            val credit = untagged.getValue(creditId)
            dao.updateTag(debit.id, Categories.SELF_TRANSFER, "To ${credit.bank} ••${credit.account}", null)
            dao.updateTag(credit.id, Categories.SELF_TRANSFER, "From ${debit.bank} ••${debit.account}", null)
        }
    }

    /** Next import re-reads from the 1st of this month. Already-stored SMS are skipped by id. */
    fun rescanThisMonth() {
        prefs.edit().remove(KEY_LAST_SMS_TIME).apply()
    }

    // First run looks back to the 1st of the current month rather than the whole inbox.
    private fun startOfThisMonth(): Long =
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val KEY_LAST_SMS_TIME = "last_sms_time"
    }
}
