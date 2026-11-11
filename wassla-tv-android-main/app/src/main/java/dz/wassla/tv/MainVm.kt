package dz.wassla.tv

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var settings by mutableStateOf(store.loadSettings()); private set
    var playlists by mutableStateOf(store.playlists); private set
    var activeId by mutableStateOf(store.activeId); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loadFailed by mutableStateOf(false); private set
    var group by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var favOnly by mutableStateOf(false)
    var favorites by mutableStateOf(store.favorites); private set
    var broken by mutableStateOf(store.broken); private set
    var deletedUrls by mutableStateOf(store.deletedUrls); private set
    var sleepAt by mutableStateOf(0L)

    var isScanning by mutableStateOf(false); private set
    var refreshProgress by mutableStateOf(0f); private set
    var refreshStatusText by mutableStateOf(""); private set
    var refreshChannels by mutableStateOf<List<Channel>>(emptyList()); private set
    var refreshNewCount by mutableStateOf(0); private set
    var refreshComplete by mutableStateOf(false); private set
    var showRefreshDialog by mutableStateOf(false)

    private var fails = 0

    val lastUrl: String get() = store.lastUrl
    val activeName: String
        get() = if (activeId == OFFICIAL_ID) OFFICIAL_NAME
        else playlists.firstOrNull { it.id == activeId }?.name ?: ""

    init {
        NavSound.enabled = settings.navSound
        ensureDefault()
        if (!store.builtinDefault) {
            // first launch of this version: start on the numbered built-in channels
            store.builtinDefault = true
            activeId = OFFICIAL_ID
            store.activeId = OFFICIAL_ID
        }
        viewModelScope.launch {
            var id = activeId
            if (id != OFFICIAL_ID && playlists.none { it.id == id }) id = playlists.firstOrNull()?.id ?: ""
            if (id.isEmpty()) id = OFFICIAL_ID // nothing yet: start with the official channels
            open(id, true)
        }
    }

    private fun currentPlaylist(id: String) = playlists.firstOrNull { it.id == id }

    private fun savePlaylists(list: List<Playlist>) {
        playlists = list
        store.playlists = list
    }

    private fun parseText(text: String): List<Channel> {
        val list = M3uParser.parse(text).distinctBy { it.url }
        return if (deletedUrls.isEmpty()) list else list.filter { it.url !in deletedUrls }
    }

    private fun ensureDefault() {
        val url = settings.defaultUrl.trim()
        val list = playlists.toMutableList()
        val i = list.indexOfFirst { it.id == DEFAULT_ID }
        if (url.isBlank()) {
            if (i >= 0) { list.removeAt(i); store.deleteCache(DEFAULT_ID); savePlaylists(list) }
        } else if (i < 0) {
            list.add(0, Playlist(DEFAULT_ID, "القائمة الافتراضية", url))
            savePlaylists(list)
            if (activeId.isEmpty()) { activeId = DEFAULT_ID; store.activeId = DEFAULT_ID }
        } else if (list[i].url != url) {
            list[i] = list[i].copy(url = url)
            store.deleteCache(DEFAULT_ID)
            savePlaylists(list)
        }
    }

    /** Built-in channels: curated numbered list (bundled, updated from the release when online). */
    private suspend fun loadOfficial(update: Boolean): Boolean {
        val ctx = getApplication<Application>()
        if (update) {
            loading = true
            try { withContext(Dispatchers.IO) { OfficialSource.refreshBuiltin(store) } } finally { loading = false }
        }
        val list = withContext(Dispatchers.IO) { OfficialSource.loadBuiltin(ctx, store) }
        val shown = if (deletedUrls.isEmpty()) list else list.filter { it.url !in deletedUrls }
        if (activeId == OFFICIAL_ID) channels = shown
        return shown.isNotEmpty()
    }

    private suspend fun open(id: String, refreshAfter: Boolean) {
        activeId = id
        store.activeId = id
        if (id != OFFICIAL_ID) store.lastUserId = id
        group = null
        if (id == OFFICIAL_ID) {
            loadOfficial(false)
            if (refreshAfter && settings.autoUpdate) loadOfficial(true)
            return
        }
        val p = currentPlaylist(id)
        if (id != OFFICIAL_ID && p == null) { channels = emptyList(); return }
        val cached = withContext(Dispatchers.IO) { store.loadCache(id) }
        channels = if (cached != null) withContext(Dispatchers.Default) { parseText(cached) } else emptyList()
        val canDownload = id == OFFICIAL_ID || p?.url?.startsWith("http") == true
        if (canDownload && (cached == null || (refreshAfter && settings.autoUpdate))) download(id)
    }

    private suspend fun download(id: String): Boolean {
        loading = true
        error = null
        var success = false
        try {
            val official = settings.officialUrls
            val url = currentPlaylist(id)?.url ?: ""
            val text = withContext(Dispatchers.IO) {
                if (id == OFFICIAL_ID) {
                    val sb = StringBuilder()
                    var ok = false
                    official.lines().map { it.trim() }.filter { it.startsWith("http") }.forEach { u ->
                        try { sb.append(downloadText(u)).append("\n"); ok = true } catch (_: Exception) { }
                    }
                    if (!ok) throw Exception("تعذر الاتصال بمصدر القنوات الرسمية")
                    sb.toString()
                } else downloadText(url)
            }
            val list = withContext(Dispatchers.Default) { parseText(text) }
            if (list.isEmpty()) throw Exception("القائمة فارغة")
            if (id == activeId) channels = list
            withContext(Dispatchers.IO) { store.saveCache(id, text) }
            success = true
        } catch (e: Exception) {
            val code = (e as? HttpFail)?.code
            error = when {
                code == 401 || code == 403 -> "تعذر الدخول للقائمة، وقد يكون الاشتراك منتهياً ($code)"
                code == 404 -> "رابط القائمة غير موجود (404)"
                else -> "تعذر تحميل القائمة. تحقق من الرابط والاتصال بالإنترنت."
            }
            if (id != OFFICIAL_ID && (channels.isEmpty() || code == 401 || code == 403 || code == 404)) loadFailed = true
        }
        loading = false
        return success
    }

    fun selectPlaylist(id: String) { viewModelScope.launch { open(id, true) } }

    fun toggleOfficial() {
        viewModelScope.launch {
            if (activeId == OFFICIAL_ID) {
                val back = store.lastUserId.takeIf { id -> playlists.any { it.id == id } } ?: playlists.firstOrNull()?.id ?: ""
                if (back.isNotEmpty()) open(back, false)
            } else open(OFFICIAL_ID, true)
        }
    }

    fun openOfficial() { loadFailed = false; viewModelScope.launch { open(OFFICIAL_ID, true) } }
    fun dismissFailed() { loadFailed = false }

    fun refresh() {
        if (loading) return
        viewModelScope.launch { refreshActiveSource() }
    }

    private suspend fun refreshActiveSource(): Boolean {
        if (activeId == OFFICIAL_ID) return loadOfficial(true)
        val p = currentPlaylist(activeId)
        if (p?.url?.startsWith("http") == true) return download(activeId)
        error = "هذا المصدر ملف محلي؛ أضف رابط قائمة لتحديثه."
        return false
    }

    fun addPlaylistUrl(name: String, url: String) {
        val p = Playlist(UUID.randomUUID().toString(), name.ifBlank { "قائمة ${playlists.size + 1}" }, url.trim())
        savePlaylists(playlists + p)
        viewModelScope.launch { open(p.id, false) }
    }

    fun addPlaylistFile(name: String, uri: Uri) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                }
                val list = withContext(Dispatchers.Default) { parseText(text) }
                if (list.isEmpty()) {
                    error = "لم يتم العثور على قنوات في الملف"
                } else {
                    val p = Playlist(UUID.randomUUID().toString(), name.ifBlank { "ملف ${playlists.size + 1}" }, "")
                    withContext(Dispatchers.IO) { store.saveCache(p.id, text) }
                    savePlaylists(playlists + p)
                    open(p.id, false)
                }
            } catch (e: Exception) {
                error = "تعذر قراءة الملف: " + (e.message ?: "")
            }
            loading = false
        }
    }

    fun deletePlaylist(id: String) {
        if (id == OFFICIAL_ID || id == DEFAULT_ID) return
        savePlaylists(playlists.filter { it.id != id })
        store.deleteCache(id)
        if (activeId == id) {
            val next = playlists.firstOrNull()?.id
            viewModelScope.launch {
                if (next != null) open(next, false)
                else { channels = emptyList(); activeId = ""; store.activeId = "" }
            }
        }
    }

    fun cycleHomeMode() = updateSettings(settings.copy(homeMode = (settings.homeMode + 1) % 4))

    fun updateSettings(s: Settings) {
        val old = settings
        settings = s
        store.saveSettings(s)
        NavSound.enabled = s.navSound
        if (s.defaultUrl != old.defaultUrl) {
            ensureDefault()
            viewModelScope.launch {
                if (s.defaultUrl.isNotBlank()) open(DEFAULT_ID, false)
                else if (activeId == DEFAULT_ID) {
                    val next = playlists.firstOrNull()?.id
                    if (next != null) open(next, false) else { channels = emptyList(); activeId = ""; store.activeId = "" }
                }
            }
        }
        if (s.officialUrls != old.officialUrls) store.deleteCache(OFFICIAL_ID)
    }

    fun toggleFav(ch: Channel) {
        favorites = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        store.favorites = favorites
    }

    fun saveLast(url: String) { store.lastUrl = url }

    fun markBroken(ch: Channel) {
        if (ch.url !in broken) { broken = broken + ch.url; store.broken = broken }
        fails++
        if (fails >= 5 && activeId != OFFICIAL_ID) { fails = 0; loadFailed = true }
    }

    fun markOk(ch: Channel) {
        fails = 0
        if (ch.url in broken) { broken = broken - ch.url; store.broken = broken }
    }

    fun clearBroken() { broken = emptySet(); store.broken = broken }

    fun deleteChannel(ch: Channel) {
        deletedUrls = deletedUrls + ch.url
        store.deletedUrls = deletedUrls
        channels = channels.filter { it.url != ch.url }
    }

    fun refreshCurrentSource() {
        if (loading || isScanning) return
        showRefreshDialog = true
        isScanning = true
        refreshComplete = false
        refreshProgress = 0f
        refreshStatusText = "جارٍ تحديث قائمة القنوات من المصدر الحالي..."
        refreshChannels = channels.takeLast(10)
        refreshNewCount = 0

        viewModelScope.launch {
            val previousUrls = channels.map { it.url }.toSet()
            val updated = refreshActiveSource()
            refreshProgress = 1f
            refreshChannels = channels.takeLast(10)
            refreshNewCount = channels.count { it.url !in previousUrls }
            refreshStatusText = if (updated) {
                "تم تحديث ${channels.size} قناة من ${activeName.ifBlank { "المصدر الحالي" }}."
            } else {
                error ?: "تعذر تحديث المصدر الحالي."
            }
            refreshComplete = true
            isScanning = false
        }
    }

    fun finishRefresh() {
        showRefreshDialog = false
    }

    fun dismissRefresh() {
        showRefreshDialog = false
    }

    fun setSleep(minutes: Int) {
        sleepAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
    }
}
