package com.example.adbtoolbox.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// 全局液态玻璃效果配置，保存用户调节的参数，全局生效
object GlassEffectConfig {
    // 基础效果参数
    val cornerRadius = mutableStateOf(0.5f)      // 圆角 0~1
    val blurRadius = mutableStateOf(16f)          // 模糊半径 0~32dp
    val refractionHeight = mutableStateOf(0.2f)   // 折射高度 0~1
    val refractionAmount = mutableStateOf(0.2f)   // 折射量 0~1
    val chromaticAberration = mutableStateOf(0f)  // 色差 0~1
    val enableVibrancy = mutableStateOf(true)      // 光域效果开关

    // 全局效果强度（0~1，控制所有玻璃效果的整体强度）
    val globalIntensity = mutableStateOf(1f)

    // 导航栏效果
    val navBlurRadius = mutableStateOf(8f)         // 导航栏模糊半径
    val navOpacity = mutableStateOf(0.4f)          // 导航栏透明度 0~1
    val navCornerRadius = mutableStateOf(0.6f)     // 导航栏圆角 0~1
    val navRefractionHeight = mutableStateOf(0.15f) // 导航栏折射高度
    val navRefractionAmount = mutableStateOf(0.15f) // 导航栏折射量
    val navChromaticAberration = mutableStateOf(0f)  // 导航栏色差
    val navEnableVibrancy = mutableStateOf(true)      // 导航栏光域效果

    // 导航栏胶囊指示器参数
    val navIndicatorShape = mutableStateOf("capsule") // "round"=圆球, "capsule"=胶囊, "square"=正方形
    val navIndicatorHeight = mutableStateOf(36f)      // 胶囊高度/圆球直径 dp（默认36，不遮文字；上限48）
    val navIndicatorWidth = mutableStateOf(0f)         // 胶囊长度 0=自适应, >0=固定宽度 dp
    val navIndicatorCorner = mutableStateOf(0.5f)      // 胶囊圆角比例 0~1 (0=直角, 1=全圆)
    val navIndicatorBlur = mutableStateOf(6f)          // 胶囊模糊半径（默认6，太大会遮字）
    val navIndicatorOpacity = mutableStateOf(0.25f)    // 胶囊透明度（默认0.25，高透明不遮字）
    val navIndicatorColor = mutableStateOf(Color(0xFF007AFF).copy(alpha = 0.3f)) // 胶囊颜色（淡蓝）

    // 主界面液态玻璃参数
    val homeBlurRadius = mutableStateOf(20f)
    val homeOpacity = mutableStateOf(0.3f)
    val homeCornerRadius = mutableStateOf(0.5f)
    val homeRefractionHeight = mutableStateOf(0.2f)
    val homeRefractionAmount = mutableStateOf(0.2f)
    val homeEnableVibrancy = mutableStateOf(true)

    // 终端页面液态玻璃参数
    val terminalBlurRadius = mutableStateOf(16f)
    val terminalOpacity = mutableStateOf(0.35f)
    val terminalCornerRadius = mutableStateOf(0.4f)
    val terminalRefractionHeight = mutableStateOf(0.15f)
    val terminalRefractionAmount = mutableStateOf(0.15f)
    val terminalEnableVibrancy = mutableStateOf(true)

    // 设置页面液态玻璃参数
    val settingsBlurRadius = mutableStateOf(18f)
    val settingsOpacity = mutableStateOf(0.3f)
    val settingsCornerRadius = mutableStateOf(0.5f)
    val settingsRefractionHeight = mutableStateOf(0.18f)
    val settingsRefractionAmount = mutableStateOf(0.18f)
    val settingsEnableVibrancy = mutableStateOf(true)

    // 应用管理页面液态玻璃参数
    val appsBlurRadius = mutableStateOf(18f)
    val appsOpacity = mutableStateOf(0.3f)
    val appsCornerRadius = mutableStateOf(0.5f)
    val appsRefractionHeight = mutableStateOf(0.18f)
    val appsRefractionAmount = mutableStateOf(0.18f)
    val appsEnableVibrancy = mutableStateOf(true)

    // 插件页面（ADB插件/Root模块）独立玻璃参数
    val pluginsBlurRadius = mutableStateOf(18f)
    val pluginsOpacity = mutableStateOf(0.3f)
    val pluginsCornerRadius = mutableStateOf(0.5f)
    val pluginsRefractionHeight = mutableStateOf(0.18f)
    val pluginsRefractionAmount = mutableStateOf(0.18f)
    val pluginsEnableVibrancy = mutableStateOf(true)

    // ADB模块页面独立玻璃参数
    val adbmoduleBlurRadius = mutableStateOf(18f)
    val adbmoduleOpacity = mutableStateOf(0.3f)
    val adbmoduleCornerRadius = mutableStateOf(0.5f)
    val adbmoduleRefractionHeight = mutableStateOf(0.18f)
    val adbmoduleRefractionAmount = mutableStateOf(0.18f)
    val adbmoduleEnableVibrancy = mutableStateOf(true)

