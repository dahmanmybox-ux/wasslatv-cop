package dz.wassla.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Channel-list refresh screen for the active online source. */
@Composable
fun ScanScreen(vm: MainVm, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { vm.refreshCurrentSource() }
    LaunchedEffect(vm.refreshComplete) {
        if (vm.refreshComplete) {
            delay(120)
            runCatching { first.requestFocus() }
        }
    }
    val shown = vm.refreshChannels.reversed()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BgTop, BgBottom)))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تحديث قائمة القنوات", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(progress = { vm.refreshProgress }, modifier = Modifier.fillMaxWidth().height(10.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                vm.refreshStatusText,
                color = Accent, fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(shown.size) { i ->
                    Text("✓ " + shown[i], color = Color(0xCCFFFFFF), fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            if (vm.refreshComplete) {
                Text(
                    "عدد القنوات الجديدة: ${vm.refreshNewCount}",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(onClick = { vm.finishRefresh(); onClose() }, modifier = Modifier.focusRequester(first)) { Text("تم") }
            }
        }
    }
}
