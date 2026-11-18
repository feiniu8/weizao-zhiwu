package com.weizao.movieapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Source::class, PlayHistory::class, Favorite::class, AppSetting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(context: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "weizao.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
