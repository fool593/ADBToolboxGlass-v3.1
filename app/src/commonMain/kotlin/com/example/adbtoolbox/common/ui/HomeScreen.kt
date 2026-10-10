package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBDestination
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.DeviceInfoData
import com.example.adbtoolbox.common.GlassEffectConfig
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onNavigate: (ADBDestination) -> Unit
) {
    var deviceInfo by remember { mutableStateOf<DeviceInfoData?>(null) }
    // Shizuku 状态来自全局单例（MainContent 统一检测），不再本页只查一次

    LaunchedEffect(Unit) {
        // 优先从预加载缓存读取，秒开
        if (AppCache.deviceInfoLoaded.value) {
            deviceInfo = AppCache.deviceInfo.value
        } else {
            // 耗时操作移到后台线程，避免主线程阻塞导致ANR
            try {
                deviceInfo = withContext(Dispatchers.Default) { ADBTools.getDeviceInfo() }
                AppCache.deviceInfo.value = deviceInfo
                AppCache.deviceInfoLoaded.value = true
            } catch (e: Exception) {
                deviceInfo = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp),
        verticalArrangement = Arrangement.spacedBy(16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        BasicText(
            AppStrings.get("adb_toolbox"),
            style = TextStyle(contentColor, 28f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
        )

        // Shizuku 状态：三态如实显示（服务在跑但没授权时提示去授权，而不是笼统说"未连接"）
        val shizukuState = AppCache.shizukuState.value
        BasicText(
            when (shizukuState) {
                "granted" -> AppStrings.get("shizuku_connected")
                "no_permission" -> AppStrings.get("shizuku_no_permission")
                else -> AppStrings.get("shizuku_not_connected")
            },
            style = TextStyle(
                if (shizukuState == "granted") Color(0xFF34C759) else Color(0xFFFF9500),
                14f.sp
            )
        )

        // 国庆主题横幅：只有主题选中"国庆"时才出现（见 ThemeBanner.kt）
        NationalDayBanner(backdrop = backdrop, contentColor = contentColor)

        // 设备信息卡片
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("device_info"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))
                deviceInfo?.let { info ->
                    InfoRow(AppStrings.get("model"), info.model, contentColor)
                    InfoRow(AppStrings.get("brand"), info.brand, contentColor)
                    InfoRow(AppStrings.get("android_version"), info.androidVersion, contentColor)
                    InfoRow(AppStrings.get("sdk"), info.sdkVersion.toString(), contentColor)
                    InfoRow(AppStrings.get("memory"), "${info.availableMemory} / ${info.totalMemory}", contentColor)
                    InfoRow(AppStrings.get("storage"), "${info.availableStorage} / ${info.totalStorage}", contentColor)
                    InfoRow(AppStrings.get("battery"), "${info.batteryLevel}%", contentColor)
                    InfoRow(AppStrings.get("refresh_rate"), info.refreshRate, contentColor)
                    InfoRow(AppStrings.get("root"), if (info.isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                }
                Spacer(Modifier.height(8f.dp))
                LiquidButton(
                    onClick = { onNavigate(ADBDestination.DeviceInfo) },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("view_details"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        // 快捷功能
                // ============ 布局重构 v3.1：分区重排 + 入场级联动画（控件/目的地全部不变） ============
        HomeSection(AppStrings.get("home_sec_system"), backdrop, contentColor, sectionIndex = 0, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("device_info"), Color(0xFF0088FF), ADBDestination.DeviceInfo),
                   Triple(AppStrings.get("adb_panel"), Color(0xFFFF9500), ADBDestination.ADBPanel)),
            listOf(Triple(AppStrings.get("permissions"), Color(0xFFFF3B30), ADBDestination.Permissions),
                   Triple(AppStrings.get("root_manager"), Color(0xFFAF52DE), ADBDestination.RootManager)),
            listOf(Triple(AppStrings.get("shell_executor"), Color(0xFF34C759), ADBDestination.ShellExecutor),
                   Triple(AppStrings.get("app_manager"), Color(0xFF5AC8FA), ADBDestination.Apps)),
        ))
        HomeSection(AppStrings.get("home_sec_perf"), backdrop, contentColor, sectionIndex = 1, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("performance_boost"), AppTheme.accent, ADBDestination.PerformanceBoost),
                   Triple(AppStrings.get("phone_inspector"), AppTheme.deep, ADBDestination.PhoneInspector)),
            listOf(Triple(AppStrings.get("brand_perf_entry"), Color(0xFFFF9500), ADBDestination.BrandPerf),
                   Triple(AppStrings.get("huawei_boost"), AppTheme.accent, ADBDestination.HuaweiBoost)),
            listOf(Triple(AppStrings.get("restore_entry"), Color(0xFFFF3B30), ADBDestination.SystemRestore)),
        ))
        HomeSection(AppStrings.get("home_sec_game"), backdrop, contentColor, sectionIndex = 2, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("game_frame_rate"), Color(0xFF34C759), ADBDestination.GameFrameRate)),
        ))
        HomeSection(AppStrings.get("home_sec_sec"), backdrop, contentColor, sectionIndex = 3, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("shield_entry"), Color(0xFFFF3B30), ADBDestination.ShieldScan)),
        ))
        HomeSection(AppStrings.get("home_sec_root"), backdrop, contentColor, sectionIndex = 4, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("kroot_entry"), Color(0xFFAF52DE), ADBDestination.KernelRoot),
                   Triple(AppStrings.get("root_tool"), Color(0xFF5856D6), ADBDestination.RootTool)),
            listOf(Triple(AppStrings.get("temp_root"), Color(0xFFFF9500), ADBDestination.TempRoot)),
        ))
        HomeSection(AppStrings.get("home_sec_mod"), backdrop, contentColor, sectionIndex = 5, onNavigate = onNavigate, rows = listOf(
            listOf(Triple(AppStrings.get("adb_module"), Color(0xFFBF5AF2), ADBDestination.ADBModule),
                   Triple(AppStrings.get("installed_root_modules"), AppTheme.accentAlt, ADBDestination.RootModules)),
        ))
        Spacer(Modifier.height(10f.dp))
        BasicText(
            "· " + AppStrings.get("restore_remind_perf"),
            Modifier.padding(horizontal = 4.dp),
            style = TextStyle(contentColor.copy(alpha = 0.55f), 11.sp)
        )
