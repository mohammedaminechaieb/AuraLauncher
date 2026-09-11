package com.auralauncher.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GridItemEntity::class, FocusModeEntity::class, IconOverrideEntity::class,
        HostedWidgetEntity::class, FolderEntity::class, FolderMemberEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(LauncherConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auralauncher.db"
                ).fallbackToDestructiveMigration() // dev-phase DB, no real migration yet — see README
                .build().also { INSTANCE = it }
            }
    }
}
