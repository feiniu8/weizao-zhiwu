package com.weizao.movieapp.db

import org.json.JSONArray
import org.json.JSONObject

/** 直播源（订阅地址）持久化在 app_setting 里，避免改动数据库结构 */
object LiveStore {
    const val KEY = "live_sources"

    val DEFAULT: List<Pair<String, String>> = listOf(
        "央视·卫视（公共源）" to "https://iptv-org.github.io/iptv/countries/cn.m3u"
    )

    fun decode(s: String?): List<Pair<String, String>> {
        if (s.isNullOrBlank()) return DEFAULT
        val arr = runCatching { JSONArray(s) }.getOrNull() ?: return DEFAULT
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optString("name")
            val u = o.optString("url")
            if (u.startsWith("http")) out += (n.ifBlank { "直播源" } to u)
        }
        return out
    }

    fun encode(list: List<Pair<String, String>>): String {
        val arr = JSONArray()
        list.forEach { (n, u) -> arr.put(JSONObject().put("name", n).put("url", u)) }
        return arr.toString()
    }
}
