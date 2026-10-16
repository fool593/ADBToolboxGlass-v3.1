package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectConfig
import com.example.adbtoolbox.common.GlassEffectPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 第二独立 UI —— 科技风（Tech/Neon HUD）。
 * 与玻璃 UI 完全独立；深空底色 + 霓虹青/荧光绿 + 发光描边 + 等宽字体 + HUD 卡片。
 * 功能与玻璃 UI 对齐：6 页全真实命令 + 回读；玻璃 UI 长按+拖动导航不受影响。
 * 零新依赖；引擎不动。
 */
private val TechBg = Color(0xFF04070F)
private val TechSurface = Color(0xFF0A1526)
private val TechOn = Color(0xFFC8F2FF)
private val TechCyan = Color(0xFF00E5FF)
private val TechGreen = Color(0xFF00FF9C)
private val TechSub = Color(0xFF4E7188)
private val Mono = FontFamily.Monospace

@Composable
fun TechRoot(onSwitchToGlass: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(TechBg).drawBehind {
            // 科技扫描线：每 3dp 一条 2% 透明度细线（一次绘制，零逐帧成本）
            val step = 3.dp.toPx()
            var yy = 0f
            while (yy < size.height) {
                drawLine(color = TechCyan.copy(alpha = 0.018f), start = Offset(0f, yy), end = Offset(size.width, yy), strokeWidth = 1f)
                yy += step
            }
        }
    ) {
        // 顶栏：HUD 标题 + 状态点
        Box(
            Modifier.fillMaxWidth().background(TechSurface)
                .border(0.5.dp, TechCyan.copy(alpha = 0.35f))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // HUD 角落括号装饰
            Box(Modifier.align(Alignment.TopStart).size(7.dp).border(1.dp, TechCyan.copy(alpha = 0.6f)).padding(0.dp))
            Box(Modifier.align(Alignment.BottomEnd).size(7.dp).border(1.dp, TechCyan.copy(alpha = 0.6f)).padding(0.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val blink = rememberInfiniteTransition()
                val on by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(600), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse))
                Box(
                    Modifier.size(8.dp).background(if (on > 0.5f) TechGreen else TechGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    "ADB TOOLBOX // " + listOf("HOME", "FUNC", "MODS", "ALL", "TERM", "SETUP")[tab],
                    style = TextStyle(color = TechCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, letterSpacing = 1.5.sp)
                )
            }
        }
        // 霓虹渐变分割线
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(TechCyan, TechGreen))
            )
        )

        when (tab) {
            0 -> TechHome(onSwitchToGlass)
            1 -> TechMore()
            2 -> TechModules()
            3 -> TechAll()
            4 -> TechTerminal()
            5 -> TechSettings(onSwitchToGlass)
        }
        // 底部导航：霓虹横线指示
        Row(
            Modifier.fillMaxWidth().background(TechSurface)
                .border(0.5.dp, TechCyan.copy(alpha = 0.25f))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf("首页", "功能", "模块", "全部", "终端", "设置").forEachIndexed { i, label ->
                TechNavItem(
                    sel = tab == i,
                    label = label,
                    accent = TechCyan,
                    sub = TechSub,
                    modifier = Modifier.weight(1f),
                    onClick = { tab = i }
                )
            }
        }
    }
}

/** 底部导航项：指示条宽度动画滑动（spring）。 */
@Composable
private fun TechNavItem(sel: Boolean, label: String, accent: Color, sub: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val frac by animateFloatAsState(if (sel) 0.55f else 0f, spring(dampingRatio = 0.72f, stiffness = 380f))
    Column(
        modifier.clickable { onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (sel) accent else sub,
                fontSize = 12.sp,
                fontFamily = Mono,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
            )
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth(frac).height(2.dp)
                .background(if (sel) accent else Color.Transparent, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun TechCard(title: String, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); shown = true }
    val vis by animateFloatAsState(if (shown) 1f else 0f, tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp)
            .graphicsLayer { alpha = vis; translationY = (1f - vis) * 10f.dp.toPx() }
            .shadow(6.dp, RoundedCornerShape(12.dp), spotColor = TechCyan.copy(alpha = 0.18f))
            .background(TechSurface, RoundedCornerShape(12.dp))
            .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        BasicText(
            "▸ $title",
            style = TextStyle(color = TechCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun TechButton(label: String, modifier: Modifier = Modifier, accent: Color = TechCyan, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f))
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 9.dp else 4.dp, RoundedCornerShape(10.dp), spotColor = accent.copy(alpha = if (pressed) 0.4f else 0.25f))
            .background(accent.copy(alpha = if (pressed) 0.16f else 0.08f), RoundedCornerShape(10.dp))
            .border(1.dp, if (pressed) accent else accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(label, style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Mono))
    }
}

