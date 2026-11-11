package dz.wassla.tv

import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val UA = "VLC/3.0.18 LibVLC/3.0.18"

class Pl(val player: ExoPlayer, val http: DefaultHttpDataSource.Factory)

@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, bufferMode: Int): Pl {
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent(UA)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        .setConstantBitrateSeekingEnabled(true)
    val b = when (bufferMode) {
        0 -> intArrayOf(8000, 30000, 1000, 2000)
        2 -> intArrayOf(30000, 90000, 4000, 8000)
        else -> intArrayOf(20000, 60000, 2000, 4000)
    }
    val load = DefaultLoadControl.Builder().setBufferDurationsMs(b[0], b[1], b[2], b[3]).build()
    val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
    val p = ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
        .setLoadControl(load)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .build()
    return Pl(p, http)
}

// attempt 0: auto-detect, 1: force HLS, 2: force raw MPEG-TS
@OptIn(UnstableApi::class)
fun startChannel(pl: Pl, ch: Channel, attempt: Int) {
    pl.http.setUserAgent(ch.headers["User-Agent"] ?: UA)
    pl.http.setDefaultRequestProperties(ch.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
    val b = MediaItem.Builder().setUri(ch.url)
    if (attempt == 1) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    else if (attempt == 2) b.setMimeType(MimeTypes.VIDEO_MP2T)
    pl.player.setMediaItem(b.build())
    pl.player.prepare()
    pl.player.playWhenReady = true
}

fun digitOf(k: Key): Int? = when (k) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

@Composable
fun ColorKeyButton(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text(label, color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChannelRow(
    number: Int, ch: Channel, current: Boolean, fav: Boolean, isBroken: Boolean,
    modifier: Modifier, onLong: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit
) {
    FocusSurface(onClick, modifier.fillMaxWidth(), onLong = onLong, selected = current, zoom = false, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                String.format(Locale.US, "%03d", number),
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(42.dp)
            )
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                if (ch.logo != null) {
                    SubcomposeAsyncImage(
                        model = ch.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        error = { Text(ch.name.trim().take(1).uppercase(), color = Accent, fontSize = 15.sp) }
                    )
                } else {
                    Text(ch.name.trim().take(1).uppercase(), color = Accent, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ch.name,
                    color = if (isBroken) Color(0x88FFFFFF) else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    ch.group,
                    color = Color(0x77FFFFFF),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            if (isBroken) Text("⚠", color = Gold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 3.dp))
            if (fav) Text("★", color = Gold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 3.dp))
            Text(
                "🗑",
                color = Color(0x88FF5555),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            if (current) Text(" ▶", color = Accent, fontSize = 13.sp)
        }
    }
}

// Receiver-style channel list: opens over the video, the current channel keeps playing
@Composable
fun ChannelPanel(
    all: List<Channel>,
    current: Int,
    favorites: Set<String>,
    broken: Set<String>,
    activeSourceName: String,
    isOfficial: Boolean,
    onPick: (Int) -> Unit,
    onFav: (Channel) -> Unit,
    onDelete: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onOpenSatDialog: () -> Unit,
    onOpenNumpad: () -> Unit,
    modifier: Modifier
) {
    var group by remember { mutableStateOf<String?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    val groups = remember(all) { all.map { it.group }.distinct() }
    val items = remember(all, group, favOnly, favorites) {
        all.withIndex().filter {
            (group == null || it.value.group == group) && (!favOnly || it.value.url in favorites)
        }
    }
    val state = rememberLazyListState()
    val req = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val pos = items.indexOfFirst { it.index == current }
        if (pos >= 0) state.scrollToItem(pos)
        delay(80)
        runCatching { req.requestFocus() }
    }

    Column(modifier.background(Color(0xF5071326)).padding(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF132743))
                .clickable(onClick = onOpenSatDialog)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isOfficial) "📡" else "📁", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(activeSourceName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isOfficial) "قنوات مدمجة • تحديث تلقائي" else "ملف مضاف خارجي",
                        color = if (isOfficial) Accent else Gold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("(${all.size})", color = Color(0x99FFFFFF), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x338FC7F2))
                    .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("تبديل SAT ⮂", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("★ المفضلة") }) }
                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("الكل") }) }
                items(groups.size) { i ->
                    val g = groups[i]
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onOpenNumpad)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("🔢 رقم", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionRight, Key.PageDown, Key.MediaFastForward -> {
                            val next = (current + 10).coerceAtMost(all.lastIndex)
                            onPick(next)
                            true
                        }
                        Key.DirectionLeft, Key.PageUp, Key.MediaRewind -> {
                            val prev = (current - 10).coerceAtLeast(0)
                            onPick(prev)
                            true
                        }
                        else -> false
                    }
                }
        ) {
            items(items.size, key = { items[it].value.url }) { i ->
                val idx = items[i].index
                val ch = items[i].value
                ChannelRow(
                    ch.numOr(idx), ch, idx == current, ch.url in favorites, ch.url in broken,
                    if (idx == current) Modifier.focusRequester(req) else Modifier,
                    onLong = { onFav(ch) },
                    onDelete = { onDelete(ch) }
                ) { onPick(idx) }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorKeyButton("🟢", "تحديث القائمة", onClick = onRefresh)
            Text("◄ / ► قفز 10 قنوات | اضغط مطولاً للتفضيل", color = Color(0x99FFFFFF), fontSize = 11.sp)
        }
    }
}

