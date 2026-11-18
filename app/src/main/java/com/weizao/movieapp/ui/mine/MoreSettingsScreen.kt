package com.weizao.movieapp.ui.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel
import java.net.NetworkInterface

/**
 * 「我的 → 设置」：主题 / 壁纸 / 我的下载 / 数据推送 / 网络 五块。
 * 主题、壁纸、两个仅 Wi-Fi 开关都会写进 prefs；壁纸会真实作用于首页背景。
 */
@Composable
fun MoreSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val ctx = LocalContext.current
    var page by remember { mutableStateOf(0) }
    // 系统返回键也逐级回退：子页 -> 设置首页 -> 退出设置
    androidx.activity.compose.BackHandler {
        if (page == 0) onBack() else page = 0
    }

    val titles = listOf("设置", "主题设置", "壁纸", "我的下载", "数据推送", "网络")

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(titles.getOrElse(page) { "设置" }) {
            Icon(
                Icons.Filled.ArrowBack, "返回",
                tint = Color.White,
                modifier = Modifier
                    .clickable { if (page == 0) onBack() else page = 0 }
                    .size(22.dp)
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (page) {
                0 -> {
                    MSection("个性化")
                    MCard {
                        MRawRow("主题设置", themeName(prefs["theme"] ?: "0")) { page = 1 }
                        MDivider()
                        MRawRow("壁纸", wallpaperName(prefs)) { page = 2 }
                    }
                    MSection("内容与传输")
                    MCard {
                        MRawRow("我的下载", "全部 / 正在下载 / 已下载") { page = 3 }
                        MDivider()
                        MRawRow("数据推送", "扫码推送") { page = 4 }
                    }
                    MSection("网络")
                    MCard {
                        MSwitchRow("仅 Wi-Fi 播放", on(prefs, "wifionlyplay")) {
                            vm.setPref("wifionlyplay", flip(prefs, "wifionlyplay"))
                        }
                        MDivider()
                        MSwitchRow("仅 Wi-Fi 下载", on(prefs, "wifionlydl")) {
                            vm.setPref("wifionlydl", flip(prefs, "wifionlydl"))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                1 -> ThemePage(prefs, vm)
                2 -> WallpaperPage(prefs, vm, ctx)
                3 -> DownloadPage(ctx)
                4 -> PushPage(ctx)
                5 -> NetworkPage(prefs, vm)
            }
        }
    }
}

/* ---------------- 主题 ---------------- */
@Composable
private fun ThemePage(prefs: Map<String, String>, vm: AppViewModel) {
    val cur = prefs["theme"] ?: "0"
    MSection("主题模式")
    MCard {
        listOf("跟随系统", "浅色", "深色").forEachIndexed { i, name ->
            if (i > 0) MDivider()
            Row(
                Modifier.fillMaxWidth().clickable { vm.setPref("theme", i.toString()) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, fontSize = 15.sp, color = C.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (cur == i.toString()) C.Blue else C.Card2),
                    contentAlignment = Alignment.Center
                ) {
                    if (cur == i.toString()) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    }
                }
            }
        }
    }
    Hint("深色主题现在生效。浅色 / 跟随系统的配色包还在接入，选择会先记下来。")
}

