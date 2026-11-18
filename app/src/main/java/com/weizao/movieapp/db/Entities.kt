package com.weizao.movieapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 数据源表 */
@Entity(tableName = "source")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val api: String = "",
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** 播放历史 */
@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vodId: String = "",
    val vodName: String = "",
    val pic: String = "",
    val playUrl: String = "",
    val sourceApi: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val finished: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** 收藏 */
@Entity(tableName = "favorite")
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vodId: String = "",
    val vodName: String = "",
    val pic: String = "",
    val sourceApi: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** 全局设置 */
@Entity(tableName = "app_setting")
data class AppSetting(
    @PrimaryKey val key: String = "",
    val value: String = ""
)
