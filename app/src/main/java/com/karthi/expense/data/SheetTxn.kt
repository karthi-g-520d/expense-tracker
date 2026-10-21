package com.karthi.expense.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A row from the Google Sheet, cached on the phone for the dashboard. */
@Entity(tableName = "sheet_txn")
data class SheetTxn(
    @PrimaryKey val id: String,
    /** ISO date, e.g. "2026-10-20". */
    val date: String,
    val type: String,
    val amount: Double,
    val bank: String,
    val counterparty: String?,
    val category: String?,
    val reason: String?,
)

@Dao
abstract class SheetTxnDao {

    @Query("SELECT * FROM sheet_txn ORDER BY date DESC")
    abstract fun observeAll(): Flow<List<SheetTxn>>

    @Query("DELETE FROM sheet_txn")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insertAll(rows: List<SheetTxn>)

    /** The sheet is the source of truth, so the cache is replaced wholesale. */
    @Transaction
    open suspend fun replaceAll(rows: List<SheetTxn>) {
        clear()
        insertAll(rows)
    }
}
