@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.weizao.movieapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.common.EmptyBox
import com.weizao.movieapp.ui.common.LoadingBox
import com.weizao.movieapp.ui.common.VodCard
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.utils.TimeUtil
import com.weizao.movieapp.viewmodel.AppViewModel
import kotlinx.coroutines.delay

// 首页这一行只做「进分类」的入口，真正的筛选在分类页里
private val TABS = listOf("首页", "电视剧", "电影", "动漫", "综艺", "短剧", "分类")
private val HOME_TAG = "热播"
private val AREAS = listOf("全部", "大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "英国", "法国")
private val YEARS = listOf("全部", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "更早")
private val SORTS = listOf("综合" to "", "最新" to "time", "最热" to "hits", "评分" to "score")

/** 某 tab 对应哪个分类（各源分类命名不统一，按关键字找最近的一个） */
private fun matchType(tab: String, cats: List<Pair<String, String>>): String {
    val keys = when (tab) {
        "电视剧" -> listOf("连续剧", "电视剧", "国产剧", "欧美剧", "日韩剧")
        "电影" -> listOf("电影", "电影片")
        "动漫" -> listOf("动漫", "动画", "番剧")
        "综艺" -> listOf("综艺", "综艺片")
        "短剧" -> listOf("短剧", "微短剧")
        else -> emptyList()
    }
    return cats.firstOrNull { c -> keys.any { c.first.contains(it) || it.contains(c.first) } }?.first
        ?: cats.firstOrNull()?.first ?: "全部"
}

/**
 * 首页：顶部搜索 + 文字 tab（首页 / 电视剧 / 电影 / 动漫 / 综艺 / 短剧）。
 * 「首页」是推荐位（Banner + 热播 / 最新 / 高分），其余 tab 里是四行筛选 + 三列海报，
 * 滚到底自动加载——和主流影视客户端的分区交互一致。
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onPlay: (Vod) -> Unit,
    onSearch: () -> Unit = {},
    onResume: (Vod, String) -> Unit = { _, _ -> },
    onOpenCategory: (String) -> Unit = {}
) {
    val history by vm.history.collectAsState()
    val list by vm.list.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val prefs by vm.prefs.collectAsState()

    var tab by rememberSaveable { mutableStateOf("首页") }
    var sections by remember { mutableStateOf<List<Pair<String, List<Vod>>>>(emptyList()) }
    var cats by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var secLoading by remember { mutableStateOf(false) }
    var typeSections by remember { mutableStateOf<List<Pair<String, List<Vod>>>>(emptyList()) }

    var typeSel by rememberSaveable { mutableStateOf("全部") }
    var area by rememberSaveable { mutableStateOf("全部") }
    var year by rememberSaveable { mutableStateOf("全部") }
    var sort by rememberSaveable { mutableStateOf("综合") }

    val refreshing = remember { mutableStateOf(false) }
    val refreshState = rememberSwipeRefreshState(isRefreshing = refreshing.value)
    val grid = rememberLazyGridState()

    suspend fun loadSections() {
        secLoading = true
        cats = vm.catsOnce()
        sections = vm.sections()
        secLoading = false
    }

    fun byCode() = SORTS.firstOrNull { it.first == sort }?.second ?: ""

    fun loadCategory(refresh: Boolean) {
        val typeId = if (typeSel == "全部") "" else cats.firstOrNull { it.first == typeSel }?.second ?: ""
        vm.loadFilter(
            typeId = typeId,
            area = if (area == "全部") "" else area,
            year = if (year == "全部") "" else if (year == "更早") "2018" else year,
            by = byCode(),
            refresh = refresh
        )
    }

    LaunchedEffect(Unit) {
        secLoading = true
        sections = vm.rankSections()
        secLoading = false
        vm.loadHome(HOME_TAG, true)
    }

    // 切 tab：重置筛选，按该分类拉第一页
    LaunchedEffect(tab) {
        if (tab != "首页") {
            if (cats.isEmpty()) cats = vm.catsOnce()
            typeSections = vm.sectionsForType(tab)
        }
    }

    // 滚到底自动加载
    LaunchedEffect(tab, typeSel, area, year, sort) {
        snapshotFlow {
            val total = grid.layoutInfo.totalItemsCount
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }.collect { near ->
            if (near && tab != "首页" && !loading) loadCategory(false)
        }
    }

    val bannerItems = sections.firstOrNull()?.second?.take(5) ?: emptyList()

    SwipeRefresh(
        state = refreshState,
        onRefresh = {
            refreshing.value = true
            if (tab == "首页") {
                kotlinx.coroutines.runBlocking { loadSections() }
            } else loadCategory(true)
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LaunchedEffect(loading, secLoading) {
            if (!loading && !secLoading && refreshing.value) refreshing.value = false
        }
        val wallUri = prefs["wallpaper_uri"].orEmpty()
        val wallUrl = prefs["wallpaper_url"].orEmpty()
        val wall = wallUrl.ifBlank { wallUri }
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            if (wall.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = wall,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color(0xE60A0D14)))
            }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 搜索栏（整宽胶囊）
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp))
                            .background(C.Card).clickable { onSearch() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = C.Hint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("搜索影片名、演员", fontSize = 12.sp, color = C.Hint)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(C.Card)
                            .clickable { onSearch() },
                        contentAlignment = Alignment.Center
                    ) { Text("搜", fontSize = 12.sp, color = C.Blue) }
                }
            }

            // 文字 tab（选中项下划线）
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    TABS.forEach { t ->
                        val on = t == tab
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                t, fontSize = 15.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) C.White else C.Gray,
                                modifier = Modifier.clickable { if (t == "分类") onOpenCategory("全部") else tab = t }.padding(bottom = 6.dp)
                            )
                            Box(
                                Modifier
                                    .width(18.dp).height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (on) C.Grad else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            )
                        }
                    }
                }
            }

            if (tab == "首页") {
                // 继续观看
                history.firstOrNull()?.let { h ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            ContinueCard(h) {
                                onResume(
                                    Vod(vodId = h.vodId.toLongOrNull() ?: 0L, name = h.vodName, pic = h.pic),
                                    h.playUrl
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                if (bannerItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column { Banner(bannerItems, onPlay); Spacer(Modifier.height(12.dp)) }
                    }
                }
                sections.forEach { (title, data) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.width(3.dp).height(14.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(C.Grad)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White)
                            Spacer(Modifier.weight(1f))
                            Row(
                                Modifier.clickable { onOpenCategory("全部") },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("完整榜单", fontSize = 11.sp, color = C.Gray)
                                Text("›", fontSize = 13.sp, color = C.Gray)
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(data) { v ->
                                val index = data.indexOf(v)
                                Box(Modifier.width(104.dp)) {
                                    VodCard(v) { onPlay(v) }
                                    // 名次角标
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (index < 3) Color(0xE6FF5A3C) else Color(0xB3000000)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (secLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { LoadingBox() }
                    }
                }
            } else {
                // 类型 tab：不显示筛选行，直接给该类型的国产推荐分区
                if (typeSections.isEmpty() && secLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { LoadingBox() }
                    }
                }
                typeSections.forEach { (title, data) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title) { onOpenCategory(tab) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(data) { v -> Box(Modifier.width(104.dp)) { VodCard(v) { onPlay(v) } } }
                        }
                    }
                }
                if (typeSections.isEmpty() && !secLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyBox(error ?: "这个分类暂时没有内容")
                    }
                }
            }
        }
    }
    }
}

/** 一行筛选：左侧标签 + 右侧横滑胶囊 */
@Composable
private fun FilterLine(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = C.Hint, modifier = Modifier.width(28.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(options) { o ->
                val on = o == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                        .clickable { onPick(o) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
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

/** 分区标题：左边标题，右边「更多」 */
@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(C.Purple))
        Spacer(Modifier.width(7.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
        Spacer(Modifier.weight(1f))
        Text("更多 ›", fontSize = 12.sp, color = C.Hint, modifier = Modifier.clickable { onMore() })
    }
}

/** 首页「继续观看」一条：海报 + 片名 + 上次进度，点一下直接全屏续播 */
@Composable
private fun ContinueCard(h: com.weizao.movieapp.db.PlayHistory, onClick: () -> Unit) {
    val pct = if (h.duration > 0) (h.position.toFloat() / h.duration).coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(9.dp)).background(C.Card2)
        ) {
            if (h.pic.isNotBlank()) {
                AsyncImage(
                    model = h.pic, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                h.vodName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "上次看到 ${TimeUtil.fmt(h.position)}" + if (h.duration > 0) " / ${TimeUtil.fmt(h.duration)}" else "",
                fontSize = 11.sp, color = C.Gray, maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.Card2)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(pct.coerceAtLeast(0.02f))
                        .background(C.Purple)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("▶ 续播", fontSize = 12.sp, color = C.Blue)
    }
}

/** 顶部大图：3 秒自动轮播，底部渐变 + 播放按钮 + 圆点 */
@Composable
private fun Banner(items: List<Vod>, onClick: (Vod) -> Unit) {
    val pager = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(items) {
        while (items.size > 1) {
            delay(3000)
            val next = (pager.currentPage + 1) % items.size
            pager.animateScrollToPage(next)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(208.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card2)
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
            val v = items[i]
            Box(Modifier.fillMaxSize().clickable { onClick(v) }) {
                AsyncImage(
                    model = v.pic, contentDescription = v.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(C.PosterShade))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 100.dp, bottom = 16.dp)) {
                    Text(
                        v.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(v.year, v.area, v.remarks).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // 播放按钮
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(C.Grad)
                .clickable { items.getOrNull(pager.currentPage)?.let { onClick(it) } }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Text("立即播放", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(items.size) { i ->
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 6.dp else 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == pager.currentPage) C.Blue else Color(0x66FFFFFF))
                )
            }
        }
    }
}
