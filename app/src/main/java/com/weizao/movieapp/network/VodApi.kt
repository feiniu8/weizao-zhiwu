package com.weizao.movieapp.network

import kotlinx.serialization.Serializable
import org.json.JSONObject
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** 苹果CMS 请求封装 */
object VodApi {

    private fun build(api: String, vararg params: Pair<String, String>): String {
        val sb = StringBuilder(api)
        sb.append(if (api.contains("?")) "&" else "?")
        params.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append("&")
            sb.append(k).append("=").append(URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }

    fun list(
        api: String,
        typeId: String = "",
        page: Int = 1,
        area: String = "",
        year: String = "",
        by: String = ""
    ): List<Vod> {
        val params = mutableListOf("ac" to "videolist", "t" to typeId, "pg" to page.toString())
        if (area.isNotBlank()) params += "area" to area
        if (year.isNotBlank()) params += "year" to year
        if (by.isNotBlank()) params += "by" to by
        val body = Http.getSafe(build(api, *params.toTypedArray())) ?: return emptyList()
        return VodParser.parse(body)
    }

    fun search(api: String, keyword: String): List<Vod> {
        val body = Http.getSafe(build(api, "ac" to "videolist", "wd" to keyword, "pg" to "1"))
            ?: return emptyList()
        return VodParser.parse(body)
    }

    fun detail(api: String, vodId: String): Vod? {
        val body = Http.getSafe(build(api, "ac" to "videolist", "ids" to vodId)) ?: return null
        return VodParser.parse(body).firstOrNull()
    }

    /** 取分类列表（ac=list 返回的 class） */
    fun classes(api: String): List<Pair<String, String>> {
        val body = Http.getSafe(build(api, "ac" to "list")) ?: return emptyList()
        return try {
            val arr = JSONObject(body).optJSONArray("class") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = c.optString("type_id").trim()
                val name = c.optString("type_name").trim()
                if (id.isBlank() || name.isBlank()) null else (name to id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 测试数据源：返回条目数，失败返回 -1 */
    fun test(api: String): Int {
        val body = Http.getSafe(build(api, "ac" to "videolist", "wd" to "测试", "pg" to "1"))
            ?: return -1
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<VodResponse>(body)
                .list.size
        } catch (e: Exception) {
            -1
        }
    }
}
