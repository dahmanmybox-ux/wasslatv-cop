package dz.wassla.tv

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One entry of the official-channels file. Order in the file = channel number. */
data class OfficialItem(
    val id: String,
    val name: String,
    val group: String,
    val logo: String?,
    val urls: List<String>,   // direct stream links, tried in order
    val web: String?          // official web page (used when there is no stream link)
)

/** The official-channels layer: independent of the player and of the user's playlists. */
object OfficialSource {

    private fun bundled(ctx: Context): String =
        ctx.assets.open("official_channels.json").bufferedReader().use { it.readText() }

    /** Throws if the text is not a valid file. */
    fun parse(text: String): List<OfficialItem> {
        val out = ArrayList<OfficialItem>()
        val arr = JSONObject(text).getJSONArray("channels")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val urls = ArrayList<String>()
            val ua = o.optJSONArray("urls")
            if (ua != null) {
                for (k in 0 until ua.length()) {
                    val u = ua.optString(k).trim()
                    if (u.startsWith("http")) urls.add(u)
                }
            }
            val web = o.optString("web").trim().takeIf { it.startsWith("http") }
            if (urls.isEmpty() && web == null) continue
            out.add(
                OfficialItem(
                    id = o.optString("id").trim().ifEmpty { name.lowercase() },
                    name = name,
                    group = o.optString("group").trim().ifEmpty { "عامة" },
                    logo = o.optString("logo").trim().takeIf { it.startsWith("http") },
                    urls = urls,
                    web = web
                )
            )
        }
        return out
    }

    /** Live file (cached from the internet) first, then the small built-in copy. */
    fun load(ctx: Context, store: Store): List<OfficialItem> {
        val cache = store.officialJsonFile()
        if (cache.exists()) {
            try {
                val l = parse(cache.readText())
                if (l.isNotEmpty()) return l
            } catch (_: Exception) {
            }
        }
        return try { parse(bundled(ctx)) } catch (_: Exception) { emptyList() }
    }

    fun toChannels(items: List<OfficialItem>, working: Map<String, String>): List<Channel> =
        items.map { item ->
            if (item.urls.isEmpty()) {
                Channel(item.name, item.web ?: "", item.logo, item.group, id = item.id, web = true)
            } else {
                val main = working[item.id]?.takeIf { w -> w in item.urls } ?: item.urls.first()
                Channel(item.name, main, item.logo, item.group, id = item.id, alt = item.urls.filter { u -> u != main })
            }
        }


    // ---------- Built-in curated list (same channels.json as the desktop app) ----------
    const val BUILTIN_URL =
        "https://github.com/dzfastlink-cloud/wasslatv-downloads/releases/download/wasschn/channels.json"

    /** Channels with fixed numbers; hidden entries (enabled=false) are skipped. */
    fun parseBuiltin(text: String): List<Channel> {
        val arr = JSONObject(text).getJSONArray("channels")
        val out = ArrayList<Channel>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optBoolean("enabled", true)) continue
            val name = o.optString("name").trim()
            val url = o.optString("url").trim()
            val num = o.optInt("num", 0)
            if (name.isEmpty() || !url.startsWith("http")) continue
            val h = LinkedHashMap<String, String>()
            o.optString("referrer").trim().takeIf { it.isNotEmpty() }?.let { h["Referer"] = it }
            o.optString("ua").trim().takeIf { it.isNotEmpty() }?.let { h["User-Agent"] = it }
            out.add(
                Channel(
                    name, url,
                    o.optString("logo").trim().takeIf { it.startsWith("http") },
                    o.optString("group").trim().ifEmpty { "عامة" },
                    h, id = "b$num", num = num
                )
            )
        }
        return out.sortedBy { if (it.num > 0) it.num else Int.MAX_VALUE }
    }

    private fun updatedOf(text: String): String =
        try { JSONObject(text).optString("updated") } catch (_: Exception) { "" }

    /** Newest of: downloaded copy / copy bundled inside the app. */
    fun loadBuiltin(ctx: Context, store: Store): List<Channel> {
        val bundled = try {
            ctx.assets.open("builtin_channels.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) { "" }
        val f = store.builtinFile()
        val cached = if (f.exists()) try { f.readText() } catch (_: Exception) { "" } else ""
        val pick = if (cached.isNotEmpty() && updatedOf(cached) >= updatedOf(bundled)) cached else bundled
        return try { parseBuiltin(pick) } catch (_: Exception) {
            try { parseBuiltin(bundled) } catch (_: Exception) { emptyList() }
        }
    }

    /** Downloads the newest list; on any failure the current list is kept. */
    fun refreshBuiltin(store: Store): Boolean = try {
        val text = downloadText(BUILTIN_URL)
        if (parseBuiltin(text).isEmpty()) false else { store.builtinFile().writeText(text); true }
    } catch (_: Exception) { false }

    /** Quick check that a stream link answers. */
    fun probe(url: String): Boolean {
        var c: HttpURLConnection? = null
        try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 6000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode !in 200..299) return false
            val head = c.inputStream.use { s ->
                val b = ByteArray(512)
                val n = s.read(b)
                if (n > 0) String(b, 0, n) else ""
            }
            return if (url.contains(".m3u8", ignoreCase = true)) head.contains("#EXTM3U") else head.isNotEmpty()
        } catch (_: Exception) {
            return false
        } finally {
            try { c?.disconnect() } catch (_: Exception) { }
        }
    }
}
