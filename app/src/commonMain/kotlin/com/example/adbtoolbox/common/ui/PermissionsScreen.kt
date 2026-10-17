package com.example.adbtoolbox.common.ui

import com.example.adbtoolbox.common.GlassEffectConfig
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppInfoData
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PermissionInfoData
import com.example.adbtoolbox.common.theme.AppLayout
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * 后台线程执行 + 异常转成 null（CancellationException 正常抛出，不吞掉协程取消）。
 * 本文件私有，避免与其它页面重名。
 */
private suspend fun <T> permissionsBackgroundOrNull(block: () -> T): T? = try {
    withContext(Dispatchers.Default) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

@Composable
fun PermissionsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfoData?>(null) }
    var permissions by remember { mutableStateOf<List<PermissionInfoData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var permsLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultOk by remember { mutableStateOf(true) }
    // null = 检测中, true = 有 Shizuku/Dhizuku 或 Root, false = 都不可用
    var privileged by remember { mutableStateOf<Boolean?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<PermissionInfoData?>(null) }
    val scope = rememberCoroutineScope()

    // 直接观察全局缓存，避免显示过期列表
    val cachedApps = AppCache.installedApps.value
    val userApps = remember(cachedApps) { cachedApps.filter { !it.isSystem } }

    LaunchedEffect(reloadTick) {
        // 缓存为空（含 Preloader 失败写入的空列表）时才真正加载，失败不置位 appsLoaded，允许重试
        if (AppCache.installedApps.value.isNotEmpty()) {
            isLoading = false
            loadFailed = false
            return@LaunchedEffect
        }
        isLoading = true
        loadFailed = false
        val loaded = permissionsBackgroundOrNull { ADBTools.getInstalledApps() }
        if (loaded.isNullOrEmpty()) {
            AppCache.appsLoaded.value = false
            loadFailed = true
        } else {
            AppCache.installedApps.value = loaded
            AppCache.appsLoaded.value = true
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        privileged = permissionsBackgroundOrNull { ADBTools.isShizukuAvailable() || ADBTools.isRooted() } ?: false
    }

    LaunchedEffect(selectedApp) {
        val app = selectedApp
        permissions = emptyList()
        pendingRevoke = null
        resultMessage = ""
        if (app != null) {
            val cached = AppCache.getPermissions(app.packageName)
            if (cached != null && cached.isNotEmpty()) {
                permissions = cached
            } else {
                permsLoading = true
                val perms = permissionsBackgroundOrNull { ADBTools.getAppPermissions(app.packageName) } ?: emptyList()
                AppCache.setPermissions(app.packageName, perms)
                permissions = perms
                permsLoading = false
            }
        }
    }

    /** 真正执行授权/撤销；失败原因显示出来，成功后重新读取真实权限状态 */
    fun applyPermission(grant: Boolean, perm: PermissionInfoData) {
        val app = selectedApp ?: return
        if (isBusy) return
        isBusy = true
        pendingRevoke = null
        resultOk = true
        val label = "${if (grant) AppStrings.get("grant") else AppStrings.get("revoke")} ${perm.name}"
        resultMessage = "${AppStrings.get("executing")}: $label..."
        scope.launch {
            var errorText: String? = null
            val ok = try {
                withContext(Dispatchers.Default) {
                    if (grant) ADBTools.grantPermission(app.packageName, perm.permission)
                    else ADBTools.revokePermission(app.packageName, perm.permission)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = e.message ?: AppStrings.get("unknown")
                false
            }
            // 重新读取权限，保证界面显示的是系统里的真实状态
            val latest = permissionsBackgroundOrNull { ADBTools.getAppPermissions(app.packageName) }
            if (latest != null) {
                AppCache.setPermissions(app.packageName, latest)
                permissions = latest
            }
            val effective = latest?.find { it.permission == perm.permission }?.isGranted
            val succeeded = if (effective != null) effective == grant else ok
            resultOk = succeeded
            resultMessage = when {
                succeeded -> "$label ${AppStrings.get("success")}"
                errorText != null -> "$label ${AppStrings.get("failed")} ($errorText)"
                else -> "$label ${AppStrings.get("failed")} (${AppStrings.get("operation_failed")})"
            }
            isBusy = false
        }
    }

    /** 点击权限项：授权直接执行；撤销是破坏性操作，先二次确认 */
    fun onPermissionClick(perm: PermissionInfoData) {
        if (isBusy) return
        if (privileged != true) {
            resultOk = false
            resultMessage = "${AppStrings.get("operation_failed")} · ${AppStrings.get("need_root_or_shizuku")}"
            return
        }
        if (perm.isGranted) {
            resultMessage = ""
            pendingRevoke = perm
            return
        }
        applyPermission(true, perm)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = AppLayout.screenH)) {
        Spacer(Modifier.height(AppLayout.screenTop))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.width(AppLayout.headerGap))
            BasicText(AppStrings.get("permission_manager"), style = TextStyle(contentColor, AppLayout.titleSize, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        // 权限能力提示：不可用时给出可操作入口，而不是让点击静默失败
        if (privileged == false) {
            StaggeredCard(0) {
    GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPadCompact)) {
                    BasicText(
                        "${AppStrings.get("need_root_or_shizuku")} · ${AppStrings.get("authorization_required")}",
                        style = TextStyle(Color(0xFFFF9500), 12f.sp)
                    )
                    Spacer(Modifier.height(8f.dp))
                    LiquidButton(
                        onClick = {
                            ADBTools.requestShizukuPermission()
                            scope.launch {
                                delay(1200) // Shizuku 状态有 1 秒缓存，稍等再复查
                                privileged = permissionsBackgroundOrNull {
                                    ADBTools.isShizukuAvailable() || ADBTools.isRooted()
                                } ?: false
                                if (privileged != true) {
                                    resultOk = false
                                    resultMessage = "${AppStrings.get("operation_failed")} · ${AppStrings.get("shizuku_not_connected")}"
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(34.dp),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(AppStrings.get("request_shizuku"), Modifier.padding(horizontal = 10f.dp), style = TextStyle(Color.White, AppLayout.captionSize))
                    }
                }
            }
    }
            Spacer(Modifier.height(10f.dp))
        }

        // 操作结果 / 进行中提示
        if (resultMessage.isNotEmpty()) {
            StaggeredCard(1) {
    GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPadCompact)) {
                    BasicText(
                        resultMessage,
                        style = TextStyle(if (resultOk) contentColor else Color(0xFFFF3B30), 12f.sp)
                    )
                }
            }
    }
            Spacer(Modifier.height(10f.dp))
        }

        if (selectedApp == null) {
            BasicText("${AppStrings.get("app_manager")} - ${AppStrings.get("app_permissions")}", style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.bodySize))
            Spacer(Modifier.height(8f.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(AppStrings.get("loading"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
                }
            } else if (userApps.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 50f.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (loadFailed) {
                        BasicText(AppStrings.get("operation_failed"), style = TextStyle(Color(0xFFFF3B30), AppLayout.bodySize))
                        Spacer(Modifier.height(6f.dp))
                    }
                    BasicText("${AppStrings.get("app_manager")} (0)", style = TextStyle(contentColor.copy(alpha = 0.5f), 13f.sp))
                    Spacer(Modifier.height(AppLayout.innerGap))
                    LiquidButton(
                        onClick = { reloadTick++ },
                        backdrop = backdrop,
                        modifier = Modifier.height(38f.dp),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(AppStrings.get("redetect"), Modifier.padding(horizontal = 14f.dp), style = TextStyle(Color.White, 12f.sp))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6f.dp)) {
                    items(userApps, key = { it.packageName }) { app ->
                        AppListItem(app = app, backdrop = backdrop, contentColor = contentColor, onClick = { selectedApp = app })
                    }
                    item { Spacer(Modifier.height(80f.dp)) }
                }
            }
        } else {
            // 应用权限详情
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidButton(
                    onClick = { selectedApp = null },
                    backdrop = backdrop,
                    modifier = Modifier.height(36f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("back"), Modifier.padding(horizontal = 10f.dp), style = TextStyle(Color.White, 12f.sp))
                }
                Spacer(Modifier.width(AppLayout.headerGap))
                BasicText(selectedApp!!.appName, style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
            }
            Spacer(Modifier.height(AppLayout.innerGap))
            BasicText(
                if (permsLoading) AppStrings.get("loading") else "${AppStrings.get("app_permissions")}: ${permissions.size}",
                style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp)
            )
            Spacer(Modifier.height(8f.dp))

            // 撤销二次确认
            pendingRevoke?.let { perm ->
                StaggeredCard(2) {
    GlassCard(backdrop = backdrop, pageType = "apps") {
                    Column(Modifier.padding(14f.dp)) {
                        BasicText(
                            "${AppStrings.get("revoke")} ${perm.name}?",
                            style = TextStyle(Color(0xFFFF9500), 12f.sp)
                        )
                        Spacer(Modifier.height(8f.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppDetailSmallButton(backdrop, AppStrings.get("execute"), Color(0xFFFF3B30)) { applyPermission(false, perm) }
                            AppDetailSmallButton(backdrop, AppStrings.get("back"), Color(0xFF8E8E93)) { pendingRevoke = null }
                        }
                    }
                }
    }
                Spacer(Modifier.height(10f.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6f.dp)) {
                items(permissions, key = { it.permission }) { perm ->
                    PermissionItem(
                        perm = perm,
                        backdrop = backdrop,
                        contentColor = contentColor,
                        onToggle = { onPermissionClick(perm) }
                    )
                }
                item { Spacer(Modifier.height(80f.dp)) }
            }
        }
    }
}

