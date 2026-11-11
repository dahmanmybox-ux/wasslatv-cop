package dz.wassla.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    valueColor: Color = Accent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusSurface(onClick, modifier.fillMaxWidth(), zoom = false) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = valueColor, fontSize = 14.sp)
        }
    }
}

@Composable
fun ToggleRow(title: String, on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    SettingRow(title, if (on) "مفعّل" else "متوقف", if (on) Accent else Color(0x88FFFFFF), modifier) { onChange(!on) }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
fun EditDialog(title: String, initial: String, singleLine: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v, onValueChange = { v = it }, singleLine = singleLine,
                minLines = if (singleLine) 1 else 3, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun SettingsScreen(vm: MainVm, onClose: () -> Unit, onAdd: () -> Unit) {
    val s = vm.settings
    val uri = LocalUriHandler.current
    var edit by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    BackHandler { onClose() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(BgTop)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚙ الإعدادات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PillButton("✕") { onClose() }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                item { SectionHeader("التشغيل") }
                item { ToggleRow("إعادة الاتصال التلقائي عند انقطاع البث", s.autoReconnect, Modifier.focusRequester(first)) { vm.updateSettings(s.copy(autoReconnect = it)) } }
                item { ToggleRow("تخطي القناة المعطلة تلقائياً", s.skipBroken) { vm.updateSettings(s.copy(skipBroken = it)) } }
                item { ToggleRow("إخفاء القنوات المعطلة", s.hideBroken) { vm.updateSettings(s.copy(hideBroken = it)) } }
                item { SettingRow("إعادة إظهار القنوات المعطلة", "${vm.broken.size}") { vm.clearBroken() } }
                item {
                    SettingRow("شكل الصورة", listOf("ملاءمة", "ملء الشاشة", "تمديد")[s.resize.coerceIn(0, 2)]) {
                        vm.updateSettings(s.copy(resize = (s.resize + 1) % 3))
                    }
                }
                item {
                    SettingRow("الذاكرة المؤقتة (تطبق عند فتح قناة)", listOf("سريع", "متوازن", "مستقر")[s.buffer.coerceIn(0, 2)]) {
                        vm.updateSettings(s.copy(buffer = (s.buffer + 1) % 3))
                    }
                }

                item { SectionHeader("مصادر القنوات") }
                val all = listOf(Playlist(OFFICIAL_ID, OFFICIAL_NAME, "")) + vm.playlists
                items(all.size) { i ->
                    val p = all[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingRow(p.name, if (vm.activeId == p.id) "✓ نشطة" else null, modifier = Modifier.weight(1f)) {
                            vm.selectPlaylist(p.id)
                            onClose()
                        }
                        if (p.id != OFFICIAL_ID && p.id != DEFAULT_ID) {
                            Spacer(Modifier.width(6.dp))
                            PillButton("🗑") { vm.deletePlaylist(p.id) }
                        }
                    }
                }
                item { SettingRow("＋ إضافة مصدر M3U أو Xtream") { onAdd() } }
                item {
                    SettingRow(
                        "تحديث المصدر الحالي",
                        if (vm.loading) "جارٍ التحديث..." else "${vm.channels.size} قناة"
                    ) { vm.refresh() }
                }
                item { ToggleRow("تحديث المصدر تلقائياً عند فتح التطبيق", s.autoUpdate) { vm.updateSettings(s.copy(autoUpdate = it)) } }
                vm.error?.let { message ->
                    item {
                        Text(
                            message,
                            color = Color(0xFFFFB4A9),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                item {
                    SettingRow("رابط القائمة الافتراضية", if (s.defaultUrl.isBlank()) "غير محدد" else "محدد") { edit = "default" }
                }
                item {
                    SettingRow("روابط القنوات الرسمية", "${s.officialUrls.lines().count { it.isNotBlank() }} رابط") { edit = "official" }
                }

                item { SectionHeader("المشاهدة") }
                item { ToggleRow("العودة إلى آخر قناة عند الفتح", s.resumeLast) { vm.updateSettings(s.copy(resumeLast = it)) } }
                item { ToggleRow("صوت التنقل", s.navSound) { vm.updateSettings(s.copy(navSound = it)) } }
                item {
                    val remain = if (vm.sleepAt > 0) ((vm.sleepAt - System.currentTimeMillis()) / 60000L + 1).toInt() else 0
                    SettingRow("مؤقت النوم", if (remain > 0) "بعد $remain د" else "متوقف") {
                        vm.setSleep(
                            when {
                                remain <= 0 -> 30
                                remain <= 30 -> 60
                                remain <= 60 -> 90
                                else -> 0
                            }
                        )
                    }
                }

                item { SectionHeader("حول التطبيق") }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(82.dp).clip(CircleShape)
                                .background(Color(0xFF132743))
                                .border(2.dp, Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("W", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Wassla TV", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("مشغل قنوات ومصادر يضيفها المستخدم", color = Gold, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("استخدم فقط قوائم ومصادر مصرحاً لك بها.", color = Color(0xCCFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
                item {
                    Text(
                        "تحديث التطبيق: لا يوجد مصدر إصدار موثوق مهيأ داخل التطبيق.",
                        color = Color(0xB3D7E4F5),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                item { SettingRow("زيارة صفحة المطور") { runCatching { uri.openUri("https://wasslatv.blogspot.com/") } } }
            }
        }
    }

    when (edit) {
        "default" -> EditDialog("رابط القائمة الافتراضية (M3U)", s.defaultUrl, true,
            { vm.updateSettings(s.copy(defaultUrl = it)); edit = null }, { edit = null })
        "official" -> EditDialog("روابط القنوات الرسمية (رابط في كل سطر)", s.officialUrls, false,
            { vm.updateSettings(s.copy(officialUrls = it.ifBlank { DEFAULT_OFFICIAL })); edit = null }, { edit = null })
    }
}
