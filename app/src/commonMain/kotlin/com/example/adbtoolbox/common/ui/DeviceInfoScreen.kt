package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.DeviceInfoData
import com.example.adbtoolbox.common.theme.AppLayout
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DeviceInfoScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var deviceInfo by remember { mutableStateOf<DeviceInfoData?>(null) }
    var cpuInfo by remember { mutableStateOf("") }
    var cpuCores by remember { mutableStateOf(0) }
    var resolution by remember { mutableStateOf("") }
    var kernel by remember { mutableStateOf("") }
    var shizukuAvailable by remember { mutableStateOf(false) }
    // 刷新率：真实测量值（当前/最高/支持档位）
    var currentRefreshRate by remember { mutableStateOf(0f) }
    var maxRefreshRate by remember { mutableStateOf(0f) }
    var supportedRefreshRates by remember { mutableStateOf<List<Float>>(emptyList()) }
    var fixingRefreshRate by remember { mutableStateOf(false) }
    var refreshRateMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // 优先从预加载缓存读取，秒开
        if (AppCache.deviceInfoLoaded.value) {
            deviceInfo = AppCache.deviceInfo.value
            cpuInfo = AppCache.cpuInfo.value
            cpuCores = AppCache.cpuCores.value
            resolution = AppCache.screenResolution.value
            kernel = AppCache.kernelVersion.value
        } else {
            deviceInfo = withContext(Dispatchers.Default) { ADBTools.getDeviceInfo() }
            cpuInfo = withContext(Dispatchers.Default) { ADBTools.getCpuInfo() }
            cpuCores = withContext(Dispatchers.Default) { ADBTools.getCpuCores() }
            resolution = withContext(Dispatchers.Default) { ADBTools.getScreenResolution() }
            kernel = withContext(Dispatchers.Default) { ADBTools.getKernelVersion() }
            AppCache.deviceInfo.value = deviceInfo
            AppCache.cpuInfo.value = cpuInfo
            AppCache.cpuCores.value = cpuCores
            AppCache.screenResolution.value = resolution
            AppCache.kernelVersion.value = kernel
            AppCache.deviceInfoLoaded.value = true
        }
        shizukuAvailable = withContext(Dispatchers.Default) { ADBTools.isShizukuAvailable() }
        // 刷新率始终实测（缓存里没有这几项，且修复后需要立刻反映真实值）
        supportedRefreshRates = withContext(Dispatchers.Default) { ADBTools.getSupportedRefreshRates() }
        maxRefreshRate = withContext(Dispatchers.Default) { ADBTools.getMaxRefreshRate() }
        currentRefreshRate = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
    }

    // 出现"高刷屏被锁在 60Hz / 当前低于屏幕能力"时给出修复入口
    val needFixRefreshRate = maxRefreshRate > 61f &&
            (currentRefreshRate < maxRefreshRate - 1f || currentRefreshRate <= 61f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            // 返回按钮与标题之间是水平间距：以前误用 height，标题会贴死按钮
            Spacer(Modifier.width(AppLayout.headerGap))
            BasicText(AppStrings.get("device_info"), style = TextStyle(contentColor, AppLayout.titleSize, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        StaggeredCard(0) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                SectionTitle(AppStrings.get("device_info"), contentColor)
                deviceInfo?.let { info ->
                    InfoRow(AppStrings.get("model"), info.model, contentColor)
                    InfoRow(AppStrings.get("brand"), info.brand, contentColor)
                    InfoRow(AppStrings.get("android_version"), info.androidVersion, contentColor)
                    InfoRow(AppStrings.get("sdk"), info.sdkVersion.toString(), contentColor)
                    InfoRow("${AppStrings.get("sdk")} ${AppStrings.get("version")}", info.buildNumber, contentColor)
                    InfoRow(AppStrings.get("kernel_version"), kernel, contentColor)
                    InfoRow("CPU ${AppStrings.get("version")}", info.cpuAbi, contentColor)
                    InfoRow("CPU ${AppStrings.get("model")}", cpuInfo, contentColor)
                    InfoRow("CPU ${AppStrings.get("cpu_cores")}", "$cpuCores", contentColor)
                    InfoRow(AppStrings.get("screen_resolution"), resolution, contentColor)
                }
            }
        }
    }

        Spacer(Modifier.height(16.dp))

        // 刷新率：显示真实测量值，并在被锁 60Hz 时提供一键修复
        StaggeredCard(1) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                SectionTitle(AppStrings.get("refresh_rate"), contentColor)
                InfoRow(
                    adbPanelStr("refresh_rate_current", "当前", "Current"),
                    formatRefreshRate(currentRefreshRate),
                    contentColor
                )
                InfoRow(
                    adbPanelStr("refresh_rate_max", "最高", "Max"),
                    formatRefreshRate(maxRefreshRate),
                    contentColor
                )
                InfoRow(
                    adbPanelStr("refresh_rate_supported", "支持档位", "Supported"),
                    supportedRefreshRates
                        .filter { it > 1f }
                        .joinToString(" / ") { "${it.roundToInt()}" }
                        .ifBlank { AppStrings.get("unknown") },
                    contentColor
                )
                if (needFixRefreshRate) {
                    Spacer(Modifier.height(AppLayout.innerGap))
                    LiquidButton(
                        onClick = {
                            // 修复中忽略重复点击，避免并发写设置
                            if (!fixingRefreshRate) {
                                fixingRefreshRate = true
                                refreshRateMessage = ""
                                scope.launch {
                                    val hasPermission = withContext(Dispatchers.Default) {
                                        ADBTools.isShizukuAvailable() || ADBTools.isRooted()
                                    }
                                    if (!hasPermission) {
                                        refreshRateMessage = adbPanelStr(
                                            "need_permission_hint",
                                            "修复刷新率需要 Shizuku(ADB) 或 Root 权限，当前二者都不可用。请先激活 Shizuku 或授予 Root 后重试。",
                                            "Fixing the refresh rate requires Shizuku (ADB) or Root; neither is available. Activate Shizuku or grant Root first."
                                        )
                                    } else {
                                        val result = try {
                                            withContext(Dispatchers.Default) { ADBTools.fixRefreshRateLock() }
                                        } catch (e: Exception) {
                                            false to "${AppStrings.get("operation_failed")}: ${e.message ?: e.javaClass.simpleName}"
                                        }
                                        refreshRateMessage = result.second.ifBlank { AppStrings.get("operation_failed") }
                                        // 修复后重新实测，界面显示真实结果
                                        supportedRefreshRates = withContext(Dispatchers.Default) { ADBTools.getSupportedRefreshRates() }
                                        maxRefreshRate = withContext(Dispatchers.Default) { ADBTools.getMaxRefreshRate() }
                                        currentRefreshRate = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                                    }
                                    fixingRefreshRate = false
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp).fillMaxWidth(),
                        tint = if (fixingRefreshRate) Color(0xFF8E8E93) else Color(0xFFFF9500)
                    ) {
                        BasicText(
                            if (fixingRefreshRate) AppStrings.get("executing")
                            else adbPanelStr("fix_refresh_rate", "修复刷新率（解除 60Hz 锁定）", "Fix refresh rate (unlock 60Hz)"),
                            Modifier.padding(horizontal = 8f.dp),
                            style = TextStyle(Color.White, AppLayout.bodySize)
                        )
                    }
                }
                if (refreshRateMessage.isNotEmpty()) {
                    Spacer(Modifier.height(8f.dp))
                    BasicText(refreshRateMessage, style = TextStyle(contentColor.copy(alpha = 0.85f), 12f.sp))
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        StaggeredCard(2) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                SectionTitle("${AppStrings.get("storage")} & ${AppStrings.get("memory")}", contentColor)
                deviceInfo?.let { info ->
                    InfoRow("${AppStrings.get("memory")} (${AppStrings.get("version")})", info.totalMemory, contentColor)
                    InfoRow("${AppStrings.get("memory")} (${AppStrings.get("output")})", info.availableMemory, contentColor)
                    InfoRow("${AppStrings.get("storage")} (${AppStrings.get("version")})", info.totalStorage, contentColor)
                    InfoRow("${AppStrings.get("storage")} (${AppStrings.get("output")})", info.availableStorage, contentColor)
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        StaggeredCard(3) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                SectionTitle(AppStrings.get("root_status"), contentColor)
                deviceInfo?.let { info ->
                    InfoRow("${AppStrings.get("battery")} (${AppStrings.get("version")})", "${info.batteryLevel}%", contentColor)
                    InfoRow(AppStrings.get("root"), if (info.isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                    InfoRow("ADB", if (info.isAdbEnabled) AppStrings.get("enable_adb") else AppStrings.get("disable_adb"), contentColor)
                    InfoRow("Shizuku", if (shizukuAvailable) AppStrings.get("shizuku_connected") else AppStrings.get("shizuku_not_connected"), contentColor)
                }
            }
        }
    }

        Spacer(Modifier.height(80f.dp))
    }
}

/** 刷新率格式化：取不到（<=1Hz）时显示"未知"，避免显示 0 Hz 这种假数据。 */
private fun formatRefreshRate(rate: Float): String {
    if (rate <= 1f) return AppStrings.get("unknown")
    return "${rate.roundToInt()} Hz"
}

@Composable
fun SectionTitle(title: String, contentColor: Color) {
    BasicText(title, style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
    Spacer(Modifier.height(AppLayout.innerGap))
}