@Composable
fun PermissionItem(
    perm: PermissionInfoData,
    backdrop: Backdrop,
    contentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlassItem(backdrop = backdrop, corner = 14.dp, onClick = onToggle)
            .padding(14f.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(perm.name, style = TextStyle(contentColor, AppLayout.bodySize, androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
            BasicText(perm.permission, style = TextStyle(contentColor.copy(alpha = 0.4f), 10f.sp), maxLines = 1)
        }
        Spacer(Modifier.width(8f.dp))
        BasicText(
            "${if (perm.isGranted) AppStrings.get("granted") else AppStrings.get("not_granted")} ›",
            style = TextStyle(if (perm.isGranted) Color(0xFF34C759) else Color(0xFFFF3B30), 12f.sp)
        )
    }
}

@Composable
fun Modifier.clipGlass(backdrop: Backdrop, radius: Float): Modifier {
    return this.then(
        Modifier
            .clip(com.kyant.shapes.RoundedRectangle(radius.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { com.kyant.shapes.RoundedRectangle(radius.dp) },
                effects = {
                    if (GlassEffectConfig.settingsEnableVibrancy.value) vibrancy()
                    blur(GlassEffectConfig.settingsBlurRadius.value.dp.toPx())
                    lens(
                        GlassEffectConfig.settingsRefractionHeight.value * 48f.dp.toPx(),
                        GlassEffectConfig.settingsRefractionAmount.value * 48f.dp.toPx(),
                        chromaticAberration = GlassEffectConfig.chromaticAberration.value > 0f
                    )
                }
            )
    )
}
