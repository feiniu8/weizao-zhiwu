package com.weizao.movieapp.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

private data class Opt(val label: String, val value: String)

/**
 * 设置页：照搬「小鸡看看」的分区样式（播放 / 播放参数 / 网络 / 显示 / 投屏 / 应用），
 * 原有可调项（倍速、画幅、自动换源、弹幕、直播源、清理记录）全部保留并归入对应分区。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenLiveSource: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    versionName: String = ""
) {
    val prefs by vm.prefs.collectAsState()
    val live by vm.liveSources.collectAsState()
    val history by vm.history.collectAsState()
    val ctx = LocalContext.current

    var dialog by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<Opt>>(emptyList()) }
    var onPick by remember { mutableStateOf<(String) -> Unit>({}) }
    var cacheText by remember { mutableStateOf(cacheSize(ctx.cacheDir)) }

    fun picker(title: String, opts: List<Opt>, cb: (String) -> Unit) {
        dialog = title; options = opts
        onPick = { v -> cb(v); dialog = null }
    }

    // 开关默认值：保持原版行为的给 1，新增默认关
    fun on(key: String, def: String = "0") = (prefs[key] ?: def) == "1"
    fun toggle(key: String, def: String = "0") = vm.setPref(key, if (on(key, def)) "0" else "1")

    val speed = prefs["speed"] ?: "1.0"
    val ratio = prefs["ratio"] ?: "0"
    val theme = prefs["theme"] ?: "0"
    val cacheOpt = prefs["cache"] ?: "0"
    val buffer = prefs["buffer"] ?: "120"
    val themeName = listOf("跟随系统", "浅色", "深色").getOrElse(theme.toIntOrNull() ?: 0) { "跟随系统" }
    val cacheName = when (cacheOpt) {
        "1" -> "256 MB"; "2" -> "512 MB"; "3" -> "1 GB"; else -> "关闭"
    }
    val bufferName = if (buffer == "0") "关闭" else "$buffer 秒"

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("设置") { }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("播放")
            Card2 {
                SwitchRow(Icons.Filled.PlayArrow, "自动播放下一集", on("autonext", "1")) {
                    toggle("autonext", "1")
                }
                Divider2()
                SwitchRow(Icons.Filled.Speed, "记住每部剧的倍速", on("remspeed")) {
                    toggle("remspeed")
                }
                Divider2()
                SwitchRow(Icons.Filled.Sync, "解析失败自动切换播放源", on("autoswitch", "1")) {
                    toggle("autoswitch", "1")
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("播放参数")
            Card2 {
                ValueRow(Icons.Filled.Speed, "默认倍速", speed + "x") {
                    picker(
                        "默认倍速",
                        listOf("0.75", "1.0", "1.25", "1.5", "2.0", "2.5", "3.0").map { Opt(it + "x", it) }
                    ) { vm.setPref("speed", it) }
                }
                Divider2()
                ValueRow(
                    Icons.Filled.Straighten, "默认画面比例",
                    listOf("默认", "16:9", "4:3", "填充", "原始", "裁剪")
                        .getOrElse(ratio.toIntOrNull() ?: 0) { "默认" }
                ) {
                    picker(
                        "默认画面比例",
                        listOf("默认", "16:9", "4:3", "填充", "原始", "裁剪").mapIndexed { i, n -> Opt(n, i.toString()) }
                    ) { vm.setPref("ratio", it) }
                }
                Divider2()
                ValueRow(Icons.Filled.Storage, "磁盘缓存容量", cacheName) {
                    picker(
                        "磁盘缓存容量",
                        listOf(Opt("关闭", "0"), Opt("256 MB", "1"), Opt("512 MB", "2"), Opt("1 GB", "3"))
                    ) { vm.setPref("cache", it) }
                }
                Divider2()
                ValueRow(Icons.Filled.Timer, "提前缓冲时长", bufferName) {
                    picker(
                        "提前缓冲时长",
                        listOf(Opt("关闭", "0"), Opt("30 秒", "30"), Opt("60 秒", "60"), Opt("120 秒", "120"))
                    ) { vm.setPref("buffer", it) }
                }
                Divider2()
                SwitchRow(Icons.Filled.BatteryFull, "显示电量", on("showbattery")) { toggle("showbattery") }
                Divider2()
                SwitchRow(Icons.Filled.Schedule, "显示时间", on("showtime", "1")) { toggle("showtime", "1") }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("网络")
            Card2 {
                SwitchRow(Icons.Filled.Wifi, "仅 Wi-Fi 播放", on("wifionlyplay")) { toggle("wifionlyplay") }
                Divider2()
                SwitchRow(Icons.Filled.Download, "仅 Wi-Fi 下载", on("wifionlydl")) { toggle("wifionlydl") }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("显示")
            Card2 {
                ValueRow(Icons.Filled.Palette, "主题", themeName) {
                    picker(
                        "主题",
                        listOf(Opt("跟随系统", "0"), Opt("浅色", "1"), Opt("深色", "2"))
                    ) { vm.setPref("theme", it) }
                }
                Divider2()
                SwitchRow(Icons.Filled.Star, "显示豆瓣评分", on("showscore", "1")) { toggle("showscore", "1") }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("投屏")
            Card2 {
                SwitchRow(Icons.Filled.Cast, "默认使用中转投屏", on("cast")) { toggle("cast") }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("其他")
            Card2 {
                ValueRow(Icons.Filled.PlayArrow, "直播数据源", "${live.size} 个") { onOpenLiveSource() }
                Divider2()
                ValueRow(Icons.Filled.Refresh, "清理播放记录", "${history.size} 条") { vm.clearHistory() }
                Divider2()
                ValueRow(Icons.Filled.Star, "弹幕", "未支持（无弹幕数据源）") { }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("应用")
            Card2 {
                ValueRow(Icons.Filled.SystemUpdate, "检查更新", versionName) { onCheckUpdate() }
                Divider2()
                ValueRow(Icons.Filled.CleaningServices, "清理缓存", cacheText) {
                    clearCache(ctx.cacheDir)
                    cacheText = cacheSize(ctx.cacheDir)
                }
            }

            Text(
                "提示：默认倍速与画幅在每次开始播放时生效；播放页内可临时调整。",
                fontSize = 11.sp, color = C.Hint,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }

    val d = dialog
    if (d != null && options.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = C.Dialog,
            shape = RoundedCornerShape(16.dp),
            title = { Text(d, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White) },
            text = {
                Column {
                    options.forEach { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(o.value) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(o.label, fontSize = 14.sp, color = C.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text("取消", fontSize = 14.sp, color = C.Gray) }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 12.sp, color = C.Hint,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun Card2(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card),
        content = content
    )
}

@Composable
private fun Divider2() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(0.6.dp)
            .background(C.Divider)
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = C.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 14.sp, color = C.White, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onChange() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = C.Green,
                uncheckedThumbColor = Color(0xFFB9C0CC),
                uncheckedTrackColor = Color(0xFF2A3140)
            )
        )
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = C.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 14.sp, color = C.White, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = C.Gray)
        Spacer(Modifier.width(6.dp))
        Text("›", fontSize = 16.sp, color = C.Hint)
    }
}

private fun cacheSize(dir: java.io.File): String {
    var total = 0L
    runCatching {
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
    }
    return if (total <= 0L) "0 MB" else String.format("%.2f MB", total / 1024.0 / 1024.0)
}

private fun clearCache(dir: java.io.File) {
    runCatching {
        dir.listFiles()?.forEach { f -> f.deleteRecursively() }
    }
}
