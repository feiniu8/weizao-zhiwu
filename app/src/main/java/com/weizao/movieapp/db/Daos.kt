package com.weizao.movieapp.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM source ORDER BY createdAt ASC")
    fun flow(): Flow<List<Source>>

    @Query("SELECT * FROM source ORDER BY createdAt ASC")
    suspend fun all(): List<Source>

    @Query("SELECT * FROM source WHERE isSelected = 1 LIMIT 1")
    suspend fun selected(): Source?

    @Query("SELECT * FROM source WHERE isSelected = 1 LIMIT 1")
    fun flowSelected(): Flow<Source?>

    @Insert
    suspend fun insert(s: Source): Long

    @Update
    suspend fun update(s: Source)

    @Delete
    suspend fun delete(s: Source)

    @Query("UPDATE source SET isSelected = 0")
    suspend fun clearSelected()

    @Query("UPDATE source SET isSelected = 1 WHERE id = :id")
    suspend fun select(id: Long)

    @Query("SELECT COUNT(*) FROM source WHERE api = :api")
    suspend fun countByApi(api: String): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history ORDER BY updatedAt DESC")
    fun flow(): Flow<List<PlayHistory>>

    @Query("SELECT * FROM play_history WHERE vodId = :vodId LIMIT 1")
    suspend fun find(vodId: String): PlayHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(h: PlayHistory)

    @Delete
    suspend fun delete(h: PlayHistory)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY updatedAt DESC")
    fun flow(): Flow<List<Favorite>>

    @Query("SELECT * FROM favorite WHERE vodId = :vodId LIMIT 1")
    suspend fun find(vodId: String): Favorite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: Favorite)

    @Query("DELETE FROM favorite WHERE vodId = :vodId")
    suspend fun deleteByVod(vodId: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM app_setting WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: AppSetting)
}
