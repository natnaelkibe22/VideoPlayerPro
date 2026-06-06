package com.natkibe.videoplayerpro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoItemEntity::class, VideoProgressEntity::class, FavoriteEntity::class, PinnedFolderEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_videos` (
                        `videoUri` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoUri`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pinned_folders` (
                        `folderName` TEXT NOT NULL,
                        `pinnedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`folderName`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "videoplayer_pro.db"
            )
                .addMigrations(MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
