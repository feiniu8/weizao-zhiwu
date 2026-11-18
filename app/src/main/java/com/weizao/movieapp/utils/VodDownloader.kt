package com.weizao.movieapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 离线缓存（下载）：
 * - 直链 mp4：单文件流式下载；
 * - m3u8：抓播放列表，逐段下载后按顺序拼成一个 .ts 文件；
 * - 任务列表用 Compose 的 SnapshotStateList 暴露给界面，随时能看进度。
 */
object VodDownloader {

    const val WAIT = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAIL = 3

    data class Task(
        val id: Long,
        val title: String,
        val url: String,
        var done: Int = 0,
        var total: Int = 0,
        var state: Int = WAIT,
        var path: String = "",
        var message: String = ""
    ) {
        val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)
    }

    /** 界面上直接 collect 这个列表 */
    val tasks = mutableStateListOf<Task>()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var seq = 1L

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "vodcache").apply { mkdirs() }

    fun start(ctx: Context, title: String, url: String) {
        if (url.isBlank()) return
        synchronized(tasks) {
            if (tasks.any { it.url == url && it.state != FAIL }) return
            val t = Task(id = seq++, title = title.ifBlank { "未命名剧集" }, url = url)
            tasks.add(0, t)
            scope.launch { run(ctx.applicationContext, t) }
        }
    }

    fun remove(t: Task) {
        runCatching {
            if (t.path.isNotBlank()) File(t.path).delete()
        }
        tasks.remove(t)
    }

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        return c
    }

    private fun run(ctx: Context, t: Task) {
        try {
            t.state = RUNNING
            val isM3u8 = t.url.substringBefore('?').endsWith(".m3u8", true)
            if (isM3u8) downloadM3u8(ctx, t) else downloadSingle(ctx, t)
            t.state = DONE
            t.done = t.total
        } catch (e: Exception) {
            t.state = FAIL
            t.message = e.message ?: "下载失败"
        }
    }

    private fun downloadSingle(ctx: Context, t: Task) {
        val out = File(dir(ctx), "${safeName(t.title)}_${t.id}.mp4")
        open(t.url).useConn { c ->
            t.total = c.contentLength.coerceAtLeast(0)
            c.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        t.done += n
                    }
                }
            }
        }
        t.path = out.absolutePath
    }

    private fun downloadM3u8(ctx: Context, t: Task) {
        val playlist = fetchText(t.url)
        if (playlist.contains("EXT-X-KEY")) throw IllegalException("该片源加密（EXT-X-KEY），暂不支持离线缓存")
        val segs = playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (segs.isEmpty()) throw IllegalException("播放列表里没有分片")
        t.total = segs.size
        val out = File(dir(ctx), "${safeName(t.title)}_${t.id}.ts")
        out.outputStream().use { os ->
            segs.forEachIndexed { i, s ->
                val u = if (s.startsWith("http")) s else resolve(t.url, s)
                open(u).useConn { c ->
                    c.inputStream.use { it.copyTo(os, 64 * 1024) }
                }
                t.done = i + 1
            }
        }
        t.path = out.absolutePath
    }

    private fun resolve(base: String, rel: String): String {
        if (rel.startsWith("/")) {
            val root = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore("/")
            return root + rel
        }
        val cut = base.substringBefore('?').substringBeforeLast('/')
        return "$cut/$rel"
    }

    private fun fetchText(url: String): String = open(url).useConn { c ->
        c.inputStream.bufferedReader().readText()
    }

    private inline fun <T> HttpURLConnection.useConn(block: (HttpURLConnection) -> T): T = try {
        connect()
        block(this)
    } finally {
        disconnect()
    }

    private class IllegalException(msg: String) : Exception(msg)

    /** 用系统播放器打开缓存好的文件（走 FileProvider，不用外部存储权限） */
    fun play(ctx: Context, t: Task) {
        val f = File(t.path)
        if (!f.exists()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(Intent.createChooser(i, "用哪个播放器打开").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun sizeOf(t: Task): String {
        val len = runCatching { File(t.path).length() }.getOrDefault(0L)
        return if (len <= 0) "" else "%.1f MB".format(len / 1048576f)
    }
}
