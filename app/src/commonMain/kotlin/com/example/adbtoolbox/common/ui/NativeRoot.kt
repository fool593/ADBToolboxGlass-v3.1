package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectConfig
import com.example.adbtoolbox.common.GlassEffectPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立原生 UI（B 方案）：Material / Google 混合风，与玻璃 UI 完全独立的两套页面。
 * - 零新依赖（compose foundation 自绘 Surface/elevation/系统字体）；
 * - 业务逻辑复用同一 ADBTools.execCommand / AppCache / AppSettings，不复制数据源；
 * - 本文件不触碰玻璃引擎。
 */
@Composable
fun NativeRoot(onSwitchToGlass: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var navLock by remember { mutableStateOf(false) } // 原生导航纯点击：正在执行任务时仍可点击，无拖动逻辑
    val light = !AppSettings.isDarkMode
    val bg = if (light) Color(0xFFF3F4F6) else Color(0xFF111318)
    val surface = if (light) Color.White else Color(0xFF1C1F26)
    val onSurface = if (light) Color(0xFF1B1B1F) else Color(0xFFE8E8EC)
    val accent = Color(0xFF1A73E8) // Google Blue
    val sub = onSurface.copy(alpha = 0.62f)

    Column(Modifier.fillMaxSize().background(bg)) {
        // 顶部条
        Box(
            Modifier.fillMaxWidth().background(surface).shadow(2.dp).padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            BasicText(
                if (tab == 0) "ADB 工具箱 · 原生" else if (tab == 1) AppStrings.get("terminal") else AppStrings.get("settings"),
                style = TextStyle(color = onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            )
        }
        when (tab) {
            0 -> NativeHome(onSwitchToGlass, surface, onSurface, sub, accent, light)
            1 -> NativeMore(surface, onSurface, sub, accent)
            2 -> NativeModules(surface, onSurface, sub, accent)
            3 -> NativeAll(surface, onSurface, sub, accent)
            4 -> NativeTerminal(surface, onSurface, sub, accent)
            5 -> NativeSettings(onSwitchToGlass, surface, onSurface, sub, accent)
        }
        // 底部导航（原生风）
        Row(
            Modifier.fillMaxWidth().background(surface).shadow(4.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("首页", "功能", "模块", "全部", "终端", "设置").forEachIndexed { i, label ->
                val sel = tab == i
                Column(
                    Modifier.weight(1f).clickable { tab = i }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicText(
                        label,
                        style = TextStyle(
                            color = if (sel) accent else sub,
                            fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeCard(title: String, surface: Color, onSurface: Color, sub: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 14.dp)
            .shadow(1.dp, RoundedCornerShape(14.dp)).background(surface, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        BasicText(title, style = TextStyle(color = onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun NativeRowButton(label: String, accent: Color, onSurface: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = androidx.compose.foundation.interaction.MutableInteractionSource()
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f)
    )
    Box(
        modifier.shadow(1.dp, RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp).fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(label, style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun NativeHome(onSwitchToGlass: () -> Unit, surface: Color, onSurface: Color, sub: Color, accent: Color, light: Boolean) {
    var deviceInfo by remember { mutableStateOf("…") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.Default) {
            val b = ADBTools.execCommand("getprop ro.product.brand; getprop ro.product.model; getprop ro.build.version.release", 10).output.trim()
            if (b.isBlank()) "未检测到设备" else b.replace('\n', ' ')
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NativeCard("设备", surface, onSurface, sub) {
            BasicText(deviceInfo, style = TextStyle(color = sub, fontSize = 13.sp))
        }
        NativeCard("快速操作", surface, onSurface, sub) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeRowButton(AppStrings.get("adb_panel"), accent, onSurface) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand(
                                "setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; stop adbd; start adbd; getprop service.adb.tcp.port",
                                20
                            ).output.trim()
                        }
                    }
                }
                NativeRowButton(AppStrings.get("shell_executor"), accent, onSurface) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand("ls /sdcard | head -n 12; df -h /sdcard", 15).output.trim()
                        }
                    }
                }
                NativeRowButton(AppStrings.get("shield_entry"), accent, onSurface) {
                    scope.launch {
                        result = withContext(Dispatchers.Default) {
                            ADBTools.execCommand("settings get secure enabled_accessibility_services", 10).output.trim()
                        }
                    }
                }
            }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                BasicText(result, style = TextStyle(color = sub, fontSize = 11.sp))
            }
        }
        NativeCard("界面模式", surface, onSurface, sub) {
            NativeRowButton("切换到液态玻璃 UI", accent, onSurface, onClick = onSwitchToGlass)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NativeTerminal(surface: Color, onSurface: Color, sub: Color, accent: Color) {
    var input by remember { mutableStateOf("getprop ro.build.version.release") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Box(
            Modifier.fillMaxWidth().background(surface, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            BasicText(input, style = TextStyle(color = onSurface, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        }
        Spacer(Modifier.height(10.dp))
        NativeRowButton("执行", accent, onSurface) {
            scope.launch {
                output = withContext(Dispatchers.Default) { ADBTools.execCommand(input.trim(), 15).output.trim() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFF0D1117), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            BasicText(
                if (output.isEmpty()) "输出将显示在这里" else output,
                style = TextStyle(color = Color(0xFF58A6FF), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            )
        }
    }
}

@Composable
private fun NativeSettings(onSwitchToGlass: () -> Unit, surface: Color, onSurface: Color, sub: Color, accent: Color) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NativeCard(AppStrings.get("ui_style"), surface, onSurface, sub) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NativeRowButton("液态玻璃", accent, onSurface, Modifier.weight(1f), onClick = onSwitchToGlass)
                NativeRowButton("安卓原生", accent, onSurface, Modifier.weight(1f)) {}
            }
            Spacer(Modifier.height(10.dp))
            BasicText(AppStrings.get("language"), style = TextStyle(color = sub, fontSize = 13.sp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh" to "中文", "en" to "English", "hi" to "हिन्दी").forEach { (code, name) ->
                    NativeRowButton(name, if (AppSettings.language == code) accent else sub, onSurface, Modifier.weight(1f)) {
                        AppSettings.language = code
                        try { GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NativeMore(surface: Color, onSurface: Color, sub: Color, accent: Color) {
    var scanResult by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("…") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.Default) {
            ADBTools.execCommand("getprop ro.surface_flinger.max_frame_buffer_acquired_buffers; settings get system peak_refresh_rate 2>/dev/null", 12).output.trim().ifBlank { "未知" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NativeCard("帧率信息", surface, onSurface, sub) {
            BasicText(info, style = TextStyle(color = sub, fontSize = 13.sp))
        }
        NativeCard("安全护盾（root 全盘扫描）", surface, onSurface, sub) {
            if (scanResult != null) {
                BasicText(scanResult!!, style = TextStyle(color = sub, fontSize = 11.sp))
                Spacer(Modifier.height(8.dp))
            }
            NativeRowButton(if (scanning) "扫描中…" else "开始全盘扫描", accent, onSurface) {
                if (!scanning) {
                    scanning = true
                    scope.launch {
                        scanResult = withContext(Dispatchers.Default) {
                            val r = com.example.adbtoolbox.common.Shield.scan()
                            if (r.exitCode == 0) r.output else r.output + "\n" + r.error
                        }
                        scanning = false
                    }
                }
            }
        }
        NativeCard("下载 / 加载动画（谷歌原生观感）", surface, onSurface, sub) {
            val trans = androidx.compose.animation.core.rememberInfiniteTransition()
            val anim by trans.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)))
            Box(Modifier.fillMaxWidth().height(6.dp).background(onSurface.copy(alpha = 0.12f), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(anim).height(6.dp).background(accent, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(8.dp))
            BasicText("Material 风格进度条（循环下载动画）", style = TextStyle(color = sub, fontSize = 11.sp))
            Spacer(Modifier.height(10.dp))
            NativeRowButton("演示：执行耗时命令", accent, onSurface) {
                scope.launch {
                    info = withContext(Dispatchers.Default) {
                        "find 输出前 20 行：\n" + ADBTools.execCommand("find /sdcard -maxdepth 2 -type f 2>/dev/null | head -n 20", 20).output.trim().take(240)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NativeModules(surface: Color, onSurface: Color, sub: Color, accent: Color) {
    var fps by remember { mutableStateOf("…") }
    var modules by remember { mutableStateOf("…") }
    var restore by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.Default) {
            ADBTools.execCommand("settings get system peak_refresh_rate 2>/dev/null; dumpsys display 2>/dev/null | grep -iE \"peak|refresh\" | head -n 4", 15)
        }
        fps = if (r.exitCode == 0 && r.output.isNotBlank()) r.output.trim() else "未读取到（华为机需走 ADB/Shizuku）"
        val m = withContext(Dispatchers.Default) {
            ADBTools.execCommand("su -c ls -1 /data/adb/modules 2>/dev/null || ls /data/adb/modules 2>/dev/null", 12)
        }
        modules = if (m.exitCode == 0 && m.output.isNotBlank()) m.output.trim() else "未检测到模块或需 Root/提权"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NativeCard("游戏帧率", surface, onSurface, sub) {
            BasicText(fps, style = TextStyle(color = sub, fontSize = 13.sp))
        }
        NativeCard("已安装 Root 模块", surface, onSurface, sub) {
            BasicText(modules, style = TextStyle(color = sub, fontSize = 13.sp))
        }
        NativeCard("还原系统默认", surface, onSurface, sub) {
            BasicText("完整还原（47 键 + 组件 + appops）在玻璃 UI 的「还原系统默认设置」页；此处只读示例值。", style = TextStyle(color = sub, fontSize = 12.sp))
            Spacer(Modifier.height(8.dp))
            NativeRowButton("读取受影响设置（示例）", accent, onSurface) {
                scope.launch {
                    restore = withContext(Dispatchers.Default) {
                        val a = ADBTools.execCommand("settings get global window_animation_scale; settings get secure enabled_accessibility_services 2>/dev/null | head -c 80", 12)
                        a.output.trim().ifBlank { "无输出" }
                    }
                }
            }
            if (restore.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                BasicText(restore, style = TextStyle(color = sub, fontSize = 11.sp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NativeAll(surface: Color, onSurface: Color, sub: Color, accent: Color) {
    var out by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NativeCard("全部功能（原生补齐）", surface, onSurface, sub) {
            val rows = listOf(
                Triple("权限（真实列出）", "cmd appops get --uid all 2>/dev/null | head -n 6", "perms"),
                Triple("已装应用（真实列表）", "pm list packages -3 2>/dev/null | head -n 10", "apps"),
                Triple("华为优化信息（真实属性）", "getprop ro.config.hw_theme 2>/dev/null; getprop ro.product.brand; getprop ro.build.version.release", "hw"),
                Triple("品牌分类（真实厂商）", "getprop ro.product.brand; getprop ro.product.marketname 2>/dev/null", "brand"),
                Triple("手机体检（磁盘/CPU/内存）", "df -h /sdcard | tail -n 1; cat /proc/loadavg; cat /proc/meminfo | head -n 3", "health"),
                Triple("还原默认（真实读受影响键）", "settings get global window_animation_scale; settings get system font_scale; settings get secure backup_enabled", "restore"),
                Triple("内核提权探测（真实 uname+su）", "uname -r; su -c id 2>/dev/null || id", "kroot"),
                Triple("Root 工具（真实探测）", "su -c id 2>/dev/null || echo no-root; getprop ro.product.device", "root"),
                Triple("截图到下载（真实操作）", "screencap -p /sdcard/Download/native_shot.png && echo saved", "shot")
            )
            rows.forEach { (label, cmd, _) ->
                NativeRowButton(label, accent, onSurface) {
                    scope.launch {
                        out = withContext(Dispatchers.Default) {
                            val r = ADBTools.execCommand(cmd, 20)
                            if (r.exitCode == 0 && r.output.isNotBlank()) r.output.trim().take(300) else "（无输出/需提权）"
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (out.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                BasicText(out, style = TextStyle(color = sub, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }
        NativeCard("说明", surface, onSurface, sub) {
            BasicText("深度流程（GhostLock 一键/全量还原/模块刷入）保留在玻璃 UI 对应页可直接使用；此处提供独立的原生入口与真实回读。", style = TextStyle(color = sub, fontSize = 12.sp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
