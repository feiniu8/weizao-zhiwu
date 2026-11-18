package com.weizao.movieapp.network

import java.net.URI

/**
 * 流地址解析：把采集站给出的播放地址变成 ExoPlayer 能直连的地址。
 *
 * 采集站返回的地址有三种：
 * 1. 直接的 .m3u8 / .mp4 —— 直接播
 * 2. 没有后缀的 CDN 地址（如 /video/xxx/hash），本身其实是 HLS —— 探测内容类型后按 HLS 播
 * 3. 分享网页（如 /share/xxx、/play/xxx），页面里藏着带签名的 m3u8 —— 在 App 内解析出来再播
 *
 * 全程不使用第三方解析接口，只做一次轻量探测 / 页面正则，拿到直链就交给播放器。
 */
object StreamResolver {

    data class Stream(val url: String, val isHls: Boolean, val note: String = "")

    private val ABS = Regex("""(?:https?:)?//[^"'\\\s]+?\.m3u8[^"'\\\s]*""")
    private val REL = Regex("""/[^"'\\\s]+?\.m3u8[^"'\\\s]*""")

    fun resolve(rawUrl: String, referer: String? = null): Stream {
        val url = rawUrl.trim()
        if (url.isEmpty()) return Stream(url, false, "空地址")

        // 1) 先看内容类型 / 开头字节
        val (ct, head) = Http.probe(url, referer)
        val body = head?.trimStart().orEmpty()

        if (ct.contains("mpegurl") || ct.contains("m3u8")) return Stream(url, true, "HLS 直连")
        if (ct.startsWith("video/") && !ct.contains("mpegurl")) return Stream(url, false, "视频直连")
        if (body.startsWith("#EXTM3U")) return Stream(url, true, "HLS 直连")

        // 2) 网页：从页面里挖 m3u8
        if (body.startsWith("<") || ct.contains("html")) {
            val page = Http.text(url, referer) ?: head.orEmpty()
            val found = extract(page, url)
            if (found != null) return Stream(found, true, "页面解析直链")
            return Stream(url, false, "未找到直链")
        }

        // 3) 有内容但类型不明（很多 CDN 不返回 Content-Type）：按后缀兜底
        val noQuery = url.substringBefore('?').lowercase()
        val hls = noQuery.endsWith(".m3u8") || noQuery.endsWith(".m3u")
        return Stream(url, hls, if (hls) "按后缀判定 HLS" else "未知类型直连")
    }

    /** 从网页源码里抽取第一个 m3u8 地址，并补全成绝对地址 */
    fun extract(page: String, pageUrl: String): String? {
        val text = page.replace("\\/", "/")
        val abs = ABS.find(text)?.value
        val rel = REL.find(text)?.value
        val picked = abs ?: rel ?: return null
        return absolute(pageUrl, picked)
    }

    private fun absolute(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return try {
            val b = URI(base)
            val scheme = b.scheme ?: "https"
            val host = b.host ?: return path
            when {
                path.startsWith("//") -> "$scheme:$path"
                path.startsWith("/") -> "$scheme://$host$path"
                else -> {
                    val dir = base.substringBeforeLast('/', "$scheme://$host")
                    "$dir/$path"
                }
            }
        } catch (e: Exception) {
            path
        }
    }

    /** 从采集接口地址推导 Referer（站点根），部分 CDN 会校验 */
    fun refererOf(api: String?): String? {
        if (api.isNullOrBlank()) return null
        return try {
            val u = URI(api)
            val scheme = u.scheme ?: "https"
            val host = u.host ?: return null
            "$scheme://$host/"
        } catch (e: Exception) {
            null
        }
    }
}
