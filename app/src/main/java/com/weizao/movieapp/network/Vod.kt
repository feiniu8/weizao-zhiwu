package com.weizao.movieapp.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 苹果CMS 标准字段 */
@Serializable
data class Vod(
    @SerialName("vod_id") val vodId: Long = 0,
    @SerialName("vod_name") val name: String = "",
    @SerialName("vod_pic") val pic: String = "",
    @SerialName("vod_remarks") val remarks: String = "",
    @SerialName("vod_year") val year: String = "",
    @SerialName("vod_area") val area: String = "",
    @SerialName("vod_type") val type: String = "",
    @SerialName("vod_actor") val actor: String = "",
    @SerialName("vod_director") val director: String = "",
    @SerialName("vod_content") val content: String = "",
    @SerialName("vod_play_from") val playFrom: String = "",
    @SerialName("vod_play_url") val playUrl: String = "",
    @SerialName("vod_score") val score: String = ""
)

@Serializable
data class VodResponse(
    val code: Int = 0,
    val msg: String = "",
    val list: List<Vod> = emptyList()
)

@Serializable
data class Road(val name: String, val episodes: List<Episode>)

@Serializable
data class Episode(val name: String, val url: String)

object VodParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 严格解析苹果CMS返回，字段缺失不崩溃 */
    fun parse(body: String): List<Vod> = try {
        json.decodeFromString<VodResponse>(body).list
    } catch (e: Exception) {
        emptyList()
    }

    /** 线路分隔 $$$ ，集名与链接分隔 $ */
    fun roads(vod: Vod): List<Road> {
        if (vod.playUrl.isBlank()) return emptyList()
        val roadNames = vod.playFrom.split("$$$")
        return vod.playUrl.split("$$$").mapIndexed { index, block ->
            val eps = block.split("#").filter { it.isNotBlank() }.mapIndexed { i, s ->
                val p = s.indexOf('$')
                if (p > 0) Episode(s.substring(0, p).trim(), s.substring(p + 1).trim())
                else Episode("第${i + 1}集", s.trim())
            }.filter { it.url.isNotBlank() }
            val name = roadNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "线路${index + 1}"
            Road(name, eps)
        }.filter { it.episodes.isNotEmpty() }
    }
}
