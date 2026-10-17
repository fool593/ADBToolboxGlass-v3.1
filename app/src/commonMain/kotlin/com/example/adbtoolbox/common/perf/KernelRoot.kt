package com.example.adbtoolbox.common.perf

import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内核提权（运行**用户自备**的 exploit）。
 *
 * 必须先说清楚这个功能**不做**什么，免得误会：
 * - 本应用**不内置、不打包、不自动下载**任何 exploit 代码；
 * - 也**不提供**"一键支持所有内核 6.x"的假承诺 —— 公开的内核提权利用（LPE）都是
 *   **针对特定 CVE + 特定内核版本 + 特定架构**编译出来的，例如 UnPlus 针对 arm64 GKI 6.6、
 *   DirtyFrag 只在个别机型实测通过。换一个内核版本或换一台机器，同一个二进制通常直接失败甚至崩内核。
 *
 * 这里做的是把"用公开 exploit 提权"这件事变得**可控、可核对**：
 * 1. 真实读出本机内核版本 / 架构 / GKI 分支 / SELinux / 当前 uid，让你去对照 exploit 的 README；
 * 2. 把你从 GitHub 下载的 arm64 可执行文件推送到 `/data/local/tmp`（分块 base64 + 字节数校验）；
 * 3. 在设备上执行并**原样回显它的输出**（成功失败都以 exploit 自己的输出为准，我们不美化）；
 * 4. 执行后真实探测一次 `su -c id`，只以 uid 是不是 0 来判断"是否真的拿到 root"。
 *
 * 风险提示（界面同样会显示）：内核漏洞利用失败可能造成内存损坏、数据丢失、无法开机；
 * 这类提权通常是**单次开机有效**（per-boot），重启后需要重新执行；请先备份。
 */
object KernelRoot {

    /** 本机内核与提权相关的真实信息，全部来自命令输出，读不到就留空。 */
    data class KernelInfo(
        /** `uname -r`，例如 6.6.89-android15-8-gxxxx */
        val kernelRelease: String,
        /** `uname -m`，例如 aarch64 */
        val arch: String,
        /** `/proc/version` 全文（截断） */
        val procVersion: String,
        /** `getenforce`：Enforcing / Permissive / 空 */
        val selinux: String,
        /** 当前执行身份，例如 uid=2000(shell) */
        val currentUid: String,
        /** Android 安全补丁级别 */
        val securityPatch: String
    ) {
        /** 是否为 arm64（公开的内核 exploit 基本都只编译了 arm64） */
        val isArm64: Boolean
            get() = arch.contains("aarch64", true) || arch.contains("arm64", true)

        /**
         * 从 `/proc/version` 里提取 GKI 分支，例如 android13-6.1 / android14-6.6；
         * 提取不到就返回空串（不猜）。
         */
        val gkiBranch: String
            get() {
                val m = Regex("""android\d{2}-[0-9]+\.[0-9]+""").find(procVersion)
                return m?.value ?: ""
            }

        /** 主内核版本号，例如 6.6 */
        val majorMinor: String
            get() {
                val m = Regex("""^(\d+\.\d+)""").find(kernelRelease.trim())
                return m?.groupValues?.get(1) ?: ""
            }
    }

    /** 读取本机内核与提权相关信息。全部真实命令，读不到就是空串。 */
    suspend fun readInfo(): KernelInfo = withContext(Dispatchers.Default) {
        fun cmd(c: String, t: Int = 10): String = try {
            ADBTools.execCommand(c, t).output.trim()
        } catch (e: Exception) {
            ""
        }
        KernelInfo(
            kernelRelease = cmd("uname -r"),
            arch = cmd("uname -m"),
            procVersion = cmd("cat /proc/version").take(400),
            selinux = cmd("getenforce"),
            currentUid = cmd("id"),
            securityPatch = cmd("getprop ro.build.version.security_patch")
        )
    }

