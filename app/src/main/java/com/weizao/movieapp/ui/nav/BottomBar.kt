package com.weizao.movieapp.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weizao.movieapp.ui.theme.C

enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Ranking("榜单", Icons.Filled.Leaderboard),
    Search("搜索", Icons.Filled.Search),
    Live("直播", Icons.Filled.LiveTv),
    Mine("我的", Icons.Filled.AccountCircle)
}

/** 底部导航：58dp，图标 23dp，选中蓝色并放大 1.05 */
@Composable
fun BottomBar(current: Tab, onChange: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(C.Card),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 底部只保留「首页 / 榜单 / 直播 / 我的」，搜索入口从底部去掉
        Tab.entries.filter { it != Tab.Search }.forEach { t ->
            val on = t == current
            val scale by animateFloatAsState(if (on) 1.05f else 1f, label = "s")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onChange(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    t.icon, t.label,
                    Modifier.size(23.dp).scale(scale),
                    tint = if (on) C.Blue else C.Gray
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) C.Blue else C.Gray
                )
            }
        }
    }
}
