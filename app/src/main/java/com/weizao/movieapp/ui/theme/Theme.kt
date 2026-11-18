package com.weizao.movieapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 唯造智物 · 主流影视 App 视觉规范 */
object C {
    // 可切换的配色（深色 / 浅色），由 C.apply(dark) 统一写入
    var Bg = Color(0xFF0A0D14); private set
    var Bg2 = Color(0xFF0F131C); private set
    var Card = Color(0xFF161B25); private set
    var Card2 = Color(0xFF1D2330); private set
    var Dialog = Color(0xFF1B2029); private set

    /** 品牌主色（与图标同源：亮蓝 → 紫） */
    val Blue = Color(0xFF3D7BFF)
    val Purple = Color(0xFF8B5CF6)
    val BlueDeep = Color(0xFF1E3FA8)

    // White 在这里的角色是「主文字色」：深色下是白，浅色下是近黑
    var White = Color(0xFFFFFFFF); private set
    var Gray = Color(0xFF9AA4B8); private set
    var Divider = Color(0xFF242A36); private set
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Warn = Color(0xFFFF5470)
    var Hint = Color(0xFF5A6478); private set
    val Gold = Color(0xFFFFC24B)

    /** 主按钮渐变 */
    val Grad = Brush.horizontalGradient(listOf(Blue, Purple))

    /** 海报底部压暗渐变 */
    val PosterShade = Brush.verticalGradient(
        listOf(Color(0x00000000), Color(0x66000000), Color(0xE6000000))
    )

    /** 是否当前是深色 */
    var dark = true; private set

    fun apply(isDark: Boolean) {
        dark = isDark
        if (isDark) {
            Bg = Color(0xFF0A0D14); Bg2 = Color(0xFF0F131C)
            Card = Color(0xFF161B25); Card2 = Color(0xFF1D2330); Dialog = Color(0xFF1B2029)
            White = Color(0xFFFFFFFF); Gray = Color(0xFF9AA4B8)
            Divider = Color(0xFF242A36); Hint = Color(0xFF5A6478)
        } else {
            Bg = Color(0xFFF3F5F9); Bg2 = Color(0xFFFAFBFD)
            Card = Color(0xFFFFFFFF); Card2 = Color(0xFFEDF0F5); Dialog = Color(0xFFFFFFFF)
            White = Color(0xFF15181F); Gray = Color(0xFF5C6474)
            Divider = Color(0xFFE2E6EE); Hint = Color(0xFF8D95A6)
        }
    }

    /** 详情页顶部大图遮罩 */
    val HeroShade = Brush.verticalGradient(
        listOf(Color(0x99000000), Color(0x33000000), Color(0xFF0A0D14))
    )
}

@Composable
private fun scheme() = (if (C.dark) darkColorScheme(
    primary = C.Blue,
    background = C.Bg,
    surface = C.Card,
    onPrimary = Color.White,
    onBackground = C.White,
    onSurface = C.White
) else androidx.compose.material3.lightColorScheme(
    primary = C.Blue,
    background = C.Bg,
    surface = C.Card,
    onPrimary = Color.White,
    onBackground = C.White,
    onSurface = C.White
))

@Composable
private fun unusedDarkScheme() = darkColorScheme(
    primary = C.Blue,
    background = C.Bg,
    surface = C.Card,
    onPrimary = Color.White,
    onBackground = C.White,
    onSurface = C.White
)

@Composable
fun WeiZaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), content = content)
}
