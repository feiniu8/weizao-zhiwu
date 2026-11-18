package com.weizao.movieapp.ui.rank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

private val BOARDS = listOf("热播榜" to "hits", "新剧榜" to "time", "高分榜" to "score")

/** 榜单页：三个榜切换，行式列表（名次 + 海报 + 信息 + 评分） */
@Composable
fun RankingScreen(vm: AppViewModel, onPlay: (Vod) -> Unit) {
    var board by rememberSaveable { mutableStateOf("热播榜") }
    var data by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(board) {
        loading = true
        data = vm.ranking(BOARDS.firstOrNull { it.first == board }?.second ?: "hits")
        loading = false
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("榜单", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BOARDS.forEach { (name, _) ->
                val on = name == board
                Box(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                        .clickable { board = name }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        name, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color.White else C.Gray
                    )
                }
            }
        }

        if (loading && data.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", fontSize = 13.sp, color = C.Hint)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(data) { i, v -> RankRow(i + 1, v) { onPlay(v) } }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, v: Vod, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.Card)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (rank <= 3) listOf("①", "②", "③")[rank - 1] else rank.toString(),
            fontSize = if (rank <= 3) 18.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) C.Warn else C.Hint,
            modifier = Modifier.width(26.dp)
        )
        Box(Modifier.width(58.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).background(C.Card2)) {
            AsyncImage(
                model = v.pic, contentDescription = v.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(v.year, v.area, v.type, v.remarks).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.sp, color = C.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (v.score.isNotBlank() && v.score.toDoubleOrNull() != null && v.score.toDouble() > 0) {
            Text(v.score, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CF7D4))
        }
    }
}
