package com.weizao.movieapp.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.weizao.movieapp.network.UpdateFile
import com.weizao.movieapp.ui.theme.C

/** 更新小窗口：发现新版本 / 下载进度 / 后台下载提示 */
@Composable
fun UpdateSheet(
    file: UpdateFile?,
    progress: Int,
    downloading: Boolean,
    failed: Boolean,
    onStart: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    if (file == null) return
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Card)
                .padding(20.dp)
        ) {
            Text("发现新版本", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White)
            Spacer(Modifier.height(10.dp))
            Text("v${file.version}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Blue)
            Spacer(Modifier.height(6.dp))
            Text("安装包 ${fmtSize(file.size)} · 来自 小飞机网盘", fontSize = 12.sp, color = C.Gray)
            Spacer(Modifier.height(14.dp))

            when {
                failed -> {
                    Text("下载失败，请检查网络后重试", fontSize = 12.sp, color = C.Warn)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("重试下载", C.Blue, onStart)
                }
                downloading -> {
                    Text("后台下载中 $progress%", fontSize = 13.sp, color = C.White)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = C.Purple,
                        trackColor = C.Card2
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "可以按返回继续用 App，下载不会中断；下完会跳到安装界面",
                        fontSize = 11.sp, color = C.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("收起来", onDismiss)
                        PrimaryButton(if (progress >= 100) "安装" else "后台下载", C.Blue, onInstall)
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("取消", onDismiss)
                        PrimaryButton("更新", C.Blue, onStart)
                    }
                }
            }
        }
    }
}

private fun fmtSize(b: Long): String = when {
    b <= 0 -> "未知大小"
    b > 1024L * 1024 * 1024 -> String.format("%.2f GB", b / 1024.0 / 1024 / 1024)
    b > 1024L * 1024 -> String.format("%.1f MB", b / 1024.0 / 1024)
    else -> "${b / 1024} KB"
}

@Composable
private fun PrimaryButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(color, color)))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, color = Color.White) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(C.Card2)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, color = C.Gray) }
}
