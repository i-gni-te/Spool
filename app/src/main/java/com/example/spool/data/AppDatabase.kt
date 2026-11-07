package com.example.spool.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaItemEntity::class, KindOverride::class, PlayStat::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        /** Adds the table that remembers manual short/video choices. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kind_overrides` " +
                            "(`uri` TEXT NOT NULL, `kind` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        /** Adds the table that counts plays. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_stats` " +
                            "(`uri` TEXT NOT NULL, `plays` INTEGER NOT NULL, " +
                            "`lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spool.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}