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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppLayout
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Root 快捷命令。requiresConfirm 标记会破坏数据/系统文件的不可逆命令。 */
private enum class RootCmd(
    val labelKey: String,
    val command: String,
    val requiresConfirm: Boolean = false
) {
    UID("view_uid", "id"),
    MOUNT_RW("mount_rw", "mount -o remount,rw /system"),
    MOUNT_RO("mount_ro", "mount -o remount,ro /system"),
    HOSTS("modify_hosts", "echo '127.0.0.1 localhost' > /etc/hosts", requiresConfirm = true),
    CLEAR_DATA("clear_data", "pm clear com.android.chrome", requiresConfirm = true),
    PROCESSES("view_processes", "ps")
}

private const val SU_TIMEOUT_SECONDS = 20L

@Composable
fun RootManagerScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var isRooted by remember { mutableStateOf(false) }
    var suVersion by remember { mutableStateOf("") }
    var busyboxVersion by remember { mutableStateOf("") }
    var resultOutput by remember { mutableStateOf("") }
    var pendingCmd by remember { mutableStateOf<RootCmd?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isRooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
        suVersion = withContext(Dispatchers.Default) { ADBTools.getSuVersion() }
        busyboxVersion = withContext(Dispatchers.Default) { ADBTools.getBusyBoxVersion() }
    }

    fun runRootCommand(cmd: RootCmd) {
        if (running) return
        pendingCmd = null
        running = true
        resultOutput = "${AppStrings.get("executing")}: ${AppStrings.get(cmd.labelKey)}"
        scope.launch {
            // 直接使用 su 执行 Root 命令，不经过 Shizuku；先判断 Root 状态，失败时给出可读原因
            val rooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
            val result = withContext(Dispatchers.Default) { execWithSu(cmd.command) }
            resultOutput = buildString {
                appendLine("[${AppStrings.get(cmd.labelKey)}]")
                appendLine("${AppStrings.get("exit_code")}: ${result.first}")
                if (result.second.isNotBlank()) appendLine("${AppStrings.get("output")}: ${result.second.trim()}")
                if (result.third.isNotBlank()) appendLine("${AppStrings.get("error_output")}: ${result.third.trim()}")
                if (result.first != 0) {
                    appendLine(
                        if (rooted) {
                            adbPanelStr(
                                "command_failed_hint",
                                "命令被拒绝或执行失败，请确认命令是否被此 ROM/内核支持。",
                                "Command rejected or failed; check whether this ROM/kernel supports it."
                            )
                        } else {
                            adbPanelStr(
                                "root_required_hint",
                                "当前未检测到 Root 权限：请先通过 Magisk/KernelSU 获取 Root，并确认已授权本应用。",
                                "No Root detected: obtain Root via Magisk/KernelSU and grant this app Root first."
                            )
                        }
                    )
                }
            }
            running = false
        }
    }

    fun requestCommand(cmd: RootCmd) {
        if (running) return
        if (cmd.requiresConfirm) {
            pendingCmd = cmd
            return
        }
        runRootCommand(cmd)
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
            BasicText(AppStrings.get("root_manager"), style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold))
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        StaggeredCard(0) {
    GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("root_status"), contentColor)
                InfoRow(AppStrings.get("root_permission"), if (isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                InfoRow(AppStrings.get("su_version"), suVersion, contentColor)
                InfoRow(AppStrings.get("busybox_version"), busyboxVersion, contentColor)
                Spacer(Modifier.height(AppLayout.innerGap))
                LiquidButton(
                    onClick = {
                        if (!running) {
                            running = true
                            scope.launch {
                                val ok = withContext(Dispatchers.Default) { ADBTools.requestRootPermission() }
                                resultOutput = if (ok) AppStrings.get("root_verified") else AppStrings.get("root_verify_failed")
                                // 验证后同步刷新所有 Root 相关状态，避免界面停留在旧数据
                                isRooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
                                suVersion = withContext(Dispatchers.Default) { ADBTools.getSuVersion() }
                                busyboxVersion = withContext(Dispatchers.Default) { ADBTools.getBusyBoxVersion() }
                                running = false
                            }
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFFAF52DE)
                ) {
                    BasicText(AppStrings.get("verify_root"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, AppLayout.bodySize))
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 破坏性命令二次确认
        pendingCmd?.let { cmd ->
            StaggeredCard(1) {
    GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        adbPanelStr("danger_operation_title", "⚠️ 危险操作，请确认", "⚠️ Dangerous operation"),
                        style = TextStyle(Color(0xFFFF3B30), AppLayout.sectionTitleSize, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8f.dp))
                    BasicText(
                        "${AppStrings.get(cmd.labelKey)}：${
                            adbPanelStr(
                                "danger_operation_msg",
                                "此操作不可撤销，可能清除应用数据或改写系统文件（如 /etc/hosts）。",
                                "This action cannot be undone; it may wipe app data or rewrite system files (e.g. /etc/hosts)."
                            )
                        }",
                        style = TextStyle(contentColor.copy(alpha = 0.85f), 13f.sp)
                    )
                    Spacer(Modifier.height(AppLayout.innerGap))
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        ActionButton(backdrop, adbPanelStr("cancel", "取消", "Cancel"), Color(0xFF8E8E93)) { pendingCmd = null }
                        ActionButton(backdrop, adbPanelStr("confirm_execute", "确认执行", "Confirm"), Color(0xFFFF3B30)) { runRootCommand(cmd) }
                    }
                }
            }
    }
            Spacer(Modifier.height(AppLayout.sectionGap))
        }

        StaggeredCard(2) {
    GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("root_quick_commands"), contentColor)
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("view_uid"), Color(0xFF0088FF)) { requestCommand(RootCmd.UID) }
                    ActionButton(backdrop, AppStrings.get("mount_rw"), Color(0xFFFF9500)) { requestCommand(RootCmd.MOUNT_RW) }
                    ActionButton(backdrop, AppStrings.get("mount_ro"), Color(0xFF34C759)) { requestCommand(RootCmd.MOUNT_RO) }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("modify_hosts"), Color(0xFFFF3B30)) { requestCommand(RootCmd.HOSTS) }
                    ActionButton(backdrop, AppStrings.get("clear_data"), Color(0xFFAF52DE)) { requestCommand(RootCmd.CLEAR_DATA) }
                    ActionButton(backdrop, AppStrings.get("view_processes"), Color(0xFF5AC8FA)) { requestCommand(RootCmd.PROCESSES) }
                }
            }
        }
    }

        Spacer(Modifier.height(AppLayout.sectionGap))

        if (resultOutput.isNotEmpty()) {
            StaggeredCard(3) {
    GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    SectionTitle(AppStrings.get("execution_result"), contentColor)
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
 * 用 su 执行命令，带超时：用户没确认授权弹窗时 su 会一直挂住，
 * 原实现 waitFor() 无超时会让按钮"永远转圈没有任何结果"。
 */
private fun execWithSu(command: String): Triple<Int, String, String> {
    var process: Process? = null
    return try {
        process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val finished = process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            Triple(
                -1,
                "",
                adbPanelStr(
                    "su_timeout_hint",
                    "su 执行超时（${SU_TIMEOUT_SECONDS}s）：可能未获取 Root，或授权弹窗未确认。",
                    "su timed out (${SU_TIMEOUT_SECONDS}s): Root may be missing or the grant dialog was not confirmed."
                )
            )
        } else {
            val output = try { process.inputStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            val error = try { process.errorStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            Triple(process.exitValue(), output, error)
        }
    } catch (e: Exception) {
        // 设备没有 su 时 Runtime.exec 直接抛 IOException，这里给出可读原因而不是静默失败
        Triple(
            -1,
            "",
            adbPanelStr(
                "su_missing_hint",
                "未找到 su 命令，设备可能未 Root。",
                "su command not found; the device may not be rooted."
            ) + " (${e.message ?: e.javaClass.simpleName})"
        )
    } finally {
        try { process?.destroy() } catch (_: Exception) {}
    }
}

@Composable
fun RowScope.ActionButton(backdrop: Backdrop, label: String, tint: Color, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.height(40f.dp).weight(1f),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, AppLayout.captionSize))
    }
}
