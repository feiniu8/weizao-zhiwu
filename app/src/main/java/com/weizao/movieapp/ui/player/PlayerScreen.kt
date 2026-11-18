package com.weizao.movieapp.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.video.VideoSize
import com.weizao.movieapp.network.Episode
import com.weizao.movieapp.network.Road
import com.weizao.movieapp.network.StreamResolver
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.network.VodParser
import com.weizao.movieapp.ui.theme.C
import com.weizao.movieapp.utils.BrightnessUtil
import com.weizao.movieapp.utils.TimeUtil
import com.weizao.movieapp.utils.VolumeUtil
import com.weizao.movieapp.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val UA_PLAYER =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)
private val RATIOS = listOf("默认", "16:9", "4:3", "填充", "原始", "裁剪")

@Composable
fun PlayerScreen(
    vod: Vod,
    vm: AppViewModel,
    startRoad: Int = 0,
    startEp: Int = 0,
    liveTitle: String? = null,
    liveUrl: String? = null,
    startEpUrl: String? = null,
    autoFullscreen: Boolean = false,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val prefs by vm.prefs.collectAsState()

    val detail = remember { mutableStateOf<Vod?>(null) }
    var roads by remember { mutableStateOf<List<Road>>(emptyList()) }
    var roadIndex by remember { mutableIntStateOf(0) }
    var episode by remember { mutableStateOf<Episode?>(null) }

    var loading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var longPressing by remember { mutableStateOf(false) }
    var retried by remember { mutableStateOf(false) }
    val mainScope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showSpeedPanel by remember { mutableStateOf(false) }
    var showSkipPanel by remember { mutableStateOf(false) }
    var epsAsc by remember { mutableStateOf(true) }
    var showCast by remember { mutableStateOf(false) }
    var castMode by remember { mutableIntStateOf(0) }
    var castScanning by remember { mutableStateOf(false) }

    // 按选集列表顺序切集：本线路走完自动落到下一线路的第一集
    fun stepEpisode(delta: Int) {
        val eps = roads.getOrNull(roadIndex)?.episodes ?: return
        if (eps.isEmpty()) return
        val idx = eps.indexOfFirst { it.url == episode?.url }
        if (idx < 0) {
            episode = if (delta > 0) eps.first() else eps.last()
            return
        }
        val target = idx + delta
        if (target in eps.indices) {
            episode = eps[target]
        } else if (delta > 0) {
            val next = roads.getOrNull(roadIndex + 1)
            if (next != null && next.episodes.isNotEmpty()) {
                roadIndex = roads.indexOf(next)
                episode = next.episodes.first()
            }
        }
    }

    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var descExpanded by remember { mutableStateOf(false) }

    var resolution by remember { mutableStateOf("") }
    var netSpeed by remember { mutableStateOf("0KB/s") }
    var clock by remember { mutableStateOf("") }
    val battery by remember {
        mutableStateOf(
            runCatching {
                val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE)
                        as android.os.BatteryManager
                bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(0)
        )
    }
    var ratioIndex by remember { mutableIntStateOf(0) }
    var skipSec by remember { mutableIntStateOf(0) }
    var roadsExpanded by remember { mutableStateOf(false) }
    var epsExpanded by remember { mutableStateOf(false) }
    var ascOrder by remember { mutableStateOf(true) }

    var totalBytes by remember { mutableLongStateOf(0L) }
    var lastBytes by remember { mutableLongStateOf(0L) }

    val scope = rememberCoroutineScope()
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val transferListener = remember {
        object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int
            ) {
                if (isNetwork) totalBytes += bytesTransferred.toLong()
            }
        }
    }

    val player = remember {
        // 更快的起播：最小 2s 缓冲即可开播，最多预存 60s（3 倍速时不容易断粮）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 60000, 600, 800)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(ctx).setLoadControl(loadControl).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            loading = false; errorMsg = null
                            if (longPressing && playbackParameters.speed < 2f) setPlaybackSpeed(3f)
                        }
                        Player.STATE_BUFFERING -> {
                            // 3 倍速把缓冲吃空了：临时降回 1 倍，边播边补，用户不用手动点播放
                            if (longPressing && playbackParameters.speed > 1.5f) {
                                setPlaybackSpeed(1f)
                                hud = "缓冲跟不上，已临时降速"
                            }
                        }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0) resolution = "${videoSize.width}x${videoSize.height}"
                }
                override fun onPlayerError(e: PlaybackException) {
                    loading = false
                    errorMsg = "播放失败：${e.errorCodeName}"
                    // 网络类错误自动重试一次（不打断长按/观看）
                    if (!retried && (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                            e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                            e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                    ) {
                        retried = true
                        mainScope.launch {
                            delay(1500)
                            loading = true
                            errorMsg = null
                            prepare()
                            playWhenReady = true
                        }
                    }
                }
            })
        }
    }

    // 直播：不查详情，直接一条"线路"
    if (liveUrl != null) {
        LaunchedEffect(liveUrl) {
            val ep = Episode(liveTitle ?: "直播", liveUrl)
            roads = listOf(Road("直播", listOf(ep)))
            roadIndex = 0
            episode = ep
            loading = false
        }
    }

    // 详情
    if (liveUrl == null) LaunchedEffect(vod.vodId) {
        vm.detail(vm.currentApi() ?: "", vod.vodId.toString()) { d ->
            detail.value = d ?: vod
            roads = VodParser.roads(d ?: vod)
            val wantUrl = startEpUrl?.takeIf { it.isNotBlank() }
            val hit = if (wantUrl != null) {
                roads.firstNotNullOfOrNull { r ->
                    r.episodes.firstOrNull { it.url == wantUrl }?.let { e -> r to e }
                }
            } else null
            if (hit != null) {
                roadIndex = roads.indexOf(hit.first).coerceAtLeast(0)
                episode = hit.second
            } else {
                val r = roads.getOrNull(startRoad) ?: roads.firstOrNull()
                val eps = r?.episodes ?: emptyList()
                if (eps.isNotEmpty()) {
                    roadIndex = roads.indexOf(r).coerceAtLeast(0)
                    episode = eps.getOrNull(startEp) ?: eps.first()
                }
            }
            loading = false
        }
    }

    // 起播
    LaunchedEffect(episode) {
        val ep = episode ?: return@LaunchedEffect
        errorMsg = null
        loading = true
        val api = vm.currentApi()
        val referer = StreamResolver.refererOf(api)
        val stream = withContext(Dispatchers.IO) {
            runCatching { StreamResolver.resolve(ep.url, referer) }
                .getOrDefault(StreamResolver.Stream(ep.url, ep.url.contains(".m3u8")))
        }
        val ds = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(UA_PLAYER)
            .setTransferListener(transferListener)
            .apply { if (referer != null) setDefaultRequestProperties(mapOf("Referer" to referer)) }
        val media = if (stream.isHls) {
            HlsMediaSource.Factory(ds).setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(stream.url))
        } else {
            ProgressiveMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(stream.url))
        }
        player.setMediaSource(media)
        player.prepare()
        player.playWhenReady = true
        player.setPlaybackSpeed(speed)
        val resume = vm.resumeFor(vod.vodId.toString(), ep.url)
        if (resume > 3000 && prefs["resume"] != "0") player.seekTo(resume)
        else if (skipSec > 0) player.seekTo(skipSec * 1000L)
    }

    // 进度 / 网速 / 时钟
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0)
            dur = player.duration.coerceAtLeast(0)
            clock = fmt.format(Date())
            val diff = (totalBytes - lastBytes).coerceAtLeast(0)
            lastBytes = totalBytes
            netSpeed = if (diff >= 1048576) String.format(Locale.getDefault(), "%.1fMB/s", diff / 1048576f)
            else "${diff / 1024}KB/s"
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val ep = episode ?: continue
            if (liveUrl == null && dur > 0 && pos > 0) {
                vm.saveHistory(
                    vod.vodId.toString(), vod.name, vod.pic, ep.url,
                    vm.currentApi() ?: "", pos, dur
                )
            }
        }
    }

    LaunchedEffect(showControls, locked) {
        if (showControls && !locked) { delay(14000); showControls = false }
    }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            val saved = (prefs["bright"] ?: "").toFloatOrNull()
            if (saved != null) activity?.window?.let { BrightnessUtil.set(it, saved) }
        }
    }

    LaunchedEffect(Unit) {
        speed = (prefs["speed"] ?: "1.0").toFloatOrNull() ?: 1.0f
        ratioIndex = (prefs["ratio"] ?: "0").toIntOrNull() ?: 0
        player.setPlaybackSpeed(speed)
        playerView?.resizeMode = ratioMode(ratioIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            activity?.window?.let { BrightnessUtil.reset(it) }
        }
    }

    // 播放页常亮，避免看着看着息屏
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = false }
    }

    fun toggleFullscreen() {
        fullscreen = !fullscreen
        activity?.requestedOrientation = if (fullscreen)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (fullscreen) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // 全屏用记住的亮度；退回竖屏恢复系统亮度
        activity?.window?.let { w ->
            if (fullscreen) {
                val saved = (prefs["bright"] ?: "").toFloatOrNull()
                if (saved != null) BrightnessUtil.set(w, saved)
            } else BrightnessUtil.reset(w)
        }
        showControls = true
    }

    var autoFs by remember { mutableStateOf(autoFullscreen) }
    LaunchedEffect(episode) {
        if (autoFs && episode != null) {
            autoFs = false
            if (!fullscreen) toggleFullscreen()
        }
    }

    // 回桌面自动暂停，回到播放页自动续播（画中画时不停）
    var resumeAfterBack by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        activity?.isInPictureInPictureMode == true
                    if (!inPip && !locked && player.isPlaying) {
                        resumeAfterBack = true
                        player.pause()
                        showControls = true
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeAfterBack) {
                        resumeAfterBack = false
                        player.playWhenReady = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler {
        when {
            showCast -> showCast = false
            showSpeedPanel || showSources || showEpisodes || showSkipPanel || showSettings -> {
                showSettings = false; showSpeedPanel = false
                showSources = false; showEpisodes = false; showSkipPanel = false
            }
            fullscreen -> { toggleFullscreen(); showControls = true }
            else -> onClose()
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } else {
            Toast.makeText(ctx, "系统版本不支持画中画", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {

            // ================= 视频区 =================
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = ratioMode(ratioIndex)
                            setPlayer(player)
                            playerView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 手势：点击/双击/长按 与 拖动分开处理（拖动单独观察，避免被点击吃掉）
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    tryAwaitRelease()
                                    if (longPressing) {
                                        player.setPlaybackSpeed(speed)
                                        longPressing = false
                                        hud = null
                                    }
                                    // 长按/拖动结束后如果停在暂停态，直接续播，不用再点一次
                                    if (!player.playWhenReady) {
                                        player.playWhenReady = true
                                        player.play()
                                    }
                                },
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    if (player.isPlaying) player.pause() else player.play()
                                    showControls = true
                                },
                                onLongPress = {
                                    val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
                                    longPressing = true
                                    if (ahead < 4000L) {
                                        // 缓冲不足时先按 1.5 倍走，等 READY 后自动升到 3 倍
                                        player.setPlaybackSpeed(1.5f)
                                        hud = "缓冲不足，先 1.5 倍，马上提速"
                                    } else {
                                        player.setPlaybackSpeed(3f)
                                        hud = "3 倍速播放中"
                                    }
                                }
                            )
                        }
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            val slop = viewConfiguration.touchSlop
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downX = down.position.x
                                    val downY = down.position.y
                                    var mode = 0          // 0 未判定 1 快进 2 亮度 3 音量
                                    var moved = false
                                    var basePos = 0L
                                    var startBright = 0.5f
                                    var startVol = 50
                                    var lastVol = -1
                                    var lastBright = -1
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val dx = c.position.x - downX
                                        val dy = c.position.y - downY
                                        if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                                            moved = true
                                            mode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1
                                            else if (downX < size.width / 2f) 2 else 3
                                            basePos = player.currentPosition
                                            startBright = activity?.window?.let { BrightnessUtil.get(it) } ?: 0.5f
                                            startVol = VolumeUtil.get(ctx)
                                        }
                                        if (moved) {
                                            c.consume()
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            when (mode) {
                                                1 -> {
                                                    // 整屏宽度 ≈ 3 分钟
                                                    val target = (basePos + (dx / w) * 180_000f).toLong()
                                                        .coerceIn(0, player.duration.coerceAtLeast(1))
                                                    player.seekTo(target)
                                                    pos = target
                                                    hud = "${TimeUtil.fmt(target)} / ${TimeUtil.fmt(dur)}"
                                                }
                                                2 -> {
                                                    // 上滑变亮、下滑变暗
                                                    val b = (startBright - dy / h).coerceIn(0.01f, 1f)
                                                    val pct = (b * 100).toInt()
                                                    if (pct != lastBright) {
                                                        lastBright = pct
                                                        activity?.window?.let { BrightnessUtil.set(it, b) }
                                                        vm.setPref("bright", "%.3f".format(b))
                                                        hud = "亮度 $pct%"
                                                    }
                                                }
                                                else -> {
                                                    // 上滑增大、下滑减小
                                                    val v = (startVol - (dy / h) * 100f).toInt().coerceIn(0, 100)
                                                    if (v != lastVol) {
                                                        lastVol = v
                                                        VolumeUtil.set(ctx, v)
                                                        hud = "音量 $v%"
                                                    }
                                                }
                                            }
                                        }
                                        if (!c.pressed) break
                                    }
                                    if (moved) { hud = null; showControls = true }
                                }
                            }
                        }
                )

                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.Blue)
                }
                errorMsg?.let { msg ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, fontSize = 13.sp, color = C.Warn)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(C.Grad)
                                .clickable { episode?.let { e -> episode = null; episode = e } }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) { Text("重试", fontSize = 14.sp, color = Color.White) }
                    }
                }

                // 中央播放键
                androidx.compose.animation.AnimatedVisibility(
                    visible = fullscreen && (showControls || !playing) && !loading && errorMsg == null && !locked,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color(0x73000000))
                            .clickable {
                                if (player.isPlaying) player.pause() else player.play()
                                showControls = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "播放暂停", tint = Color.White, modifier = Modifier.size(32.dp)
                        )
                    }
                }

                hud?.let {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 86.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(it, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }

                // 锁屏态：只留解锁
                if (locked) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 14.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { locked = false; showControls = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, "解锁", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // 顶部（竖屏）：返回 / 投屏 / 画中画
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && !fullscreen,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack, "返回", tint = Color.White,
                            modifier = Modifier.clickable { onClose() }.size(26.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Filled.Cast, "投屏", tint = Color.White,
                            modifier = Modifier
                                .clickable { showCast = true }
                                .size(26.dp)
                        )
                        Spacer(Modifier.width(22.dp))
                        Icon(
                            Icons.Filled.PictureInPicture, "画中画", tint = Color.White,
                            modifier = Modifier.clickable { enterPip() }.size(26.dp)
                        )
                    }
                }

                // 顶部（全屏）：返回 / 剧名+集数 / 耳机 / 投屏 / 画中画 / 全屏
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && fullscreen,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack, "返回", tint = Color.White,
                            modifier = Modifier.clickable { toggleFullscreen(); showControls = true }.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(vod.name)
                                    episode?.name?.takeIf { it.isNotBlank() }?.let { append("  ").append(it) }
                                },
                                fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val meta = buildList {
                                if (resolution.isNotBlank()) add(resolution)
                                add(netSpeed)
                                if (prefs["showtime"] != "0") add(clock)
                                if (prefs["showbattery"] == "1") add("$battery%")
                            }.joinToString(" · ")
                            Text(meta, fontSize = 10.sp, color = Color(0xFFB9C0CC), maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Headset, "音频模式", tint = Color.White,
                            modifier = Modifier
                                .clickable {
                                    Toast.makeText(ctx, "音频模式（后台听剧）需常驻服务，当前版本未接入", Toast.LENGTH_SHORT).show()
                                }
                                .size(24.dp)
                        )
                        Spacer(Modifier.width(18.dp))
                        Icon(
                            Icons.Filled.Cast, "投屏", tint = Color.White,
                            modifier = Modifier
                                .clickable { showCast = true }
                                .size(24.dp)
                        )
                        Spacer(Modifier.width(18.dp))
                        Icon(
                            Icons.Filled.PictureInPicture, "画中画", tint = Color.White,
                            modifier = Modifier.clickable { enterPip() }.size(24.dp)
                        )
                        Spacer(Modifier.width(18.dp))
                        Icon(
                            Icons.Filled.FullscreenExit, "退出全屏", tint = Color.White,
                            modifier = Modifier.clickable { toggleFullscreen() }.size(24.dp)
                        )
                    }
                }

                // 左侧锁屏
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Box(
                        Modifier
                            .padding(start = 14.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { locked = true; showControls = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LockOpen, "锁屏", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // 右侧（全屏）：后退 / 前进 15 秒
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && fullscreen,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Column(
                        Modifier.padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable {
                                    player.seekTo((player.currentPosition - 15000L).coerceAtLeast(0L)); showControls = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Undo, "后退15秒", tint = Color.White, modifier = Modifier.size(34.dp))
                            Text("15", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(26.dp))
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable {
                                    player.seekTo((player.currentPosition + 15000L).coerceAtMost(player.duration.coerceAtLeast(0L))); showControls = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Redo, "前进15秒", tint = Color.White, modifier = Modifier.size(34.dp))
                            Text("15", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 右侧设置（仅全屏）
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && fullscreen,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Box(
                        Modifier
                            .statusBarsPadding()
                            .padding(top = 52.dp, end = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Settings, "设置", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // 底部（竖屏）：暂停 / 时间 / 进度 / 时长 / 全屏
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && !fullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xF2000000))))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "播放暂停", tint = Color.White,
                            modifier = Modifier
                                .clickable {
                                    if (player.isPlaying) player.pause() else player.play()
                                    showControls = true
                                }
                                .size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(TimeUtil.fmt(pos), fontSize = 12.sp, color = Color.White)
                        Slider(
                            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                            onValueChange = { f ->
                                if (dur > 0) {
                                    val t = (f * dur).toLong()
                                    player.seekTo(t); pos = t
                                }
                            },
                            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF35E0B0),
                                activeTrackColor = Color(0xFF35E0B0),
                                inactiveTrackColor = Color(0x66FFFFFF)
                            )
                        )
                        Text(TimeUtil.fmt(dur), fontSize = 12.sp, color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Filled.Fullscreen, "全屏", tint = Color.White,
                            modifier = Modifier.clickable { toggleFullscreen() }.size(26.dp)
                        )
                    }
                }

                // 底部（全屏）：进度 + 一排操作（收藏 / 片头尾 / 倍速 / 播放 / 选集）
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls && !locked && fullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xE6000000), Color(0xF2000000))
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(TimeUtil.fmt(pos), fontSize = 11.sp, color = Color.White)
                            Slider(
                                value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                                onValueChange = { f ->
                                    if (dur > 0) {
                                        val t = (f * dur).toLong()
                                        player.seekTo(t); pos = t
                                    }
                                },
                                modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 10.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF35E0B0),
                                    activeTrackColor = Color(0xFF35E0B0),
                                    inactiveTrackColor = Color(0x66FFFFFF)
                                )
                            )
                            Text(TimeUtil.fmt(dur), fontSize = 11.sp, color = Color.White)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "播放暂停", tint = Color.White,
                                modifier = Modifier
                                    .clickable {
                                        if (player.isPlaying) player.pause() else player.play()
                                        showControls = true
                                    }
                                    .size(26.dp)
                            )
                            Spacer(Modifier.width(26.dp))
                            Icon(
                                Icons.Filled.SkipNext, "下一集", tint = Color.White,
                                modifier = Modifier.clickable { stepEpisode(1) }.size(24.dp)
                            )
                            Spacer(Modifier.width(26.dp))
                            Icon(
                                Icons.Filled.Subtitles,
                                "弹幕",
                                tint = if (prefs["danmaku"] == "1") Color(0xFF35E0B0) else Color(0xFFB9C0CC),
                                modifier = Modifier
                                    .clickable {
                                        val on = prefs["danmaku"] != "1"
                                        vm.setPref("danmaku", if (on) "1" else "0")
                                        Toast.makeText(
                                            ctx,
                                            if (on) "弹幕已开（当前接口暂无弹幕数据）" else "弹幕已关",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .size(24.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val favedNow = vm.isFavorite(vod.vodId.toString())
                                Text(
                                    if (favedNow) "已收藏" else "收藏",
                                    fontSize = 13.sp,
                                    color = if (favedNow) C.Gold else Color(0xFFEDEFF4),
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            vm.toggleFavorite(vod.vodId.toString(), vod.name, vod.pic, vm.currentApi() ?: "")
                                        }
                                    }
                                )
                                Text(
                                    "片头/尾", fontSize = 13.sp, color = Color(0xFFEDEFF4),
                                    modifier = Modifier.clickable { showSkipPanel = true }
                                )
                                Text(
                                    "倍速", fontSize = 13.sp, color = Color(0xFFEDEFF4),
                                    modifier = Modifier.clickable { showSpeedPanel = true }
                                )
                                Text(
                                    "播放", fontSize = 13.sp, color = Color(0xFFEDEFF4),
                                    modifier = Modifier.clickable { showSources = true }
                                )
                                Text(
                                    "选集", fontSize = 13.sp, color = Color(0xFFEDEFF4),
                                    modifier = Modifier.clickable { showEpisodes = true }
                                )
                            }
                        }
                    }
                }
            }

            // ================= 竖屏下方信息 =================
            if (!fullscreen) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 头部：海报 + 片名 / 标签 / 状态 / 豆瓣评分 / 简介入口
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = vod.pic,
                            contentDescription = vod.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .height(132.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(C.Card2)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    vod.name, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    color = C.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "简介 ›", fontSize = 12.sp, color = C.Gray,
                                    modifier = Modifier
                                        .clickable { descExpanded = !descExpanded }
                                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                                )
                            }
                            val meta = listOf(vod.type, vod.area, vod.year)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(meta, fontSize = 12.sp, color = C.Gray)
                            }
                            val epCount = roads.getOrNull(roadIndex)?.episodes?.size ?: 0
                            if (epCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (epCount > 1) "更新至第 $epCount 集" else "已完结",
                                    fontSize = 12.sp, color = C.Gray
                                )
                            }
                            val sc = vod.score.toDoubleOrNull()
                            if (sc != null && sc > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "豆瓣 ${vod.score}", fontSize = 12.sp, color = C.Gold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33FFC24B))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // 操作行：收藏 / 分享 / 下载 / 反馈
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val faved = vm.isFavorite(vod.vodId.toString())
                        PlayerAction(
                            if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (faved) "已收藏" else "收藏"
                        ) {
                            scope.launch {
                                vm.toggleFavorite(vod.vodId.toString(), vod.name, vod.pic, vm.currentApi() ?: "")
                            }
                        }
                        PlayerAction(Icons.Filled.Share, "分享") {
                            val txt = "${vod.name} ${vod.year} · 用「唯造智物」观看"
                            val it = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, txt)
                            }
                            runCatching {
                                ctx.startActivity(android.content.Intent.createChooser(it, "分享"))
                            }
                        }
                        PlayerAction(Icons.Filled.Download, "下载") {
                            val e = episode
                            if (e == null) {
                                Toast.makeText(ctx, "还没选中剧集", Toast.LENGTH_SHORT).show()
                            } else {
                                com.weizao.movieapp.utils.VodDownloader.start(
                                    ctx, "${vod.name} ${e.name}", e.url
                                )
                                Toast.makeText(ctx, "已加入离线缓存：${e.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PlayerAction(Icons.Filled.Feedback, "反馈") {
                            Toast.makeText(ctx, "可在「我的 - 设置」里查看版本与清理缓存", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 播放源
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(C.Green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("播放源", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (roadsExpanded) "收起 ›" else "展开 ›",
                            fontSize = 12.sp, color = C.Gray,
                            modifier = Modifier.clickable { roadsExpanded = !roadsExpanded }.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (roadsExpanded) {
                        roads.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEachIndexed { j, r ->
                                    val i = roads.indexOf(r)
                                    RoadChip(r.name, r.episodes.size, i == roadIndex, Modifier.weight(1f)) {
                                        roadIndex = i
                                        roads.getOrNull(i)?.episodes?.firstOrNull()?.let { episode = it }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(roads.size) { i ->
                                RoadChip(roads[i].name, roads[i].episodes.size, i == roadIndex, Modifier.width(112.dp)) {
                                    roadIndex = i
                                    roads.getOrNull(i)?.episodes?.firstOrNull()?.let { episode = it }
                                }
                            }
                        }
                    }

                    // 选集
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FormatListBulleted, null, tint = C.Green,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("选集", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (ascOrder) "升序 ↓" else "降序 ↑",
                            fontSize = 12.sp, color = C.Gray,
                            modifier = Modifier.clickable { ascOrder = !ascOrder }.padding(vertical = 6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (epsExpanded) "收起 ›" else "展开 ›",
                            fontSize = 12.sp, color = C.Gray,
                            modifier = Modifier.clickable { epsExpanded = !epsExpanded }.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val eps = roads.getOrNull(roadIndex)?.episodes ?: emptyList()
                    val ordered = if (ascOrder) eps else eps.reversed()
                    if (epsExpanded) {
                        ordered.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { e ->
                                    EpisodeChip(e.name, e.url == episode?.url, Modifier.weight(1f)) { episode = e }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ordered.size) { idx ->
                                val e = ordered[idx]
                                EpisodeChip(e.name, e.url == episode?.url, Modifier.width(64.dp)) { episode = e }
                            }
                        }
                    }

                    val desc = (detail.value?.content ?: vod.content).replace(Regex("<[^>]+>"), "").trim()
                    if (desc.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("简介", fontSize = 13.sp, color = C.Gray)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (descExpanded) desc else desc.take(64) + if (desc.length > 64) "…" else "",
                            fontSize = 13.sp, color = C.White, lineHeight = 20.sp
                        )
                        if (desc.length > 64) {
                            Text(
                                if (descExpanded) "收起" else "展开",
                                fontSize = 12.sp, color = C.Blue,
                                modifier = Modifier.clickable { descExpanded = !descExpanded }.padding(vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        if (showCast) {
            CastDialog(
                mode = castMode,
                scanning = castScanning,
                onMode = { castMode = it },
                onToggleScan = { castScanning = !castScanning },
                onClose = { showCast = false; castScanning = false }
            )
            if (castScanning) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(6000)
                    castScanning = false
                }
            }
        }

        // ================= 设置面板 =================
        if (showSettings) {
            SettingsPanel(
                speed = speed,
                ratioIndex = ratioIndex,
                onSpeed = { s -> speed = s; player.setPlaybackSpeed(s) },
                onRatio = { i ->
                    ratioIndex = i
                    playerView?.resizeMode = ratioMode(i)
                },
                onReplay = { player.seekTo(0); player.playWhenReady = true },
                onRefresh = { episode?.let { e -> episode = null; episode = e } },
                onSwitchSource = {
                    if (roads.size > 1) {
                        roadIndex = (roadIndex + 1) % roads.size
                        roads[roadIndex].episodes.firstOrNull()?.let { episode = it }
                        Toast.makeText(ctx, "已切到 ${roads[roadIndex].name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "只有一条线路", Toast.LENGTH_SHORT).show()
                    }
                },
                onOrientation = { toggleFullscreen() },
                skipSec = skipSec,
                onSkip = { d -> skipSec = (skipSec + d).coerceAtLeast(0) },
                onSkipReset = { skipSec = 0 },
                onToast = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                onClose = { showSettings = false }
            )
        }

        // ================= 选集面板 =================
        // 选集（右侧半屏面板，照「小鸡看看」）
        if (showEpisodes) {
            val eps = roads.getOrNull(roadIndex)?.episodes ?: emptyList()
            val ordered = if (epsAsc) eps else eps.reversed()
            SidePanel(
                title = "选集",
                sortAsc = epsAsc,
                onSort = { epsAsc = !epsAsc },
                onClose = { showEpisodes = false }
            ) {
                ordered.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { e ->
                            EpisodeGridItem(
                                e.name, e.url == episode?.url, Modifier.weight(1f)
                            ) { episode = e; showEpisodes = false }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // 播放源（右侧半屏面板）
        if (showSources) {
            SidePanel(title = "播放源", onClose = { showSources = false }) {
                roads.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { r ->
                            val i = roads.indexOf(r)
                            SourceGridItem(
                                r.name, r.episodes.size, i == roadIndex, Modifier.weight(1f)
                            ) {
                                roadIndex = i
                                roads.getOrNull(i)?.episodes?.firstOrNull()?.let { episode = it }
                                showSources = false
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // 播放速度（右侧半屏面板）
        if (showSpeedPanel) {
            SidePanel(
                title = "播放速度",
                subtitle = "播放倍速：${speed}x",
                hint = "点按切换倍速",
                onClose = { showSpeedPanel = false }
            ) {
                SPEEDS.chunked(4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { sp ->
                            SpeedGridItem("${sp}x", sp == speed, Modifier.weight(1f)) {
                                speed = sp
                                player.setPlaybackSpeed(sp)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // 片头 / 片尾
        if (showSkipPanel) {
            SidePanel(
                title = "片头 / 片尾",
                subtitle = "片头片尾各跳过 $skipSec 秒",
                hint = "打开剧集时自动跳过",
                onClose = { showSkipPanel = false }
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkipButton("-1 秒", Modifier.weight(1f)) { skipSec = (skipSec - 1).coerceAtLeast(0) }
                    SkipButton("重置", Modifier.weight(1f)) { skipSec = 0 }
                    SkipButton("+1 秒", Modifier.weight(1f)) { skipSec += 1 }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkipButton("-10 秒", Modifier.weight(1f)) { skipSec = (skipSec - 10).coerceAtLeast(0) }
                    SkipButton("+10 秒", Modifier.weight(1f)) { skipSec += 10 }
                }
                Spacer(Modifier.height(18.dp))
                SkipButton("立即跳到第 $skipSec 秒", Modifier.fillMaxWidth()) {
                    player.seekTo(skipSec * 1000L)
                }
            }
        }
    }
}

private fun ratioMode(i: Int): Int = when (i) {
    1 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    2 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    5 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

@Composable
private fun SettingsPanel(
    speed: Float,
    ratioIndex: Int,
    onSpeed: (Float) -> Unit,
    onRatio: (Int) -> Unit,
    onReplay: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchSource: () -> Unit,
    onOrientation: () -> Unit,
    skipSec: Int,
    onSkip: (Int) -> Unit,
    onSkipReset: () -> Unit,
    onToast: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() }) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(C.Bg2)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("播放设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White)
            Spacer(Modifier.height(16.dp))

            Text("倍速", fontSize = 13.sp, color = C.Gray)
            Spacer(Modifier.height(8.dp))
            SPEEDS.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        val on = s == speed
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                                .clickable { onSpeed(s) },
                            contentAlignment = Alignment.Center
                        ) { Text("${s}x", fontSize = 12.sp, color = if (on) Color.White else C.Gray) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text("画面比例", fontSize = 13.sp, color = C.Gray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RATIOS.forEachIndexed { i, name ->
                    val on = i == ratioIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                            .clickable { onRatio(i) },
                        contentAlignment = Alignment.Center
                    ) { Text(name, fontSize = 12.sp, color = if (on) Color.White else C.Gray) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("片头片尾", fontSize = 13.sp, color = C.Gray)
                Spacer(Modifier.weight(1f))
                Text("跳过 ${skipSec} 秒", fontSize = 12.sp, color = C.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("减1秒") { onSkip(-1) }
                SettingChip("重置") { onSkipReset() }
                SettingChip("加1秒") { onSkip(1) }
            }

            Spacer(Modifier.height(18.dp))
            Text("其他", fontSize = 13.sp, color = C.Gray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("硬解码") { onToast("已使用硬件解码") }
                SettingChip("刷新") { onRefresh() }
                SettingChip("重播") { onReplay() }
                SettingChip("字幕") { onToast("当前片源无字幕轨") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("音轨") { onToast("当前片源单音轨") }
                SettingChip("横竖屏") { onOrientation() }
                SettingChip("换源") { onSwitchSource() }
                SettingChip("下载") { onToast("暂不支持下载") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("后台播放") { onToast("后台播放需系统授权") }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RowScope.SettingChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.Card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, color = C.Gray) }
}

@Composable
private fun EpisodePanel(
    roads: List<Road>,
    roadIndex: Int,
    episode: Episode?,
    onRoad: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() }) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(C.Bg2)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选集", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.White)
                Spacer(Modifier.weight(1f))
                Text("共 ${roads.getOrNull(roadIndex)?.episodes?.size ?: 0} 集", fontSize = 12.sp, color = C.Gray)
            }
            if (roads.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roads.size) { i ->
                        val on = i == roadIndex
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) C.Blue else C.Card)
                                .clickable { onRoad(i) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(roads[i].name, fontSize = 12.sp, color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val eps = roads.getOrNull(roadIndex)?.episodes ?: emptyList()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                eps.chunked(5).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { e ->
                            val on = e.url == episode?.url
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) C.Grad else Brush.linearGradient(listOf(C.Card, C.Card)))
                                    .clickable { onEpisode(e) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    e.name, fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (on) Color.White else C.Gray
                                )
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = C.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = C.Gray)
    }
}

@Composable
private fun RoadChip(name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x3322C55E) else C.Card2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            name, fontSize = 12.sp,
            color = if (selected) C.Green else C.Gray,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (count > 0) {
            Text(
                "$count", fontSize = 9.sp, color = if (selected) C.Green else C.Hint,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp)
            )
        }
    }
}

@Composable
private fun EpisodeChip(name: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) C.Grad else Brush.linearGradient(listOf(C.Card2, C.Card2)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Color.White else C.Gray
        )
    }
}

@Composable
private fun SidePanel(
    title: String,
    subtitle: String? = null,
    hint: String? = null,
    sortAsc: Boolean? = null,
    onSort: (() -> Unit)? = null,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable { onClose() })
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .background(Color(0xF2161B24))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.White)
                        if (sortAsc != null && onSort != null) {
                            Spacer(Modifier.width(16.dp))
                            Text(
                                if (sortAsc) "↓" else "↑",
                                fontSize = 22.sp, color = C.White,
                                modifier = Modifier.clickable { onSort() }.padding(horizontal = 6.dp)
                            )
                        }
                    }
                    subtitle?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = C.Gray)
                    }
                    hint?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, fontSize = 13.sp, color = C.Gray)
                    }
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(C.Divider))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun EpisodeGridItem(name: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x3322C55E) else C.Card2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Color(0xFF35E0B0) else C.White
        )
        if (selected) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(6, 11, 4, 9).forEach { h ->
                    Box(Modifier.width(2.dp).height(h.dp).background(Color(0xFF35E0B0)))
                }
            }
        }
    }
}

