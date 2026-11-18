package com.weizao.movieapp.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.common.EmptyBox
import com.weizao.movieapp.ui.common.LoadingBox
import com.weizao.movieapp.ui.common.VodCard
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

private val AREAS = listOf("全部", "大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "英国", "法国")
private val YEARS = listOf("全部", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "更早")
private val SORTS = listOf("综合" to "", "最新" to "time", "最热" to "hits", "评分" to "score")

/**
 * 分类页（参照腾讯视频的筛选式布局）：
 * 类型 / 地区 / 年份 / 排序 四行可选，下面三列海报，滚到底自动加载下一页。
 */
@Composable
fun CategoryScreen(
    vm: AppViewModel,
    initialType: String = "全部",
    onPlay: (Vod) -> Unit,
    onBack: () -> Unit = {}
) {
    val list by vm.list.collectAsState()
    val loading by vm.loading.collectAsState()

    var cats by remember { mutableStateOf(listOf("全部" to "")) }
    // 用 rememberSaveable：从分类页点进详情再返回时，筛选条件还在
    var curType by rememberSaveable { mutableStateOf(initialType) }
    var area by rememberSaveable { mutableStateOf("全部") }
    var year by rememberSaveable { mutableStateOf("全部") }
    var sort by rememberSaveable { mutableStateOf("综合") }

    // 先拿接口的 class 列表，拿不到就用常见分类兜底
    LaunchedEffect(Unit) {
        vm.categories { raw ->
            val names = raw.map { it.first to it.second }
            cats = listOf("全部" to "") + names.ifEmpty {
                listOf("电影" to "1", "电视剧" to "2", "动漫" to "3", "综艺" to "4")
            }
            val hit = cats.firstOrNull { it.first == curType } ?: cats.firstOrNull()
            curType = hit?.first ?: "全部"
            reload(vm, cats, curType, area, year, sort, true)
        }
    }

    fun pick(type: String? = null, a: String? = null, y: String? = null, s: String? = null) {
        if (type != null) curType = type
        if (a != null) area = a
        if (y != null) year = y
        if (s != null) sort = s
        reload(vm, cats, curType, area, year, sort, true)
    }

    val grid = rememberLazyGridState()

    // 滚到底自动加载下一页（不用再点「查看更多」）
    LaunchedEffect(Unit) {
        snapshotFlow {
            val total = grid.layoutInfo.totalItemsCount
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 5
        }.collect { near ->
            if (near && !loading) {
                reload(vm, cats, curType, area, year, sort, false)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        // 顶部：返回 + 标题
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("分类", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White)
            Spacer(Modifier.weight(1f))
            Text(
                if (loading) "加载中…" else "共 ${list.size} 部",
                fontSize = 11.sp, color = C.Hint
            )
        }

        FilterRow("类型", cats.map { it.first }, curType) { pick(type = it) }
        FilterRow("地区", AREAS, area) { pick(a = it) }
        FilterRow("年份", YEARS, year) { pick(y = it) }
        FilterRow("排序", SORTS.map { it.first }, sort) { pick(s = it) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && list.isEmpty() -> LoadingBox()
                list.isEmpty() -> EmptyBox("该筛选没有内容，换个条件试试")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = grid,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(list) { v -> VodCard(v) { onPlay(v) } }
                    if (loading) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("加载中…", fontSize = 12.sp, color = C.Hint)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一行筛选：左侧小标签 + 右侧可横滑的胶囊 */
@Composable
private fun FilterRow(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = C.Hint, modifier = Modifier.width(30.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { o ->
                val on = o == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                        .clickable { onPick(o) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Text(
                        o, fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color.White else C.Gray
                    )
                }
            }
        }
    }
}

private fun reload(
    vm: AppViewModel,
    cats: List<Pair<String, String>>,
    type: String,
    area: String,
    year: String,
    sort: String,
    refresh: Boolean
) {
    val typeId = cats.firstOrNull { it.first == type }?.second ?: ""
    val by = SORTS.firstOrNull { it.first == sort }?.second ?: ""
    vm.loadFilter(
        typeId = typeId,
        area = if (area == "全部") "" else area,
        year = if (year == "全部") "" else if (year == "更早") "2018" else year,
        by = by,
        refresh = refresh
    )
}