/* ---------------- 壁纸 ---------------- */
@Composable
private fun WallpaperPage(prefs: Map<String, String>, vm: AppViewModel, ctx: Context) {
    var urlDialog by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf(prefs["wallpaper_url"].orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.setPref("wallpaper_uri", uri.toString())
            vm.setPref("wallpaper_url", "")
            Toast.makeText(ctx, "已设为本地壁纸", Toast.LENGTH_SHORT).show()
        }
    }

    MSection("壁纸来源")
    MCard {
        MRawRow("本地图片", if (prefs["wallpaper_uri"].orEmpty().isNotBlank()) "已选择" else "从相册选图") {
            picker.launch("image/*")
        }
        MDivider()
        MRawRow("互联网", if (prefs["wallpaper_url"].orEmpty().isNotBlank()) "已设置链接" else "填写图片地址") {
            urlDialog = true
        }
    }
    Spacer(Modifier.height(12.dp))
    MCard {
        MRawRow("清除壁纸", "恢复纯色背景") {
            vm.setPref("wallpaper_uri", "")
            vm.setPref("wallpaper_url", "")
            Toast.makeText(ctx, "已恢复默认背景", Toast.LENGTH_SHORT).show()
        }
    }
    Hint("壁纸作用于首页背景，会自动压一层深色遮罩保证文字可读。")

    if (urlDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { urlDialog = false },
            containerColor = C.Dialog,
            title = { Text("互联网壁纸", color = C.White, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = urlText, onValueChange = { urlText = it },
                    placeholder = { Text("https://…/wallpaper.jpg", color = C.Hint, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.White, unfocusedTextColor = C.White,
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Card2, cursorColor = C.Blue
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.setPref("wallpaper_url", urlText.trim())
                    vm.setPref("wallpaper_uri", "")
                    urlDialog = false
                }) { Text("使用", color = C.Blue) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { urlDialog = false }) { Text("取消", color = C.Gray) }
            }
        )
    }
}

/* ---------------- 我的下载 ---------------- */
@Composable
private fun DownloadPage(ctx: android.content.Context) {
    var tab by remember { mutableStateOf(0) }
    val tasks = com.weizao.movieapp.utils.VodDownloader.tasks
    val shown = tasks.filter {
        when (tab) {
            1 -> it.state == com.weizao.movieapp.utils.VodDownloader.RUNNING ||
                it.state == com.weizao.movieapp.utils.VodDownloader.WAIT
            2 -> it.state == com.weizao.movieapp.utils.VodDownloader.DONE
            else -> true
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("全部", "正在下载", "已下载").forEachIndexed { i, name ->
            val on = tab == i
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) C.Blue else C.Card)
                    .clickable { tab = i }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(name, fontSize = 13.sp, color = if (on) Color.White else C.Gray) }
        }
    }

    if (shown.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无下载任务", fontSize = 15.sp, color = C.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "在播放页点「下载」即可缓存当前集：m3u8 会分片下完再合成一个文件。",
                    fontSize = 12.sp, color = C.Hint
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { t ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(C.Card).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, fontSize = 14.sp, color = C.White, modifier = Modifier.weight(1f), maxLines = 1)
                        when (t.state) {
                            com.weizao.movieapp.utils.VodDownloader.DONE ->
                                Text("已完成 ${com.weizao.movieapp.utils.VodDownloader.sizeOf(t)}", fontSize = 11.sp, color = C.Green)
                            com.weizao.movieapp.utils.VodDownloader.FAIL ->
                                Text("失败", fontSize = 11.sp, color = C.Warn)
                            else -> Text("${t.percent}%", fontSize = 11.sp, color = C.Hint)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (t.state != com.weizao.movieapp.utils.VodDownloader.DONE) {
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Card2)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(t.percent / 100f).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(C.Blue)
                            )
                        }
                        if (t.state == com.weizao.movieapp.utils.VodDownloader.FAIL && t.message.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(t.message, fontSize = 11.sp, color = C.Warn)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (t.state == com.weizao.movieapp.utils.VodDownloader.DONE) {
                            Chip2("播放") { com.weizao.movieapp.utils.VodDownloader.play(ctx, t) }
                        }
                        Chip2("删除") { com.weizao.movieapp.utils.VodDownloader.remove(t) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

/* ---------------- 数据推送 ---------------- */
@Composable
private fun PushPage(ctx: Context) {
    var link by remember { mutableStateOf("") }
    var autoClip by remember { mutableStateOf(false) }
    val addr = remember { "http://${localIp()}:9978/" }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = link, onValueChange = { link = it },
            placeholder = {
                Text("视频链接/磁力/带视频的网页/荐片", fontSize = 12.sp, color = C.Hint)
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = C.White, unfocusedTextColor = C.White,
                focusedBorderColor = C.Blue, unfocusedBorderColor = C.Card2, cursorColor = C.Blue
            )
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.height(52.dp).clip(RoundedCornerShape(8.dp)).background(C.Card)
                .clickable {
                    Toast.makeText(
                        ctx,
                        if (link.isBlank()) "先粘贴一个视频链接" else "已收到链接，直链播放通道待接入",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("播放", fontSize = 13.sp, color = C.Gray) }
    }

    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
                .background(if (autoClip) C.Purple else C.Card2)
                .clickable {
                    autoClip = !autoClip
                    if (autoClip) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val txt = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (txt.startsWith("http")) link = txt
                    }
                }
        )
        Spacer(Modifier.width(10.dp))
        Text("自动读取剪贴板", fontSize = 14.sp, color = C.White)
    }

    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.fillMaxWidth().height(220.dp)
            .clip(RoundedCornerShape(12.dp)).background(C.Card2),
        contentAlignment = Alignment.Center
    ) {
        Text("二维码待接入本地推送服务", fontSize = 12.sp, color = C.Hint)
    }
    Spacer(Modifier.height(14.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("扫描二维码或者浏览器输入地址", fontSize = 13.sp, color = C.White)
        Spacer(Modifier.height(6.dp))
        Text(
            addr, fontSize = 13.sp, color = C.Gray,
            modifier = Modifier.clickable {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("addr", addr))
                Toast.makeText(ctx, "地址已复制", Toast.LENGTH_SHORT).show()
            }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "需要手机和电脑在同一 Wi-Fi；推送服务（局域网收链接）还没接入，当前只展示入口和信息。",
            fontSize = 11.sp, color = C.Hint
        )
    }
    Spacer(Modifier.height(20.dp))
}

