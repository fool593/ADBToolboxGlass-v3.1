package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.CommandResult
import com.example.adbtoolbox.common.GlassEffectConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalLine(
    val text: String,
    val type: LineType
)

enum class LineType {
    INPUT, OUTPUT, ERROR, INFO
}

/**
 * 终端输出保留的最大行数。
 *
 * 为什么需要上界：每执行一条命令都会把整段输出作为一个 [TerminalLine] 追加进 `lines`，
 * 而 `lines` 是只增不减的 compose 状态 —— 一次会话里敲几条 `dumpsys` / `pm list packages`
 * 就能把几百 KB 到几 MB 的字符串长期留在内存里（页面退出前不会被回收）。
 * 同时 `lines + ...` 每次都复制整条列表，行数越多追加越慢。
 *
 * 400 行足够覆盖一屏可见内容与向上回看；这里只按"整行"淘汰最旧的行，
 * 不截断任何一行的文本，因此用户看到的输出内容不会被改动。
 */
private const val MAX_TERMINAL_LINES = 400

/** 终端输出保留的总字符数上界：防止"行数不多但每行很长"（例如 cat 一个大日志文件）把内存撑起来。 */
private const val MAX_TERMINAL_CHARS = 160_000

/** 本地命令历史上界：`history` 用它回显，原来同样是只增不减。 */
private const val MAX_COMMAND_HISTORY = 200

/**
 * 追加终端输出，并在超过 [MAX_TERMINAL_LINES] / [MAX_TERMINAL_CHARS] 时丢弃最旧的行。
 *
 * 返回新的不可变列表（Compose 需要新实例才会重组）；**至少保留最新一条**，
 * 所以单条超长输出不会被砍掉一半。
 */
private fun appendTerminalLines(
    old: List<TerminalLine>,
    added: List<TerminalLine>
): List<TerminalLine> {
    if (added.isEmpty()) return old
    val merged = ArrayList<TerminalLine>(old.size + added.size)
    merged.addAll(old)
    merged.addAll(added)
    var totalChars = 0
    for (line in merged) totalChars += line.text.length
    var from = 0
    while (from < merged.size - 1 &&
        (merged.size - from > MAX_TERMINAL_LINES || totalChars > MAX_TERMINAL_CHARS)
    ) {
        totalChars -= merged[from].text.length
        from++
    }
    if (from == 0) return merged
    val trimmed = ArrayList<TerminalLine>(merged.size - from)
    for (index in from until merged.size) trimmed.add(merged[index])
    return trimmed
}

/** 追加单行的便捷写法。 */
private fun appendTerminalLine(old: List<TerminalLine>, line: TerminalLine): List<TerminalLine> =
    appendTerminalLines(old, listOf(line))

