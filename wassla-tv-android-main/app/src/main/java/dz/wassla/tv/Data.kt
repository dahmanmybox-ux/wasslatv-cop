package dz.wassla.tv

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val OFFICIAL_ID = "official"
const val DEFAULT_ID = "default"
const val OFFICIAL_NAME = "📡 وصلة كلاسيك"
const val DEFAULT_OFFICIAL =
    "https://iptv-org.github.io/iptv/countries/dz.m3u\nhttps://iptv-org.github.io/iptv/languages/ara.m3u"

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val headers: Map<String, String> = emptyMap(),
    val id: String = "",
    val alt: List<String> = emptyList(),
    val web: Boolean = false,
    val num: Int = 0          // fixed receiver number (0 = use list position)
)

data class Playlist(val id: String, val name: String, val url: String)

data class Settings(
    val autoReconnect: Boolean = true,
    val skipBroken: Boolean = true,
    val hideBroken: Boolean = false,
    val resize: Int = 0,          // 0 fit, 1 fill (zoom), 2 stretch
    val buffer: Int = 1,          // 0 fast, 1 balanced, 2 stable
    val autoUpdate: Boolean = true,
    val resumeLast: Boolean = true,
    val navSound: Boolean = true,
    val defaultUrl: String = "",
    val updatesUrl: String = "https://wasslatv.blogspot.com/",
    val officialUrls: String = DEFAULT_OFFICIAL,
    val homeMode: Int = 0         // 0 classic, 1 TiviMate, 2 Smarters, 3 cinema
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private const val NO_GROUP = "غير مصنف"

    fun classifyGroup(name: String, currentGroup: String): String {
        val n = name.lowercase()
        val g = currentGroup.lowercase()
        return when {
            n.contains("algerie") || n.contains("algeria") || n.contains("الجزائر") ||
                n.contains("entv") || n.contains("ennahar") || n.contains("echorouk") ||
                n.contains("el bilad") || n.contains("el heddef") || n.contains("elheddaf") ||
                n.contains("el wataniya") || n.contains("bahia") || n.contains("fadjr") ||
                g.contains("algeria") || g.contains("الجزائر") || n.contains("a3") ||
                n.contains("كنال") || n.contains("شروق") || n.contains("نهار") || n.contains("بلاد") ||
                n.contains("هداف") || n.contains("وطنية") -> "🇩🇿 القنوات الجزائرية"

            n.contains("quran") || n.contains("coran") || n.contains("قرآن") ||
                n.contains("sunnah") || n.contains("سنة") || n.contains("مكة") ||
                n.contains("مدينة") || n.contains("مجد") || n.contains("اقرأ") ||
                n.contains("رسالة") || g.contains("quran") || g.contains("دينية") -> "📖 القرآن الكريم والسنة"

            n.contains("news") || n.contains("أخبار") || n.contains("اخبار") ||
                n.contains("جزيرة") || n.contains("al jazeera") || n.contains("عربية") ||
                n.contains("alarabiya") || n.contains("حدث") || n.contains("hadath") ||
                n.contains("عربي") || n.contains("alaraby") || n.contains("sky news") ||
                n.contains("سكاي") || n.contains("bbc") || n.contains("trt") ||
                n.contains("روسيا اليوم") || n.contains("rt ara") || g.contains("news") -> "🌍 الأخبار"

            n.contains("kids") || n.contains("طفل") || n.contains("أطفال") ||
                n.contains("اطفال") || n.contains("spacetoon") || n.contains("سبيستون") ||
                n.contains("طيور") || n.contains("toyor") || n.contains("كراميش") ||
                n.contains("ماجد") || n.contains("majid") || g.contains("kids") -> "👶 أطفال"

            n.contains("sport") || n.contains("رياضة") || n.contains("bein") ||
                n.contains("كأس") || n.contains("alkass") || n.contains("دبي الرياضية") ||
                n.contains("أبوظبي الرياضية") || n.contains("ontime") || g.contains("sport") -> "⚽ رياضة"

            n.contains("doc") || n.contains("وثائقي") || n.contains("geographic") ||
                g.contains("documentary") -> "🎬 وثائقيات"

            currentGroup.isNotBlank() && currentGroup != NO_GROUP -> currentGroup
            else -> "📡 قنوات عربية منوعة"
        }
    }

    fun sortReceiverStyle(list: List<Channel>): List<Channel> {
        val priority = listOf(
            "🇩🇿 القنوات الجزائرية",
            "📖 القرآن الكريم والسنة",
            "🌍 الأخبار",
            "👶 أطفال",
            "⚽ رياضة",
            "🎬 وثائقيات",
            "📡 قنوات عربية منوعة"
        )
        return list.sortedWith(compareBy<Channel> { ch ->
            val p = priority.indexOf(ch.group)
            if (p >= 0) p else priority.size + 1
        }.thenBy { it.name })
    }

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = NO_GROUP
        var hdr = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: NO_GROUP
                    val title = line.substringAfterLast(",", "").trim()
                    name = title.ifEmpty { attrs["tvg-name"] ?: "قناة" }
                }
                line.startsWith("#EXTGRP:") -> {
                    group = line.removePrefix("#EXTGRP:").trim().ifEmpty { group }
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val kv = line.removePrefix("#EXTVLCOPT:")
                    val k = kv.substringBefore("=").trim().lowercase()
                    val v = kv.substringAfter("=", "").trim()
                    when (k) {
                        "http-user-agent" -> { hdr["User-Agent"] = v }
                        "http-referrer" -> { hdr["Referer"] = v }
                        "http-origin" -> { hdr["Origin"] = v }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val base = line.substringBefore("|").trim()
                    val extra = line.substringAfter("|", "")
                    val h = LinkedHashMap<String, String>(hdr)
                    if (extra.isNotEmpty()) {
                        for (part in extra.split("&", "|")) {
                            val k = part.substringBefore("=").trim()
                            val v = part.substringAfter("=", "").trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) h[k] = v
                        }
                    }
                    val chName = name ?: base.substringAfterLast('/')
                    val finalGroup = classifyGroup(chName, group)
                    out.add(Channel(chName, base, logo, finalGroup, h))
                    name = null; logo = null; group = NO_GROUP; hdr = LinkedHashMap()
                }
            }
        }
        return sortReceiverStyle(out)
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("wassla", Context.MODE_PRIVATE)
    private val dir = ctx.filesDir

    private fun cacheFile(id: String) = File(dir, "pl_$id.m3u")
    fun officialJsonFile() = File(dir, "official_channels.json")
    fun builtinFile() = File(dir, "builtin_channels.json")
    fun saveCache(id: String, text: String) = cacheFile(id).writeText(text)
    fun loadCache(id: String): String? = cacheFile(id).takeIf { it.exists() }?.readText()
    fun deleteCache(id: String) { cacheFile(id).delete() }

    var deletedUrls: Set<String>
        get() = (sp.getStringSet("deleted", emptySet()) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("deleted", v).apply() }

    var playlists: List<Playlist>
        get() = try {
            val a = JSONArray(sp.getString("playlists", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Playlist(o.getString("id"), o.getString("name"), o.optString("url", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
            sp.edit().putString("playlists", a.toString()).apply()
        }

    var activeId: String
        get() = sp.getString("active", "") ?: ""
        set(v) { sp.edit().putString("active", v).apply() }

    var lastUserId: String
        get() = sp.getString("lastUser", "") ?: ""
        set(v) { sp.edit().putString("lastUser", v).apply() }

    var lastUrl: String
        get() = sp.getString("last", "") ?: ""
        set(v) { sp.edit().putString("last", v).apply() }

    var favorites: Set<String>
        get() = (sp.getStringSet("fav", emptySet()) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("fav", v).apply() }

    var broken: Set<String>
        get() = (sp.getStringSet("broken", emptySet()) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("broken", v).apply() }

    /** True once this version has switched the app to the numbered built-in channels. */
    var builtinDefault: Boolean
        get() = sp.getBoolean("v5_builtin", false)
        set(v) { sp.edit().putBoolean("v5_builtin", v).apply() }

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            autoReconnect = sp.getBoolean("s_reconnect", d.autoReconnect),
            skipBroken = sp.getBoolean("s_skip", d.skipBroken),
            hideBroken = sp.getBoolean("s_hide", d.hideBroken),
            resize = sp.getInt("s_resize", d.resize),
            buffer = sp.getInt("s_buffer", d.buffer),
            autoUpdate = sp.getBoolean("s_autoupdate", d.autoUpdate),
            resumeLast = sp.getBoolean("s_resume", d.resumeLast),
            navSound = sp.getBoolean("s_sound", d.navSound),
            defaultUrl = sp.getString("s_default", d.defaultUrl) ?: "",
            updatesUrl = sp.getString("s_updates", d.updatesUrl) ?: d.updatesUrl,
            officialUrls = sp.getString("s_official", d.officialUrls) ?: d.officialUrls,
            homeMode = sp.getInt("s_homemode", d.homeMode)
        )
    }

    fun saveSettings(s: Settings) {
        sp.edit()
            .putBoolean("s_reconnect", s.autoReconnect)
            .putBoolean("s_skip", s.skipBroken)
            .putBoolean("s_hide", s.hideBroken)
            .putInt("s_resize", s.resize)
            .putInt("s_buffer", s.buffer)
            .putBoolean("s_autoupdate", s.autoUpdate)
            .putBoolean("s_resume", s.resumeLast)
            .putBoolean("s_sound", s.navSound)
            .putString("s_default", s.defaultUrl)
            .putString("s_updates", s.updatesUrl)
            .putString("s_official", s.officialUrls)
            .putInt("s_homemode", s.homeMode)
            .apply()
    }
}

/** Number shown for a channel: its fixed number if it has one, otherwise its position. */
fun Channel.numOr(index: Int): Int = if (num > 0) num else index + 1

class HttpFail(val code: Int) : Exception("HTTP $code")

fun downloadText(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 30000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) WasslaTV")
    val code = c.responseCode
    if (code !in 200..299) throw HttpFail(code)
    return c.inputStream.bufferedReader().use { it.readText() }
}

fun buildXtreamM3uUrl(server: String, username: String, password: String): String? {
    val base = Uri.parse(server.trim())
    if (base.scheme != "https" || base.host.isNullOrBlank()) return null
    if (username.isBlank() || password.isBlank()) return null

    val path = base.path.orEmpty().trimEnd('/') + "/get.php"
    return base.buildUpon()
        .path(path)
        .clearQuery()
        .fragment(null)
        .appendQueryParameter("username", username.trim())
        .appendQueryParameter("password", password)
        .appendQueryParameter("type", "m3u_plus")
        .appendQueryParameter("output", "ts")
        .build()
        .toString()
}
