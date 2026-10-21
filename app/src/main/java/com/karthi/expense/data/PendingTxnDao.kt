package com.karthi.expense.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTxnDao {

    /** Returns the row id per item, or -1 when that transaction was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txns: List<PendingTxn>): List<Long>

    @Query("SELECT * FROM pending_txn WHERE syncedAt IS NULL ORDER BY smsTime DESC")
    fun observeUnsynced(): Flow<List<PendingTxn>>
}
