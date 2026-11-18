package com.weizao.movieapp.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 用系统 DownloadManager 下载安装包：能后台下、通知栏可见、返回不影响 */
object Downloader {

    fun start(ctx: Context, url: String, fileName: String): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("唯造智物 新版本")
            .setDescription("下载中：$fileName")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .apply {
                // 小飞机网盘的直链必须带分享页 Referer，否则返回错误 JSON
                if (url.contains("feijipan")) addRequestHeader("Referer", "https://share.feijipan.com/")
            }
            .setDestinationInExternalFilesDir(ctx, null, "update/$fileName")
        return dm.enqueue(req)
    }

    /** 0~100；-1 表示失败/已删除 */
    fun progress(ctx: Context, id: Long): Int {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val q = DownloadManager.Query().setFilterById(id)
        dm.query(q).use { c ->
            if (c == null || !c.moveToFirst()) return -1
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_FAILED) return -1
            if (status == DownloadManager.STATUS_SUCCESSFUL) return 100
            return if (total > 0) ((done * 100) / total).toInt() else 0
        }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** 安装：用系统下载 Uri 直接拉起安装器 */
    fun install(ctx: Context, id: Long) {
        if (!canInstall(ctx)) { openInstallSettings(ctx); return }
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val uri = dm.getUriForDownloadedFile(id) ?: return
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(i) }
    }
}
