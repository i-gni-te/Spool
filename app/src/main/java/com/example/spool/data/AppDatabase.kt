package com.example.spool.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        KindOverride::class,
        PlayStat::class,
        WatchPosition::class,
        SubtitleFile::class,
        Favorite::class,
        LyricsEntry::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kind_overrides` " +
                            "(`uri` TEXT NOT NULL, `kind` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_stats` " +
                            "(`uri` TEXT NOT NULL, `plays` INTEGER NOT NULL, " +
                            "`lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_positions` " +
                            "(`uri` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, " +
                            "`durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`uri`))"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_files` " +
                            "(`videoUri` TEXT NOT NULL, `subtitleUri` TEXT NOT NULL, " +
                            "`mimeType` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                            "PRIMARY KEY(`videoUri`))"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` " +
                            "(`uri` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        /** Adds the table that holds the lyrics you added. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` " +
                            "(`uri` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spool.db")
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                )
                .fallbackToDestructiveMigration(true)
                .build()
    }
}