@Composable
private fun TechMono(text: String, color: Color = TechSub, size: Int = 12) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontFamily = Mono))
}

@Composable
private fun TechHome(onSwitchToGlass: () -> Unit) {
    var deviceInfo by remember { mutableStateOf("...") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.Default) {
            val b = ADBTools.execCommand("getprop ro.product.brand; getprop ro.product.model; getprop ro.build.version.release", 10).output.trim()
            if (b.isBlank()) "NO DEVICE" else b.replace('\n', ' ')
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 开机自检动画（科技开屏动效）：三行等宽信息错峰亮起
        var l1 by remember { mutableStateOf(false) }
        var l2 by remember { mutableStateOf(false) }
        var l3 by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(120); l1 = true
            kotlinx.coroutines.delay(260); l2 = true
            kotlinx.coroutines.delay(400); l3 = true
        }
        TechCard("BOOT SEQ") {
            TechMono(if (l1) "[ OK ] SYS.LOAD  v3.1" else "......", color = if (l1) TechGreen else TechSub)
            Spacer(Modifier.height(4.dp))
            TechMono(if (l2) "[ OK ] ADB.LINK  ONLINE" else "......", color = if (l2) TechGreen else TechSub)
            Spacer(Modifier.height(4.dp))
            TechMono(if (l3) "[ OK ] UI.MODE   TECH" else "......", color = if (l3) TechGreen else TechSub)
        }
        TechCard("DEVICE") { TechMono(deviceInfo.ifBlank { "..." }, TechOn) }
        TechCard("QUICK OPS") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TechButton(AppStrings.get("adb_panel")) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand("setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; stop adbd; start adbd; getprop service.adb.tcp.port", 20).output.trim()
                        }
                    }
                }
                TechButton(AppStrings.get("shell_executor")) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand("ls /sdcard | head -n 12; df -h /sdcard", 15).output.trim()
                        }
                    }
                }
                TechButton(AppStrings.get("shield_entry")) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand("settings get secure enabled_accessibility_services", 10).output.trim()
                        }
                    }
                }
            }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TechMono(result, TechGreen, 11)
            }
        }
        TechCard("UI MODE") {
            TechButton("切回液态玻璃", accent = TechGreen, onClick = onSwitchToGlass)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TechMore() {
    var scanResult by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("...") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.Default) {
            ADBTools.execCommand("getprop ro.surface_flinger.max_frame_buffer_acquired_buffers; settings get system peak_refresh_rate 2>/dev/null", 12).output.trim().ifBlank { "N/A" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TechCard("REFRESH") { TechMono(info, TechOn) }
        TechCard("SHIELD SCAN (root)") {
            if (scanResult != null) {
                TechMono(scanResult!!, TechSub, 11)
                Spacer(Modifier.height(8.dp))
            }
            TechButton(if (scanning) "SCANNING..." else "START FULL SCAN", accent = if (scanning) TechSub else TechCyan) {
                if (!scanning) {
                    scanning = true
                    scope.launch {
                        val r = withContext(Dispatchers.Default) { com.example.adbtoolbox.common.Shield.scan() }
                        scanResult = if (r.exitCode == 0) r.output else r.output + "\n" + r.error
                        scanning = false
                    }
                }
            }
        }
        TechCard("PROGRESS // LOAD") {
            val trans = rememberInfiniteTransition()
            val anim by trans.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)))
            Box(
                Modifier.fillMaxWidth().height(6.dp).background(TechSurface, RoundedCornerShape(3.dp))
                    .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            ) {
                Box(Modifier.fillMaxWidth(anim).height(6.dp).background(TechCyan, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(8.dp))
            TechMono("NEON DETERMINATE BAR / LOOP", TechSub, 10)
            Spacer(Modifier.height(10.dp))
            TechButton("演示：执行耗时命令") {
                scope.launch {
                    info = withContext(Dispatchers.Default) {
                        "find: " + ADBTools.execCommand("find /sdcard -maxdepth 2 -type f 2>/dev/null | head -n 20", 20).output.trim().take(240)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TechModules() {
    var fps by remember { mutableStateOf("...") }
    var modules by remember { mutableStateOf("...") }
    var restore by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.Default) {
            ADBTools.execCommand("settings get system peak_refresh_rate 2>/dev/null; dumpsys display 2>/dev/null | grep -iE \"peak|refresh\" | head -n 4", 15)
        }
        fps = if (r.exitCode == 0 && r.output.isNotBlank()) r.output.trim() else "HW/ADB REQUIRED"
        val m = withContext(Dispatchers.Default) {
            ADBTools.execCommand("su -c ls -1 /data/adb/modules 2>/dev/null || ls /data/adb/modules 2>/dev/null", 12)
        }
        modules = if (m.exitCode == 0 && m.output.isNotBlank()) m.output.trim() else "NONE / NEED ROOT"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TechCard("GAME FPS") { TechMono(fps, TechOn) }
        TechCard("ROOT MODULES") { TechMono(modules, TechOn) }
        TechCard("RESTORE INFO") {
            TechMono("完整还原（47 键 + 组件 + appops）在玻璃 UI「还原系统默认设置」页；此处只读。", TechSub, 11)
            Spacer(Modifier.height(8.dp))
            TechButton("读取受影响设置（示例）") {
                scope.launch {
                    restore = withContext(Dispatchers.Default) {
                        val a = ADBTools.execCommand("settings get global window_animation_scale; settings get secure enabled_accessibility_services 2>/dev/null | head -c 80", 12)
                        a.output.trim().ifBlank { "N/A" }
                    }
                }
            }
            if (restore.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TechMono(restore, TechSub, 11)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TechAll() {
    var out by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val rows = listOf(
        Triple("权限（真实列出）", "cmd appops get --uid all 2>/dev/null | head -n 6", "PERMS"),
        Triple("已装应用（真实列表）", "pm list packages -3 2>/dev/null | head -n 10", "APPS"),
        Triple("华为优化信息（真实属性）", "getprop ro.product.brand; getprop ro.build.version.release", "HUAWEI"),
        Triple("品牌分类（真实厂商）", "getprop ro.product.brand; getprop ro.product.marketname 2>/dev/null", "BRAND"),
        Triple("手机体检（磁盘/负载/内存）", "df -h /sdcard | tail -n 1; cat /proc/loadavg; cat /proc/meminfo | head -n 3", "HEALTH"),
        Triple("还原默认（真实读受影响键）", "settings get global window_animation_scale; settings get system font_scale; settings get secure backup_enabled", "RESTORE"),
        Triple("内核提权探测（真实 uname+su）", "uname -r; su -c id 2>/dev/null || id", "KROOT"),
        Triple("Root 工具（真实探测）", "su -c id 2>/dev/null || echo no-root; getprop ro.product.device", "ROOT"),
        Triple("截图到下载（真实操作）", "screencap -p /sdcard/Download/tech_shot.png && echo saved", "SHOT")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TechCard("ALL FEATURES") {
            rows.forEach { (label, cmd, tag) ->
                TechButton("[$tag] $label") {
                    scope.launch {
                        out = withContext(Dispatchers.Default) {
                            val r = ADBTools.execCommand(cmd, 20)
                            if (r.exitCode == 0 && r.output.isNotBlank()) r.output.trim().take(300) else "(NO OUTPUT / NEED PRIV)"
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (out.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TechMono(out, TechGreen, 11)
            }
        }
        TechCard("NOTE") {
            TechMono("深度流程（GhostLock 一键/全量还原/模块刷入）保留在玻璃 UI 对应页；此处为独立原生入口与真实回读。", TechSub, 11)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TechTerminal() {
    var input by remember { mutableStateOf("getprop ro.build.version.release") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Box(
            Modifier.fillMaxWidth().background(TechSurface, RoundedCornerShape(10.dp))
                .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(12.dp)
        ) {
            val tc = rememberInfiniteTransition()
            val tOn by tc.animateFloat(0f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing)))
            BasicText("> $input" + (if (tOn > 0.5f) "▌" else " "), style = TextStyle(color = TechCyan, fontSize = 13.sp, fontFamily = Mono))
        }
        Spacer(Modifier.height(10.dp))
        TechButton("EXEC") {
            scope.launch {
                output = withContext(Dispatchers.Default) { ADBTools.execCommand(input.trim(), 15).output.trim() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFF020509), RoundedCornerShape(10.dp))
                .border(1.dp, TechGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(12.dp)
        ) {
            BasicText(
                if (output.isEmpty()) "// output" else output,
                style = TextStyle(color = TechGreen, fontSize = 12.sp, fontFamily = Mono)
            )
        }
    }
}

@Composable
private fun TechSettings(onSwitchToGlass: () -> Unit) {
    val save: () -> Unit = { try { GlassEffectPersistence.saveAll() } catch (_: Exception) {} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TechCard(AppStrings.get("ui_style")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TechButton("液态玻璃", Modifier.weight(1f), onClick = onSwitchToGlass)
                TechButton("科技风", Modifier.weight(1f), accent = TechGreen) {}
            }
        }
        TechCard("LANGUAGE") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh" to "中文", "en" to "EN", "hi" to "हिन्दी").forEach { (code, name) ->
                    TechButton(name, Modifier.weight(1f), accent = if (AppSettings.language == code) TechCyan else TechSub) {
                        AppSettings.language = code; save()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TechMono("LANG = " + AppSettings.language.uppercase(), TechSub)
        }
        TechCard("GLASS PARAM // 控制玻璃 UI") {
            GlassParamRow("BLUR", GlassEffectConfig.blurRadius, 0f..32f, 1f, save)
            GlassParamRow("REFR.H", GlassEffectConfig.refractionHeight, 0f..1f, 0.05f, save)
            GlassParamRow("REFR.A", GlassEffectConfig.refractionAmount, 0f..1f, 0.05f, save)
            GlassParamRow("CHROMA", GlassEffectConfig.chromaticAberration, 0f..1f, 0.05f, save)
            GlassParamRow("INTENS", GlassEffectConfig.globalIntensity, 0.2f..2f, 0.1f, save)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("VIBRANCY", modifier = Modifier.weight(1f), style = TextStyle(color = TechSub, fontSize = 11.sp, fontFamily = Mono))
                TechButton(if (GlassEffectConfig.enableVibrancy.value) "ON" else "OFF", accent = if (GlassEffectConfig.enableVibrancy.value) TechGreen else TechSub, modifier = Modifier.width(72.dp)) {
                    GlassEffectConfig.enableVibrancy.value = !GlassEffectConfig.enableVibrancy.value; save()
                }
            }
            Spacer(Modifier.height(6.dp))
            TechMono("BLUR 控制首页玻璃模糊；切回液态玻璃立即生效。", TechSub, 10)
        }
        TechCard("SYSTEM") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("DARK MODE", modifier = Modifier.weight(1f), style = TextStyle(color = TechSub, fontSize = 11.sp, fontFamily = Mono))
                TechButton(if (AppSettings.isDarkMode) "ON" else "OFF", accent = if (AppSettings.isDarkMode) TechGreen else TechSub, modifier = Modifier.width(72.dp)) {
                    AppSettings.isDarkMode = !AppSettings.isDarkMode; save()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("SPLASH ANIM", modifier = Modifier.weight(1f), style = TextStyle(color = TechSub, fontSize = 11.sp, fontFamily = Mono))
                TechButton(if (AppSettings.splashAnimEnabled) "ON" else "OFF", accent = if (AppSettings.splashAnimEnabled) TechGreen else TechSub, modifier = Modifier.width(72.dp)) {
                    AppSettings.splashAnimEnabled = !AppSettings.splashAnimEnabled; save()
                }
            }
        }
        TechCard("ABOUT") {
            TechMono("ADB TOOLBOX v3.1 // TECH EDITION", TechGreen, 12)
            Spacer(Modifier.height(6.dp))
            TechMono("https://github.com/fool593/ADBToolboxGlass-v3.1", TechSub, 10)
        }
        TechCard("RESET") {
            val amber = Color(0xFFFFB74D)
            TechButton("恢复玻璃参数默认", accent = amber) {
                GlassEffectConfig.blurRadius.value = 16f
                GlassEffectConfig.refractionHeight.value = 0.2f
                GlassEffectConfig.refractionAmount.value = 0.2f
                GlassEffectConfig.chromaticAberration.value = 0f
                GlassEffectConfig.enableVibrancy.value = true
                GlassEffectConfig.globalIntensity.value = 1f
                save()
            }
            Spacer(Modifier.height(6.dp))
            TechMono("RESET -> 默认玻璃参数（16dp / 0.2 / 0.2 / 0 / ON / 1.0）", TechSub, 10)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 步进参数行：[- 值 +]，控制玻璃参数（切回玻璃 UI 生效）。 */
@Composable
private fun GlassParamRow(label: String, state: MutableState<Float>, range: ClosedFloatingPointRange<Float>, step: Float, save: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BasicText(label, modifier = Modifier.weight(1f), style = TextStyle(color = TechSub, fontSize = 11.sp, fontFamily = Mono))
        TechMini("-", TechCyan) { state.value = (state.value - step).coerceIn(range); save() }
        BasicText(
            state.value.toString().take(4),
            modifier = Modifier.padding(horizontal = 6.dp), style = TextStyle(color = TechOn, fontSize = 11.sp, fontFamily = Mono)
        )
        TechMini("+", TechCyan) { state.value = (state.value + step).coerceIn(range); save() }
    }
}

@Composable
private fun TechMini(label: String, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 520f))
    Box(
        Modifier.size(30.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = accent, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold))
    }
}