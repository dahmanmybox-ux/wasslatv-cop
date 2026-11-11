package dz.wassla.tv

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wassla TV shell: the whole interface lives in assets/ (HTML + CSS + JS).
 * This class only provides what a web page cannot do by itself:
 * remote-control keys, exit logic, file picking / scanning and saving.
 */
class WebActivity : Activity() {

    private lateinit var web: WebView
    private var isTv = false
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var lastBack = 0L
    private var pickedName = ""
    private var pickedText = ""

    private val reqPick = 4101
    private val reqTree = 4102
    private val reqPerm = 4103

    private val chrome = object : WebChromeClient() {
        // يمنع رسم أيقونة التشغيل الافتراضية (المثلث الأبيض) قبل بدء البث
        override fun getDefaultVideoPoster(): Bitmap? =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            if (view == null) return
            customView = view
            customCb = callback
            setContentView(view)
        }

        override fun onHideCustomView() {
            customCb?.onCustomViewHidden()
            customView = null
            customCb = null
            setContentView(web)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        isTv = ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        setContentView(web)
        hideSystemBars()

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT

        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = chrome
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.scheme == "http" || u.scheme == "https") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                    return true
                }
                return false
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun js(code: String) {
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun isChannelFile(name: String): Boolean {
        val n = name.lowercase()
        if (n.endsWith(".m3u") || n.endsWith(".m3u8")) return true
        val textual = n.endsWith(".json") || n.endsWith(".txt")
        return textual && (n.contains("wassla") || name.contains("وصلة") || n.contains("channels"))
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "ملف"
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment ?: "ملف"
    }

    inner class Bridge {
        @JavascriptInterface
        fun isTv(): Boolean = this@WebActivity.isTv

        @JavascriptInterface
        fun version(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        @JavascriptInterface
        fun readAsset(name: String): String = try {
            assets.open(name).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun showKeyboard() {
            runOnUiThread {
                web.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(web, 0)
            }
        }

        /* ---------- اختيار ملف من الجهاز ---------- */
        @JavascriptInterface
        fun pickFile() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "*/*"
                    startActivityForResult(i, reqPick)
                } catch (e: Exception) {
                    js("window.__onPicked&&window.__onPicked(false,'nohandler')")
                }
            }
        }

        @JavascriptInterface
        fun pickedName(): String = pickedName

        @JavascriptInterface
        fun pickedText(): String = pickedText

        /* ---------- البحث عن الملفات الجديدة ---------- */
        @JavascriptInterface
        fun needsPerm(): Boolean =
            Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        @JavascriptInterface
        fun askPerm() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), reqPerm)
                } else {
                    js("window.__onPerm&&window.__onPerm()")
                }
            }
        }

        @JavascriptInterface
        fun scanNew(): String {
            val out = JSONArray()
            val seen = HashSet<String>()
            val dirs = ArrayList<File>()
            try {
                val ext = Environment.getExternalStorageDirectory()
                for (sub in listOf("Download", "Downloads", "Documents", "")) dirs.add(File(ext, sub))
            } catch (_: Exception) { }
            try {
                getExternalFilesDirs(null)?.forEach { if (it != null) dirs.add(it) }
            } catch (_: Exception) { }
            try {
                File("/storage").listFiles()?.forEach { v ->
                    if (v.isDirectory && v.name != "emulated" && v.name != "self") {
                        dirs.add(v)
                        dirs.add(File(v, "Download"))
                    }
                }
            } catch (_: Exception) { }
            for (d in dirs) {
                val fs = try { d.listFiles() } catch (_: Exception) { null } ?: continue
                for (f in fs) {
                    if (!f.isFile || f.length() == 0L || f.length() > 60L * 1024 * 1024) continue
                    if (!isChannelFile(f.name) || !seen.add(f.absolutePath)) continue
                    out.put(
                        JSONObject().put("name", f.name).put("path", f.absolutePath)
                            .put("size", f.length()).put("mod", f.lastModified())
                    )
                }
            }
            try { scanTree(out) } catch (_: Exception) { }
            return out.toString()
        }

        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    startActivityForResult(i, reqTree)
                } catch (e: Exception) {
                    js("window.__onFolder&&window.__onFolder(false)")
                }
            }
        }

        @JavascriptInterface
        fun readFile(path: String): String = try {
            if (path.startsWith("uri:")) {
                contentResolver.openInputStream(Uri.parse(path.substring(4)))?.bufferedReader()?.use { it.readText() } ?: ""
            } else {
                File(path).readText()
            }
        } catch (e: Exception) {
            ""
        }

        @JavascriptInterface
        fun appDir(): String = (getExternalFilesDir(null) ?: filesDir).absolutePath

        @JavascriptInterface
        fun saveFile(name: String, text: String): String = try {
            val safe = name.replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF ]"), "_")
            val f = File(getExternalFilesDir(null) ?: filesDir, safe)
            f.writeText(text)
            f.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun scanTree(out: JSONArray) {
        val saved = getSharedPreferences("wassla", MODE_PRIVATE).getString("tree", null) ?: return
        val tree = Uri.parse(saved)
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val size = c.getLong(2)
                val mod = c.getLong(3)
                if (size == 0L || !isChannelFile(name)) continue
                val u = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                out.put(JSONObject().put("name", name).put("path", "uri:" + u.toString()).put("size", size).put("mod", mod))
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == reqPick) {
            if (resultCode != RESULT_OK || uri == null) {
                js("window.__onPicked&&window.__onPicked(false,'cancel')")
                return
            }
            Thread {
                try {
                    val name = displayName(uri)
                    val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    pickedName = name
                    pickedText = txt
                    js("window.__onPicked&&window.__onPicked(true,'')")
                } catch (e: Exception) {
                    js("window.__onPicked&&window.__onPicked(false,'error')")
                }
            }.start()
        } else if (requestCode == reqTree) {
            if (resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                getSharedPreferences("wassla", MODE_PRIVATE).edit().putString("tree", uri.toString()).apply()
                js("window.__onFolder&&window.__onFolder(true)")
            } else {
                js("window.__onFolder&&window.__onFolder(false)")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqPerm) js("window.__onPerm&&window.__onPerm()")
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Remote keys that the page does not know are translated to the keys it uses. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_CHANNEL_UP || e.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val d = if (e.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1
                web.evaluateJavascript("window.__zap&&window.__zap($d)", null)
            }
            return true
        }
        val mapped = when (e.keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> KeyEvent.KEYCODE_F1
            KeyEvent.KEYCODE_PROG_GREEN -> KeyEvent.KEYCODE_F2
            KeyEvent.KEYCODE_PROG_YELLOW -> KeyEvent.KEYCODE_F3
            KeyEvent.KEYCODE_PROG_BLUE -> KeyEvent.KEYCODE_F4
            KeyEvent.KEYCODE_INFO -> KeyEvent.KEYCODE_I
            else -> 0
        }
        if (mapped != 0) {
            return super.dispatchKeyEvent(
                KeyEvent(e.downTime, e.eventTime, e.action, mapped, e.repeatCount, e.metaState)
            )
        }
        return super.dispatchKeyEvent(e)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            chrome.onHideCustomView()
            return
        }
        web.evaluateJavascript("(function(){return window.__back?window.__back():false})()") { r ->
            if (r == "\"exit\"") {
                finish()
            } else if (r != "true") {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBack < 2000) {
                    finish()
                } else {
                    lastBack = now
                    Toast.makeText(this, "اضغط رجوع مرة أخرى للخروج", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
