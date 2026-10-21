package com.karthi.expense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PendingTxn::class, SheetTxn::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingTxnDao(): PendingTxnDao
    abstract fun sheetTxnDao(): SheetTxnDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // v2 adds the dashboard cache; keeps any unsynced transactions from v1.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sheet_txn` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amount` REAL NOT NULL, `bank` TEXT NOT NULL, " +
                        "`counterparty` TEXT, `category` TEXT, `reason` TEXT, PRIMARY KEY(`id`))"
                )
            }
        }

        // v3 adds other people's shares to both tables.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_txn` ADD COLUMN `shares` TEXT")
                db.execSQL("ALTER TABLE `sheet_txn` ADD COLUMN `shares` TEXT")
            }
        }

        // v4 adds the split across categories for transactions not yet synced.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_txn` ADD COLUMN `parts` TEXT")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
