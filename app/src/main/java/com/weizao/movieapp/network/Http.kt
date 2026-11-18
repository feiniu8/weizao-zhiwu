package com.weizao.movieapp.network

import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.util.concurrent.TimeUnit

/** OkHttp 手动封装：统一超时、UA、异常处理 */
object Http {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    /** 返回响应体，失败抛异常（调用方统一捕获） */
    fun get(url: String, referer: String? = null): String {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) b.header("Referer", referer)
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    fun getSafe(url: String): String? = try {
        get(url)
    } catch (e: Exception) {
        null
    }

    /** 空 body 的 POST，用于网盘这类"参数放 query"的接口 */
    fun postText(url: String, body: String = "", referer: String? = null): String? = try {
        val rb = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) rb.header("Referer", referer)
        val b = rb.post(body.toRequestBody("text/plain".toMediaType()))
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    } catch (e: Exception) { null }

    /** POST JSON，失败返回 null */
    fun postJson(url: String, body: String, referer: String? = null): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
        if (referer != null) b.header("Referer", referer)
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    } catch (e: Exception) { null }

    /**
     * 探测一个地址：一次 GET（Range: 前若干字节）拿到 Content-Type 和开头内容。
     * 用于判断是 HLS 播放列表、直链视频，还是一个需要再解析的网页。
     */
    fun probe(url: String, referer: String? = null, bytes: Long = 4096L): Pair<String, String?> {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Range", "bytes=0-$bytes")
            if (referer != null) b.header("Referer", referer)
            client.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use "" to null
                val ct = (resp.header("Content-Type") ?: "").lowercase()
                ct to readAtMost(resp, bytes)
            }
        } catch (e: Exception) {
            "" to null
        }
    }

    /** 读取整段文本（限制最大字节数，避免拖大文件） */
    fun text(url: String, referer: String? = null, maxBytes: Long = 400_000L): String? {
        return try {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            if (referer != null) b.header("Referer", referer)
            client.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                readAtMost(resp, maxBytes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readAtMost(resp: okhttp3.Response, maxBytes: Long): String? {
        val src = resp.body?.source() ?: return null
        val buf = Buffer()
        var left = maxBytes
        while (left > 0) {
            val n = src.read(buf, minOf(left, 8192L))
            if (n == -1L) break
            left -= n
        }
        return buf.readUtf8()
    }
}