    /**
     * 执行位于设备上的 exploit 并回读结果。
     *
     * [remotePath] 必须是 `/data/local/tmp/...`（由 [ADBTools.pushLocalFileToTemp] 返回）。
     * 执行完成后立刻做一次真实的 root 探测：`su -c id` 拿不到就退回 `id`，
     * 由界面按 uid 是否为 0 判定成功——**不按 exploit 自己打印的 success 字样判定**。
     */
    suspend fun runExploit(remotePath: String, timeoutSec: Int = 120): CommandResult = withContext(Dispatchers.Default) {
        if (remotePath.isBlank() || !remotePath.startsWith("/data/local/tmp/")) {
            return@withContext CommandResult("", "refused: remote path must be under /data/local/tmp", 1)
        }
        val dir = remotePath.substringBeforeLast('/')
        val file = remotePath.substringAfterLast('/')
        // 用 sh -c 进入目录再执行：部分 exploit 依赖当前工作目录写临时文件
        val escapedDir = "'" + dir.replace("'", "'\\''") + "'"
        val escapedFile = "'" + "./" + file.replace("'", "'\\''") + "'"
        // 官方实现对照（ghostlock main.cpp CLI）：裸运行只打印 usage，
        // 必须给入口参数才能走攻击链路 —— --ghostlock-app-call（app 入口）或
        // --load-prebuilt-profile <bin>（预置 profile.bin）。同目录有 *.bin 时优先后者。
        val ghostArgs: String = if (file.contains("ghostlock", ignoreCase = true)) {
            try {
                val bin = java.io.File(dir).listFiles()
                    ?.firstOrNull { it.name.endsWith(".bin") }
                if (bin != null) "--load-prebuilt-profile '" + bin.name.replace("'", "'\\''") + "'" else "--ghostlock-app-call"
            } catch (e: Exception) {
                "--ghostlock-app-call"
            }
        } else {
            ""
        }
        val run = try {
            ADBTools.execCommand("cd $escapedDir && $escapedFile $ghostArgs 2>&1; echo EXIT=\$?", timeoutSec)
        } catch (e: Exception) {
            CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
        }
        val probe = probeRoot()
        return@withContext CommandResult(
            output = buildString {
                append(run.output.trim())
                append("\n" + com.example.adbtoolbox.common.AppStrings.get("kroot_probe_label") + "\n")
                append(probe)
            },
            error = run.error,
            exitCode = if (probe.contains("uid=0")) 0 else run.exitCode
        )
    }

    /** 真实探测是否已经拿到 root：优先 `su -c id`，没有 su 就用 `id`；两者都照原样返回。 */
    suspend fun probeRoot(): String = withContext(Dispatchers.Default) {
        val viaSu = try {
            ADBTools.execCommand("su -c id 2>/dev/null", 15).output.trim()
        } catch (e: Exception) {
            ""
        }
        if (viaSu.contains("uid=0")) return@withContext viaSu
        val plain = try {
            ADBTools.execCommand("id", 10).output.trim()
        } catch (e: Exception) {
            ""
        }
        if (plain.isBlank()) viaSu else "$plain\n(su: ${viaSu.ifBlank { "不可用" }})"
    }
    /** 官方 CLI 全集执行：跑 ghostlock 二进制并显式追加 flags（见 main.cpp），
     *  裸运行只会打印 usage；同 runExploit 一样最后做 uid=0 探测。 */
    suspend fun runGhostlockCli(remotePath: String, flags: String, timeoutSec: Int = 120): CommandResult =
        withContext(Dispatchers.Default) {
            if (remotePath.isBlank() || !remotePath.startsWith("/data/local/tmp/")) {
                return@withContext CommandResult("", "refused: remote path must be under /data/local/tmp", 1)
            }
            val dir = remotePath.substringBeforeLast('/')
            val file = "./" + remotePath.substringAfterLast('/')
            val ed = "'" + dir.replace("'", "'\\''") + "'"
            val ef = "'" + file.replace("'", "'\\''") + "'"
            val run = try {
                ADBTools.execCommand("cd $ed && $ef $flags 2>&1; echo EXIT=\$?", timeoutSec)
            } catch (e: Exception) {
                CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
            }
            val probe = probeRoot()
            CommandResult(
                output = buildString {
                    append(run.output.trim())
                    append("\n" + com.example.adbtoolbox.common.AppStrings.get("kroot_probe_label") + "\n")
                    append(probe)
                },
                error = run.error,
                exitCode = if (probe.contains("uid=0")) 0 else run.exitCode
            )
        }
}