/* ---------------- 网络 ---------------- */
@Composable
private fun NetworkPage(prefs: Map<String, String>, vm: AppViewModel) {
    MSection("仅在 Wi-Fi 下")
    MCard {
        MSwitchRow("仅 Wi-Fi 播放", on(prefs, "wifionlyplay")) {
            vm.setPref("wifionlyplay", flip(prefs, "wifionlyplay"))
        }
        MDivider()
        MSwitchRow("仅 Wi-Fi 下载", on(prefs, "wifionlydl")) {
            vm.setPref("wifionlydl", flip(prefs, "wifionlydl"))
        }
    }
    Hint("打开后，移动网络下播放前会先提醒；下载任务只在 Wi-Fi 下开始。")
}

/* ---------------- 小工具 ---------------- */

private fun themeName(v: String) = listOf("跟随系统", "浅色", "深色").getOrElse(v.toIntOrNull() ?: 0) { "跟随系统" }

private fun wallpaperName(prefs: Map<String, String>) = when {
    prefs["wallpaper_url"].orEmpty().isNotBlank() -> "互联网图片"
    prefs["wallpaper_uri"].orEmpty().isNotBlank() -> "本地图片"
    else -> "默认纯色"
}

private fun on(prefs: Map<String, String>, key: String) = (prefs[key] ?: "0") == "1"
private fun flip(prefs: Map<String, String>, key: String) = if (on(prefs, key)) "0" else "1"

private fun localIp(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
        ?.hostAddress
}.getOrNull() ?: "0.0.0.0"

@Composable
private fun Chip2(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(C.Card2)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) { Text(text, fontSize = 12.sp, color = C.White) }
}

@Composable
private fun MSection(text: String) {
    Text(
        text, fontSize = 12.sp, color = C.Hint,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp)
    )
}

@Composable
private fun MCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card),
        content = content
    )
}

@Composable
private fun MDivider() {
    Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color(0x14FFFFFF)))
}

@Composable
private fun MRawRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = C.White)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 11.sp, color = C.Hint)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = C.Hint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MSwitchRow(title: String, checked: Boolean, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = C.White, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = { onChange() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = C.Blue,
                uncheckedThumbColor = Color(0xFF8A93A5), uncheckedTrackColor = C.Card2
            )
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text, fontSize = 11.sp, color = C.Hint, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp)
    )
}
