package com.weizao.movieapp.db

/** 内置默认源（实测可用，按速度排序） */
object Defaults {
    val SOURCES: List<Pair<String, String>> = listOf(
        "360资源" to "https://360zy.com/api.php/provide/vod",
        "红牛采集" to "http://hongniuzy2.com/api.php/provide/vod/from/hnm3u8/at/json",
        "西瓜资源" to "https://caiji.xgzyapi.com/api.php/provide/vod",
        "暴风资源" to "https://bfzyapi.com/api.php/provide/vod",
        "八九影视" to "https://api.wujinapi.com/api.php/provide/vod",
        "量子影视" to "https://cj.lziapi.com/api.php/provide/vod",
        "360影视" to "https://360zyzz.com/api.php/provide/vod/at/json",
        "非凡影视" to "https://cj.ffzyapi.com/api.php/provide/vod/from/ffm3u8"
    )
}