Spacer(Modifier.height(16f.dp))
    }
}

/** 首页分区：标题 + 1~2 列入口；控件复用 QuickActionButton（布局重构不换控件）。
 *  入场动画：全区淡入+上移，sectionIndex 错峰 60ms 级联；容器内容尺寸动画。 */
@Composable
private fun HomeSection(
    title: String,
    backdrop: Backdrop,
    contentColor: Color,
    sectionIndex: Int,
    onNavigate: (ADBDestination) -> Unit,
    rows: List<List<Triple<String, Color, ADBDestination>>>
) {
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((sectionIndex * 60L))
        appear = true
    }
    val shown by androidx.compose.animation.core.animateFloatAsState(
        if (appear) 1f else 0f,
        androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(androidx.compose.animation.core.tween(300))
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 12f.dp.toPx()
            }
    ) {
        Spacer(Modifier.height(16f.dp))
        BasicText(title, style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
        Spacer(Modifier.height(8f.dp))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
                row.forEach { (label, color, dest) ->
                    com.example.adbtoolbox.common.ui.NewUi2.Tile(label, color, Modifier.weight(1f)) { onNavigate(dest) }
                }
            }
            Spacer(Modifier.height(10f.dp))
        }
    }
}

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    pageType: String = "default", // "default","home","terminal","settings","apps","plugins","adbmodule"
    content: @Composable () -> Unit
) {
    // 从全局配置读取参数，根据页面类型选择对应参数
    val config = GlassEffectConfig
    val intensity = config.globalIntensity.value

    // 根据页面类型选择参数
    val blurRadius: Float
    val opacity: Float
    val corner: Float
    val refHeight: Float
    val refAmount: Float
    val enableVibrancy: Boolean
    when (pageType) {
        "home" -> {
            blurRadius = config.homeBlurRadius.value
            opacity = config.homeOpacity.value
            corner = config.homeCornerRadius.value
            refHeight = config.homeRefractionHeight.value
            refAmount = config.homeRefractionAmount.value
            enableVibrancy = config.homeEnableVibrancy.value
        }
        "terminal" -> {
            blurRadius = config.terminalBlurRadius.value
            opacity = config.terminalOpacity.value
            corner = config.terminalCornerRadius.value
            refHeight = config.terminalRefractionHeight.value
            refAmount = config.terminalRefractionAmount.value
            enableVibrancy = config.terminalEnableVibrancy.value
        }
        "settings" -> {
            blurRadius = config.settingsBlurRadius.value
            opacity = config.settingsOpacity.value
            corner = config.settingsCornerRadius.value
            refHeight = config.settingsRefractionHeight.value
            refAmount = config.settingsRefractionAmount.value
            enableVibrancy = config.settingsEnableVibrancy.value
        }
        "apps" -> {
            blurRadius = config.appsBlurRadius.value
            opacity = config.appsOpacity.value
            corner = config.appsCornerRadius.value
            refHeight = config.appsRefractionHeight.value
            refAmount = config.appsRefractionAmount.value
            enableVibrancy = config.appsEnableVibrancy.value
        }
        "plugins" -> {
            blurRadius = config.pluginsBlurRadius.value
            opacity = config.pluginsOpacity.value
            corner = config.pluginsCornerRadius.value
            refHeight = config.pluginsRefractionHeight.value
            refAmount = config.pluginsRefractionAmount.value
            enableVibrancy = config.pluginsEnableVibrancy.value
        }
        "adbmodule" -> {
            blurRadius = config.adbmoduleBlurRadius.value
            opacity = config.adbmoduleOpacity.value
            corner = config.adbmoduleCornerRadius.value
            refHeight = config.adbmoduleRefractionHeight.value
            refAmount = config.adbmoduleRefractionAmount.value
            enableVibrancy = config.adbmoduleEnableVibrancy.value
        }
        else -> {
            blurRadius = config.cardBlurRadius.value
            opacity = config.cardOpacity.value
            corner = config.cardCornerRadius.value
            refHeight = config.refractionHeight.value
            refAmount = config.refractionAmount.value
            enableVibrancy = config.enableVibrancy.value
        }
    }

    val cornerDp = 24f.dp * corner * intensity
    val glassColor = config.glassColor.value

    // 长按边缘发光 / 折射：这四项配置之前在卡片上是空壳（从不读取），现在真正生效
    val glowIntensity = config.longPressGlowIntensity.value
    val glowSize = config.longPressGlowSize.value
    val glowRefraction = config.longPressRefraction.value
    val glowColor = config.longPressGlowColor.value
    val chromatic = config.chromaticAberration.value > 0f

    val animationScope = rememberCoroutineScope()
    val cardShape = remember(cornerDp) { RoundedRectangle(cornerDp) }
    val rimPath = remember { Path() }
    val highlight = remember(animationScope, cardShape) {
        InteractiveHighlight(
            animationScope = animationScope,
            shape = cardShape,
            drawAboveContent = true,
            // 传 lambda：绘制阶段求值 ⇒ 设置页改颜色/强度立刻生效，无需重建实例
            glowColor = { GlassEffectConfig.longPressGlowColor.value },
            glowIntensity = { 0.6f + 0.4f * GlassEffectConfig.longPressGlowIntensity.value },
            edgeBoost = 0.8f
        )
    }

    // 长按进度的量化快照（1/16 台阶）：effects 的读数发生在 observeReads 观察区内，
    // 直接读动画值会让长按期间每一帧都重建整条 RenderEffect 链（全是原生对象）。
    // 读量化后的 derivedState 只有跨台阶才算变化；1/16 的折射增强台阶看不出来。
    val longPressStep = remember(highlight) {
        derivedStateOf { (highlight.longPressProgress * 16f).toInt() / 16f }
    }
    // 以下 lambda 都在绘制阶段执行并读最新状态；remember 缓存是为了避免每次重组重建 RenderEffect / Lens 造成掉帧
    val effects: BackdropEffectScope.() -> Unit = remember(
        blurRadius, enableVibrancy, intensity, refHeight, refAmount, chromatic, glowRefraction, highlight
    ) {
        {
            val minDim = size.minDimension
            // 全局渲染强度映射为 0.5~1.5 倍：即使拉到 200% 也不会翻倍压垮渲染，同时保证低强度也有可见效果
            val eff = 0.5f + intensity * 0.5f
            if (enableVibrancy) vibrancy()
            blur((blurRadius.dp.toPx() * eff).coerceAtMost(40f.dp.toPx()))
            // 长按：按 longPressRefraction 加强边缘折射（仍有 clamp，防止渲染崩溃、内容消失）
            val boost = 1f + 0.65f * longPressStep.value * glowRefraction
            lens(
                // 折射量 clamp 到卡片尺寸的安全比例且保证最小可见效果，防止渲染崩溃、内容消失
                refractionHeight = (refHeight * minDim * 1.0f * eff * boost).coerceIn(minDim * 0.05f, minDim * 0.25f),
                refractionAmount = (refAmount * minDim * 1.5f * eff * boost).coerceIn(minDim * 0.08f, minDim * 0.35f),
                depthEffect = true,
                chromaticAberration = chromatic
            )
        }
    }
    val cardHighlight: () -> Highlight? = remember(highlight, glowIntensity, glowSize, glowColor) {
        {
            val lp = highlight.longPressProgress
            if (lp <= 0.01f) {
                Highlight.Default
            } else {
                // 长按：默认描边换成配置的发光色，变粗并加模糊 ⇒ 边缘发光
                Highlight(
                    width = (0.6f + 1.5f * glowSize).dp,
                    blurRadius = (0.6f + 5f * glowSize).dp,
                    alpha = (0.4f + 0.6f * lp * glowIntensity).coerceIn(0f, 1f),
                    style = HighlightStyle.Plain(color = glowColor.copy(alpha = 1f))
                )
            }
        }
    }
    val cardShadow: () -> Shadow? = remember(highlight, glowIntensity, glowSize, glowColor) {
        {
            val lp = highlight.longPressProgress
            // 静止时不画阴影：卡片原先外面有 clip，阴影本来就被裁掉不可见，白白每帧算一张大图层的模糊；
            // 而且黑色投影糊在卡片下面会让边缘看起来是"暗边"（用户要的是亮边）
            if (lp <= 0.01f) {
                null
            } else {
                // 长按：只留一圈紧贴边缘的薄发光（半径 4~12dp、透明度上限 0.3），不再往外飘
                Shadow(
                    radius = (4f + 8f * glowSize).dp,
                    offset = DpOffset.Zero,
                    color = glowColor,
                    alpha = (0.30f * lp * glowIntensity).coerceIn(0f, 1f)
                )
            }
        }
    }
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(highlight) {
        {
            // graphicsLayer 的 block 在绘制阶段执行，读到的按压缩放一定是最新值；卡片只做轻微缩放，不做拖动位移
            if (size.width > 0f && size.height > 0f) {
                val progress = highlight.pressProgress
                scaleX = 1f + 0.012f * progress
                scaleY = 1f + 0.012f * progress
            }
        }
    }
    val onDrawSurface: DrawScope.() -> Unit = remember(glassColor, opacity, intensity) {
        {
            if (glassColor != Color.Transparent) {
                drawRect(glassColor.copy(alpha = glassColor.alpha * opacity * intensity))
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            // 注意：这里不再用 .clip()。drawBackdrop 自身会按形状裁剪内容，而外层 clip 会把长按外发光一起裁掉
            // （原来的默认外阴影因此完全不可见，却仍在每帧计算）
            .then(highlight.gestureModifier)
            .drawWithContent {
                // 边缘高光描边：静息就有一条很淡的亮边，按压/长按按配置加强。
                // 整条描边裁在卡片形状内部 ⇒ 只贴在玻璃内侧，不会越过轮廓飘到卡片外面。
                drawContent()
                val press = highlight.pressProgress
                val lp = highlight.longPressProgress
                val rimAlpha = (0.10f + 0.16f * press + 0.74f * lp * glowIntensity).coerceIn(0f, 1f)
                if (size.width > 0f && size.height > 0f) {
                    rimPath.reset()
                    rimPath.addOutline(cardShape.createOutline(size, layoutDirection, this))
                    val strokeWidth =
                        (0.9f.dp.toPx() + 1.6f.dp.toPx() * glowSize * (0.35f + 0.65f * lp))
                            .coerceAtMost(size.minDimension * 0.2f)
                    clipPath(rimPath) {
                        drawPath(
                            rimPath,
                            color = glowColor.copy(alpha = rimAlpha * 0.85f),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
            }
            .then(highlight.modifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cardShape },
                effects = effects,
                highlight = cardHighlight,
                shadow = cardShadow,
                layerBlock = layerBlock,
                onDrawSurface = onDrawSurface
            )
    ) {
        content()
    }
}

@Composable
fun InfoRow(label: String, value: String, contentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4f.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.6f), 14f.sp))
        BasicText(value, style = TextStyle(contentColor, 14f.sp))
    }
}

@Composable
fun QuickActionButton(
    backdrop: Backdrop,
    label: String,
    tint: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(80f.dp)
            .liquidGlassItem(backdrop = backdrop, corner = 20.dp, tint = tint, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(contentColor, 15f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
    }
}