@Composable
fun ReceiverInfoBar(
    number: Int,
    channel: Channel,
    totalChannels: Int,
    currentTime: String,
    onFav: () -> Unit,
    isFav: Boolean,
    onRefresh: () -> Unit,
    onResize: () -> Unit,
    onSettings: () -> Unit,
    onPanel: () -> Unit,
    isOfficial: Boolean = true,
    activeSourceName: String = "📡 وصلة كلاسيك",
    onOpenNumpad: () -> Unit = {},
    onOpenSatDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF2071326))
            .border(1.5.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF132743))
                    .border(1.5.dp, Gold, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%03d", number),
                    color = Gold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo != null) {
                    SubcomposeAsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        error = { Text(channel.name.trim().take(1), color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    )
                } else {
                    Text(channel.name.trim().take(1), color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOfficial) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x338FC7F2))
                                .border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("📡 مدمجة", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33F2B84B))
                                .border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("📁 ملف: $activeSourceName", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = channel.group,
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("  •  ", color = Color(0x66FFFFFF), fontSize = 12.sp)
                    Text(
                        text = "$number / $totalChannels",
                        color = Color(0xAAFFFFFF),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x338FC7F2))
                            .border(1.dp, Accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("LIVE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorKeyButton("🔴", if (isFav) "★ بالمفضلة" else "إضافة للمفضلة", onClick = onFav)
            ColorKeyButton("🟢", "تحديث القائمة", onClick = onRefresh)
            ColorKeyButton("🟡", "⛶ مقاس الشاشة", onClick = onResize)
            ColorKeyButton("🔵", "⚙️ الإعدادات", onClick = onSettings)
            ColorKeyButton("🔢", "رقم القناة", onClick = onOpenNumpad)
            ColorKeyButton("🛰️", "المصدر (SAT)", onClick = onOpenSatDialog)
            ColorKeyButton("☰", "قائمة القنوات (OK)", onClick = onPanel)
        }
    }
}

@Composable
fun QuickActionsDialog(
    onOpenChannels: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF50B1930))
                .border(2.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("قائمة سريعة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            FocusSurface(
                onClick = {
                    onOpenChannels()
                    onDismiss()
                },
                selected = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📺 قائمة القنوات", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
            }

            Spacer(Modifier.height(8.dp))

            FocusSurface(
                onClick = {
                    onOpenSources()
                    onDismiss()
                },
                selected = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📁 تبديل القوائم", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
            }

            Spacer(Modifier.height(8.dp))

            FocusSurface(
                onClick = {
                    onOpenSettings()
                    onDismiss()
                },
                selected = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("⚙️ الإعدادات", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("إغلاق", color = Accent) }
        }
    }
}

