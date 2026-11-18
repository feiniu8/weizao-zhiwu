package com.weizao.movieapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.viewmodel.AppViewModel

/** 直播源管理：支持 m3u / txt 订阅，也支持 TVBox JSON（lives 字段） */
@Composable
fun LiveSourceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val sources by vm.liveSources.collectAsState()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("直播源管理") {
                    }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.Card)
                .padding(14.dp)
        ) {
            Text("添加直播源", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持 m3u/m3u8 订阅地址，也支持 TVBox 配置里的直播地址；粘贴后会自动识别",
                fontSize = 11.sp, color = C.Gray
            )
            Spacer(Modifier.height(8.dp))
            Edit(name, { name = it }, "名称（可留空）")
            Spacer(Modifier.height(8.dp))
            Edit(url, { url = it }, "订阅地址，如 https://xxx.tv/live.m3u")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (busy) Brush.linearGradient(listOf(C.Card2, C.Card2)) else C.Grad
                        )
                        .clickable {
                            if (busy) return@clickable
                            if (url.isBlank()) { msg = "请先填地址"; return@clickable }
                            busy = true
                            msg = "正在检测…"
                            vm.importUrl(url) { r, _, lv ->
                                busy = false
                                msg = if (r.ok) "已导入（直播源 ${lv} 个）" else r.note
                                if (r.ok) { name = ""; url = "" }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (busy) "检测中…" else "添加并检测", fontSize = 13.sp, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                if (msg.isNotBlank()) {
                    Text(
                        msg, fontSize = 12.sp,
                        color = if (msg.startsWith("已导入")) C.Green else C.Warn,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            "已添加 ${sources.size} 个直播源", fontSize = 12.sp, color = C.Hint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(sources) { (n, u) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.Card)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(n, fontSize = 14.sp, color = C.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(u, fontSize = 11.sp, color = C.Hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "删除", fontSize = 12.sp, color = C.Red,
                        modifier = Modifier
                            .clickable { vm.removeLiveSource(u) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Edit(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, fontSize = 12.sp, color = C.Hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = C.Card2,
            unfocusedContainerColor = C.Card2,
            focusedTextColor = C.White,
            unfocusedTextColor = C.White,
            cursorColor = C.Blue,
            focusedIndicatorColor = C.Blue,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
