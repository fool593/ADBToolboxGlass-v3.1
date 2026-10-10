package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectPersistence
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.shapes.RoundedRectangle

@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onGlassPlayground: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(128f) }
    var brightnessResult by remember { mutableStateOf<String?>(null) }
    var screenTimeout by remember { mutableIntStateOf(30) }
    // Shizuku 状态显示在下面的"Shizuku 服务"卡片里，直接读全局三态
    // （MainContent 统一检测 + 回到前台/授权回调后刷新），本页不再自己查一次。
    var dhizukuInstalled by remember { mutableStateOf(false) }
    var dhizukuActive by remember { mutableStateOf(false) }
    var dhizukuMessage by remember { mutableStateOf("") }
    var dhizukuLoading by remember { mutableStateOf(false) }
    val dhizukuScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        brightness = ADBTools.getBrightness().toFloat()
        screenTimeout = ADBTools.getScreenTimeout()
        dhizukuInstalled = ADBTools.isDhizukuInstalled()
        dhizukuActive = ADBTools.isDhizukuActive()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))
        BasicText(AppStrings.get("settings"), style = TextStyle(contentColor, AppLayout.titleSize, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(AppLayout.sectionGap))

        // 壁纸设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("wallpaper"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(AppLayout.innerGap))
                LiquidButton(
                    onClick = onPickWallpaper,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("set_wallpaper"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                }
                Spacer(Modifier.height(8f.dp))
                LiquidButton(
                    onClick = onClearWallpaper,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFFFF3B30)
                ) {
                    BasicText(AppStrings.get("clear_wallpaper"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 动态壁纸设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad), verticalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                BasicText(AppStrings.get("dynamic_wallpaper"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))

                // 动态壁纸开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(AppStrings.get("enable_dynamic_wallpaper"), style = TextStyle(contentColor, AppLayout.bodySize), modifier = Modifier.weight(1f))
                    BasicText(
                        if (AppCache.dynamicWallpaperEnabled.value) AppStrings.get("enabled") else AppStrings.get("disabled"),
                        style = TextStyle(if (AppCache.dynamicWallpaperEnabled.value) Color(0xFF34C759) else Color(0xFF8E8E93), 12f.sp)
                    )
                }
                LiquidSlider(
                    value = { if (AppCache.dynamicWallpaperEnabled.value) 1f else 0f },
                    onValueChange = {
                        val newVal = it > 0.5f
                        if (AppCache.dynamicWallpaperEnabled.value != newVal) {
                            AppCache.dynamicWallpaperEnabled.value = newVal
                            try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                        }
                    },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.01f,
                    backdrop = backdrop
                )

                // 视频动态壁纸选择
                if (AppCache.dynamicWallpaperEnabled.value) {
                    BasicText(AppStrings.get("video_file"), style = TextStyle(contentColor, AppLayout.bodySize))
                    LiquidButton(
                        onClick = { AppCache.pickDynamicVideoTrigger.value++ },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp).fillMaxWidth(),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(
                            if (AppCache.dynamicWallpaperVideoPath.value != null) AppStrings.get("video_selected") else AppStrings.get("select_video"),
                            Modifier.padding(horizontal = 8f.dp),
                            style = TextStyle(Color.White, AppLayout.bodySize)
                        )
                    }
                    if (AppCache.dynamicWallpaperVideoPath.value != null) {
                        BasicText(
                            "${AppStrings.get("current_video")} ${AppCache.dynamicWallpaperVideoPath.value?.substringAfterLast('/')}",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                        Spacer(Modifier.height(4f.dp))
                        LiquidButton(
                            onClick = {
                                AppCache.dynamicWallpaperVideoPath.value = null
                                AppCache.dynamicWallpaperEnabled.value = false
                                try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(40f.dp).fillMaxWidth(),
                            tint = Color(0xFFFF3B30)
                        ) {
                            BasicText(AppStrings.get("delete_video"), style = TextStyle(Color.White, AppLayout.bodySize))
                        }
                    }
                    BasicText(AppStrings.get("video_hint"), style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 开屏动画设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad), verticalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                BasicText(AppStrings.get("splash_animation"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                BasicText(AppStrings.get("splash_video_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))

                LiquidButton(
                    onClick = { AppCache.pickSplashVideoTrigger.value++ },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(
                        if (AppCache.splashVideoPath.value != null) AppStrings.get("splash_video_selected") else AppStrings.get("splash_select_video"),
                        Modifier.padding(horizontal = 8f.dp),
                        style = TextStyle(Color.White, AppLayout.bodySize)
                    )
                }

                if (AppCache.splashVideoPath.value != null) {
                    BasicText(
                        "${AppStrings.get("splash_current")}: ${AppCache.splashVideoPath.value?.substringAfterLast('/')}",
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                    )
                    LiquidButton(
                        onClick = { AppCache.clearSplashVideoTrigger.value++ },
                        backdrop = backdrop,
                        modifier = Modifier.height(40f.dp).fillMaxWidth(),
                        tint = Color(0xFFFF3B30)
                    ) {
                        BasicText(AppStrings.get("splash_clear"), style = TextStyle(Color.White, AppLayout.bodySize))
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("glass_effect"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(AppLayout.innerGap))
                LiquidButton(
                    onClick = onGlassPlayground,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("glass_playground"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 主题（配色方案）
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("theme"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(4f.dp))
                BasicText(AppStrings.get("theme_hint"), style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize))
                Spacer(Modifier.height(14f.dp))

                AppTheme.all.forEach { palette ->
                    val selected = AppTheme.id == palette.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(62f.dp)
                            // 原先是静态底色 + 无反馈点击：改成液态玻璃可点项，尺寸/内边距不变，
                            // 选中/未选中底色浓度按 liquidGlassItem 的 alpha * 0.45f 换算保留
                            .liquidGlassItem(
                                backdrop = backdrop,
                                corner = 14.dp,
                                tint = if (selected) palette.accent.copy(alpha = (0.18f / 0.45f).coerceAtMost(1f))
                                else contentColor.copy(alpha = (0.06f / 0.45f).coerceAtMost(1f)),
                                onClick = {
                                    AppSettings.themeChosenByUser = true
                                    AppTheme.apply(palette.id)
                                }
                            )
                            .padding(horizontal = 12f.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemePreviewSwatch(
                            colors = palette.preview,
                            modifier = Modifier.size(46f.dp, 26f.dp).clip(RoundedCornerShape(13f.dp))
                        )
                        Spacer(Modifier.width(AppLayout.innerGap))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                AppStrings.get(palette.nameKey),
                                style = TextStyle(
                                    if (selected) palette.accent else contentColor,
                                    AppLayout.bodySize,
                                    androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            )
                            Spacer(Modifier.height(2f.dp))
                            BasicText(
                                AppStrings.get(palette.descKey),
                                style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
                            )
                        }
                        if (selected) {
                            BasicText(
                                AppStrings.get("theme_current"),
                                style = TextStyle(palette.accent, AppLayout.captionSize)
                            )
                        }
                    }
                    Spacer(Modifier.height(8f.dp))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 主题设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("display_settings"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(16f.dp))

                // 深色/浅色模式切换
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (AppSettings.isDarkMode) AppStrings.get("dark_mode") else AppStrings.get("light_mode"), style = TextStyle(contentColor, AppLayout.bodySize))
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { AppSettings.isDarkMode },
                        onSelect = { AppSettings.isDarkMode = it },
                        backdrop = backdrop,
                        modifier = Modifier.size(51f.dp, 31f.dp)
                    )
                }

                Spacer(Modifier.height(16f.dp))

                // 语言切换
                BasicText(AppStrings.get("language"), style = TextStyle(contentColor, AppLayout.bodySize))
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    val langs = listOf("zh" to AppStrings.get("chinese"), "en" to AppStrings.get("english"), "hi" to AppStrings.get("hindi"))
                    langs.forEach { (code, name) ->
                        val selected = AppSettings.language == code
                        LiquidButton(
                            onClick = { AppSettings.language = code; try { GlassEffectPersistence.saveAll() } catch (_: Exception) {} },
                            backdrop = backdrop,
                            modifier = Modifier.height(36f.dp),
                            tint = if (selected) Color(0xFF0088FF) else Color.Unspecified
                        ) {
                            BasicText(name, Modifier.padding(horizontal = 12f.dp), style = TextStyle(if (selected) Color.White else contentColor, 12f.sp))
                        }
                    }
                }

                Spacer(Modifier.height(16f.dp))

                // 界面风格（双 UI：液态玻璃 / 安卓原生·Google 混合）
                BasicText(AppStrings.get("ui_style"), style = TextStyle(contentColor, AppLayout.bodySize))
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    listOf(false to AppStrings.get("ui_style_glass"), true to AppStrings.get("ui_style_native")).forEach { (isNative, name) ->
                        val sel = AppSettings.nativeUi == isNative
                        LiquidButton(
                            onClick = {
                                AppSettings.nativeUi = isNative
                                com.example.adbtoolbox.common.GlassEffectConfig.applyUiStyle()
                                try { GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(36f.dp),
                            tint = if (sel) Color(0xFFAF52DE) else Color.Unspecified
                        ) {
                            BasicText(name, Modifier.padding(horizontal = 12f.dp), style = TextStyle(if (sel) Color.White else contentColor, 12f.sp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // Shizuku 状态
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("shizuku_service"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    // 三态如实显示：服务在跑但没授权时提示去授权，而不是笼统说"未连接"
                    when (AppCache.shizukuState.value) {
                        "granted" -> AppStrings.get("shizuku_connected")
                        "no_permission" -> AppStrings.get("shizuku_no_permission")
                        else -> AppStrings.get("shizuku_disconnected")
                    },
                    style = TextStyle(
                        if (AppCache.shizukuState.value == "granted") Color(0xFF34C759) else Color(0xFFFF9500),
                        AppLayout.bodySize
                    )
                )
                Spacer(Modifier.height(AppLayout.innerGap))
                LiquidButton(
                    onClick = {
                        ADBTools.requestShizukuPermission()
                        // 授权结果主要由 MainActivity 的 Shizuku 回调触发刷新；
                        // 这里再兜底延迟查一次，覆盖"回调没触发、用户只是在系统页里授权"的情况
                        dhizukuScope.launch {
                            delay(1200)
                            AppCache.requestShizukuRefresh()
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("request_shizuku"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // Dhizuku 设备所有者
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText("Dhizuku (Device Owner)", style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    if (!dhizukuInstalled) AppStrings.get("dhizuku_not_installed") else if (dhizukuActive) AppStrings.get("dhizuku_active") else AppStrings.get("dhizuku_inactive"),
                    style = TextStyle(if (dhizukuActive) Color(0xFF34C759) else if (dhizukuInstalled) Color(0xFFFF9500) else Color(0xFFFF3B30), AppLayout.bodySize)
                )
                Spacer(Modifier.height(8f.dp))
                BasicText(AppStrings.get("dhizuku_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                Spacer(Modifier.height(AppLayout.innerGap))
                if (dhizukuMessage.isNotEmpty()) {
                    BasicText(dhizukuMessage, style = TextStyle(contentColor.copy(alpha = 0.8f), 12f.sp))
                    Spacer(Modifier.height(8f.dp))
                }
                // 两个按钮：请求权限 + 使用开关
                if (dhizukuInstalled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        // 请求 Dhizuku 权限按钮（Dhizuku 作为设备所有者给应用授权）
                        LiquidButton(
                            onClick = {
                                dhizukuLoading = true
                                dhizukuMessage = if (dhizukuActive) AppStrings.get("dhizuku_requesting") else AppStrings.get("dhizuku_activate_hint")
                                dhizukuScope.launch {
                                    if (dhizukuActive) {
                                        val granted = withContext(Dispatchers.Default) { ADBTools.requestDhizukuPermission() }
                                        dhizukuMessage = if (granted) AppStrings.get("dhizuku_granted") else AppStrings.get("dhizuku_denied")
                                    }
                                    dhizukuActive = ADBTools.isDhizukuActive()
                                    dhizukuLoading = false
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(44f.dp).weight(1f),
                            tint = if (dhizukuActive) Color(0xFFAF52DE) else Color(0xFFFF9500)
                        ) {
                            BasicText(
                                if (dhizukuLoading) AppStrings.get("dhizuku_requesting") else if (dhizukuActive) AppStrings.get("dhizuku_request") else AppStrings.get("dhizuku_not_active"),
                                Modifier.padding(horizontal = 4f.dp),
                                style = TextStyle(Color.White, 13f.sp)
                            )
                        }
                        // 使用 Dhizuku 权限开关按钮
                        LiquidButton(
                            onClick = {
                                AppCache.useDhizuku.value = !AppCache.useDhizuku.value
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(44f.dp).weight(1f),
                            tint = if (AppCache.useDhizuku.value) Color(0xFF34C759) else Color(0xFF8E8E93)
                        ) {
                            BasicText(
                                if (AppCache.useDhizuku.value) AppStrings.get("use_dhizuku") + ": " + AppStrings.get("enabled") else AppStrings.get("use_dhizuku") + ": " + AppStrings.get("disabled"),
                                Modifier.padding(horizontal = 4f.dp),
                                style = TextStyle(Color.White, 13f.sp)
                            )
                        }
                    }
                } else {
                    BasicText(AppStrings.get("dhizuku_install_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                }
                Spacer(Modifier.height(8f.dp))
                BasicText(AppStrings.get("dhizuku_notice"), style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize))
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 显示设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText("${AppStrings.get("screen_brightness")}: ${brightness.toInt()}", style = TextStyle(contentColor, AppLayout.bodySize))
                Spacer(Modifier.height(8f.dp))
                LiquidSlider(
                    value = { brightness / 255f },
                    onValueChange = { brightness = it * 255f },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.01f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(44f.dp)
                )
                Spacer(Modifier.height(8f.dp))
                // 之前这里只改本地 state、从不写设备（空壳，审计抓到），已接上真实写入。
                // 滑条拖动先实时预览，点「应用」才真正写入并回读验证，无权限时如实报失败。
                LiquidButton(
                    onClick = {
                        if (brightnessResult == AppStrings.get("settings_brightness_applying")) return@LiquidButton
                        brightnessResult = AppStrings.get("settings_brightness_applying")
                        dhizukuScope.launch {
                            val target = brightness.toInt()
                            val ok = withContext(Dispatchers.Default) { ADBTools.setBrightness(target) }
                            val read = withContext(Dispatchers.Default) { ADBTools.getBrightness() }
                            brightnessResult = if (ok) {
                                AppStrings.get("settings_brightness_applied") + " $read"
                            } else {
                                AppStrings.get("settings_brightness_failed")
                            }
                            if (ok) brightness = read.toFloat()
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(40f.dp).fillMaxWidth(),
                    tint = AppTheme.accent
                ) {
                    BasicText(AppStrings.get("settings_brightness_apply"), style = TextStyle(AppTheme.onAccent, 13f.sp))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(AppStrings.get("settings_splash_anim"), style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium))
                        BasicText(AppStrings.get("settings_splash_anim_desc"), style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize))
                    }
                Spacer(Modifier.height(14.dp))
                val crash = com.example.adbtoolbox.common.AppCache.crashLog.value
                if (crash != null) {
                    BasicText(AppStrings.get("settings_crashlog"), style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(contentColor.copy(alpha = 0.06f)).padding(10.dp)
                    ) {
                        BasicText(
                            crash.take(900),
                            style = TextStyle(
                                color = Color(0xFFE8A33D),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        AppStrings.get("settings_crashlog_hint"),
                        style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize)
                    )
                }
                    LiquidToggle(
                        selected = { com.example.adbtoolbox.common.AppSettings.splashAnimEnabled },
                        onSelect = {
                            com.example.adbtoolbox.common.AppSettings.splashAnimEnabled = it
                            com.example.adbtoolbox.common.GlassEffectPersistence.saveAll()
                        },
                        backdrop = backdrop,
                        modifier = Modifier.size(51.dp, 31.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                brightnessResult?.let { msg ->
                    Spacer(Modifier.height(6f.dp))
                    BasicText(msg, style = TextStyle(contentColor.copy(alpha = 0.7f), AppLayout.captionSize))
                }

                Spacer(Modifier.height(16f.dp))
                BasicText("${AppStrings.get("screen_timeout")}: ${screenTimeout}s", style = TextStyle(contentColor, AppLayout.bodySize))
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    listOf(15, 30, 60, 120, 300).forEach { sec ->
                        val selected = screenTimeout == sec
                        LiquidButton(
                            onClick = {
                                screenTimeout = sec
                                ADBTools.setScreenTimeout(sec)
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(36f.dp),
                            tint = if (selected) Color(0xFF0088FF) else Color.Unspecified
                        ) {
                            BasicText("${sec}s", Modifier.padding(horizontal = 8f.dp), style = TextStyle(if (selected) Color.White else contentColor, 12f.sp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 系统操作
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(AppStrings.get("system_actions"), style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(AppLayout.innerGap))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    LiquidButton(
                        onClick = { ADBTools.rebootDevice() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFFF9500)
                    ) {
                        BasicText(AppStrings.get("reboot"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                    }
                    LiquidButton(
                        onClick = { ADBTools.rebootRecovery() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFFF3B30)
                    ) {
                        BasicText(AppStrings.get("recovery"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                    }
                    LiquidButton(
                        onClick = { ADBTools.rebootBootloader() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFAF52DE)
                    ) {
                        BasicText(AppStrings.get("bootloader"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                    }
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}
