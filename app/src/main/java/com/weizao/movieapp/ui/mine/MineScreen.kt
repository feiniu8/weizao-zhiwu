package com.weizao.movieapp.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.common.ConfirmDialog
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

private enum class Panel { None, History, Favorite }

@Composable
fun MineScreen(
    vm: AppViewModel,
    onOpenDetail: (Vod) -> Unit,
    onOpenSource: () -> Unit = {},
    onOpenLiveSource: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenMore: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onResume: (Vod, String) -> Unit = { _, _ -> }
) {
    val history by vm.history.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val live by vm.liveSources.collectAsState()
    var panel by remember { mutableStateOf(Panel.None) }
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(
            when (panel) {
                Panel.History -> "播放历史"
                Panel.Favorite -> "我的收藏"
                else -> "我的"
            }
        ) {
        }

        BackHandler(enabled = panel != Panel.None) { panel = Panel.None }

        when (panel) {
            Panel.None -> MenuList(
                historyCount = history.size,
                favCount = favorites.size,
                liveCount = live.size,
                onSource = onOpenSource,
                onLiveSource = onOpenLiveSource,
                onSettings = onOpenSettings,
                onMore = onOpenMore,
                onHistory = { panel = Panel.History },
                onFavorite = { panel = Panel.Favorite },
                onClear = { confirmClear = true },
                onAbout = { about = true },
                onCheckUpdate = onCheckUpdate
            )

            Panel.History -> LazyColumn(Modifier.fillMaxSize()) {
                if (history.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("还没有播放记录", fontSize = 13.sp, color = C.Hint)
                    }
                }
                items(history) { h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onResume(
                                    Vod(
                                        vodId = h.vodId.toLongOrNull() ?: 0L,
                                        name = h.vodName, pic = h.pic
                                    ),
                                    h.playUrl
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Poster(h.pic)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                h.vodName, fontSize = 14.sp, color = C.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (h.finished) "已看完"
                                else "看到 ${h.position / 60000} 分 ${h.position / 1000 % 60} 秒",
                                fontSize = 11.sp, color = C.Gray
                            )
                        }
                        Text(
                            "删除", fontSize = 12.sp, color = C.Red,
                            modifier = Modifier.clickable { vm.deleteHistory(h) }.padding(8.dp)
                        )
                    }
                }
            }

            Panel.Favorite -> LazyColumn(Modifier.fillMaxSize()) {
                if (favorites.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("还没有收藏", fontSize = 13.sp, color = C.Hint)
                    }
                }
                items(favorites) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDetail(
                                    Vod(
                                        vodId = f.vodId.toLongOrNull() ?: 0L,
                                        name = f.vodName, pic = f.pic
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Poster(f.pic)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.vodName, fontSize = 14.sp, color = C.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("点开看详情 / 选集", fontSize = 11.sp, color = C.Gray)
                        }
                        Text(
                            "取消收藏", fontSize = 12.sp, color = C.Gray,
                            modifier = Modifier
                                .clickable { vm.toggleFavorite(f.vodId, f.vodName, f.pic, f.sourceApi) }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) ConfirmDialog(
        title = "清除缓存",
        message = "将清除播放记录与图片缓存，确定继续？",
        onConfirm = { vm.clearHistory(); confirmClear = false },
        onDismiss = { confirmClear = false }
    )

    if (about) AlertDialog(
        onDismissRequest = { about = false },
        containerColor = C.Dialog,
        shape = RoundedCornerShape(16.dp),
        title = { Text("关于唯造智物", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White) },
        text = {
            Text(
                "唯造智物 1.0\n\n本机播放，无登录、无会员、无广告。\n影视数据来自你自行添加的网络公开接口，本应用不存储、不传播任何版权内容。",
                fontSize = 13.sp, color = C.Gray
            )
        },
        confirmButton = {
            TextButton(onClick = { about = false }) { Text("知道了", fontSize = 14.sp, color = C.Blue) }
        }
    )
}

@Composable
private fun Poster(pic: String) {
    Box(
        Modifier
            .size(46.dp, 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.Card2)
    ) {
        if (pic.isNotBlank()) {
            AsyncImage(
                model = pic, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
    }
}

private fun currentVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.5.1"
} catch (e: Throwable) { "1.5.1" }

@Composable
private fun MenuList(
    historyCount: Int,
    favCount: Int,
    liveCount: Int,
    onSource: () -> Unit,
    onLiveSource: () -> Unit,
    onSettings: () -> Unit,
    onMore: () -> Unit,
    onHistory: () -> Unit,
    onFavorite: () -> Unit,
    onClear: () -> Unit,
    onAbout: () -> Unit,
    onCheckUpdate: () -> Unit = {}
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ver = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()?.let { "v$it" } ?: ""
    }
    val items = listOf(
        "采集接口管理" to onSource,
        "直播源管理（$liveCount）" to onLiveSource,
        "播放设置" to onSettings,
        "设置" to onMore,
        "播放历史（$historyCount）" to onHistory,
        "我的收藏（$favCount）" to onFavorite,
        "清除缓存" to onClear,
        "检查更新" to onCheckUpdate,
        "关于我们" to onAbout,
        "当前版本 $ver" to onAbout
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(C.Card)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.Grad),
                contentAlignment = Alignment.Center
            ) { Text("唯", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("唯造智物", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White)
                Spacer(Modifier.height(2.dp))
                Text("本地模式 · 无需登录 · $ver", fontSize = 12.sp, color = C.Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$historyCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Blue)
                Text("看过", fontSize = 11.sp, color = C.Gray)
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$favCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Blue)
                Text("收藏", fontSize = 11.sp, color = C.Gray)
            }
        }
        items.forEach { (t, action) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Card)
                    .clickable { action() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t, fontSize = 15.sp, color = C.White, modifier = Modifier.weight(1f))
                Text("›", fontSize = 18.sp, color = C.Hint)
            }
        }
    }
}