@Composable
fun RefreshDialog(
    isScanning: Boolean,
    progress: Float,
    statusText: String,
    discovered: List<Channel>,
    newCount: Int,
    complete: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val refreshSucceeded = statusText.startsWith("تم تحديث")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF50B1930))
                .border(2.dp, Accent, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⟳", fontSize = 24.sp, color = Gold)
                Spacer(Modifier.width(10.dp))
                Text("تحديث قائمة القنوات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = Accent,
                    trackColor = Color(0x33FFFFFF)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = Accent,
                    trackColor = Color(0x33FFFFFF)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(statusText, color = Color(0xCCFFFFFF), fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (!isScanning) Text("${(progress * 100).toInt()}%", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x44000000))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (discovered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (isScanning) "جارٍ الاتصال بالمصدر الحالي..." else "لا توجد قنوات في هذه القائمة.", color = Color(0xB3D7E4F5), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(discovered.size) { i ->
                            val ch = discovered[i]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("•", color = Accent, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(ch.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                                Spacer(Modifier.weight(1f))
                                Text(ch.group, color = Color(0x88FFFFFF), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (complete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (refreshSucceeded) Color(0x228FC7F2) else Color(0x33FF8A80))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (refreshSucceeded) {
                            "اكتمل التحديث • ظهرت $newCount قناة لم تكن في القائمة السابقة."
                        } else {
                            statusText
                        },
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (complete) {
                    Button(onClick = onApply) {
                        Text("تم")
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(onClick = onDismiss) { Text(if (complete) "إغلاق" else "إخفاء") }
            }
        }
    }
}

@Composable
fun VirtualNumpadDialog(
    totalChannels: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    val focusFirst = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focusFirst.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF50B1930))
                .border(2.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🔢 إدخال رقم القناة",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "اختر الرقم بالريموت واضغط انتقال (من 1 إلى $totalChannels)",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF02100D))
                    .border(1.5.dp, Gold, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (digits.isEmpty()) "___" else digits,
                    color = Gold,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("⌫ مسح", "0", "✔ انتقال")
            )

            for ((rIdx, row) in rows.withIndex()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((cIdx, label) in row.withIndex()) {
                        val isFirst = rIdx == 0 && cIdx == 0
                        val isSpecial = label.startsWith("⌫") || label.startsWith("✔")

                        FocusSurface(
                            onClick = {
                                when {
                                    label.startsWith("⌫") -> {
                                        if (digits.isNotEmpty()) digits = digits.dropLast(1)
                                    }
                                    label.startsWith("✔") -> {
                                        val n = digits.toIntOrNull()
                                        if (n != null && n in 1..totalChannels) {
                                            onJump(n - 1)
                                        }
                                    }
                                    else -> {
                                        if (digits.length < 4) {
                                            val next = digits + label
                                            val num = next.toIntOrNull()
                                            if (num != null && num <= totalChannels) {
                                                digits = next
                                            } else if (digits.isEmpty() && label != "0") {
                                                digits = label
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .then(if (isFirst) Modifier.focusRequester(focusFirst) else Modifier),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        when {
                                            label.startsWith("✔") -> Accent.copy(alpha = 0.25f)
                                            label.startsWith("⌫") -> Color(0x33FF5555)
                                            else -> Color(0x22FFFFFF)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (label.startsWith("✔")) Accent else Color.White,
                                    fontSize = if (isSpecial) 12.sp else 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xAAFFFFFF), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SatSourceDialog(
    sources: List<Pair<String, String>>,
    activeId: String,
    onSelectSource: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val req = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { req.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF50B1930))
                .border(2.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🛰️ قائمة الأقمار والمصادر (SAT)", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("إغلاق", color = Accent) }
            }
            Spacer(Modifier.height(4.dp))
            Text("اختر المصدر للتبديل الفوري بين القنوات المدمجة والملفات:", color = Color(0x99FFFFFF), fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                items(sources.size) { i ->
                    val (id, name) = sources[i]
                    val isSel = (id == activeId)
                    val isOfficial = (id == OFFICIAL_ID)
                    FocusSurface(
                        onClick = { onSelectSource(id); onDismiss() },
                        selected = isSel,
                        modifier = if (isSel || i == 0) Modifier.focusRequester(req) else Modifier,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isOfficial) "📡" else "📁", fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        name,
                                        color = if (isSel) Gold else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isOfficial) "باقة وصلة المدمجة (تحديث تلقائي)" else "ملف مضاف من طرفك",
                                        color = Color(0x88FFFFFF),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (isSel) {
                                Text("✔ نشط", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    all: List<Channel>,
    start: Int,
    settings: Settings,
    favorites: Set<String>,
    broken: Set<String>,
    blocked: Boolean,
    onPlayed: (Channel) -> Unit,
    onOk: (Channel) -> Unit,
    onBroken: (Channel) -> Unit,
    onFav: (Channel) -> Unit,
    onResize: (Int) -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onDeleteChannel: (Channel) -> Unit = {},
    onRefresh: () -> Unit = {},
    isScanning: Boolean = false,
    refreshProgress: Float = 0f,
    refreshStatusText: String = "",
    refreshChannels: List<Channel> = emptyList(),
    refreshNewCount: Int = 0,
    refreshComplete: Boolean = false,
    showRefreshDialog: Boolean = false,
    onFinishRefresh: () -> Unit = {},
    onDismissRefresh: () -> Unit = {},
    activeSourceName: String = "📡 وصلة كلاسيك",
    isOfficial: Boolean = true,
    sources: List<Pair<String, String>> = emptyList(),
    activeSourceId: String = OFFICIAL_ID,
    onSelectSource: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by rememberUpdatedState(settings)
    val brokenNow by rememberUpdatedState(broken)
    var index by remember { mutableIntStateOf(start.coerceIn(0, (all.size - 1).coerceAtLeast(0))) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var quickMenuOpen by remember { mutableStateOf(false) }
    var okPressStartedAt by remember { mutableStateOf<Long?>(null) }
    var typed by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var stall by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf("") }
    var showNumpad by remember { mutableStateOf(false) }
    var showSatDialog by remember { mutableStateOf(false) }
    val attempt = remember { intArrayOf(0) }
    val rounds = remember { intArrayOf(0) }
    val pl = remember { buildPlayer(ctx, settings.buffer) }
    val player = pl.player
    val focus = remember { FocusRequester() }
    val wide = LocalConfiguration.current.screenWidthDp >= 700

    LaunchedEffect(all) {
        if (all.isNotEmpty() && index >= all.size) {
            index = 0
        }
    }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = sdf.format(Date())
            delay(10000)
        }
    }

    LaunchedEffect(okPressStartedAt) {
        val startedAt = okPressStartedAt ?: return@LaunchedEffect
        delay(420)
        if (okPressStartedAt == startedAt && !panelOpen && !quickMenuOpen && !blocked && !showRefreshDialog && !showNumpad && !showSatDialog) {
            quickMenuOpen = true
        }
    }

    BackHandler(enabled = !blocked) {
        if (showRefreshDialog) {
            onDismissRefresh()
        } else if (showNumpad) {
            showNumpad = false
        } else if (showSatDialog) {
            showSatDialog = false
        } else if (quickMenuOpen) {
            quickMenuOpen = false
        } else if (panelOpen) {
            panelOpen = false
        } else {
            onExit()
        }
    }

    fun giveUp(code: String) {
        if (all.isEmpty()) return
        val ch = all[index.coerceIn(0, all.lastIndex)]
        onBroken(ch)
        if (st.skipBroken) {
            var j = (index + 1) % all.size
            var tries = 0
            while (tries < all.size - 1 && all[j].url in brokenNow) { j = (j + 1) % all.size; tries++ }
            if (all[j].url !in brokenNow) index = j
        }
        notice = "القناة الحالية غير متاحة: $code"
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                buffering = !isPlaying
                if (isPlaying) {
                    notice = null
                    rounds[0] = 0
                    stall = 0
                    if (all.isNotEmpty()) onOk(all[index.coerceIn(0, all.lastIndex)])
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                if (attempt[0] < 2 && all.isNotEmpty()) {
                    attempt[0]++
                    startChannel(pl, all[index.coerceIn(0, all.lastIndex)], attempt[0])
                    return
                }
                if (st.autoReconnect && rounds[0] < 3 && all.isNotEmpty()) {
                    rounds[0]++
                    notice = "إعادة الاتصال..."
                    val my = index
                    scope.launch {
                        delay(2500L * rounds[0])
                        if (index == my && all.isNotEmpty()) {
                            attempt[0] = 0
                            startChannel(pl, all[my.coerceIn(0, all.lastIndex)], 0)
                        }
                    }
                    return
                }
                giveUp(e.errorCodeName)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(index, reload, all.size) {
        if (all.isEmpty()) return@LaunchedEffect
        val safeIdx = index.coerceIn(0, all.lastIndex)
        error = null
        buffering = true
        attempt[0] = 0
        rounds[0] = 0
        stall = 0
        showInfo = true
        startChannel(pl, all[safeIdx], 0)
        onPlayed(all[safeIdx])
        delay(4000)
        showInfo = false
    }

    LaunchedEffect(buffering, stall, index) {
        if (buffering && error == null && st.autoReconnect && all.isNotEmpty()) {
            delay(20000)
            if (stall < 2) {
                stall++
                attempt[0] = 0
                notice = "إعادة الاتصال..."
                startChannel(pl, all[index.coerceIn(0, all.lastIndex)], 0)
            } else giveUp("TIMEOUT")
        }
    }

    LaunchedEffect(notice) { if (notice != null) { delay(2800); notice = null } }
    LaunchedEffect(panelOpen, quickMenuOpen, blocked, showRefreshDialog, showNumpad, showSatDialog) {
        if (!panelOpen && !quickMenuOpen && !blocked && !showRefreshDialog && !showNumpad && !showSatDialog) runCatching { focus.requestFocus() }
    }

    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(1500)
            val n = typed.toIntOrNull()
            if (n != null) {
                val byNum = all.indexOfFirst { it.num == n }
                if (byNum >= 0) index = byNum
                else if (all.none { it.num > 0 } && n in 1..all.size) index = n - 1
            }
            typed = ""
        }
    }

    fun next() { if (all.isNotEmpty()) index = (index + 1) % all.size }
    fun prev() { if (all.isNotEmpty()) index = (index - 1 + all.size) % all.size }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown && ev.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val d = digitOf(ev.key)
                if (d != null) {
                    if (ev.type == KeyEventType.KeyDown && typed.length < 4) typed += d.toString()
                    return@onPreviewKeyEvent true
                }
                val nativeCode = ev.key.keyCode.toInt()
                when {
                    ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter -> {
                        if (ev.type == KeyEventType.KeyDown) {
                            okPressStartedAt = SystemClock.elapsedRealtime()
                            return@onPreviewKeyEvent true
                        }
                        if (ev.type == KeyEventType.KeyUp) {
                            val startedAt = okPressStartedAt
                            val holdMs = startedAt?.let { SystemClock.elapsedRealtime() - it } ?: 0L
                            if (holdMs < 420L && !panelOpen && !quickMenuOpen && !blocked && !showRefreshDialog && !showNumpad && !showSatDialog) {
                                panelOpen = true
                            }
                            okPressStartedAt = null
                            return@onPreviewKeyEvent true
                        }
                    }
                    nativeCode == 183 -> {
                        val safeIdx = index.coerceIn(0, (all.size - 1).coerceAtLeast(0))
                        if (all.isNotEmpty()) onFav(all[safeIdx])
                        true
                    }
                    nativeCode == 184 -> {
                        onRefresh()
                        true
                    }
                    nativeCode == 185 -> {
                        val n = (settings.resize + 1) % 3
                        onResize(n)
                        notice = when (n) { 1 -> "ملء الشاشة (Zoom)"; 2 -> "تمديد (Stretch)"; else -> "ملاءمة (Fit)" }
                        true
                    }
                    nativeCode == 186 -> {
                        onSettings()
                        true
                    }
                    nativeCode == 165 || nativeCode == 172 || ev.key == Key.Info || ev.key == Key.Guide -> {
                        showInfo = !showInfo
                        true
                    }
                    ev.key == Key.DirectionUp || ev.key == Key.ChannelUp -> { next(); true }
                    ev.key == Key.DirectionDown || ev.key == Key.ChannelDown -> { prev(); true }
                    ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight -> { showInfo = !showInfo; true }
                    ev.key == Key.MediaPlayPause -> { player.playWhenReady = !player.playWhenReady; showInfo = true; true }
                    ev.key == Key.MediaNext -> { next(); true }
                    ev.key == Key.MediaPrevious -> { prev(); true }
                    else -> false
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showInfo = !showInfo }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    this.player = player
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = {
                it.resizeMode = when (settings.resize) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (buffering && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))

        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { reload++ }) { Text("إعادة المحاولة") }
                    Button(onClick = { next() }) { Text("القناة التالية") }
                }
            }
        }

        if (showInfo && !panelOpen && !quickMenuOpen && all.isNotEmpty()) {
            val safeIdx = index.coerceIn(0, all.lastIndex)
            val currentCh = all[safeIdx]
            ReceiverInfoBar(
                number = currentCh.numOr(safeIdx),
                channel = currentCh,
                totalChannels = all.size,
                currentTime = currentTime,
                onFav = { onFav(currentCh) },
                isFav = currentCh.url in favorites,
                onRefresh = onRefresh,
                onResize = {
                    val n = (settings.resize + 1) % 3
                    onResize(n)
                    notice = when (n) { 1 -> "ملء الشاشة (Zoom)"; 2 -> "تمديد (Stretch)"; else -> "ملاءمة (Fit)" }
                },
                onSettings = onSettings,
                onPanel = { panelOpen = true },
                isOfficial = isOfficial,
                activeSourceName = activeSourceName,
                onOpenNumpad = { showNumpad = true },
                onOpenSatDialog = { showSatDialog = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (quickMenuOpen) {
            QuickActionsDialog(
                onOpenChannels = { panelOpen = true },
                onOpenSources = { showSatDialog = true },
                onOpenSettings = onSettings,
                onDismiss = { quickMenuOpen = false }
            )
        }

        if (panelOpen) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { panelOpen = false } })
            ChannelPanel(
                all = all,
                current = index,
                favorites = favorites,
                broken = broken,
                activeSourceName = activeSourceName,
                isOfficial = isOfficial,
                onPick = { i -> index = i; panelOpen = false },
                onFav = onFav,
                onDelete = onDeleteChannel,
                onRefresh = onRefresh,
                onOpenSatDialog = { showSatDialog = true },
                onOpenNumpad = { showNumpad = true },
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(if (wide) 0.42f else 0.92f)
            )
        }

        if (showNumpad) {
            VirtualNumpadDialog(
                totalChannels = (all.maxOfOrNull { it.num } ?: 0).takeIf { it > 0 } ?: all.size,
                onJump = { targetIdx ->
                    val wanted = targetIdx + 1
                    val byNum = all.indexOfFirst { it.num == wanted }
                    if (byNum >= 0) index = byNum else if (all.none { it.num > 0 }) index = targetIdx
                    showNumpad = false
                    showInfo = true
                },
                onDismiss = { showNumpad = false }
            )
        }

        if (showSatDialog) {
            SatSourceDialog(
                sources = sources,
                activeId = activeSourceId,
                onSelectSource = { id ->
                    onSelectSource(id)
                    showSatDialog = false
                    panelOpen = false
                    quickMenuOpen = false
                },
                onDismiss = { showSatDialog = false }
            )
        }

        if (showRefreshDialog) {
            RefreshDialog(
                isScanning = isScanning,
                progress = refreshProgress,
                statusText = refreshStatusText,
                discovered = refreshChannels,
                newCount = refreshNewCount,
                complete = refreshComplete,
                onApply = onFinishRefresh,
                onDismiss = onDismissRefresh
            )
        }

        notice?.let {
            Text(
                it, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
                    .background(Color(0xDD000000), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
