package com.weizao.movieapp.ui.live

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.weizao.movieapp.network.M3u
import com.weizao.movieapp.ui.common.EmptyBox
import com.weizao.movieapp.ui.common.LoadingBox
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

/** 频道分类：按名称推断 */
private val REGIONS = listOf(
    "北京", "上海", "广东", "湖南", "浙江", "江苏", "四川", "山东", "河南", "湖北",
    "福建", "安徽", "江西", "辽宁", "陕西", "重庆", "天津", "河北", "山西", "云南",
    "贵州", "广西", "黑龙江", "吉林", "甘肃", "新疆", "内蒙古", "海南", "宁夏", "青海", "西藏"
)
private val HMT_WORDS = listOf(
    "凤凰", "翡翠", "TVB", "明珠", "香港", "台湾", "澳门", "中天", "东森", "三立",
    "民视", "无线", "澳视", "华视", "台视", "八大", "纬来"
)

private fun regionOf(n: String): String {
    if (HMT_WORDS.any { n.contains(it, ignoreCase = true) }) return "港澳台"
    REGIONS.forEach { if (n.contains(it)) return it }
    return ""
}

private fun catOf(n: String): String {
    if (n.contains("CCTV", true) || n.contains("央视") || n.contains("中央")) return "央视"
    if (n.contains("卫视")) return "卫视"
    val r = regionOf(n)
    return when {
        r == "港澳台" -> "港澳台"
        r.isNotEmpty() -> "地方"
        else -> "其他"
    }
}

/** 直播：顶部内嵌播放器，下面按分类浏览频道 */
@Composable
fun LiveScreen(
    vm: AppViewModel,
    onPlay: (String, String) -> Unit,
    onManage: () -> Unit
) {
    val sources by vm.liveSources.collectAsState()
    var sel by remember { mutableIntStateOf(0) }
    var channels by remember { mutableStateOf<List<M3u.Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var cat by remember { mutableStateOf("全部") }
    var region by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<M3u.Channel?>(null) }

    LaunchedEffect(sources, sel) {
        loading = true
        channels = emptyList()
        current = null
        cat = "全部"; region = ""; keyword = ""
        val url = sources.getOrNull(sel)?.second
        if (url == null) { loading = false } else {
            vm.channels(url) { list ->
                channels = list
                loading = false
            }
        }
    }

    val regions = remember(channels) {
        channels.map { regionOf(it.name) }.filter { it.isNotEmpty() }.distinct()
    }
    val shown = remember(channels, cat, region, keyword) {
        channels.filter { ch ->
            (cat == "全部" || catOf(ch.name) == cat) &&
                (region.isBlank() || regionOf(ch.name) == region) &&
                (keyword.isBlank() || ch.name.contains(keyword, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("直播") {
            Text(
                "直播源管理", fontSize = 13.sp, color = C.Blue,
                modifier = Modifier.clickable { onManage() }.padding(8.dp)
            )
        }

        // 顶端播放画面
        LiveMiniPlayer(
            url = current?.url,
            title = current?.name ?: "选一个频道开始播放",
            onFullscreen = { current?.let { onPlay(it.name, it.url) } }
        )

        // 订阅源
        LazyRow(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources.size) { i ->
                val on = i == sel
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                        .clickable { sel = i }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(sources[i].first, fontSize = 12.sp, color = if (on) Color.White else C.Gray, maxLines = 1)
                }
            }
        }

        // 分类 + 名称筛选
        LazyRow(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("全部", "央视", "卫视", "地方", "港澳台", "其他").size) { i ->
                val c = listOf("全部", "央视", "卫视", "地方", "港澳台", "其他")[i]
                GroupChip(c, c == cat) { cat = c }
            }
        }
        if (regions.isNotEmpty()) {
            LazyRow(
                Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { GroupChip("全部地区", region.isBlank()) { region = "" } }
                items(regions.size) { i ->
                    GroupChip(regions[i], regions[i] == region) {
                        region = if (region == regions[i]) "" else regions[i]
                    }
                }
            }
        }
        TextField(
            value = keyword, onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(44.dp),
            placeholder = { Text("搜索频道名称", fontSize = 12.sp, color = C.Hint) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = C.Card, unfocusedContainerColor = C.Card,
                focusedTextColor = C.White, unfocusedTextColor = C.White,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> LoadingBox()
                sources.isEmpty() -> EmptyBox("还没有直播源，点右上角添加")
                channels.isEmpty() -> EmptyBox("该直播源没有解析到频道（可能是空列表或格式不对）")
                shown.isEmpty() -> EmptyBox("这个分类下没有频道，换个分类或清空搜索")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(shown) { ch ->
                        val on = ch.url == current?.url
                        Box(
                            Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) C.Card2 else C.Card)
                                .clickable { current = ch }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ch.name, fontSize = 12.sp, color = if (on) C.Blue else C.White, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Text(
            "共 ${shown.size} 个频道 · 频道数据来自网络公开订阅",
            fontSize = 11.sp, color = C.Hint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** 内嵌直播播放器 */
@Composable
private fun LiveMiniPlayer(url: String?, title: String, onFullscreen: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var playing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                err = "播放失败：${error.errorCodeName}"
            }
        })
    }
    LaunchedEffect(url) {
        err = ""
        if (url.isNullOrBlank()) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    Box(
        Modifier.fillMaxWidth().height(200.dp).background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PlayArrow, null, tint = C.Hint, modifier = Modifier.size(38.dp))
                    Text("选择下面的频道开始播放", fontSize = 12.sp, color = C.Hint)
                }
            }
        }
        // 名称
        Text(
            title, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        // 全屏
        if (!url.isNullOrBlank()) {
            Icon(
                Icons.Filled.Fullscreen, "全屏", tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clickable { onFullscreen() }
                    .size(24.dp)
            )
        }
        if (err.isNotEmpty()) {
            Text(
                err, fontSize = 11.sp, color = Color(0xFFFF8A80),
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }
    }
}

@Composable
private fun GroupChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (on) Brush.linearGradient(listOf(C.Card2, C.Card2))
                else Brush.linearGradient(listOf(C.Card, C.Card))
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text, fontSize = 11.sp,
            color = if (on) C.White else C.Hint,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}
