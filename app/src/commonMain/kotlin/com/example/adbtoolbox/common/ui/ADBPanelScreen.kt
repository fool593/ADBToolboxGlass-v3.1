package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.CommandResult
import com.example.adbtoolbox.common.theme.AppLayout
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本文件内使用的多语言文案兜底：
 * 优先取 AppStrings 里已有的 key；若该 key 尚未加入 AppStrings（由主协调者统一补充），
 * 则退回调用点提供的中/英/印兜底文案，保证界面不会显示成裸 key。补齐后自动生效。
 */
internal fun adbPanelStr(key: String, zh: String, en: String, hi: String = en): String {
    val existing = AppStrings.get(key)
    if (existing != key) return existing
    return when (AppSettings.language) {
        "en" -> en
        "hi" -> hi
        else -> zh
    }
}

/**
 * ADB 快捷面板上的可执行操作。
 *
 * - [requiresConfirm]：破坏性/不可逆操作（重启、关机、关闭 ADB 等），执行前必须二次确认；
 * - [requiresRoot]：只有 Root 才能生效的操作（如关机 `reboot -p`），执行前先判断权限并给出明确提示，
 *   避免"点了没反应"。
 */
private enum class PanelOp(
    val labelKey: String,
    val command: String? = null,
    val requiresConfirm: Boolean = false,
    val requiresRoot: Boolean = false
) {
    REBOOT("reboot", "reboot", requiresConfirm = true),
    RECOVERY("recovery", "reboot recovery", requiresConfirm = true),
    BOOTLOADER("bootloader", "reboot bootloader", requiresConfirm = true),
    SHUTDOWN("shutdown", "reboot -p", requiresConfirm = true, requiresRoot = true),
    // pkill 只按进程名匹配，部分 ROM 进程名带前缀匹配不到，补上 killall 兜底
    RESTART_SYSTEMUI("restart_systemui", "pkill -f com.android.systemui || killall com.android.systemui"),
    ENABLE_ADB("enable_adb", "settings put global adb_enabled 1"),
    // 关闭 ADB 会切断本应用正在使用的权限通道，属于危险操作
    DISABLE_ADB("disable_adb", "settings put global adb_enabled 0", requiresConfirm = true),
    // 强开网络 ADB：不依赖电脑、不依赖系统"无线调试"配对——设置 TCP 端口并重启 adbd。
    // 需要 Shizuku / Root（protected 属性）；执行后另一台设备 adb connect <本机IP>:5555 即可。
    ADB_OVER_NETWORK("adb_over_network", "setprop service.adb.tcp.port 5555; setprop persist.adb.tcp.port 5555; stop adbd; start adbd"),
    INSTALL_UNKNOWN_APPS("install_unknown_apps", "settings put global install_non_market_apps 1"),
    SCREENSHOT("screenshot"),
    CLEAR_ALL_CACHE("clean_all_cache"),
    CLEAR_LOGCAT("clean_logcat");

    /** 写设置类操作回读校验，确认真的写进去了（部分 ROM/版本会忽略该设置）。 */
    val verifyCommand: String?
        get() = when (this) {
            ENABLE_ADB, DISABLE_ADB -> "settings get global adb_enabled"
            ADB_OVER_NETWORK -> "getprop service.adb.tcp.port; ip -4 addr show 2>/dev/null | grep -oP 'inet \\K[\\d.]+' | grep -v '^127' | head -n 1"
            INSTALL_UNKNOWN_APPS -> "settings get global install_non_market_apps"
            else -> null
        }
}

private const val SCREENSHOT_PATH = "/sdcard/screenshot.png"

