package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
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
            1 -> NativeTerminal(surface, onSurface, sub, accent)
            2 -> NativeSettings(onSwitchToGlass, surface, onSurface, sub, accent)
        }
        // 底部导航（原生风）
        Row(
            Modifier.fillMaxWidth().background(surface).shadow(4.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("首页", "终端", "设置").forEachIndexed { i, label ->
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
    Box(
        modifier.shadow(1.dp, RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp).fillMaxWidth(),
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