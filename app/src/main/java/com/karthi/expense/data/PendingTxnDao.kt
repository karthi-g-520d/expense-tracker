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

    @Query("UPDATE pending_txn SET category = :category, reason = :reason, shares = :shares WHERE id = :id")
    suspend fun updateTag(id: String, category: String?, reason: String?, shares: String?)

    @Query("SELECT * FROM pending_txn WHERE syncedAt IS NULL AND category IS NOT NULL")
    suspend fun taggedUnsynced(): List<PendingTxn>

    @Query("SELECT * FROM pending_txn WHERE syncedAt IS NOT NULL")
    suspend fun synced(): List<PendingTxn>

    @Query("UPDATE pending_txn SET syncedAt = :time WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, time: Long)
}