@Composable
fun TerminalScreen(
    backdrop: Backdrop,
    contentColor: Color
) {
    var lines by remember { mutableStateOf(listOf(TerminalLine(AppStrings.get("adb_terminal_hint"), LineType.INFO))) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 快捷命令分类状态
    var currentCmdCategory by remember { mutableStateOf(QuickCmdCategory.SYSTEM) }
    var showQuickCommands by remember { mutableStateOf(false) }
    // 本地命令历史（供 history 命令使用）
    var commandHistory by remember { mutableStateOf(listOf<String>()) }

    // 自动滚到底部。
    // key 用 lines 而不是 lines.size：加了行数上界后，行数到达上限时 size 不再变化，
    // 用 size 做 key 会让"溢出后新追加的行"不再触发滚动（终端看着像卡住了）。
    // lines 每次追加都是新实例且内容不同，结构相等比较能正确判定为变化。
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    /**
     * 真正执行一条命令并等待结果。
     * 用挂起函数而不是「launch 后立刻返回」，是为了让多行初始命令按顺序执行、按顺序输出。
     */
    suspend fun runCommand(cmd: String) {
        val result = try {
            withContext(Dispatchers.Default) { ADBTools.execCommand(cmd) }
        } catch (e: Exception) {
            CommandResult("", "${AppStrings.get("operation_failed")}: ${e.javaClass.simpleName}: ${e.message}", -1)
        }
        lines = appendTerminalLines(lines, buildList {
            if (result.output.isNotBlank()) add(TerminalLine(result.output.trim(), LineType.OUTPUT))
            if (result.error.isNotBlank()) add(TerminalLine(result.error.trim(), LineType.ERROR))
            if (result.exitCode != 0) {
                add(TerminalLine("${AppStrings.get("exit_code_bracket")}${result.exitCode}]", LineType.ERROR))
            }
        })
    }

    /**
     * 拦截几条「本地内置命令」。
     * clear / history / exit 原本会被丢给设备 shell 执行 —— 在一次性 shell 进程里它们
     * 既清不掉终端显示、也留不下历史、更不会退出本 App，等于点了完全没用。
     */
    suspend fun runLocalCommand(cmd: String): Boolean {
        val normalized = cmd.trim().lowercase()
        when (normalized) {
            "clear", "cls" -> lines = mutableListOf(TerminalLine(AppStrings.get("clear_screen"), LineType.INFO))
            "history" -> lines = appendTerminalLines(lines, if (commandHistory.isEmpty()) {
                listOf(TerminalLine("(no history)", LineType.INFO))
            } else {
                commandHistory.mapIndexed { index, item ->
                    TerminalLine("${index + 1}  $item", LineType.OUTPUT)
                }
            })
            "exit" -> lines = appendTerminalLine(
                lines,
                TerminalLine("Exit is not supported here - use the back button to leave the terminal.", LineType.INFO)
            )
            else -> return false
        }
        return true
    }

    suspend fun executeCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        // 已在设备端 shell 里执行，用户却按 PC 习惯敲 adb 前缀时自动剥掉，避免 "adb: not found"
        val shellCommand = Regex("^adb\\s+shell\\s+", RegexOption.IGNORE_CASE).replace(trimmed, "")
        lines = appendTerminalLine(lines, TerminalLine("$ $shellCommand", LineType.INPUT))
        input = ""
        if (runLocalCommand(shellCommand)) {
            return
        }
        commandHistory = (commandHistory + shellCommand).takeLast(MAX_COMMAND_HISTORY)
        if (Regex("^adb(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(shellCommand)) {
            // adb push/pull/install 需要连接电脑，本 App 无法直接执行，明确告知而不是静默失败
            lines = appendTerminalLine(
                lines,
                TerminalLine(
                    "This app already runs commands in the device shell, so the 'adb' prefix is not needed.\n" +
                        "For file push/pull or APK install, use the ADB Panel instead.",
                    LineType.INFO
                )
            )
            return
        }
        runCommand(shellCommand)
    }

    // 读取并执行初始命令（由其他页面跳转时设置）
    LaunchedEffect(Unit) {
        val initialCmd = AppCache.terminalInitialCommand.value
        if (!initialCmd.isNullOrBlank()) {
            // 清除初始命令，避免重复执行
            AppCache.terminalInitialCommand.value = null
            // 逐行顺序执行初始命令（原来每条命令各自 launch，150ms 的 delay 并不能保证输出顺序）
            initialCmd.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    executeCommand(line)
                    kotlinx.coroutines.delay(150)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16f.dp)) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(AppStrings.get("terminal_title"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f))
            LiquidButton(
                onClick = {
                    lines = appendTerminalLine(lines, TerminalLine("$ diag", LineType.INPUT))
                    scope.launch {
                        val diag = withContext(Dispatchers.Default) {
                            ADBTools.getShizukuDiagnostics()
                        }
                        // 诊断输出末尾附一行进程内内存快照（缓存条数 + 玻璃图层收支）。
                        // 用户反馈的"内存泄漏"里，图标位图与玻璃图层都在原生内存上，堆快照看不到；
                        // 进出一轮页面后再点一次"诊断"，比较这两行的数字即可判断是否只增不减。
                        // 这是技术数据，不走本地化文案。
                        lines = appendTerminalLines(
                            lines,
                            listOf(
                                TerminalLine(diag, LineType.OUTPUT),
                                TerminalLine("mem: ${AppCache.diagnosticsSnapshot()}", LineType.INFO)
                            )
                        )
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.height(36f.dp),
                tint = Color(0xFFFF9500)
            ) {
                BasicText(AppStrings.get("diagnose"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(Color.White, 12f.sp))
            }
        }
        Spacer(Modifier.height(12f.dp))

        // 终端输出区域
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedRectangle(20f.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(20f.dp * GlassEffectConfig.terminalCornerRadius.value) },
                    effects = {
                        if (GlassEffectConfig.terminalEnableVibrancy.value) vibrancy()
                        blur(GlassEffectConfig.terminalBlurRadius.value.dp.toPx())
                        lens(
                            GlassEffectConfig.terminalRefractionHeight.value * 48f.dp.toPx(),
                            GlassEffectConfig.terminalRefractionAmount.value * 48f.dp.toPx()
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = GlassEffectConfig.terminalOpacity.value * 0.3f))
                    }
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16f.dp),
                verticalArrangement = Arrangement.spacedBy(2f.dp)
            ) {
                items(lines) { line ->
                    val color = when (line.type) {
                        LineType.INPUT -> Color(0xFF0088FF)
                        LineType.OUTPUT -> contentColor
                        LineType.ERROR -> Color(0xFFFF3B30)
                        LineType.INFO -> contentColor.copy(alpha = 0.5f)
                    }
                    BasicText(
                        line.text,
                        style = TextStyle(color, 12f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        Spacer(Modifier.height(12f.dp))

        // 输入区域
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48f.dp)
                    .clip(RoundedRectangle(16f.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(16f.dp * GlassEffectConfig.terminalCornerRadius.value) },
                        effects = {
                            if (GlassEffectConfig.terminalEnableVibrancy.value) vibrancy()
                            blur(GlassEffectConfig.terminalBlurRadius.value.dp.toPx())
                            lens(
                                GlassEffectConfig.terminalRefractionHeight.value * 48f.dp.toPx(),
                                GlassEffectConfig.terminalRefractionAmount.value * 48f.dp.toPx()
                            )
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = GlassEffectConfig.terminalOpacity.value * 0.3f))
                        }
                    )
                    .padding(horizontal = 16f.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(contentColor, 14f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            BasicText(AppStrings.get("enter_command"), style = TextStyle(contentColor.copy(alpha = 0.4f), 14f.sp))
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(Modifier.width(8f.dp))
            LiquidButton(
                onClick = { scope.launch { executeCommand(input) } },
                backdrop = backdrop,
                modifier = Modifier.height(48f.dp),
                tint = Color(0xFF0088FF)
            ) {
                BasicText(AppStrings.get("execute_btn"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(Color.White, 14f.sp))
            }
        }

        // 快捷命令面板（Root/ADB/System 分类，每条命令带解释）
        QuickCommandPanel(
            backdrop = backdrop,
            contentColor = contentColor,
            currentCategory = currentCmdCategory,
            onCategoryChange = { currentCmdCategory = it },
            showPanel = showQuickCommands,
            onTogglePanel = { showQuickCommands = !showQuickCommands },
            onCommandClick = { cmd ->
                if (cmd.command.contains("[") && cmd.command.contains("]")) {
                    // 含占位符的命令填入输入框让用户修改
                    input = cmd.command
                } else {
                    scope.launch { executeCommand(cmd.command) }
                }
            }
        )
        Spacer(Modifier.height(16f.dp))
    }
}

// 快捷命令面板 Composable
@Composable
fun QuickCommandPanel(
    backdrop: Backdrop,
    contentColor: Color,
    currentCategory: QuickCmdCategory,
    onCategoryChange: (QuickCmdCategory) -> Unit,
    showPanel: Boolean,
    onTogglePanel: () -> Unit,
    onCommandClick: (QuickCommand) -> Unit
) {
    Column {
        Spacer(Modifier.height(8f.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            QuickCmdButton(backdrop, QuickCmdCategory.ROOT.title, if (currentCategory == QuickCmdCategory.ROOT) Color(0xFF0088FF) else contentColor) {
                onCategoryChange(QuickCmdCategory.ROOT)
            }
            QuickCmdButton(backdrop, QuickCmdCategory.ADB.title, if (currentCategory == QuickCmdCategory.ADB) Color(0xFF0088FF) else contentColor) {
                onCategoryChange(QuickCmdCategory.ADB)
            }
            QuickCmdButton(backdrop, QuickCmdCategory.SYSTEM.title, if (currentCategory == QuickCmdCategory.SYSTEM) Color(0xFF0088FF) else contentColor) {
                onCategoryChange(QuickCmdCategory.SYSTEM)
            }
            QuickCmdButton(backdrop, if (showPanel) AppStrings.get("terminal_hide") else AppStrings.get("terminal_show"), contentColor) {
                onTogglePanel()
            }
        }

        if (showPanel) {
            Spacer(Modifier.height(8f.dp))
            val cmdList = when (currentCategory) {
                QuickCmdCategory.ROOT -> RootCommands
                QuickCmdCategory.ADB -> ADBCommands
                QuickCmdCategory.SYSTEM -> SystemCommands
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200f.dp)
                    .clip(RoundedRectangle(12f.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(12f.dp) },
                        effects = {
                            blur(GlassEffectConfig.terminalBlurRadius.value.dp.toPx() * 0.5f)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = GlassEffectConfig.terminalOpacity.value * 0.15f))
                        }
                    )
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8f.dp),
                    verticalArrangement = Arrangement.spacedBy(4f.dp)
                ) {
                    items(cmdList) { cmd ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassItem(backdrop = backdrop, corner = 10.dp, onClick = { onCommandClick(cmd) })
                                .padding(horizontal = 8f.dp, vertical = 4f.dp)
                        ) {
                            BasicText(
                                cmd.command,
                                style = TextStyle(Color(0xFF0088FF), 11f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                            BasicText(
                                cmd.description,
                                style = TextStyle(contentColor.copy(alpha = 0.6f), 10f.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickCmdButton(backdrop: Backdrop, label: String, contentColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            // 原先是静态色块 + 无反馈点击：改成液态玻璃可点项，保留原模糊半径与圆角
            // （liquidGlassItem 按 tint.alpha * 0.45f 着色，这里除以 0.45f 还原原来的白色蒙层浓度）
            .liquidGlassItem(
                backdrop = backdrop,
                corner = 12f.dp * GlassEffectConfig.terminalCornerRadius.value,
                blurPx = GlassEffectConfig.terminalBlurRadius.value * 0.5f,
                tint = Color.White.copy(alpha = (GlassEffectConfig.terminalOpacity.value * 0.2f / 0.45f).coerceAtMost(1f)),
                onClick = onClick
            )
            .padding(horizontal = 14f.dp, vertical = 8f.dp)
    ) {
        BasicText(label, style = TextStyle(contentColor, 12f.sp))
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick))
}


// 快捷命令数据类
data class QuickCommand(
    val command: String,
    val description: String
)

// 快捷命令分类
enum class QuickCmdCategory(val title: String) {
    ROOT("Root"),
    ADB("ADB"),
    SYSTEM("System")
}

// Root 命令列表
val RootCommands = listOf(
    QuickCommand("su", "切换到 root 用户"),
    QuickCommand("id", "查看当前用户 ID"),
    QuickCommand("getenforce", "查看 SELinux 状态"),
    QuickCommand("setenforce 0", "设置 SELinux 为宽容模式"),
    QuickCommand("setenforce 1", "设置 SELinux 为强制模式"),
    QuickCommand("pm list packages", "列出所有应用包名"),
    QuickCommand("pm clear [package]", "清除应用数据和缓存"),
    QuickCommand("pm disable [package]", "禁用/冻结应用"),
    QuickCommand("pm enable [package]", "启用/解冻应用"),
    QuickCommand("pm uninstall [package]", "卸载应用"),
    QuickCommand("reboot", "重启设备"),
    QuickCommand("reboot recovery", "重启到恢复模式"),
    QuickCommand("reboot bootloader", "重启到引导模式"),
    QuickCommand("cat /proc/ksu_version", "查看 KernelSU 版本"),
    QuickCommand("/data/adb/ksud live", "KernelSU 临时 root"),
    QuickCommand("dd if=/dev/block/by-name/boot of=/sdcard/boot.img", "提取 boot 分区镜像"),
    QuickCommand("chmod 755 [file]", "设置文件可执行权限"),
    QuickCommand("dumpsys package [package]", "查看应用详细信息"),
    QuickCommand("dumpsys battery", "查看电池信息"),
    QuickCommand("settings put global adb_enabled 1", "启用 ADB 调试"),
    QuickCommand("wm size 1080x2400", "设置屏幕分辨率"),
    QuickCommand("wm density 480", "设置屏幕密度")
)

// ADB 命令列表
val ADBCommands = listOf(
    QuickCommand("adb devices", "列出已连接的 ADB 设备"),
    QuickCommand("adb shell", "进入设备 shell"),
    QuickCommand("adb install [apk]", "安装 APK 应用"),
    QuickCommand("adb install -r [apk]", "重新安装应用，保留数据"),
    QuickCommand("adb uninstall [package]", "卸载应用"),
    QuickCommand("adb push [local] [remote]", "推送文件到设备"),
    QuickCommand("adb pull [remote] [local]", "从设备拉取文件"),
    QuickCommand("adb reboot", "重启设备"),
    QuickCommand("adb reboot recovery", "重启到恢复模式"),
    QuickCommand("adb reboot bootloader", "重启到引导模式"),
    QuickCommand("adb logcat", "实时查看设备日志"),
    QuickCommand("adb logcat -c", "清空日志缓冲区"),
    QuickCommand("adb shell pm list packages", "列出所有应用包名"),
    QuickCommand("adb shell pm clear [package]", "清除应用数据"),
    QuickCommand("adb shell pm disable-user [package]", "禁用应用（用户级）"),
    QuickCommand("adb shell pm enable [package]", "启用应用"),
    QuickCommand("adb shell dumpsys package [package]", "查看应用详细信息"),
    QuickCommand("adb shell dumpsys activity", "查看 Activity 管理器状态"),
    QuickCommand("adb shell dumpsys battery", "查看电池状态"),
    QuickCommand("adb shell settings get global [key]", "获取全局设置值"),
    QuickCommand("adb shell settings put global [key] [value]", "设置全局设置"),
    QuickCommand("adb shell wm size", "查看屏幕分辨率"),
    QuickCommand("adb shell wm density", "查看屏幕密度"),
    QuickCommand("adb shell getprop [prop]", "获取系统属性"),
    QuickCommand("adb shell setprop [prop] [value]", "设置系统属性"),
    QuickCommand("adb shell getprop ro.build.version.release", "查看 Android 版本"),
    QuickCommand("adb shell getprop ro.product.model", "查看设备型号"),
    QuickCommand("adb shell input tap [x] [y]", "模拟点击屏幕"),
    QuickCommand("adb shell input swipe [x1] [y1] [x2] [y2]", "模拟滑动屏幕"),
    QuickCommand("adb shell input keyevent 26", "模拟电源键"),
    QuickCommand("adb shell screencap -p /sdcard/screenshot.png", "截取屏幕"),
    QuickCommand("adb shell screenrecord /sdcard/video.mp4", "录制屏幕视频"),
    QuickCommand("adb version", "查看 ADB 版本")
)

// 系统命令列表
val SystemCommands = listOf(
    QuickCommand("ls", "列出当前目录文件"),
    QuickCommand("ls -la", "详细列出文件（权限/大小/时间）"),
    QuickCommand("pwd", "显示当前目录路径"),
    QuickCommand("cd [dir]", "切换到指定目录"),
    QuickCommand("cd ..", "返回上一级目录"),
    QuickCommand("cd /", "切换到根目录"),
    QuickCommand("cat [file]", "查看文件内容"),
    QuickCommand("echo [text]", "输出文本到屏幕"),
    QuickCommand("echo [text] > [file]", "写入文件（覆盖）"),
    QuickCommand("echo [text] >> [file]", "追加文本到文件"),
    QuickCommand("mkdir [dir]", "创建新目录"),
    QuickCommand("mkdir -p [dir]", "递归创建目录"),
    QuickCommand("rm [file]", "删除文件"),
    QuickCommand("rm -f [file]", "强制删除文件"),
    QuickCommand("rm -r [dir]", "递归删除目录"),
    QuickCommand("rm -rf [dir]", "强制递归删除目录（谨慎！）"),
    QuickCommand("cp [source] [dest]", "复制文件"),
    QuickCommand("cp -r [source] [dest]", "递归复制目录"),
    QuickCommand("mv [source] [dest]", "移动或重命名文件"),
    QuickCommand("chmod [perm] [file]", "修改文件权限"),
    QuickCommand("chmod +x [file]", "添加可执行权限"),
    QuickCommand("ps", "查看运行中的进程"),
    QuickCommand("ps -A", "查看所有进程"),
    QuickCommand("top", "实时查看系统资源占用"),
    QuickCommand("free", "查看内存使用情况"),
    QuickCommand("free -h", "查看内存（可读格式）"),
    QuickCommand("df", "查看磁盘分区使用情况"),
    QuickCommand("df -h", "查看磁盘（可读格式）"),
    QuickCommand("du -sh [dir]", "查看目录总大小"),
    QuickCommand("uname -a", "查看系统内核完整信息"),
    QuickCommand("uname -r", "查看内核版本"),
    QuickCommand("uname -m", "查看系统架构"),
    QuickCommand("whoami", "查看当前用户名"),
    QuickCommand("id", "查看当前用户 ID 和组"),
    QuickCommand("date", "查看当前日期和时间"),
    QuickCommand("uptime", "查看系统运行时间和负载"),
    QuickCommand("clear", "清空终端屏幕"),
    QuickCommand("exit", "退出当前 shell"),
    QuickCommand("history", "查看命令历史记录"),
    QuickCommand("which [cmd]", "查看命令可执行文件路径"),
    QuickCommand("file [file]", "查看文件类型"),
    QuickCommand("wc -l [file]", "统计文件行数"),
    QuickCommand("grep [keyword] [file]", "在文件中搜索关键词"),
    QuickCommand("grep -r [keyword] [dir]", "递归搜索关键词"),
    QuickCommand("grep -i [keyword] [file]", "忽略大小写搜索"),
    QuickCommand("find [dir] -name [file]", "按名称查找文件"),
    QuickCommand("find [dir] -type f", "列出目录下所有文件"),
    QuickCommand("tar -cvf [archive].tar [files]", "创建 tar 归档"),
    QuickCommand("tar -xvf [archive].tar", "解压 tar 归档"),
    QuickCommand("gzip [file]", "压缩文件为 .gz"),
    QuickCommand("gunzip [file].gz", "解压 .gz 文件"),
    QuickCommand("sleep [seconds]", "休眠指定秒数"),
    QuickCommand("reboot", "重启设备"),
    QuickCommand("mount", "查看所有挂载的文件系统"),
    QuickCommand("umount [mountpoint]", "卸载指定挂载点"),
    QuickCommand("ln -s [source] [link]", "创建符号链接"),
    QuickCommand("touch [file]", "创建空文件或更新时间戳"),
    QuickCommand("head -n [N] [file]", "查看文件前 N 行"),
    QuickCommand("tail -n [N] [file]", "查看文件后 N 行"),
    QuickCommand("tail -f [file]", "实时跟踪文件内容"),
    QuickCommand("sort [file]", "对文件内容排序"),
    QuickCommand("uniq [file]", "去除连续重复行"),
    QuickCommand("kill [pid]", "按进程 ID 终止进程"),
    QuickCommand("kill -9 [pid]", "强制终止进程"),
    QuickCommand("killall [name]", "按名称终止所有进程"),
    QuickCommand("env", "查看所有环境变量"),
    QuickCommand("export [var]=[value]", "设置环境变量")
)
