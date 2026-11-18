package com.weizao.movieapp.db

import org.json.JSONArray
import org.json.JSONObject

/** TVBox 订阅地址（只存地址，不保证能解析） */
object SubStore {
    const val KEY = "subscriptions"

    val DEFAULT: List<Pair<String, String>> = listOf(
        "示例订阅·高天流云（含 7 个可用接口）" to
            "https://raw.githubusercontent.com/gaotianliuyun/gao/master/0825.json"
    )

    fun decode(s: String?): List<Pair<String, String>> {
        if (s.isNullOrBlank()) return DEFAULT
        val arr = runCatching { JSONArray(s) }.getOrNull() ?: return DEFAULT
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optString("name")
            val u = o.optString("url")
            if (u.isNotBlank()) out += (n.ifBlank { "订阅" } to u)
        }
        return out
    }

    fun encode(list: List<Pair<String, String>>): String {
        val arr = JSONArray()
        list.forEach { (n, u) -> arr.put(JSONObject().put("name", n).put("url", u)) }
        return arr.toString()
    }
}
