package com.weizao.movieapp.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.weizao.movieapp.network.Road
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.network.VodParser
import com.weizao.movieapp.ui.common.GhostButton
import com.weizao.movieapp.ui.common.GradButton
import com.weizao.movieapp.ui.common.LoadingBox
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

/** 详情页：大图 + 信息 + 简介展开 + 线路 + 选集（主流影视 App 结构） */
@Composable
fun DetailScreen(
    vod: Vod,
    vm: AppViewModel,
    onPlay: (Vod, Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var full by remember { mutableStateOf<Vod?>(null) }
    var roads by remember { mutableStateOf<List<Road>>(emptyList()) }
    var road by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var asc by remember { mutableStateOf(false) }
    var fav by remember { mutableStateOf(vm.isFavorite(vod.vodId.toString())) }
    var ready by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf("") }

    LaunchedEffect(vod.vodId) {
        val api = vm.currentApi() ?: ""
        apiUrl = api
        vm.detail(api, vod.vodId.toString()) { r ->
            val f = r ?: vod
            full = f
            roads = VodParser.roads(f)
            ready = true
        }
    }

    val d = full ?: vod
    val eps = roads.getOrNull(road)?.episodes ?: emptyList()
    val meta = listOf(d.year, d.area, d.type, d.remarks).filter { it.isNotBlank() }

    Column(Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState())) {

        // 顶部大图
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(
                model = d.pic, contentDescription = d.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(C.HeroShade))

            // 返回交给系统返回键（页面上不放返回按钮）

            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    d.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.take(4).forEach { m ->
                        Text(
                            m, fontSize = 11.sp, color = Color(0xFFE6E9F0),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 操作区
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradButton("播放", onClick = {
                if (eps.isNotEmpty()) onPlay(d, road, 0)
            })
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(C.Card2)
                    .clickable {
                        vm.toggleFavorite(d.vodId.toString(), d.name, d.pic, apiUrl)
                        fav = !fav
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null, tint = if (fav) C.Warn else C.Gray, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(if (fav) "已收藏" else "收藏", fontSize = 13.sp, color = if (fav) C.White else C.Gray)
            }
            Spacer(Modifier.weight(1f))
            if (eps.isNotEmpty()) {
                Text("共 ${eps.size} 集", fontSize = 12.sp, color = C.Gray)
            }
        }

        // 简介
        if (d.content.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    d.content.replace(Regex("<[^>]+>"), "").trim(),
                    fontSize = 13.sp, color = C.Gray, lineHeight = 20.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (expanded) "收起" else "展开全部",
                    fontSize = 12.sp, color = C.Blue,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp)
                )
            }
        }

        // 演员 / 导演
        if (d.actor.isNotBlank() || d.director.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (d.director.isNotBlank()) Text("导演：${d.director}", fontSize = 12.sp, color = C.Gray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.actor.isNotBlank()) Text("主演：${d.actor}", fontSize = 12.sp, color = C.Gray,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        // 播放源（每条线路显示集数，和主流客户端一致）
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("播放源", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
            Spacer(Modifier.weight(1f))
            Text("${roads.size} 条线路", fontSize = 11.sp, color = C.Hint)
        }
        if (roads.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                roads.indices.forEach { i ->
                    val on = i == road
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) C.Blue else C.Card2)
                            .clickable { road = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            roads[i].name + "  " + roads[i].episodes.size + "集", fontSize = 12.sp,
                            color = if (on) Color.White else C.Gray,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 选集
        if (eps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选集", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White)
                Spacer(Modifier.width(6.dp))
                Text("共 ${eps.size} 集", fontSize = 11.sp, color = C.Hint)
                Spacer(Modifier.weight(1f))
                Text(
                    if (asc) "升序 ↑" else "降序 ↓", fontSize = 12.sp, color = C.Blue,
                    modifier = Modifier.clickable { asc = !asc }.padding(6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                (if (asc) eps else eps.reversed()).chunked(5).forEachIndexed { rowIdx, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEachIndexed { colIdx, ep ->
                            val pos = rowIdx * 5 + colIdx
                            val index = if (asc) pos else eps.size - 1 - pos
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(C.Card)
                                    .clickable { onPlay(d, road, index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ep.name, fontSize = 12.sp, color = C.White, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (!ready) {
            Box(Modifier.fillMaxWidth().height(120.dp)) { LoadingBox() }
        }
        Spacer(Modifier.height(28.dp))
    }
}
