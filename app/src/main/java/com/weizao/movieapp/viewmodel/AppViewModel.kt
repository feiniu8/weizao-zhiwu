package com.weizao.movieapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weizao.movieapp.App
import com.weizao.movieapp.db.*
import com.weizao.movieapp.network.Http
import com.weizao.movieapp.network.M3u
import com.weizao.movieapp.network.SourceImporter
import com.weizao.movieapp.network.Vod
import com.weizao.movieapp.network.VodApi
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 全局共享：源、列表、收藏、历史、设置 */
class AppViewModel : ViewModel() {

    private val db = App.db
    private val sourceDao = db.sourceDao()
    private val historyDao = db.historyDao()
    private val favoriteDao = db.favoriteDao()
    private val settingDao = db.settingDao()

    val sources: StateFlow<List<Source>> = MutableStateFlow(emptyList())
    val list = MutableStateFlow<List<Vod>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val history = MutableStateFlow<List<PlayHistory>>(emptyList())
    val favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val prefs = MutableStateFlow<Map<String, String>>(emptyMap())
    val liveSources = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val subs = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private var page = 1
    private var tag = "热播"

    init {
        viewModelScope.launch {
            sourceDao.flow().collect { (sources as MutableStateFlow).value = it }
        }
        viewModelScope.launch {
            historyDao.flow().collect { (history as MutableStateFlow).value = it }
        }
        viewModelScope.launch {
            favoriteDao.flow().collect { (favorites as MutableStateFlow).value = it }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val raw = settingDao.get("prefs")?.value
                val map = mutableMapOf<String, String>()
                if (!raw.isNullOrBlank()) {
                    runCatching {
                        val o = JSONObject(raw)
                        o.keys().forEach { k -> map[k] = o.optString(k) }
                    }
                }
                prefs.value = map
                liveSources.value = LiveStore.decode(settingDao.get(LiveStore.KEY)?.value)
                subs.value = SubStore.decode(settingDao.get(SubStore.KEY)?.value)
            }
        }
    }

    // ===== 播放设置 =====
    fun setPref(key: String, value: String) = viewModelScope.launch(Dispatchers.IO) {
        val m = prefs.value.toMutableMap()
        m[key] = value
        prefs.value = m
        settingDao.put(AppSetting("prefs", JSONObject(m as Map<*, *>).toString()))
    }

    // ===== 订阅（TVBox 方式：地址先存下来，解析失败也不拒收） =====
    fun addSub(name: String, url: String) = viewModelScope.launch(Dispatchers.IO) {
        val u = SourceImporter.normalize(url)
        if (u.isBlank()) return@launch
        val list = subs.value.filterNot { it.second == u } +
                ((name.ifBlank { SourceImporter.hostOf(u) } to u))
        subs.value = list
        settingDao.put(AppSetting(SubStore.KEY, SubStore.encode(list)))
    }

    fun removeSub(url: String) = viewModelScope.launch(Dispatchers.IO) {
        val list = subs.value.filterNot { it.second == url }
        subs.value = list
        settingDao.put(AppSetting(SubStore.KEY, SubStore.encode(list)))
    }

    // ===== 直播源 =====
    fun addLiveSource(name: String, url: String) = viewModelScope.launch(Dispatchers.IO) {
        val u = SourceImporter.normalize(url)
        if (!u.startsWith("http")) return@launch
        val list = liveSources.value.filterNot { it.second == u } +
                ((name.ifBlank { SourceImporter.hostOf(u) } to u))
        liveSources.value = list
        settingDao.put(AppSetting(LiveStore.KEY, LiveStore.encode(list)))
    }

    fun removeLiveSource(url: String) = viewModelScope.launch(Dispatchers.IO) {
        val list = liveSources.value.filterNot { it.second == url }
        liveSources.value = list
        settingDao.put(AppSetting(LiveStore.KEY, LiveStore.encode(list)))
    }

    fun channels(url: String, cb: (List<M3u.Channel>) -> Unit) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            Http.text(SourceImporter.normalize(url), maxBytes = 900_000L)
        }
        cb(if (text == null) emptyList() else M3u.parse(text))
    }

    /** 导入订阅地址：自动识别 TVBox JSON / m3u / 采集接口 */
    fun importUrl(url: String, cb: (SourceImporter.Result, Int, Int) -> Unit) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) { SourceImporter.fromUrl(url) }
        var added = 0
        val lives = withContext(Dispatchers.IO) {
            r.csp.forEach { c ->
                if (sourceDao.countByApi(c.api) == 0) {
                    sourceDao.insert(
                        Source(name = c.name, api = c.api, isSelected = sourceDao.all().isEmpty())
                    )
                    added++
                }
            }
            var lv = 0
            if (r.lives.isNotEmpty()) {
                val list = (liveSources.value + r.lives.map { it.name to it.url }).distinctBy { it.second }
                liveSources.value = list
                settingDao.put(AppSetting(LiveStore.KEY, LiveStore.encode(list)))
                lv = r.lives.size
            }
            lv
        }
        withContext(Dispatchers.Main) { cb(r, added, lives) }
    }

    /** 直接粘贴配置文本（JSON / m3u / 源列表），不联网 */
    fun importText(text: String, cb: (SourceImporter.Result, Int, Int) -> Unit) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) { SourceImporter.parse(text) }
        var added = 0
        withContext(Dispatchers.IO) {
            r.csp.forEach { c ->
                if (sourceDao.countByApi(c.api) == 0) {
                    sourceDao.insert(
                        Source(name = c.name, api = c.api, isSelected = sourceDao.all().isEmpty())
                    )
                    added++
                }
            }
            if (r.lives.isNotEmpty()) {
                val list = (liveSources.value + r.lives.map { it.name to it.url }).distinctBy { it.second }
                liveSources.value = list
                settingDao.put(AppSetting(LiveStore.KEY, LiveStore.encode(list)))
            }
        }
        withContext(Dispatchers.Main) { cb(r, added, r.lives.size) }
    }

    // ===== 分类 =====
    fun categories(cb: (List<Pair<String, String>>) -> Unit) = viewModelScope.launch {
        val api = currentApi()
        if (api == null) { cb(emptyList()); return@launch }
        val list = withContext(Dispatchers.IO) { VodApi.classes(api) }
        cb(list)
    }

    /** 一次性拿分类列表（首页/分类页共用） */
    suspend fun catsOnce(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        VodApi.classes(api)
    }

    /** 首页混排：国产剧 + 国产电影 的热播 / 最新 / 高分，全部限定「大陆」 */
    suspend fun sectionsHomeCN(): List<Pair<String, List<Vod>>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        val cats = VodApi.classes(api)
        fun findType(vararg keys: String) =
            cats.firstOrNull { c -> keys.any { k -> c.first.contains(k) || k.contains(c.first) } }?.second ?: ""
        val tv = findType("连续剧", "电视剧", "国产剧")
        val mv = findType("电影", "电影片")
        fun pick(t: String, by: String) =
            (if (t.isBlank()) VodApi.list(api, "", 1, "大陆", "", by)
            else VodApi.list(api, t, 1, "大陆", "", by)).take(12)
        listOf(
            "国产热播剧" to pick(tv, "hits"),
            "国产热播电影" to pick(mv, "hits"),
            "国产新剧上架" to pick(tv, "time"),
            "高分国产口碑" to pick(tv, "score")
        ).filter { it.second.isNotEmpty() }
    }

    /** 首页推荐：全部按评分来，不再按剧集/电影/动漫分区；没有评分排序时退回通用列表 */
    suspend fun rankSections(): List<Pair<String, List<Vod>>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        fun pick(): List<Vod> {
            var r = VodApi.list(api, "", 1, "大陆", "", "score")
            if (r.isEmpty()) r = VodApi.list(api, "", 1, "", "", "score")
            if (r.isEmpty()) r = VodApi.list(api, "", 1, "大陆", "", "")
            if (r.isEmpty()) r = VodApi.list(api)
            return r.distinctBy { it.vodId }.take(24)
        }
        val top = pick()
        if (top.isEmpty()) emptyList()
        else listOf("按评分推荐 · 高分榜" to top)
    }

    /** 某个类型 tab 的推荐分区（也全部限定国产/大陆） */
    suspend fun sectionsForType(typeName: String): List<Pair<String, List<Vod>>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        val cats = VodApi.classes(api)
        fun findType(vararg keys: String) =
            cats.firstOrNull { c -> keys.any { k -> c.first.contains(k) || k.contains(c.first) } }?.second ?: ""
        val typeId = when (typeName) {
            "电视剧" -> findType("连续剧", "电视剧", "国产剧")
            "电影" -> findType("电影", "电影片")
            "动漫" -> findType("动漫", "动画", "番剧")
            "综艺" -> findType("综艺", "综艺片")
            "短剧" -> findType("短剧", "微短剧")
            else -> ""
        }
        fun pick(by: String): List<Vod> {
            var r = if (typeId.isBlank()) VodApi.list(api, "", 1, "大陆", "", by)
            else VodApi.list(api, typeId, 1, "大陆", "", by)
            if (r.isEmpty()) r = VodApi.list(api, typeId, 1, "", "", by)
            if (r.isEmpty()) r = VodApi.list(api, typeId, 1)
            if (r.isEmpty()) r = VodApi.list(api)
            return r.distinctBy { it.vodId }.take(12)
        }
        listOf(
            "${typeName}热播推荐" to pick("hits"),
            "最新上架" to pick("time"),
            "按评分推荐" to pick("score")
        ).filter { it.second.isNotEmpty() }
    }

    /** 榜单：热播 / 最新 / 高分（抓两页凑 40 条） */
    suspend fun ranking(by: String): List<Vod> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        val p1 = VodApi.list(api, "", 1, "", "", by)
        val p2 = if (p1.isEmpty()) emptyList() else VodApi.list(api, "", 2, "", "", by)
        p1 + p2
    }

    /** 首页推荐位：热播榜 / 最新上线 / 高分推荐 */
    suspend fun sections(): List<Pair<String, List<Vod>>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext emptyList()
        fun pick(area: String, by: String): List<Vod> {
            val local = VodApi.list(api, "", 1, area, "", by)
            return (local.ifEmpty { VodApi.list(api, "", 1, "", "", by) }).take(12)
        }
        listOf(
            "国产热播剧" to pick("大陆", "hits"),
            "国产新剧上架" to pick("大陆", "time"),
            "高分口碑" to pick("", "score")
        ).filter { it.second.isNotEmpty() }
    }

    suspend fun currentApi(): String? = withContext(Dispatchers.IO) {
        sourceDao.selected()?.api ?: sourceDao.all().firstOrNull()?.api
    }

    fun selectSource(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        sourceDao.clearSelected()
        sourceDao.select(id)
    }

    fun addSource(name: String, api: String) = viewModelScope.launch(Dispatchers.IO) {
        if (sourceDao.countByApi(api) == 0) sourceDao.insert(Source(name = name, api = api))
    }

    fun deleteSource(s: Source) = viewModelScope.launch(Dispatchers.IO) {
        sourceDao.delete(s)
    }

    fun testSource(api: String, cb: (Int) -> Unit) = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) { VodApi.test(api) }
        cb(n)
    }

    /** 恢复内置源（只补缺失的，不动已有的） */
    fun restoreDefaults(cb: (Int) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        var added = 0
        for (i in Defaults.SOURCES.indices) {
            val (name, api) = Defaults.SOURCES[i]
            if (sourceDao.countByApi(api) == 0) {
                sourceDao.insert(Source(name = name, api = api, isSelected = sourceDao.all().isEmpty()))
                added++
            }
        }
        cb(added)
    }

    fun batchImport(text: String, cb: (Int) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        var ok = 0
        text.split("\n").forEach { line ->
            val l = line.replace('，', ',').trim()
            if (l.isEmpty()) return@forEach
            val parts = l.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2 && parts[1].startsWith("http")) {
                if (sourceDao.countByApi(parts[1]) == 0) {
                    sourceDao.insert(Source(name = parts[0].take(20), api = parts[1]))
                    ok++
                }
            }
        }
        withContext(Dispatchers.Main) { cb(ok) }
    }

    /** 首页列表 */
    fun loadHome(tagName: String = tag, refresh: Boolean = false) = viewModelScope.launch {
        tag = tagName
        if (refresh) page = 1
        loading.value = true
        error.value = null
        if (currentApi() == null) {
            loading.value = false
            error.value = "暂无数据源"
            return@launch
        }
        val data = listSmart(tagTypeId(tagName), page)
        list.value = if (page == 1) data else list.value + data
        if (data.isNotEmpty()) page++
        loading.value = false
        if (data.isEmpty() && page == 1) error.value = "暂无影片，请切换数据源"
    }

    fun loadCategory(typeId: String, refresh: Boolean = false) = viewModelScope.launch {
        if (refresh) page = 1
        loading.value = true
        val data = listSmart(typeId, page)
        list.value = if (page == 1) data else list.value + data
        if (data.isNotEmpty()) page++
        loading.value = false
    }

    /** 分类页的筛选式加载：类型 + 地区 + 年份 + 排序 */
    fun loadFilter(
        typeId: String,
        area: String = "",
        year: String = "",
        by: String = "",
        refresh: Boolean = false
    ) = viewModelScope.launch {
        if (refresh) page = 1
        loading.value = true
        error.value = null
        val data = listSmart(typeId, page, area, year, by)
        list.value = if (page == 1) data else list.value + data
        if (data.isNotEmpty()) page++
        loading.value = false
        if (data.isEmpty() && page == 1) error.value = "该筛选没有内容"
    }

    /** 当前源没数据就按顺序换一个可用源（修「分类没海报」这类空列表） */
    private suspend fun listSmart(
        typeId: String,
        pg: Int,
        area: String = "",
        year: String = "",
        by: String = ""
    ): List<Vod> = withContext(Dispatchers.IO) {
        val cur = currentApi() ?: return@withContext emptyList()
        val first = VodApi.list(cur, typeId, pg, area, year, by)
        if (first.isNotEmpty()) return@withContext first
        for (s in sourceDao.all()) {
            if (s.api == cur) continue
            val d = VodApi.list(s.api, typeId, pg, area, year, by)
            if (d.isNotEmpty()) {
                runCatching { sourceDao.clearSelected(); sourceDao.select(s.id) }
                return@withContext d
            }
        }
        emptyList()
    }

    private fun tagTypeId(tag: String): String = when (tag) {
        "最新" -> "1"
        "高分" -> "2"
        "短剧" -> "3"
        else -> ""
    }

    fun search(keyword: String, cb: (List<Vod>) -> Unit) = viewModelScope.launch {
        loading.value = true
        val api = currentApi()
        val data = if (api == null) emptyList() else withContext(Dispatchers.IO) { VodApi.search(api, keyword) }
        loading.value = false
        cb(data)
    }

    fun detail(api: String, vodId: String, cb: (Vod?) -> Unit) = viewModelScope.launch {
        val v = withContext(Dispatchers.IO) { VodApi.detail(api, vodId) }
        cb(v)
    }

    fun saveHistory(vodId: String, name: String, pic: String, url: String, api: String, pos: Long, dur: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            val old = historyDao.find(vodId)
            historyDao.insert(
                PlayHistory(
                    id = old?.id ?: 0,
                    vodId = vodId, vodName = name, pic = pic, playUrl = url,
                    sourceApi = api, position = pos, duration = dur,
                    finished = dur > 0 && pos >= dur * 0.95
                )
            )
        }

    fun resumePosition(vodId: String): Long = runCatching { history.value.firstOrNull { it.vodId == vodId }?.position ?: 0L }.getOrDefault(0L)

    /** 只有当前这一集是上次看的那一集，才回到上次进度 */
    fun resumeFor(vodId: String, epUrl: String): Long = runCatching {
        val h = history.value.firstOrNull { it.vodId == vodId && it.playUrl == epUrl }
        if (h != null && !h.finished) h.position else 0L
    }.getOrDefault(0L)

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { historyDao.clear() }

    fun deleteHistory(h: PlayHistory) = viewModelScope.launch(Dispatchers.IO) { historyDao.delete(h) }

    fun toggleFavorite(vodId: String, name: String, pic: String, api: String) = viewModelScope.launch(Dispatchers.IO) {
        if (favoriteDao.find(vodId) == null) {
            favoriteDao.insert(Favorite(vodId = vodId, vodName = name, pic = pic, sourceApi = api))
        } else {
            favoriteDao.deleteByVod(vodId)
        }
    }

    fun isFavorite(vodId: String): Boolean = favorites.value.any { it.vodId == vodId }

    fun putSetting(key: String, value: String) = viewModelScope.launch(Dispatchers.IO) {
        settingDao.put(AppSetting(key, value))
    }
}
