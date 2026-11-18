package com.weizao.movieapp.network

import org.json.JSONObject

/** 一个待更新的文件 */
data class UpdateFile(
    val name: String,
    val size: Long,       // 字节
    val fileId: Long,     // 兼容字段（GitHub 版本为 0）
    val s3KeyFlag: String = "",
    val type: Int = 0,
    val version: String = ""
)

/**
 * 在线更新：读取 GitHub Releases（默认仓库 feiniu8/weizao-zhiwu），
 * 取最新 release 的 apk 资源做直链下载。
 * 设置里如果填的是 github.com/owner/repo 形式的地址，会优先用它。
 */
object Updater {

    const val REPO = "feiniu8/weizao-zhiwu"

    /** 兜底地址：GitHub Releases 页面 */
    const val DEFAULT_URL = "https://github.com/feiniu8/weizao-zhiwu/releases/latest"

    private const val API = "https://api.github.com/repos/"

    /** 从文件名里抠版本号，如 唯造智物-v1.7.5-评分推荐版.apk -> 1.7.5 */
    fun versionIn(name: String): String {
        val m = Regex("[vV]?([0-9]+[.][0-9]+(?:[.][0-9]+){0,2})").find(name) ?: return ""
        return m.groupValues[1]
    }

    /** 逐段比较：a>b 返回 1，相等 0，小于 -1 */
    fun compare(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    /** 从设置地址里解析 owner/repo，解析不到就用内置仓库 */
    private fun repoOf(url: String?): String {
        if (url.isNullOrBlank()) return REPO
        val m = Regex("github\\.com/([^/]+)/([^/?#]+)").find(url) ?: return REPO
        val owner = m.groupValues[1]
        val name = m.groupValues[2].removeSuffix(".git")
        return if (owner.isBlank() || name.isBlank()) REPO else "$owner/$name"
    }

    /** 有更高版本就返回它，否则 null */
    fun check(url: String, currentVersion: String): UpdateFile? = checkAny(url, currentVersion)?.second

    /**
     * 查最新 release。返回 Pair(apk 直链, 文件信息)，没有更高版本返回 null。
     */
    fun checkAny(prefUrl: String?, currentVersion: String): Pair<String, UpdateFile>? {
        val repo = repoOf(prefUrl)
        val body = Http.getSafe("$API$repo/releases/latest") ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = root.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (tag.isBlank() || compare(tag, currentVersion) <= 0) return null

        val assets = root.optJSONArray("assets") ?: return null
        var hit: JSONObject? = null
        for (i in 0 until assets.length()) {
            val o = assets.optJSONObject(i) ?: continue
            if (o.optString("name").lowercase().endsWith(".apk")) {
                hit = o
                break
            }
        }
        val a = hit ?: return null
        val link = a.optString("browser_download_url")
        if (link.isBlank()) return null
        return link to UpdateFile(
            name = a.optString("name").ifBlank { "weizao-$tag.apk" },
            size = a.optLong("size"),
            fileId = 0L,
            type = 0,
            version = tag
        )
    }

    /** 直链：GitHub 资源地址本身就是直链 */
    fun downloadLink(url: String, f: UpdateFile): String? =
        url.takeIf { it.startsWith("http") && (it.contains("/releases/download/") || it.endsWith(".apk", true)) }
}
