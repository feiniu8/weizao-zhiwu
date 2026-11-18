package com.weizao.movieapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.theme.C

/** 海报卡片：主流影视 App 样式——无卡片底，海报 + 角标 + 两行文字 */
@Composable
fun VodCard(v: Vod, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val model = remember(v.pic) {
        if (v.pic.isBlank()) null
        else ImageRequest.Builder(ctx)
            .data(v.pic)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", refererOf(v.pic))
            .crossfade(true)
            .build()
    }
    Column(Modifier.clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .background(C.Card)
        ) {
            AsyncImage(
                model = model, contentDescription = v.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(C.PosterShade))
            if (UiFlags.showScore && v.score.isNotBlank() && v.score.toDoubleOrNull() != null && v.score.toDouble() > 0) {
                Text(
                    v.score, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CF7D4),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (v.remarks.isNotBlank()) {
                Text(
                    v.remarks,
                    fontSize = 10.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color(0x66000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            v.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = C.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            listOf(v.year, v.area).filter { it.isNotBlank() }.joinToString(" · ")
                .ifBlank { v.type.ifBlank { " " } },
            fontSize = 11.sp, color = C.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** 海报防盗链：多数采集站图片要求来源站 Referer */
private fun refererOf(pic: String): String = runCatching {
    val m = Regex("^(https?)://([^/?#]+)", RegexOption.IGNORE_CASE).find(pic) ?: return@runCatching ""
    val scheme = m.groupValues[1].lowercase()
    val host = m.groupValues[2]
    "$scheme://$host/"
}.getOrDefault("")

/** 主按钮：品牌渐变胶囊 */
@Composable
fun GradButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(C.Grad)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 次级按钮：描边胶囊 */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(C.Card2)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(text, fontSize = 13.sp, color = C.White)
    }
}

/** 栏目标题 */
@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(15.dp).clip(RoundedCornerShape(2.dp)).background(C.Grad)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, fontSize = 12.sp, color = C.Gray, modifier = Modifier.clickable { onAction() })
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⊙", fontSize = 44.sp, color = C.Hint)
            Spacer(Modifier.height(10.dp))
            Text(text, fontSize = 13.sp, color = C.Hint)
        }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.Blue, modifier = Modifier.size(32.dp))
    }
}

/** 通用确认弹窗 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Dialog,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.White) },
        text = { Text(message, fontSize = 13.sp, color = C.Gray) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, fontSize = 14.sp, color = C.Blue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 14.sp, color = C.Gray) }
        }
    )
}

/** 顶部标题栏 */
@Composable
fun TopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.White, modifier = Modifier.weight(1f))
        actions()
    }
}


/** 轻量 UI 开关：设置页改了立刻生效，避免每处都传参 */
object UiFlags {
    var showScore by mutableStateOf(true)
}
