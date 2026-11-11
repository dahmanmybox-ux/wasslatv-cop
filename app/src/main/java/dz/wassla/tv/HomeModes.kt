package dz.wassla.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/** Home display modes, same idea as the desktop app: classic / TiviMate / Smarters / cinema. */
val HOME_MODE_NAMES = listOf("كلاسيك", "TiviMate", "Smarters", "سينما")
val HOME_MODE_ICONS = listOf("▤", "☰", "⊞", "🎬")

@Composable
fun ChannelsView(
    vm: MainVm,
    list: List<Channel>,
    groups: List<Pair<String, Int>>,
    wide: Boolean,
    onPlay: (Channel) -> Unit
) {
    var showAll by remember(vm.settings.homeMode) { mutableStateOf(false) }
    when (vm.settings.homeMode) {
        1 -> TiviMateView(vm, list, onPlay)
        3 -> PosterGrid(vm, list, wide, onPlay)
        2 -> {
            val tiles = vm.group == null && !vm.favOnly && vm.query.isBlank() && !showAll
            if (tiles) {
                GroupTilesView(vm, groups) { showAll = true }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PillButton("🏠 الأقسام", false, "العودة إلى الأقسام") {
                        vm.group = null
                        vm.favOnly = false
                        showAll = false
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f)) { ClassicGrid(vm, list, wide, onPlay) }
                }
            }
        }
        else -> ClassicGrid(vm, list, wide, onPlay)
    }
}

@Composable
fun ClassicGrid(vm: MainVm, list: List<Channel>, wide: Boolean, onPlay: (Channel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 160.dp else 128.dp),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list.size, key = { list[it].url }) { i ->
            val ch = list[i]
            ChannelCard(
                ch.numOr(i), ch, ch.url in vm.favorites, ch.url in vm.broken,
                onClick = { onPlay(ch) }, onLong = { vm.toggleFav(ch) }
            )
        }
    }
}

/* ---------------- TiviMate: groups (sidebar) | channel rows | info panel ---------------- */

@Composable
fun TiviMateView(vm: MainVm, list: List<Channel>, onPlay: (Channel) -> Unit) {
    var sel by remember(list) { mutableStateOf(list.firstOrNull()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val info = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(list.size, key = { list[it].url }) { i ->
                    val ch = list[i]
                    ChannelRowItem(
                        ch.numOr(i), ch, ch.url in vm.favorites, ch.url in vm.broken,
                        onFocus = { sel = ch }, onClick = { onPlay(ch) }, onLong = { vm.toggleFav(ch) }
                    )
                }
            }
            val s = sel
            if (info && s != null) {
                Spacer(Modifier.width(12.dp))
                ChannelInfoPanel(
                    s, s.numOr(list.indexOf(s)), s.url in vm.favorites,
                    Modifier.width(260.dp).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun ChannelRowItem(
    number: Int,
    ch: Channel,
    fav: Boolean,
    isBroken: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    FocusSurface(
        onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        onLong = onLong,
        zoom = false,
        contentLabel = "القناة $number: ${ch.name}"
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%03d".format(number),
                color = Gold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                if (ch.logo != null) {
                    SubcomposeAsyncImage(
                        model = ch.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        error = { Initial(ch.name) }
                    )
                } else {
                    Initial(ch.name)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                ch.name,
                color = if (isBroken) Color(0x88FFFFFF) else Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (fav) Text("★", color = Gold, fontSize = 16.sp)
            if (isBroken) Text(" ⚠", color = Gold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ChannelInfoPanel(ch: Channel, number: Int, fav: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Glass).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33000000)),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) {
                SubcomposeAsyncImage(
                    model = ch.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    error = { Initial(ch.name) }
                )
            } else {
                Initial(ch.name)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("%03d".format(number), color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            ch.name,
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(ch.group, color = Color(0xAAFFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        if (fav) Text("★ في المفضلة", color = Gold, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(
            "OK للتشغيل · ضغط مطوّل للمفضلة",
            color = Color(0x99FFFFFF),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/* ---------------- Smarters: big section tiles ---------------- */

@Composable
fun GroupTilesView(vm: MainVm, groups: List<Pair<String, Int>>, onAll: () -> Unit) {
    val total = groups.sumOf { it.second }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { GroupTile("📺 كل القنوات", total, onAll) }
        item { GroupTile("★ المفضلة", vm.favorites.size) { vm.favOnly = true } }
        items(groups.size) { i ->
            val g = groups[i]
            GroupTile(g.first, g.second) { vm.group = g.first }
        }
    }
}

@Composable
fun GroupTile(label: String, count: Int, onClick: () -> Unit) {
    FocusSurface(
        onClick,
        modifier = Modifier.fillMaxWidth().height(110.dp),
        shape = RoundedCornerShape(20.dp),
        contentLabel = "$label، $count قناة"
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text("$count قناة", color = Gold, fontSize = 13.sp)
        }
    }
}

/* ---------------- Cinema: poster cards ---------------- */

@Composable
fun PosterGrid(vm: MainVm, list: List<Channel>, wide: Boolean, onPlay: (Channel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 150.dp else 115.dp),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list.size, key = { list[it].url }) { i ->
            val ch = list[i]
            PosterCard(
                ch.numOr(i), ch, ch.url in vm.favorites, ch.url in vm.broken,
                onClick = { onPlay(ch) }, onLong = { vm.toggleFav(ch) }
            )
        }
    }
}

@Composable
fun PosterCard(
    number: Int,
    ch: Channel,
    fav: Boolean,
    isBroken: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    FocusSurface(
        onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        onLong = onLong,
        shape = RoundedCornerShape(16.dp),
        contentLabel = "القناة $number: ${ch.name}"
    ) {
        if (ch.logo != null) {
            SubcomposeAsyncImage(
                model = ch.logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { Box(Modifier.fillMaxSize(), Alignment.Center) { Initial(ch.name) } }
            )
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Initial(ch.name) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("%03d".format(number), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                ch.name,
                color = if (isBroken) Color(0x88FFFFFF) else Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (fav) Text("★", color = Gold, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        if (isBroken) Text("⚠", color = Gold, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