    // 卡片效果
    val cardBlurRadius = mutableStateOf(20f)        // 卡片模糊半径
    val cardOpacity = mutableStateOf(0.3f)          // 卡片透明度 0~1
    val cardCornerRadius = mutableStateOf(0.5f)     // 卡片圆角 0~1

    // 按钮效果
    val buttonBlurRadius = mutableStateOf(12f)       // 按钮模糊半径
    val buttonOpacity = mutableStateOf(0.5f)         // 按钮透明度 0~1

    // 玻璃颜色（叠加色，alpha 控制透明度）
    val glassColor = mutableStateOf(Color.White.copy(alpha = 0.15f))

    // 长按边缘发光 + 折射效果
    val longPressGlowIntensity = mutableStateOf(0.6f)  // 长按边缘发光强度 0~1
    val longPressGlowSize = mutableStateOf(0.5f)       // 光晕大小 0~1
    val longPressRefraction = mutableStateOf(0.5f)     // 边缘折射/扭曲量 0~1
    val longPressGlowColor = mutableStateOf(Color.White) // 发光颜色

    // 字体颜色
    val fontColor = mutableStateOf(Color.Unspecified) // Unspecified=跟随主题

    // 预设字体颜色（32种，key 为语言 key，显示时用 AppStrings.get）
    val presetFontColors = listOf(
        "color_default" to Color.Unspecified,
        "color_white" to Color.White,
        "color_black" to Color.Black,
        "color_red" to Color(0xFFFF3B30),
        "color_orange" to Color(0xFFFF9500),
        "color_yellow" to Color(0xFFFFCC00),
        "color_green" to Color(0xFF34C759),
        "color_cyan" to Color(0xFF5AC8FA),
        "color_blue" to Color(0xFF007AFF),
        "color_purple" to Color(0xFFAF52DE),
        "color_pink" to Color(0xFFFF2D55),
        "color_brown" to Color(0xFFA2845E),
        "color_gray" to Color(0xFF8E8E93),
        "color_dark_red" to Color(0xFFC70000),
        "color_dark_orange" to Color(0xFFC93400),
        "color_dark_green" to Color(0xFF007A33),
        "color_dark_blue" to Color(0xFF0040DD),
        "color_dark_purple" to Color(0xFF5E5CE6),
        "color_light_red" to Color(0xFFFF6B6B),
        "color_light_orange" to Color(0xFFFFB347),
        "color_light_yellow" to Color(0xFFFFE066),
        "color_light_green" to Color(0xFF7BED9F),
        "color_light_blue" to Color(0xFF64D2FF),
        "color_light_purple" to Color(0xFFC77DFF),
        "color_orange_red" to Color(0xFFFF4500),
        "color_yellow_green" to Color(0xFF9ACD32),
        "color_teal" to Color(0xFF008080),
        "color_indigo" to Color(0xFF4B0082),
        "color_magenta" to Color(0xFFFF00FF),
        "color_gold" to Color(0xFFFFD700),
        "color_silver" to Color(0xFFC0C0C0),
        "color_cream" to Color(0xFFFFFDD0)
    )

    // 预设颜色
    val presetColors = listOf(
        "透明" to Color.Transparent,
        "白色" to Color.White.copy(alpha = 0.15f),
        "赤" to Color(0xFFFF3B30).copy(alpha = 0.2f),
        "橙" to Color(0xFFFF9500).copy(alpha = 0.2f),
        "黄" to Color(0xFFFFCC00).copy(alpha = 0.2f),
        "绿" to Color(0xFF34C759).copy(alpha = 0.2f),
        "青" to Color(0xFF5AC8FA).copy(alpha = 0.2f),
        "蓝" to Color(0xFF007AFF).copy(alpha = 0.2f),
        "紫" to Color(0xFFAF52DE).copy(alpha = 0.2f)
    )

    // 设置颜色
    fun setColor(color: Color) {
        glassColor.value = color
        // 手动改过玻璃参数 = 从此刻起不再让主题强行覆盖（见 AppSettings.themeUserOverrode）
        AppSettings.markThemeUserOverride()
    }

    // 原生风格运行时覆盖标记：只影响渲染读值，绝不写入/覆盖用户玻璃参数（修复切回后卡片永久变平的 bug）
    val nativeFlat = mutableStateOf(false)

    /** 双 UI 切换（非破坏性）：原生=只打开运行时扁平标记，用户玻璃参数原样保留，切回立即恢复。 */
    fun applyUiStyle() {
        nativeFlat.value = com.example.adbtoolbox.common.AppSettings.nativeUi
    }
}