@Composable
private fun SourceGridItem(name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x3322C55E) else C.Card2)
            .clickable { onClick() },
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp, end = 34.dp)
        ) {
            Text(
                name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) Color(0xFF35E0B0) else C.White
            )
            Spacer(Modifier.height(4.dp))
            Text("$count 集", fontSize = 11.sp, color = C.Gray)
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF35E0B0)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, null, tint = Color(0xFF10221A), modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun SpeedGridItem(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x3322C55E) else C.Card2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, color = if (selected) Color(0xFF35E0B0) else C.White)
    }
}

@Composable
private fun SkipButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.Card2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, color = C.White) }
}

@Composable
private fun CastDialog(
    mode: Int,
    scanning: Boolean,
    onMode: (Int) -> Unit,
    onToggleScan: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable { onClose() }) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(16.dp))
                .background(C.Dialog)
                .clickable { }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("投屏", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.White)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            Text("投屏方式", fontSize = 12.sp, color = C.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CastModeChip("镜像投屏", mode == 0, Modifier.weight(1f)) { onMode(0) }
                CastModeChip("推送投屏", mode == 1, Modifier.weight(1f)) { onMode(1) }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (mode == 0) "把手机画面整屏投到电视（DLNA/无线显示）"
                else "把视频地址直接推给电视播放（DLNA 推送）",
                fontSize = 11.sp, color = C.Hint
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(C.Divider))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF35E0B0),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (scanning) "正在搜索可用设备…" else "未发现可用设备",
                    fontSize = 13.sp, color = C.White, modifier = Modifier.weight(1f)
                )
                Text(
                    if (scanning) "停止搜索" else "重新搜索",
                    fontSize = 12.sp, color = Color(0xFF35E0B0),
                    modifier = Modifier.clickable { onToggleScan() }.padding(6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "请确认手机与电视/盒子连在同一个 Wi-Fi。当前版本还没有接入 DLNA 广播，搜到设备后会显示在这里——先给出完整的搜索/停止交互。",
                fontSize = 11.sp, color = C.Hint, lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun CastModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x3322C55E) else C.Card2)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) Color(0xFF35E0B0) else C.Gray)
    }
}
