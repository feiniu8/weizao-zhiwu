package com.weizao.movieapp.ui.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.db.Source
import com.weizao.movieapp.ui.common.ConfirmDialog
import com.weizao.movieapp.ui.common.EmptyBox
import com.weizao.movieapp.ui.common.TopBar
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.utils.ValidUtil
import com.weizao.movieapp.viewmodel.AppViewModel

@Composable
fun SourceScreen(vm: AppViewModel, onBack: (() -> Unit)? = null) {
    val sources by vm.sources.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<Source?>(null) }
    var showTest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var testTitle by remember { mutableStateOf("") }
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    var quick by remember { mutableStateOf("") }
    var quickMsg by remember { mutableStateOf("") }
    val subs by vm.subs.collectAsState()
    var subMsg by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("采集接口管理") {
            Text("批量导入", fontSize = 14.sp, color = C.Blue,
                modifier = Modifier.clickable { showImport = true }.padding(8.dp))
        }

        // 顶部：直接粘贴接口地址添加
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp)).background(C.Card).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("添加采集接口", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
                Spacer(Modifier.weight(1f))
                Text("共 ${sources.size} 个源", fontSize = 12.sp, color = C.Gray)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = quick, onValueChange = { quick = it; quickMsg = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴后自动识别：采集接口 / TVBox 订阅 JSON / m3u 直播订阅", fontSize = 12.sp, color = C.Hint) },
                minLines = 1, maxLines = 2, singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = C.White, unfocusedTextColor = C.White,
                    focusedBorderColor = C.Blue, unfocusedBorderColor = C.Divider
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("恢复内置源", fontSize = 13.sp, color = C.Gray,
                    modifier = Modifier.clickable {
                        vm.restoreDefaults { n -> quickMsg = if (n > 0) "已补回 $n 个内置源" else "内置源都在，无需补回" }
                    }.padding(vertical = 6.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "添加订阅", fontSize = 13.sp, color = C.Gray,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.Card2)
                        .clickable {
                            val a = quick.trim()
                            if (a.isEmpty()) { quickMsg = "请先粘贴地址"; return@clickable }
                            vm.addSub(nameFromUrl(a), a)
                            quick = ""
                            quickMsg = "已添加订阅（TVBox 方式：先保存，之后在下方点「解析」导入接口）"
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(C.Blue)
                        .clickable {
                            val a = quick.trim()
                            if (a.isEmpty()) { quickMsg = "请先粘贴地址"; return@clickable }
                            quickMsg = "正在识别…"
                            val isText = a.startsWith("{") || a.startsWith("#EXTM3U")
                            val cb: (com.weizao.movieapp.network.SourceImporter.Result, Int, Int) -> Unit =
                                { r, added, lives ->
                                    quickMsg = when {
                                        added > 0 || lives > 0 -> "已导入：采集接口 $added 个、直播源 $lives 个"
                                        else -> r.note
                                    }
                                    if (added > 0) quick = ""
                                }
                            if (isText) vm.importText(a, cb) else vm.importUrl(a, cb)
                        }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) { Text("添加", fontSize = 14.sp, color = Color.White) }
            }
            if (quickMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    quickMsg, fontSize = 12.sp,
                    color = if (quickMsg.startsWith("已导入") || quickMsg.startsWith("已添加")) C.Green else C.Warn
                )
                if (!quickMsg.startsWith("已导入") && !quickMsg.startsWith("已添加") &&
                    !quickMsg.startsWith("这是直播订阅") && quick.isNotBlank()
                ) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "仍然按采集接口添加", fontSize = 12.sp, color = C.Blue,
                        modifier = Modifier.clickable {
                            val a = quick.trim()
                            vm.addSource(nameFromUrl(a), a)
                            quick = ""
                            quickMsg = "已添加（若测试仍失败，说明该地址确实不是采集接口）"
                        }.padding(vertical = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subs.isNotEmpty()) {
                item {
                    Text(
                        "我的订阅（${subs.size}）· 影迷同款：先保存，再解析",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.White,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                items(subs) { (n, u) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.Card)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n, fontSize = 14.sp, color = C.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(u, fontSize = 11.sp, color = C.Hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val m = subMsg[u]
                            if (!m.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    m, fontSize = 11.sp,
                                    color = if (m.startsWith("解析成功")) C.Green else C.Warn, maxLines = 2
                                )
                            }
                        }
                        Text(
                            "解析", fontSize = 12.sp, color = C.Blue,
                            modifier = Modifier
                                .clickable {
                                    subMsg = subMsg + (u to "正在解析…")
                                    vm.importUrl(u) { r, added, lives ->
                                        subMsg = subMsg + (u to if (added > 0 || lives > 0)
                                            "解析成功：接口 $added 个、直播源 $lives 个"
                                        else r.note)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Text(
                            "删除", fontSize = 12.sp, color = C.Red,
                            modifier = Modifier
                                .clickable { vm.removeSub(u) }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item {
                Text(
                    "采集接口（${sources.size}）",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.White
                )
            }

            if (sources.isEmpty()) {
                item { EmptyBox("还没有数据源，在上方粘贴一个接口地址即可添加") }
            }

            items(sources) { s ->
                SourceCard(
                    s = s,
                    onClick = { vm.selectSource(s.id) },
                    onTest = {
                        testing = true; testTitle = s.name; testMsg = "正在测试…"
                        showTest = s.name to s.api
                        vm.testSource(s.api) { n ->
                            testing = false
                            testMsg = if (n >= 0) "测试成功，共 $n 条影片" else "链接失效 / 超时 / 非 JSON"
                        }
                    },
                    onDelete = { showDelete = s }
                )
            }
        }
    }

    if (showAdd) AddSourceDialog(
        onDismiss = { showAdd = false },
        onSave = { n, a -> vm.addSource(n, a); showAdd = false }
    )

    if (showImport) BatchDialog(
        onDismiss = { showImport = false },
        onImport = { t, cb -> vm.batchImport(t) { n -> cb(n) } }
    )

    showDelete?.let { s ->
        ConfirmDialog(
            title = "删除数据源",
            message = "确定删除该数据源？删除后无法恢复",
            confirmText = "删除",
            onConfirm = { vm.deleteSource(s); showDelete = null },
            onDismiss = { showDelete = null }
        )
    }

    showTest?.let {
        AlertDialog(
            onDismissRequest = { if (!testing) showTest = null },
            containerColor = C.Dialog,
            shape = RoundedCornerShape(16.dp),
            title = { Text("测试 $testTitle", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = C.Blue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(testMsg, fontSize = 13.sp, color = if (testMsg.contains("成功")) C.Green else C.Warn)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTest = null }, enabled = !testing) {
                    Text("关闭", fontSize = 14.sp, color = C.Blue)
                }
            }
        )
    }
}

@Composable
private fun SourceCard(s: Source, onClick: () -> Unit, onTest: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card)
            .then(if (s.isSelected) Modifier.border(2.dp, C.Blue, RoundedCornerShape(14.dp)) else Modifier)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White, modifier = Modifier.weight(1f))
            if (s.isSelected) Text("当前源", fontSize = 11.sp, color = C.Blue)
        }
        Spacer(Modifier.height(6.dp))
        Text(s.api, fontSize = 12.sp, color = C.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(72.dp, 32.dp).clip(RoundedCornerShape(8.dp)).background(C.Green)
                    .clickable(onClick = onTest),
                contentAlignment = Alignment.Center
            ) { Text("测试", fontSize = 14.sp, color = Color.White) }
            Box(
                Modifier.size(72.dp, 32.dp).clip(RoundedCornerShape(8.dp)).background(C.Red)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { Text("删除", fontSize = 14.sp, color = Color.White) }
        }
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var api by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Dialog,
        shape = RoundedCornerShape(16.dp),
        title = { Text("添加数据源", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("名称（最多20字）") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.White, unfocusedTextColor = C.White,
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Divider
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = api, onValueChange = { api = it },
                    label = { Text("API 地址") }, minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.White, unfocusedTextColor = C.White,
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Divider
                    )
                )
                if (err.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(err, fontSize = 12.sp, color = C.Warn)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim(); val a = api.trim()
                if (n.isEmpty() || a.isEmpty()) { err = "名称和地址不能为空"; return@TextButton }
                if (!ValidUtil.isUrl(a)) { err = "地址必须以 http:// 或 https:// 开头"; return@TextButton }
                onSave(n, a)
            }) { Text("保存", fontSize = 14.sp, color = C.Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", fontSize = 14.sp, color = C.Gray) } }
    )
}

@Composable
private fun BatchDialog(onDismiss: () -> Unit, onImport: (String, (Int) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Dialog,
        shape = RoundedCornerShape(16.dp),
        title = { Text("批量导入", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White) },
        text = {
            Column {
                Text("每行一条：名称,链接", fontSize = 12.sp, color = C.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.height(220.dp).fillMaxWidth(),
                    placeholder = { Text("源A,http://xxx/api.php/provide/vod", fontSize = 12.sp, color = C.Hint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.White, unfocusedTextColor = C.White,
                        focusedBorderColor = C.Blue, unfocusedBorderColor = C.Divider
                    )
                )
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 12.sp, color = C.Green)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onImport(text) { n -> msg = "成功导入 $n 条"; text = "" }
            }) { Text("导入", fontSize = 14.sp, color = C.Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", fontSize = 14.sp, color = C.Gray) } }
    )
}


/** 从接口地址推导一个默认名称，例如 caiji.xgzyapi.com -> Xgzyapi */
private fun nameFromUrl(url: String): String {
    return try {
        val host = java.net.URI(url).host ?: return "新数据源"
        host.removePrefix("www.").substringBefore('.')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } catch (e: Exception) {
        "新数据源"
    }
}
