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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.KernelRoot
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内核提权（用你自备的公开 exploit）。
 *
 * 这个页面刻意把话说白：
 * - 本应用**不内置、不下载** exploit，你要自己从 GitHub 下载与**本机内核版本完全对应**的
 *   arm64 可执行文件；
 * - 公开的内核 LPE 都是针对特定 CVE + 特定内核 + 特定架构编译的，"内核 6.x 通用"并不存在；
 * - 失败可能崩内核（丢数据、开不了机），且这类提权通常**只在本次开机有效**；
 * - 成功与否**只看 `su -c id` 是不是 uid=0**，不看 exploit 自己打印的 success。
 */
@Composable
fun KernelRootScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    selectedExploitPath: String?,
    onPickExploit: () -> Unit,
    onOpenTerminal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<KernelRoot.KernelInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var remotePath by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var runOutput by remember { mutableStateOf<String?>(null) }
    var rootProbe by remember { mutableStateOf<String?>(null) }
    var confirmRun by remember { mutableStateOf(false) }
    var builtinKits by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchLine by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        info = KernelRoot.readInfo()
        rootProbe = KernelRoot.probeRoot()
        builtinKits = withContext(Dispatchers.Default) { ADBTools.listAssetKits() }
    }

        var ghFlags by remember { mutableStateOf<String?>(null) }
    var ghAdvBusy by remember { mutableStateOf(false) }
Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.width(AppLayout.headerGap))
            BasicText(
                AppStrings.get("kroot_title"),
                style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 醒目风险提醒 ----------------
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FF3B30))
                .padding(AppLayout.cardPadCompact)
        ) {
            Column {
                BasicText(
                    AppStrings.get("kroot_warn_title"),
                    style = TextStyle(Color(0xFFFF6B60), AppLayout.bodySize, FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    AppStrings.get("kroot_warn_body"),
                    style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.captionSize)
                )
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 本机内核信息（真实读取） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_info_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("kroot_info_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                val k = info
                if (k == null) {
                    BasicText(
                        AppStrings.get("loading"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.bodySize)
                    )
                } else {
                    PerfInfoRow(AppStrings.get("kroot_kernel"), k.kernelRelease.ifBlank { AppStrings.get("unknown") }, contentColor)
                    PerfInfoRow(AppStrings.get("kroot_arch"), k.arch.ifBlank { AppStrings.get("unknown") }, contentColor)
                    PerfInfoRow(AppStrings.get("kroot_gki"), k.gkiBranch.ifBlank { AppStrings.get("kroot_gki_none") }, contentColor)
                    PerfInfoRow(AppStrings.get("kroot_selinux"), k.selinux.ifBlank { AppStrings.get("unknown") }, contentColor)
                    PerfInfoRow(AppStrings.get("kroot_patch"), k.securityPatch.ifBlank { AppStrings.get("unknown") }, contentColor)
                    BasicText(
                        k.currentUid,
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp, fontFamily = FontFamily.Monospace)
                    )
                    if (!k.isArm64) {
                        Spacer(Modifier.height(6.dp))
                        PerfBadge(AppStrings.get("kroot_not_arm64"), Color(0xFFFF9500))
                    }
                    if (k.majorMinor.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            AppStrings.get("kroot_match_hint") + ": " + k.kernelRelease,
                            style = TextStyle(AppTheme.accentAlt, AppLayout.captionSize)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

    // ---------------- 内置工具包（随应用打包的，可一键执行） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_builtin_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(6.dp))
                if (builtinKits.isEmpty()) {
                    BasicText(
                        AppStrings.get("kroot_builtin_none"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                    )
                } else {
                    BasicText(
                        AppStrings.get("kroot_builtin_found"),
                        style = TextStyle(Color(0xFF34C759), AppLayout.captionSize)
                    )
                    builtinKits.forEach { kit ->
                        Spacer(Modifier.height(10.dp))
                        BasicText(
                            kit,
                            style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                        )
                        if (matchLine.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                matchLine,
                                style = TextStyle(
                                    if (matchLine.startsWith(AppStrings.get("kroot_gh_unmatched"))) Color(0xFFFF9500)
                                    else AppTheme.accentAlt,
                                    11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val kitDir = "kx_asset_" + kit.take(16)
                        LiquidButton(
                            onClick = {
                                if (busy) return@LiquidButton
                                busy = true
                                statusText = AppStrings.get("kroot_pushing")
                                runOutput = null
                                matchLine = ""
                                scope.launch {
                                    // 解出内置包 → 推送（目录方式）→ 执行 → 真实探测
                                    val extract = withContext(Dispatchers.Default) {
                                        ADBTools.extractAssetKit(kit)
                                    }
                                    if (extract.exitCode != 0) {
                                        statusText = AppStrings.get("kroot_push_failed") + ": " + extract.error
                                        busy = false
                                        return@launch
                                    }
                                    val push = withContext(Dispatchers.Default) {
                                        ADBTools.prepareAndPushKit(
                                            extract.output.trim(),
                                            kitDir
                                        )
                                    }
                                    if (push.exitCode != 0) {
                                        statusText = AppStrings.get("kroot_push_failed") + ": " + push.error
                                        busy = false
                                        return@launch
                                    }
                                    // 契约：第 1 行=入口（无执行入口时为 NO_RUNNABLE_ENTRY），第 2 行=远端目录
                                    val lines = push.output.lineSequence().filter { it.isNotBlank() }.toList()
                                    val entry = lines.getOrNull(0)?.trim().orEmpty()
                                    val remoteDir = lines.getOrNull(1)?.trim()
                                        ?: entry.substringBeforeLast('/')
                                    remotePath = entry
                                    // GhostLock 内核偏移表核对：.conf 文件名以 uname -r 开头 = 已收录。
                                    // 只保留安全字符再拼进 grep，避免设备返回的版本串里带 shell 元字符。
                                    val rel = info?.kernelRelease.orEmpty().trim()
                                        .filter { it.isLetterOrDigit() || it in "._-+" }
                                    val matched = withContext(Dispatchers.Default) {
                                        if (rel.isEmpty()) ""
                                        else ADBTools.execCommand(
                                            "ls ${remoteDir} | grep -i '^${rel}' | head -n 3",
                                            10
                                        ).output.trim()
                                    }
                                    matchLine = when {
                                        rel.isEmpty() -> AppStrings.get("kroot_gh_unkernel")
                                        matched.isNotEmpty() -> AppStrings.get("kroot_gh_matched") + "\n" + matched
                                        else -> AppStrings.get("kroot_gh_unmatched")
                                    }
                                    if (entry == "NO_RUNNABLE_ENTRY") {
                                        // 数据型工具包（例如仅偏移表）：如实说明，不假装能一键提权
                                        statusText = AppStrings.get("kroot_no_native")
                                        busy = false
                                        return@launch
                                    }
                                    statusText = AppStrings.get("kroot_running")
                                    val run = withContext(Dispatchers.Default) {
                                        KernelRoot.runExploit(entry, 120)
                                    }
                                    runOutput = buildString {
                                        append(run.output)
                                        if (run.error.isNotBlank()) append("\n[stderr] ").append(run.error)
                                    }
                                    rootProbe = withContext(Dispatchers.Default) { KernelRoot.probeRoot() }
                                    statusText = if ((rootProbe ?: "").contains("uid=0")) {
                                        AppStrings.get("kroot_root_ok")
                                    } else {
                                        AppStrings.get("kroot_root_failed")
                                    }
                                    busy = false
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(44.dp).fillMaxWidth(),
                            tint = if (busy) Color(0xFF8E8E93) else Color(0xFFFF3B30)
                        ) {
                            BasicText(
                                if (busy) AppStrings.get("kroot_working") else AppStrings.get("kroot_builtin_run"),
                                style = TextStyle(Color.White, 13.sp, FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- GhostLock 高级终端（官方 CLI 全集：--ghostlock-app-call / --load-prebuilt-profile / --force-attack / --enable-status-record / --dump-kernel-log / 内核信息） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_adv_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("kroot_adv_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                val advItems = listOf(
                    "--ghostlock-app-call" to "kroot_adv_app",
                    "--load-prebuilt-profile" to "kroot_adv_profile",
                    "--force-attack" to "kroot_adv_force",
                    "--enable-status-record" to "kroot_adv_status",
                    "--dump-kernel-log /data/local/tmp/kx_klog" to "kroot_adv_klog",
                    "uname -r" to "kroot_adv_uname"
                )
                advItems.forEach { (flags, labelKey) ->
                    LiquidButton(
                        onClick = {
                            if (ghAdvBusy) return@LiquidButton
                            if (remotePath.isNullOrBlank() || !remotePath!!.startsWith("/data/local/tmp/")) {
                                statusText = AppStrings.get("kroot_adv_need_push")
                                return@LiquidButton
                            }
                            ghFlags = flags
                            ghAdvBusy = true
                            statusText = AppStrings.get("kroot_adv_prefix") + flags
                            scope.launch {
                                val r = withContext(Dispatchers.Default) {
                                    if (flags == "uname -r") com.example.adbtoolbox.common.ADBTools.execCommand("uname -r", 15)
                                    else com.example.adbtoolbox.common.perf.KernelRoot.runGhostlockCli(remotePath!!, flags, 120)
                                }
                                runOutput = buildString {
                                    append(r.output.trim())
                                    if (r.error.isNotBlank()) append("\n[stderr] ").append(r.error)
                                }
                                rootProbe = withContext(Dispatchers.Default) { com.example.adbtoolbox.common.perf.KernelRoot.probeRoot() }
                                ghAdvBusy = false
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(38.dp).fillMaxWidth(),
                        tint = if (ghAdvBusy) Color(0xFF8E8E93) else Color(0xFF5856D6)
                    ) {
                        BasicText(
                            if (ghAdvBusy && ghFlags == flags) AppStrings.get("kroot_working") else flags,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (ghFlags != null && runOutput != null) {
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        runOutput!!,
                        style = TextStyle(contentColor.copy(alpha = 0.9f), 10.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        // ---------------- 步骤 1：选择本地 exploit ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_step1"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("kroot_step1_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    selectedExploitPath ?: AppStrings.get("kroot_no_file"),
                    style = TextStyle(
                        if (selectedExploitPath != null) AppTheme.accentAlt else contentColor.copy(alpha = 0.5f),
                        11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Spacer(Modifier.height(10.dp))
                LiquidButton(
                    onClick = onPickExploit,
                    backdrop = backdrop,
                    modifier = Modifier.height(44.dp).fillMaxWidth(),
                    tint = AppTheme.accent
                ) {
                    BasicText(AppStrings.get("kroot_pick"), style = TextStyle(AppTheme.onAccent, 13.sp))
                }
                Spacer(Modifier.height(8.dp))
                BasicText(
                    AppStrings.get("kroot_ref_repos"),
                    style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
                )
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 步骤 2：推送 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_step2"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("kroot_step2_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                if (remotePath != null) {
                    BasicText(
                        remotePath!!,
                        style = TextStyle(AppTheme.accentAlt, 11.sp, fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LiquidButton(
                    onClick = {
                        val path = selectedExploitPath ?: return@LiquidButton
                        if (busy) return@LiquidButton
                        busy = true
                        statusText = AppStrings.get("kroot_pushing")
                        scope.launch {
                            val name = "kx_" + System.currentTimeMillis().toString().takeLast(9)
                            // 支持整套 kit：传 zip 会在本机解压后整体推送，并返回入口脚本的远端路径
                            val r = withContext(Dispatchers.Default) {
                                ADBTools.prepareAndPushKit(path, name)
                            }
                            if (r.exitCode == 0) {
                                remotePath = r.output.lineSequence().firstOrNull()?.trim()
                                statusText = AppStrings.get("kroot_push_ok") + "\n" + r.output.trim()
                            } else {
                                statusText = AppStrings.get("kroot_push_failed") + ": " + r.error
                            }
                            busy = false
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(44.dp).fillMaxWidth(),
                    tint = if (selectedExploitPath == null || busy) Color(0xFF8E8E93) else AppTheme.accent
                ) {
                    BasicText(
                        if (busy) AppStrings.get("kroot_working") else AppStrings.get("kroot_push"),
                        style = TextStyle(Color.White, 13.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 步骤 3：执行 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("kroot_step3"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("kroot_step3_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                LiquidButton(
                    onClick = { if (!busy && remotePath != null) confirmRun = true },
                    backdrop = backdrop,
                    modifier = Modifier.height(46.dp).fillMaxWidth(),
                    tint = if (busy || remotePath == null) Color(0xFF8E8E93) else Color(0xFFFF3B30)
                ) {
                    BasicText(
                        if (busy) AppStrings.get("kroot_working") else AppStrings.get("kroot_run"),
                        style = TextStyle(Color.White, 14.sp, FontWeight.Medium)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidButton(
                        onClick = {
                            scope.launch {
                                rootProbe = withContext(Dispatchers.Default) { KernelRoot.probeRoot() }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f).height(42.dp),
                        tint = AppTheme.accent
                    ) {
                        BasicText(AppStrings.get("kroot_probe"), style = TextStyle(AppTheme.onAccent, 12.sp))
                    }
                    LiquidButton(
                        onClick = {
                            onOpenTerminal(
                                "uname -r\nls -l /data/local/tmp\nsu -c id 2>/dev/null || id"
                            )
                        },
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f).height(42.dp),
                        tint = AppTheme.deep
                    ) {
                        BasicText(AppStrings.get("kroot_terminal"), style = TextStyle(Color.White, 12.sp))
                    }
                }

                if (confirmRun) {
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        AppStrings.get("kroot_confirm_body"),
                        style = TextStyle(Color(0xFFFF6B60), AppLayout.bodySize, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidButton(
                            onClick = { confirmRun = false },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(44.dp),
                            tint = Color(0xFF8E8E93)
                        ) {
                            BasicText(AppStrings.get("cancel"), style = TextStyle(Color.White, 13.sp))
                        }
                        LiquidButton(
                            onClick = {
                                confirmRun = false
                                busy = true
                                statusText = AppStrings.get("kroot_running")
                                runOutput = null
                                scope.launch {
                                    val path = remotePath
                                    val r = if (path == null) {
                                        com.example.adbtoolbox.common.CommandResult("", "no remote path", 1)
                                    } else {
                                        withContext(Dispatchers.Default) { KernelRoot.runExploit(path, 120) }
                                    }
                                    runOutput = buildString {
                                        append(r.output)
                                        if (r.error.isNotBlank()) append("\n[stderr] ").append(r.error)
                                    }
                                    rootProbe = withContext(Dispatchers.Default) { KernelRoot.probeRoot() }
                                    statusText = if ((rootProbe ?: "").contains("uid=0")) {
                                        AppStrings.get("kroot_root_ok")
                                    } else {
                                        AppStrings.get("kroot_root_failed")
                                    }
                                    busy = false
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1.4f).height(44.dp),
                            tint = Color(0xFFFF3B30)
                        ) {
                            BasicText(
                                AppStrings.get("kroot_confirm_ok"),
                                style = TextStyle(Color.White, 13.sp, FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 结果 ----------------
        statusText?.let { text ->
            Spacer(Modifier.height(AppLayout.sectionGap))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        text,
                        style = TextStyle(
                            if (text == AppStrings.get("kroot_root_ok")) Color(0xFF34C759)
                            else contentColor,
                            AppLayout.bodySize,
                            FontWeight.Medium
                        )
                    )
                    rootProbe?.let { probe ->
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            AppStrings.get("kroot_probe_result") + ":",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                        )
                        BasicText(
                            probe,
                            style = TextStyle(contentColor.copy(alpha = 0.85f), 11.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                    runOutput?.let { out ->
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            out.take(3000),
                            style = TextStyle(contentColor.copy(alpha = 0.8f), 10.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        AppStrings.get("kroot_perboot_note"),
                        style = TextStyle(Color(0xFFFF9500), AppLayout.captionSize)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))
        BasicText(
            AppStrings.get("kroot_note"),
            style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize)
        )
        Spacer(Modifier.height(80.dp))
    }
}