@Composable
fun ADBPanelScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var resultOutput by remember { mutableStateOf("") }
    var pendingOp by remember { mutableStateOf<PanelOp?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun execute(op: PanelOp) {
        if (busy) return
        pendingOp = null
        busy = true
        resultOutput = "${AppStrings.get("executing")}: ${AppStrings.get(op.labelKey)}"
        scope.launch {
            // 所有耗时/阻塞调用都放到默认调度器，异常不再被静默吞掉
            val text = try {
                performPanelOp(op)
            } catch (e: Exception) {
                "${AppStrings.get("operation_failed")}: ${e.message ?: e.javaClass.simpleName}"
            }
            resultOutput = text
            busy = false
        }
    }

    fun request(op: PanelOp) {
        if (busy) return
        if (op.requiresConfirm) {
            pendingOp = op
            return
        }
        execute(op)
    }

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
            BasicText(
                AppStrings.get("adb_quick_panel"),
                style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        // 高危操作二次确认
        pendingOp?.let { op ->
            StaggeredCard(0) {
    GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        adbPanelStr("danger_operation_title", "⚠️ 危险操作，请确认", "⚠️ Dangerous operation"),
                        style = TextStyle(Color(0xFFFF3B30), AppLayout.sectionTitleSize, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8f.dp))
                    BasicText(
                        "${AppStrings.get(op.labelKey)}：${
                            adbPanelStr(
                                "danger_operation_msg",
                                "此操作不可撤销，可能导致设备重启/关机、数据不可恢复或当前权限通道中断。",
                                "This action cannot be undone and may reboot the device, lose data or cut the current permission channel."
                            )
                        }",
                        style = TextStyle(contentColor.copy(alpha = 0.85f), 13f.sp)
                    )
                    Spacer(Modifier.height(AppLayout.innerGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        ActionButton(
                            backdrop,
                            adbPanelStr("cancel", "取消", "Cancel"),
                            Color(0xFF8E8E93)
                        ) { pendingOp = null }
                        ActionButton(
                            backdrop,
                            adbPanelStr("confirm_execute", "确认执行", "Confirm"),
                            Color(0xFFFF3B30)
                        ) { execute(op) }
                    }
                }
            }
    }
            Spacer(Modifier.height(AppLayout.sectionGap))
        }

        // 设备控制
        StaggeredCard(1) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("device_control"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("reboot"), Color(0xFFFF9500)) { request(PanelOp.REBOOT) }
                    ActionButton(backdrop, AppStrings.get("recovery"), Color(0xFFFF3B30)) { request(PanelOp.RECOVERY) }
                    ActionButton(backdrop, AppStrings.get("bootloader"), Color(0xFFAF52DE)) { request(PanelOp.BOOTLOADER) }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("shutdown"), Color(0xFF1C1C1E)) { request(PanelOp.SHUTDOWN) }
                    ActionButton(backdrop, AppStrings.get("restart_systemui"), Color(0xFF0088FF)) { request(PanelOp.RESTART_SYSTEMUI) }
                    ActionButton(backdrop, AppStrings.get("screenshot"), Color(0xFF34C759)) { request(PanelOp.SCREENSHOT) }
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 系统设置
        StaggeredCard(2) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("system_settings"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("enable_adb"), Color(0xFF34C759)) { request(PanelOp.ENABLE_ADB) }
            ActionButton(backdrop, adbPanelStr("adb_over_network", "强开网络ADB(端口5555)", "Force ADB-over-net :5555", "नेट ADB :5555 चालू"), Color(0xFF00C7BE)) { request(PanelOp.ADB_OVER_NETWORK) }
                    ActionButton(backdrop, AppStrings.get("disable_adb"), Color(0xFFFF3B30)) { request(PanelOp.DISABLE_ADB) }
                    ActionButton(backdrop, AppStrings.get("install_unknown_apps"), Color(0xFFFF9500)) { request(PanelOp.INSTALL_UNKNOWN_APPS) }
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 清理操作
        StaggeredCard(3) {
    GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("cleanup_operations"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("clean_all_cache"), Color(0xFF0088FF)) { request(PanelOp.CLEAR_ALL_CACHE) }
                    ActionButton(backdrop, AppStrings.get("clean_logcat"), Color(0xFFAF52DE)) { request(PanelOp.CLEAR_LOGCAT) }
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 执行结果
        if (resultOutput.isNotEmpty()) {
            StaggeredCard(4) {
    GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    SectionTitle(AppStrings.get("output"), contentColor)
                    BasicText(
                        resultOutput,
                        style = TextStyle(contentColor, 12f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
    }
        }

        Spacer(Modifier.height(80f.dp))
    }
}

/**
 * 真正执行操作：全部在默认调度器上跑（避免主线程阻塞/ANR），且做完整权限前置判断，
 * 让"点了没反应"变成"点了有明确结果或明确原因"。
 */
private suspend fun performPanelOp(op: PanelOp): String = withContext(Dispatchers.Default) {
    val rootAvailable = try { ADBTools.isRooted() } catch (e: Exception) { false }
    val shizukuAvailable = try { ADBTools.isShizukuAvailable() } catch (e: Exception) { false }

    if (op.requiresRoot && !rootAvailable) {
        return@withContext adbPanelStr(
            "root_required_hint",
            "该操作需要 Root 权限，当前未检测到 Root，已取消执行。",
            "This operation requires Root; no Root detected, cancelled."
        )
    }
    if (!rootAvailable && !shizukuAvailable) {
        return@withContext adbPanelStr(
            "need_permission_hint",
            "该操作需要 Shizuku(ADB) 或 Root 权限，当前二者都不可用。请先激活 Shizuku 或授予 Root 后重试。",
            "Shizuku (ADB) or Root permission is required; neither is available. Activate Shizuku or grant Root first."
        )
    }

    when (op) {
        PanelOp.SCREENSHOT -> performScreenshot()
        PanelOp.CLEAR_ALL_CACHE -> {
            val ok = try { ADBTools.clearAllCache() } catch (e: Exception) { false }
            if (ok) AppStrings.get("cache_cleared")
            else "${AppStrings.get("operation_failed")}: pm trim-caches"
        }
        PanelOp.CLEAR_LOGCAT -> {
            // 与 ADBTools.clearLogcat() 是同一命令，但这里能拿到退出码，避免"清理失败也报成功"
            val result = ADBTools.execCommand("logcat -c", timeout = 10)
            if (result.exitCode == 0) AppStrings.get("clear_logcat_done")
            else describeResult(op, result)
        }
        else -> {
            val command = op.command ?: return@withContext AppStrings.get("operation_failed")
            val result = ADBTools.execCommand(command, timeout = 20)
            var text = describeResult(op, result)
            op.verifyCommand?.let { verify ->
                val after = ADBTools.execCommand(verify, timeout = 8)
                text += "\n$verify → ${after.output.trim().ifBlank { "-" }}"
            }
            text
        }
    }
}

/** 截屏：写 /sdcard 需要 shell(root/Shizuku) 身份；成功后提示路径并刷新媒体库。 */
private suspend fun performScreenshot(): String {
    val label = AppStrings.get("screenshot")
    val shot = ADBTools.execCommand("screencap -p $SCREENSHOT_PATH", timeout = 20)
    if (shot.exitCode != 0) {
        return buildString {
            appendLine("[$label]")
            appendLine("${AppStrings.get("exit_code")}: ${shot.exitCode}")
            if (shot.output.isNotBlank()) appendLine("${AppStrings.get("output")}: ${shot.output.trim()}")
            val reason = shot.error.trim().ifBlank {
                adbPanelStr(
                    "screenshot_failed_hint",
                    "截屏失败：普通 shell 无权限写 /sdcard，请确认 Shizuku(ADB) 或 Root 已授权。",
                    "Screenshot failed: plain shell cannot write /sdcard. Make sure Shizuku (ADB) or Root is granted."
                )
            }
            appendLine("${AppStrings.get("error_output")}: $reason")
        }
    }
    // 校验文件真的生成了（有退出码 0 但文件 0 字节的情况）
    val ls = ADBTools.execCommand("ls -l $SCREENSHOT_PATH", timeout = 10)
    // 通知媒体库扫描，否则相册/文件管理器看不到截图
    val scan = ADBTools.execCommand(
        "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$SCREENSHOT_PATH",
        timeout = 10
    )
    return buildString {
        appendLine("[$label]")
        appendLine("${AppStrings.get("success")}: $SCREENSHOT_PATH")
        if (ls.output.isNotBlank()) {
            appendLine(ls.output.trim())
        } else {
            appendLine(
                adbPanelStr(
                    "screenshot_missing_hint",
                    "警告：未读取到截图文件信息，文件可能未生成。",
                    "Warning: no screenshot file info; the file may not exist."
                )
            )
        }
        appendLine("${AppStrings.get("output")}: MEDIA_SCANNER_SCAN_FILE ${AppStrings.get(if (scan.exitCode == 0) "success" else "failed")}")
        if (scan.error.isNotBlank()) appendLine("${AppStrings.get("error_output")}: ${scan.error.trim()}")
    }
}

private fun describeResult(op: PanelOp, result: CommandResult): String = buildString {
    appendLine("[${AppStrings.get(op.labelKey)}]")
    appendLine("${AppStrings.get("exit_code")}: ${result.exitCode}")
    if (result.output.isNotBlank()) appendLine("${AppStrings.get("output")}: ${result.output.trim()}")
    if (result.error.isNotBlank()) appendLine("${AppStrings.get("error_output")}: ${result.error.trim()}")
    if (result.exitCode == 0) {
        appendLine("${AppStrings.get("success")}: ${result.exitCode}")
    } else {
        appendLine(
            "${AppStrings.get("failed")}: ${
                adbPanelStr(
                    "command_failed_hint",
                    "命令被拒绝或执行失败，请确认 Shizuku/Root 权限是否已授权、命令是否被此 ROM 支持。",
                    "Command rejected or failed; check Shizuku/Root authorization and ROM support."
                )
            }"
        )
    }
}

@Composable
fun RowScope.ActionButton(backdrop: Backdrop, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.height(44f.dp).weight(1f),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 12f.sp))
    }
}
