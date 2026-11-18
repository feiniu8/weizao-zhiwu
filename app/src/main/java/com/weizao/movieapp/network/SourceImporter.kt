package com.weizao.movieapp.network

import org.json.JSONObject
import java.net.IDN

/**
 * 通用源导入：苹果CMS 采集接口 / TVBox 订阅 JSON（sites+lives）/ m3u 直播订阅 / 网页里自动发现配置。
 */
object SourceImporter {

    data class Csp(val name: String, val api: String)
    data class Live(val name: String, val url: String)
    data class Result(
        val csp: List<Csp> = emptyList(),
        val lives: List<Live> = emptyList(),
        val kind: String = "",
        val note: String = ""
    ) {
        val ok: Boolean get() = csp.isNotEmpty() || lives.isNotEmpty()
    }

    fun normalize(raw: String): String {
        var u = raw.trim().replace(" ", "")
        if (u.isEmpty()) return u
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
        val m = Regex("^(https?)://([^/?#]+)(.*)$", RegexOption.IGNORE_CASE).find(u) ?: return u
        val host = m.groupValues[2]
        val ascii = runCatching { IDN.toASCII(host) }.getOrDefault(host)
        return m.groupValues[1].lowercase() + "://" + ascii + m.groupValues[3]
    }

    fun hostOf(url: String): String = runCatching {
        Regex("^(?:https?)://([^/?#]+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1) ?: ""
    }.getOrDefault("")

    fun fromUrl(raw: String, fallbackName: String = "", discover: Boolean = true): Result {
        val url = normalize(raw)
        val text = Http.text(url, maxBytes = 800_000L)
            ?: return Result(note = "连接失败 / 超时：$url")
        val t = text.trim()

        // 1) JSON：TVBox 配置 或 苹果CMS 列表
        if (t.startsWith("{")) {
            val obj = runCatching { JSONObject(t) }.getOrNull()
            if (obj != null) {
                if (obj.has("sites") || obj.has("lives") || obj.has("urls")) return parseTvBox(obj, url)
                if (obj.has("list") || obj.has("code") || obj.has("total")) {
                    return Result(
                        csp = listOf(Csp(fallbackName.ifBlank { hostOf(url) }, url)),
                        kind = "api", note = "识别为苹果CMS采集接口"
                    )
                }
            }
            return Result(note = "是 JSON，但既不是 TVBox 配置，也没有影片列表")
        }

        // 2) m3u 直播订阅
        if (t.contains("#EXTINF") || t.startsWith("#EXTM3U")) {
            return Result(
                lives = listOf(Live(fallbackName.ifBlank { "订阅直播源" }, url)),
                kind = "m3u", note = "直播订阅，含 ${M3u.parse(t).size} 个频道"
            )
        }

        // 3) 网页：在页面里找 .json / .m3u 配置链接
        if (t.startsWith("<")) {
            if (!discover) return Result(note = "该地址是网页，不是配置文件")
            for (c in discoverLinks(t, url).take(5)) {
                val r = runCatching { fromUrl(c, "", discover = false) }.getOrNull()
                if (r != null && r.ok) return r.copy(note = "${r.note}（从页面发现：$c）")
            }
            return Result(note = "该地址是网页，页面里也没找到 .json / .m3u 配置链接")
        }

        // 4) 二进制（图片、下载包等）
        if (looksBinary(t)) {
            return Result(note = "该地址返回的是图片/二进制文件，不是配置文件")
        }

        // 5) 纯文本源列表
        return parse(t, fallbackName.ifBlank { hostOf(url) })
    }

    private fun looksBinary(s: String): Boolean {
        val head = s.take(2000)
        if (head.isEmpty()) return false
        var bad = 0
        head.forEach { c -> if (c.code < 9 || (c.code in 14..31) || c.code == 0xFFFD) bad++ }
        return bad * 100 / head.length > 25
    }

    private fun discoverLinks(html: String, base: String): List<String> {
        val out = mutableListOf<String>()
        Regex("(?i)(?:href|src|data-url|value)=[\"']([^\"']+)[\"']").findAll(html).forEach { m ->
            val link = m.groupValues[1]
            if (!link.contains("json", true) && !link.contains("m3u", true) &&
                !link.contains(".txt", true) && !link.contains("api.php", true)
            ) return@forEach
            out += resolve(base, link)
        }
        return out.distinct()
    }

    private fun resolve(base: String, link: String): String {
        if (link.startsWith("http")) return link
        val root = Regex("^(?:https?)://[^/?#]+", RegexOption.IGNORE_CASE).find(base)?.value ?: return link
        return if (link.startsWith("/")) root + link else root + "/" + link.removePrefix("./")
    }

    fun parse(text: String, name: String = ""): Result {
        val t = text.trim()
        if (t.isEmpty()) return Result(note = "内容为空")
        if (t.startsWith("{")) return runCatching { parseTvBox(JSONObject(t)) }
            .getOrElse { Result(note = "不是合法 JSON") }
        if (t.contains("#EXTINF") || t.startsWith("#EXTM3U")) {
            return Result(
                lives = listOf(Live(name.ifBlank { "订阅直播源" }, name)),
                kind = "m3u", note = "直播订阅，含 ${M3u.parse(t).size} 个频道"
            )
        }
        if (t.startsWith("<")) return Result(note = "该地址返回的是网页，不是 TXT / JSON 列表")

        val csp = mutableListOf<Csp>()
        t.lineSequence().filter { it.isNotBlank() }.take(3000).toList().forEach { line ->
            val l = line.trim().trim('"').replace('，', ',')
            if (l.startsWith("#") || l.startsWith("//")) return@forEach
            val parts = l.split(",", "\t", "|").map { it.trim() }.filter { it.isNotEmpty() }
            val api = parts.lastOrNull { it.startsWith("http") } ?: return@forEach
            if (api.endsWith(".m3u") || api.endsWith(".m3u8")) return@forEach
            val n = parts.firstOrNull { it != api }?.take(24) ?: name.ifBlank { "采集源" }
            csp += Csp(n, api)
        }
        return if (csp.isEmpty()) Result(note = "没有识别到可用的采集接口")
        else Result(csp = csp, kind = "txt", note = "解析到 ${csp.size} 个采集接口")
    }

    private fun parseTvBox(obj: JSONObject, base: String = ""): Result {
        val csp = mutableListOf<Csp>()
        val lives = mutableListOf<Live>()

        obj.optJSONArray("sites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val api = o.optString("api").trim()
                if (!api.startsWith("http")) continue
                if (api.endsWith(".js")) continue
                val ty = o.optString("type", "1")
                if (ty == "3" || ty == "4") continue
                csp += Csp(o.optString("name").ifBlank { "接口${i + 1}" }.take(24), api)
            }
        }
        listOf("lives", "urls").forEach { key ->
            obj.optJSONArray(key)?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    var u = o.optString("url").trim()
                    if (u.startsWith("./") || u.startsWith("/")) {
                        if (base.isNotBlank()) u = resolve(base, u)
                    }
                    if (u.startsWith("http")) {
                        lives += Live(o.optString("name").ifBlank { "直播${i + 1}" }.take(24), u)
                    }
                }
            }
        }

        if (csp.isEmpty() && lives.isEmpty()) {
            return Result(note = "是 JSON，但没有可用的采集接口或直播源（sites 里都是需要 TVBox 内核的 csp_/spider）")
        }
        return Result(
            csp = csp, lives = lives, kind = "tvbox",
            note = "解析到 ${csp.size} 个采集接口、${lives.size} 个直播源"
        )
    }
}

/** m3u / m3u8 直播列表解析 */
object M3u {
    data class Channel(val group: String, val name: String, val url: String)

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var group = "其他"
        var name = ""
        text.lineSequence().forEach { line ->
            val l = line.trim()
            when {
                l.startsWith("#EXTINF") -> {
                    val g = Regex("group-title=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                    group = if (!g.isNullOrBlank()) g else "其他"
                    name = l.substringAfterLast(',').trim()
                }
                l.startsWith("http") -> {
                    out += Channel(group, name.ifBlank { "频道${out.size + 1}" }, l)
                    name = ""
                }
            }
        }
        return out
    }
}
