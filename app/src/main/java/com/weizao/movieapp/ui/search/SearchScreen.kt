package com.weizao.movieapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.common.EmptyBox
import com.weizao.movieapp.ui.common.LoadingBox
import com.weizao.movieapp.ui.common.VodCard
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel
import kotlinx.coroutines.delay

private val HOT = listOf("热播剧", "电影", "动漫", "短剧", "悬疑", "动作", "喜剧", "科幻", "2026")

@Composable
fun SearchScreen(vm: AppViewModel, onPlay: (Vod) -> Unit) {
    var kw by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val kb = LocalSoftwareKeyboardController.current

    // 400ms 防抖
    LaunchedEffect(kw) {
        if (kw.isBlank()) { result = emptyList(); searched = false; return@LaunchedEffect }
        delay(400)
        loading = true
        vm.search(kw) { r ->
            result = r; loading = false; searched = true
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(C.Card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = C.Gray, modifier = Modifier.padding(horizontal = 10.dp).size(20.dp))
            Box(Modifier.weight(1f)) {
                if (kw.isEmpty()) Text("搜索影片名", fontSize = 14.sp, color = C.Hint)
                BasicTextField(
                    value = kw, onValueChange = { kw = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.White, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (kw.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null, tint = C.Gray,
                    modifier = Modifier.padding(10.dp).size(18.dp).clickable { kw = "" }
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingBox()
                !searched -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("大家都在搜", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = C.White)
                    Spacer(Modifier.height(10.dp))
                    HOT.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { h ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(C.Card)
                                        .clickable { kw = h },
                                    contentAlignment = Alignment.Center
                                ) { Text(h, fontSize = 12.sp, color = C.Gray) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                result.isEmpty() -> EmptyBox("没有找到相关影片")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { items(result) { v -> VodCard(v) { onPlay(v) } } }
            }
        }
    }
}
