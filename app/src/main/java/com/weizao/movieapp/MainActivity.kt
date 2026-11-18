package com.weizao.movieapp

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weizao.movieapp.network.Updater
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.ui.category.CategoryScreen
import com.weizao.movieapp.ui.rank.RankingScreen
import com.weizao.movieapp.ui.detail.DetailScreen
import com.weizao.movieapp.ui.home.HomeScreen
import com.weizao.movieapp.ui.live.LiveScreen
import com.weizao.movieapp.ui.live.LiveSourceScreen
import com.weizao.movieapp.ui.mine.MineScreen
import com.weizao.movieapp.ui.mine.SettingsScreen
import com.weizao.movieapp.ui.mine.MoreSettingsScreen
import com.weizao.movieapp.ui.nav.BottomBar
import com.weizao.movieapp.ui.nav.Tab
import com.weizao.movieapp.ui.player.PlayerScreen
import com.weizao.movieapp.ui.search.SearchScreen
import com.weizao.movieapp.ui.source.SourceScreen
import com.weizao.movieapp.ui.update.UpdateSheet
import com.weizao.movieapp.utils.Downloader
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.ui.theme.WeiZaoTheme
import com.weizao.movieapp.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            val vm: AppViewModel = viewModel()
            val themePrefs by vm.prefs.collectAsState()
            val themeMode = themePrefs["theme"] ?: "0"
            val dark = when (themeMode) {
                "1" -> false   // 浅色
                "2" -> true    // 深色
                else -> sysDark // 跟随系统
            }
            C.apply(dark)
            WeiZaoTheme {
                androidx.compose.runtime.key(dark) {
                val upPrefs = themePrefs
                LaunchedEffect(upPrefs["showscore"]) {
                    com.weizao.movieapp.ui.common.UiFlags.showScore = upPrefs["showscore"] != "0"
                }
                var tab by remember { mutableStateOf(Tab.Home) }
                var playing by remember { mutableStateOf<Vod?>(null) }
                var livePlay by remember { mutableStateOf<Pair<String, String>?>(null) }
                var startRoad by remember { mutableIntStateOf(0) }
                var startEp by remember { mutableIntStateOf(0) }
                var startEpUrl by remember { mutableStateOf<String?>(null) }
                var autoFull by remember { mutableStateOf(false) }
                var detail by remember { mutableStateOf<Vod?>(null) }
                var extra by remember { mutableStateOf<String?>(null) }
                var catFilter by remember { mutableStateOf<String?>(null) }

                val lp = livePlay
                val p = playing
                val dv = detail

                // 系统返回键：逐层退回；回到首页后需连按两次才退出 App
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                var lastBack by remember { mutableLongStateOf(0L) }

                var upFile by remember { mutableStateOf<com.weizao.movieapp.network.UpdateFile?>(null) }
                var upShow by remember { mutableStateOf(false) }
                var upDl by remember { mutableLongStateOf(-1L) }
                var upProg by remember { mutableIntStateOf(0) }
                var upFail by remember { mutableStateOf(false) }
                var upUrl by remember { mutableStateOf<String?>(null) }
                val verName = remember {
                    runCatching {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                    }.getOrNull() ?: "1.5.2"
                }
                fun startUpdate() {
                    val f = upFile ?: return
                    val url = upUrl ?: vm.prefs.value["update_url"]
                        ?.takeIf { it.isNotBlank() && !it.contains("123pan") }
                        ?: Updater.DEFAULT_URL
                    upFail = false
                    Toast.makeText(ctx, "正在获取下载地址…", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val link = withContext(Dispatchers.IO) {
                            runCatching { Updater.downloadLink(url, f) }.getOrNull()
                        }
                        if (link == null) {
                            upFail = true
                            Toast.makeText(ctx, "未取到直链，已打开分享页手动下载", Toast.LENGTH_LONG).show()
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            return@launch
                        }
                        upProg = 0
                        upDl = Downloader.start(ctx, link, f.name)
                        Toast.makeText(ctx, "已开始后台下载，返回不影响", Toast.LENGTH_SHORT).show()
                    }
                }
                fun checkUpdate(showAlways: Boolean) {
                    scope.launch {
                        val hit = withContext(Dispatchers.IO) {
                            runCatching { Updater.checkAny(vm.prefs.value["update_url"], verName) }.getOrNull()
                        }
                        if (hit != null) {
                            upUrl = hit.first
                            upFile = hit.second; upFail = false; upProg = 0; upShow = true
                        } else if (showAlways) {
                            Toast.makeText(ctx, "已是最新版本 v$verName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    checkUpdate(false)
                }
                LaunchedEffect(upDl) {
                    if (upDl <= 0) return@LaunchedEffect
                    while (true) {
                        val p = withContext(Dispatchers.IO) { Downloader.progress(ctx, upDl) }
                        if (p < 0) { upFail = true; break }
                        upProg = p
                        if (p >= 100) {
                            Downloader.install(ctx, upDl)
                            break
                        }
                        kotlinx.coroutines.delay(700)
                    }
                }

                BackHandler(enabled = true) {
                    when {
                        lp != null -> livePlay = null
                        p != null -> {
                            // 无论从详情页还是首页「继续观看」进入，返回都落到选集页
                            if (detail == null) detail = p
                            playing = null
                            startEpUrl = null; autoFull = false
                        }
                        dv != null -> detail = null
                        catFilter != null -> catFilter = null
                        extra != null -> extra = null
                        else -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBack < 2000L) {
                                (ctx as? Activity)?.finish()
                            } else {
                                lastBack = now
                                Toast.makeText(ctx, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                if (lp != null) {
                    PlayerScreen(
                        vod = Vod(name = lp.first),
                        vm = vm, liveTitle = lp.first, liveUrl = lp.second,
                        onClose = { livePlay = null }
                    )
                } else if (p != null) {
                    PlayerScreen(
                        vod = p, vm = vm, startRoad = startRoad, startEp = startEp,
                        startEpUrl = startEpUrl, autoFullscreen = autoFull,
                        onClose = {
                            playing = null; startEpUrl = null; autoFull = false
                        }
                    )
                } else if (dv != null) {
                    DetailScreen(
                        vod = dv, vm = vm,
                        onPlay = { v, r, e ->
                            // 保留 detail：全屏播放按返回键时回到这一页（选集/简介）
                            startRoad = r; startEp = e
                            startEpUrl = null; autoFull = false; playing = v
                        },
                        onBack = { detail = null }
                    )
                } else if (catFilter != null) {
                    CategoryScreen(
                        vm = vm,
                        initialType = catFilter ?: "全部",
                        onPlay = { detail = it },
                        onBack = { catFilter = null }
                    )
                } else if (extra != null) {
                    Box(Modifier.fillMaxSize().background(C.Bg)) {
                        when (extra) {
                            "source" -> SourceScreen(vm, onBack = { extra = null })
                            "live" -> LiveSourceScreen(vm, onBack = { extra = null })
                            "more" -> MoreSettingsScreen(vm, onBack = { extra = null })
                            "settings" -> SettingsScreen(
                                vm,
                                onBack = { extra = null },
                                onOpenLiveSource = { extra = "live" },
                                onCheckUpdate = { checkUpdate(true) },
                                versionName = "v$verName"
                            )
                        }
                    }
                } else {
                    Scaffold(
                        containerColor = C.Bg,
                        bottomBar = { BottomBar(current = tab, onChange = { tab = it }) }
                    ) { pad ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(pad)
                                .background(C.Bg)
                        ) {
                            Crossfade(targetState = tab, animationSpec = tween(200), label = "tab") { t ->
                                when (t) {
                                    Tab.Home -> HomeScreen(
                                        vm, onPlay = { detail = it },
                                        onSearch = { tab = Tab.Search },
                                        onOpenCategory = { catFilter = it },
                                        onResume = { v, url ->
                                            startRoad = 0; startEp = 0
                                            startEpUrl = url
                                            autoFull = true
                                            playing = v
                                        }
                                    )
                                    Tab.Ranking -> RankingScreen(vm, onPlay = { detail = it })
                                    Tab.Search -> SearchScreen(vm, onPlay = { detail = it })
                                    Tab.Live -> LiveScreen(
                                        vm,
                                        onPlay = { n, u -> livePlay = n to u },
                                        onManage = { extra = "live" }
                                    )
                                    Tab.Mine -> MineScreen(
                                        vm,
                                        onOpenDetail = { detail = it },
                                        onOpenSource = { extra = "source" },
                                        onOpenLiveSource = { extra = "live" },
                                        onOpenSettings = { extra = "settings" },
                                        onOpenMore = { extra = "more" },
                                        onCheckUpdate = { checkUpdate(true) },
                                        onResume = { v, url ->
                                            startRoad = 0; startEp = 0
                                            startEpUrl = url
                                            autoFull = true
                                            playing = v
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (upShow) {
                    UpdateSheet(
                        file = upFile,
                        progress = upProg,
                        downloading = upDl > 0 && !upFail,
                        failed = upFail,
                        onStart = { startUpdate() },
                        onInstall = {
                            if (upProg >= 100 && upDl > 0) Downloader.install(ctx, upDl)
                            else upShow = false
                        },
                        onDismiss = { upShow = false }
                    )
                }
            }
                }   // key(dark)
            }       // WeiZaoTheme
        }           // setContent
}
