package dz.wassla.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Palette taken from the app icon (green/teal + orange)
val Accent = Color(0xFF8FC7F2)
val Gold = Color(0xFFF2B84B)
val Glass = Color(0x1AFFFFFF)
val GlassEdge = Color(0x26FFFFFF)
val BgTop = Color(0xFF071326)
val BgBottom = Color(0xFF0E1D35)

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, secondary = Gold, background = BgTop, surface = Color(0xFF132743))
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(vm)
                }
            }
        }
    }
}

@Composable
fun Splash(onDone: () -> Unit) {
    val pop = remember { Animatable(0.4f) }
    val tri = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val txt = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) {
        delay(300)
        tri.animateTo(1f, tween(450))
        dot.animateTo(1f, spring(dampingRatio = 0.4f))
    }
    LaunchedEffect(Unit) {
        delay(850)
        txt.animateTo(1f, tween(700))
        delay(750)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(132.dp).scale(pop.value)) {
            val w = size.width
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFF132743), Color(0xFF24446A))),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )
            val k = 0.6f + 0.4f * tri.value
            fun pt(x: Float, y: Float) = Offset(w * (0.5f + (x / 256f - 0.5f) * k), w * (0.5f + (y / 256f - 0.5f) * k))
            val a = pt(95f, 71f)
            val b = pt(95f, 185f)
            val c = pt(191f, 128f)
            val p = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
            }
            drawPath(p, Color.White.copy(alpha = tri.value))
            drawCircle(Gold, radius = w * 17f / 256f * dot.value, center = Offset(w * 207f / 256f, w * 52f / 256f))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "WASSLA TV",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (14f - 8f * txt.value).sp,
            modifier = Modifier.alpha(txt.value)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "دليل قنوات للتلفاز",
            color = Gold,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.alpha(txt.value)
        )
        Spacer(Modifier.height(8.dp))
        Text("تصميم المبرمج dahman atallaoui", color = Color(0xAAFFFFFF), fontSize = 13.sp, modifier = Modifier.alpha(txt.value))
        val ctx = LocalContext.current
        val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
        Spacer(Modifier.height(14.dp))
        Text("الإصدار $ver", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(txt.value))
    }
}

@Composable
fun App(vm: MainVm) {
    var playList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var playIndex by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var splash by remember { mutableStateOf(true) }
    var resumed by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("") }

    // Keep playList in sync with active channels
    LaunchedEffect(vm.channels, vm.broken, vm.settings.hideBroken) {
        val list = vm.channels.filter { it.url !in vm.broken || !vm.settings.hideBroken }
        playList = list
    }

    // Receiver-style: after start-up, go straight to live TV (last watched or first channel)
    LaunchedEffect(splash, vm.channels) {
        if (!splash && !resumed && vm.channels.isNotEmpty()) {
            resumed = true
            val list = vm.channels.filter { it.url !in vm.broken || !vm.settings.hideBroken }
            if (list.isNotEmpty()) {
                playList = list
                var targetIdx = 0
                if (vm.settings.resumeLast) {
                    val lastIdx = list.indexOfFirst { it.url == vm.lastUrl }
                    if (lastIdx >= 0) targetIdx = lastIdx
                }
                playIndex = targetIdx
            }
        }
    }

    // Sleep timer
    LaunchedEffect(vm.sleepAt) {
        if (vm.sleepAt > 0) {
            delay((vm.sleepAt - System.currentTimeMillis()).coerceAtLeast(0))
            if (vm.sleepAt > 0 && System.currentTimeMillis() >= vm.sleepAt) {
                vm.sleepAt = 0L
                playIndex = null
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.addPlaylistFile(pendingName, uri); showAdd = false }
    }

    Box(Modifier.fillMaxSize().background(BgTop)) {
        if (splash) {
            Splash(onDone = { splash = false })
        } else {
            val idx = playIndex
            val isOfficial = (vm.activeId == OFFICIAL_ID)
            val sources = remember(vm.playlists, vm.activeId) {
                listOf(OFFICIAL_ID to OFFICIAL_NAME) + vm.playlists.map { it.id to it.name }
            }
            if (idx != null && playList.isNotEmpty()) {
                PlayerScreen(
                    all = playList,
                    start = idx,
                    settings = vm.settings,
                    favorites = vm.favorites,
                    broken = vm.broken,
                    blocked = showSettings || showAdd || vm.loadFailed || vm.showRefreshDialog,
                    onPlayed = { vm.saveLast(it.url) },
                    onOk = { vm.markOk(it) },
                    onBroken = { vm.markBroken(it) },
                    onFav = { vm.toggleFav(it) },
                    onResize = { vm.updateSettings(vm.settings.copy(resize = it)) },
                    onSettings = { showSettings = true },
                    onExit = { playIndex = null },
                    onDeleteChannel = { vm.deleteChannel(it) },
                    onRefresh = { vm.refreshCurrentSource() },
                    isScanning = vm.isScanning,
                    refreshProgress = vm.refreshProgress,
                    refreshStatusText = vm.refreshStatusText,
                    refreshChannels = vm.refreshChannels,
                    refreshNewCount = vm.refreshNewCount,
                    refreshComplete = vm.refreshComplete,
                    showRefreshDialog = vm.showRefreshDialog,
                    onFinishRefresh = { vm.finishRefresh() },
                    onDismissRefresh = { vm.dismissRefresh() },
                    activeSourceName = vm.activeName,
                    isOfficial = isOfficial,
                    sources = sources,
                    activeSourceId = vm.activeId,
                    onSelectSource = { id -> vm.selectPlaylist(id) }
                )
            } else {
                HomeScreen(
                    vm,
                    onPlay = { ch ->
                        val l = vm.channels.filter { it.url !in vm.broken || !vm.settings.hideBroken }
                        val i = l.indexOfFirst { it === ch }
                        if (i >= 0) { playList = l; playIndex = i }
                    },
                    onAdd = { showAdd = true },
                    onSettings = { showSettings = true }
                )
            }
            if (showSettings) SettingsScreen(vm, onClose = { showSettings = false }, onAdd = { showAdd = true })
        }
    }

    if (showAdd && !splash) {
        AddDialog(
            onDismiss = { showAdd = false },
            onUrl = { n, u -> vm.addPlaylistUrl(n, u); showAdd = false; showSettings = false },
            onPick = { n -> pendingName = n; picker.launch(arrayOf("*/*")) }
        )
    }

    if (vm.showRefreshDialog && !splash && playIndex == null) {
        RefreshDialog(
            isScanning = vm.isScanning,
            progress = vm.refreshProgress,
            statusText = vm.refreshStatusText,
            discovered = vm.refreshChannels,
            newCount = vm.refreshNewCount,
            complete = vm.refreshComplete,
            onApply = { vm.finishRefresh() },
            onDismiss = { vm.dismissRefresh() }
        )
    }

    if (vm.loadFailed && !splash) {
        AlertDialog(
            onDismissRequest = { vm.dismissFailed() },
            title = { Text("تعذر استخدام قائمتك") },
            text = { Text("قد يكون ملف القنوات غير متاح أو انتهى اشتراكه. هل تريد العودة إلى وصلة كلاسيك المجانية؟") },
            confirmButton = {
                TextButton(onClick = { vm.openOfficial(); playIndex = 0; showSettings = false }) { Text("📡 وصلة كلاسيك") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissFailed() }) { Text("إغلاق") } }
        )
    }
}
