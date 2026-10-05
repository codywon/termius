package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.ui.components.AppUpdateDialog
import com.termius.clone.ui.components.UpdateUiState
import com.termius.clone.ui.theme.*
import com.termius.clone.util.AppLanguage
import com.termius.clone.util.AppUpdateManager
import com.termius.clone.util.BatteryOptimizationHelper
import com.termius.clone.util.LanguageManager
import com.termius.clone.util.Strings
import com.termius.clone.util.UpdateCheckResult
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val identities by db.identityDao().getAllIdentities().collectAsState(initial = emptyList())
    var isVaultExpanded by remember { mutableStateOf(false) }
    var showAddIdentityDialog by remember { mutableStateOf(false) }

    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    var sshKeepaliveEnabled by remember { mutableStateOf(true) }
    var isIgnoringBattery by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    var showBatteryGuideDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTerminalThemeDialog by remember { mutableStateOf(false) }

    val aiConfigManager = remember { com.termius.clone.data.local.AiConfigManager(context) }
    var aiConfig by remember { mutableStateOf(aiConfigManager.loadConfig()) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    // 交互优化：从系统设置或授权弹窗返回时自动刷新电池优化状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val theme = LocalAppTheme.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部极简标题栏 (参考 ConnectBot 简洁原生风)
            Surface(color = theme.surfaceContainerLow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.settingsTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ObsidianTextPrimary
                    )
                }
            }

            // 扁平原生分组设置列表 (对标 ConnectBot)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ---------------- 1. 界面与外观 ----------------
                SettingsSectionHeader(title = Strings.groupAppearance)

                // 应用主题
                SettingsItem(
                    title = Strings.sectionAppearance,
                    subtitle = if (Strings.isZh) theme.titleZh else theme.titleEn,
                    onClick = { showThemeDialog = true },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(theme.previewGradient),
                                    shape = CircleShape
                                )
                                .border(1.dp, ObsidianOutlineVariant, CircleShape)
                        )
                    }
                )
                DividerLine()

                // 终端配色 (黑底白字、黑客绿字、复古琥珀等)
                SettingsItem(
                    title = Strings.sectionTerminalTheme,
                    subtitle = if (Strings.isZh) ThemeManager.currentTerminalTheme.nameZh else ThemeManager.currentTerminalTheme.nameEn,
                    onClick = { showTerminalThemeDialog = true },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ThemeManager.currentTerminalTheme.background,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Text(
                                text = " >_ ",
                                color = ThemeManager.currentTerminalTheme.foreground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
                DividerLine()

                // 终端字号
                SettingsItem(
                    title = Strings.terminalFontSizeTitle,
                    subtitle = "${ThemeManager.terminalFontSizeSp.toInt()} SP",
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { ThemeManager.setTerminalFontSize(ThemeManager.terminalFontSizeSp - 1f) },
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = theme.surfaceContainerHigh,
                                    contentColor = ObsidianTextPrimary
                                )
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${ThemeManager.terminalFontSizeSp.toInt()} SP",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = theme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalIconButton(
                                onClick = { ThemeManager.setTerminalFontSize(ThemeManager.terminalFontSizeSp + 1f) },
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = theme.surfaceContainerHigh,
                                    contentColor = ObsidianTextPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                )
                DividerLine()

                // 界面语言
                SettingsItem(
                    title = Strings.sectionLanguage,
                    subtitle = if (Strings.isZh) "简体中文" else "English",
                    onClick = { showLanguageDialog = true },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(18.dp))
                    }
                )
                DividerLine()

                // ---------------- 2. 凭据管理 ----------------
                SettingsSectionHeader(title = Strings.groupCredentials)

                SettingsItem(
                    title = Strings.sectionVault,
                    subtitle = "${identities.size} ${Strings.identitiesCount}",
                    onClick = { isVaultExpanded = !isVaultExpanded },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showAddIdentityDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(Strings.newLabel, fontSize = 12.sp, color = theme.primary)
                            }
                            Icon(
                                if (isVaultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = ObsidianTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                // 凭据展开列表
                AnimatedVisibility(visible = isVaultExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surfaceContainerLow)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (identities.isEmpty()) {
                            Text(
                                text = if (Strings.isZh) "暂无保存的凭据，点击右侧「新建」添加" else "No saved credentials. Tap New to add.",
                                fontSize = 12.sp,
                                color = ObsidianTextMuted,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            identities.forEach { identity ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = theme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (identity.privateKey.isNotEmpty()) Icons.Default.Key else Icons.Default.Password,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(identity.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary)
                                            Text(
                                                text = "${identity.username} • ${if (identity.privateKey.isNotEmpty()) "SSH Key (${identity.keyType})" else "密码"}",
                                                fontSize = 11.sp,
                                                color = ObsidianTextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        IconButton(
                                            onClick = { scope.launch { db.identityDao().deleteIdentity(identity) } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ObsidianError, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DividerLine()

                // ---------------- 3. 连接与保活 (对标 ConnectBot) ----------------
                SettingsSectionHeader(title = Strings.groupConnection)

                // SSH 心跳保持
                SettingsItem(
                    title = Strings.keepalivePing,
                    subtitle = Strings.keepalivePingDesc,
                    trailingContent = {
                        Switch(
                            checked = sshKeepaliveEnabled,
                            onCheckedChange = { sshKeepaliveEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.primary,
                                checkedTrackColor = theme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                )
                DividerLine()

                // 后台常驻服务
                SettingsItem(
                    title = Strings.wakeLockStatus,
                    subtitle = Strings.wakeLockStatusDesc,
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = theme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(modifier = Modifier.size(5.dp).background(theme.primary, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = Strings.runningStatus,
                                    fontSize = 11.sp,
                                    color = theme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )
                DividerLine()

                // 电池优化 (问号帮助紧挨标题，右侧保留纯粹胶囊状态)
                val badgeColor = if (isIgnoringBattery) theme.primary else ObsidianWarning
                SettingsItem(
                    title = Strings.batteryOptimizationTitle,
                    titleExtra = {
                        IconButton(
                            onClick = { showBatteryGuideDialog = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Help",
                                tint = ObsidianTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    },
                    subtitle = Strings.batteryOptimizationDesc,
                    onClick = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                    },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(modifier = Modifier.size(5.dp).background(badgeColor, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isIgnoringBattery) Strings.batteryIgnoredTag else Strings.batteryOptimizedTag,
                                    fontSize = 11.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )
                DividerLine()

                // ---------------- 4. AI 智能运维设置 ----------------
                SettingsSectionHeader(title = if (Strings.isZh) "AI 运维智能体" else "AI Ops Agent")

                SettingsItem(
                    title = if (Strings.isZh) "大模型服务接口" else "LLM Model Configuration",
                    subtitle = if (aiConfig.apiKey.isBlank()) (if (Strings.isZh) "未配置 API Key · 点击配置" else "Not configured · Tap to setup") else "${aiConfig.modelName} (${aiConfig.baseUrl})",
                    onClick = { showAiSettingsDialog = true },
                    trailingContent = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    }
                )
                DividerLine()

                // ---------------- 5. 关于 ----------------
                SettingsSectionHeader(title = Strings.groupAbout)

                val versionName = remember { AppUpdateManager.getCurrentVersionName(context) }
                SettingsItem(
                    title = "TermX Mobile",
                    subtitle = "v$versionName • ${Strings.poweredBy}",
                    trailingContent = {
                        TextButton(
                            onClick = {
                                if (isCheckingUpdate) return@TextButton
                                isCheckingUpdate = true
                                scope.launch {
                                    Toast.makeText(context, if (Strings.isZh) "正在检查更新..." else "Checking...", Toast.LENGTH_SHORT).show()
                                    when (val result = AppUpdateManager.checkUpdate(context, isManual = true)) {
                                        is UpdateCheckResult.HasUpdate -> {
                                            updateUiState = UpdateUiState.HasUpdate(result.info)
                                        }
                                        is UpdateCheckResult.NoUpdate -> {
                                            Toast.makeText(context, if (Strings.isZh) "已是最新版本 (v${result.currentVersion})" else "Latest version (v${result.currentVersion})", Toast.LENGTH_SHORT).show()
                                        }
                                        is UpdateCheckResult.Error -> {
                                            Toast.makeText(context, if (Strings.isZh) "检查更新失败: ${result.message}" else "Failed: ${result.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                    isCheckingUpdate = false
                                }
                            }
                        ) {
                            Text(
                                text = if (isCheckingUpdate) (if (Strings.isZh) "检测中" else "Checking") else Strings.checkUpdateBtn,
                                fontSize = 12.sp,
                                color = theme.primary
                            )
                        }
                    }
                )
                DividerLine()

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // 主题切换对话框
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(Strings.sectionAppearance, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { appTheme ->
                        val isSelected = theme == appTheme
                        val title = if (Strings.isZh) appTheme.titleZh else appTheme.titleEn
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) appTheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, appTheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setTheme(appTheme)
                                    showThemeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.linearGradient(appTheme.previewGradient),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = ObsidianTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = appTheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 终端配色选择对话框
    if (showTerminalThemeDialog) {
        AlertDialog(
            onDismissRequest = { showTerminalThemeDialog = false },
            title = { Text(Strings.sectionTerminalTheme, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.termius.clone.data.model.TerminalThemes.allThemes.forEach { termTheme ->
                        val isSelected = ThemeManager.currentTerminalTheme.id == termTheme.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) theme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setTerminalTheme(termTheme)
                                    showTerminalThemeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = termTheme.background,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = " >_ ",
                                        color = termTheme.foreground,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = if (Strings.isZh) termTheme.nameZh else termTheme.nameEn,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = ObsidianTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTerminalThemeDialog = false }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 语言选择对话框
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(Strings.sectionLanguage, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = LanguageManager.currentLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) theme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LanguageManager.setLanguage(lang)
                                    showLanguageDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang.titleZh,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = ObsidianTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 在线升级对话框
    AppUpdateDialog(
        state = updateUiState,
        onStartDownload = { info ->
            scope.launch {
                updateUiState = UpdateUiState.Downloading(
                    info = info,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = info.fileSize,
                    speedText = "测速竞选...",
                    channelName = "优选节点中..."
                )
                val result = AppUpdateManager.downloadApk(context, info) { progress, downloaded, total, speed, channel ->
                    updateUiState = UpdateUiState.Downloading(info, progress, downloaded, total, speed, channel)
                }
                result.onSuccess { apkFile ->
                    if (AppUpdateManager.canInstallPackages(context)) {
                        updateUiState = UpdateUiState.ReadyToInstall(apkFile, info)
                        AppUpdateManager.installApk(context, apkFile)
                    } else {
                        updateUiState = UpdateUiState.PermissionRequired(apkFile, info)
                    }
                }.onFailure { err ->
                    updateUiState = UpdateUiState.Error(err.message ?: "下载失败", info)
                }
            }
        },
        onInstall = { file ->
            if (AppUpdateManager.canInstallPackages(context)) {
                AppUpdateManager.installApk(context, file)
            } else {
                val currentInfo = (updateUiState as? UpdateUiState.ReadyToInstall)?.info
                if (currentInfo != null) {
                    updateUiState = UpdateUiState.PermissionRequired(file, currentInfo)
                }
            }
        },
        onIgnore = { tagName ->
            AppUpdateManager.ignoreVersion(context, tagName)
            updateUiState = UpdateUiState.Idle
        },
        onDismiss = {
            updateUiState = UpdateUiState.Idle
        }
    )

    // 添加凭据对话框 (默认用户名 root)
    if (showAddIdentityDialog) {
        AddIdentityDialog(
            onDismiss = { showAddIdentityDialog = false },
            onSave = { identity ->
                scope.launch {
                    db.identityDao().insertIdentity(identity)
                    showAddIdentityDialog = false
                    Toast.makeText(context, if (Strings.isZh) "凭据已保存" else "Saved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // AI 智能运维大模型设置对话框
    if (showAiSettingsDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            onDismiss = { showAiSettingsDialog = false },
            onSaveConfig = { newConfig ->
                aiConfig = newConfig
                aiConfigManager.saveConfig(newConfig)
                Toast.makeText(context, if (Strings.isZh) "AI 配置已保存" else "AI settings saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 保活说明指南弹窗
    if (showBatteryGuideDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = theme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (Strings.isZh) "系统后台保活指引" else "Keepalive Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (Strings.isZh)
                            "Android 系统默认会在手机锁屏休眠后限制网络。若需保持后台长时间不掉线，请设置："
                        else
                            "Android locks out background connections when idle. Please allow unconstrained background execution:",
                        fontSize = 12.sp,
                        color = ObsidianTextSecondary,
                        lineHeight = 17.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("• 小米/澎湃：省电策略选【无限制】，开启【自启动】", fontSize = 11.sp, color = ObsidianTextPrimary)
                            Text("• 华为/荣耀：应用启动管理选【手动管理】，允许后台活动", fontSize = 11.sp, color = ObsidianTextPrimary)
                            Text("• OPPO/vivo：电池管理允许【高耗电】或【不优化】", fontSize = 11.sp, color = ObsidianTextPrimary)
                            Text("• 原生/三星：应用信息 ➜ 电池 ➜ 设为【不受限制】", fontSize = 11.sp, color = ObsidianTextPrimary)
                            Text("• 多任务加锁：多任务界面长按 TermX Mobile 卡片加锁", fontSize = 11.sp, color = theme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        BatteryOptimizationHelper.openAppSettings(context)
                        showBatteryGuideDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = ObsidianOnPrimary)
                ) {
                    Text(if (Strings.isZh) "前往系统设置" else "Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryGuideDialog = false }) {
                    Text(Strings.close, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val theme = LocalAppTheme.current
    Text(
        text = title,
        color = theme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    titleExtra: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ObsidianTextPrimary
                )
                if (titleExtra != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    titleExtra()
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = ObsidianTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(
        color = ObsidianOutlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp
    )
}

@Composable
private fun AddIdentityDialog(
    onDismiss: () -> Unit,
    onSave: (IdentityEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(0) }
    val theme = LocalAppTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (Strings.isZh) "新建凭据" else "New Credential", fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (Strings.isZh) "凭据名称" else "Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(if (Strings.isZh) "用户名 (默认: root)" else "Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TabRow(
                    selectedTabIndex = authMode,
                    containerColor = ObsidianSurfaceContainerLowest,
                    contentColor = theme.primary
                ) {
                    Tab(
                        selected = authMode == 0,
                        onClick = { authMode = 0 },
                        text = { Text(if (Strings.isZh) "密码认证" else "Password", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = authMode == 1,
                        onClick = { authMode = 1 },
                        text = { Text(if (Strings.isZh) "SSH 私钥" else "SSH Key", fontSize = 12.sp) }
                    )
                }

                if (authMode == 0) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (Strings.isZh) "密码" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text(if (Strings.isZh) "私钥内容 (OpenSSH / RSA / Ed25519)" else "Private Key") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(if (Strings.isZh) "密码短语 Passphrase (选填)" else "Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val keyType = if (privateKey.contains("ED25519", ignoreCase = true)) "ED25519"
                        else if (privateKey.contains("RSA", ignoreCase = true)) "RSA"
                        else if (privateKey.isNotBlank()) "OPENSSH" else "NONE"

                        onSave(
                            IdentityEntity(
                                name = name.trim(),
                                username = username.trim().ifBlank { "root" },
                                password = password,
                                privateKey = privateKey.trim(),
                                passphrase = passphrase,
                                keyType = keyType
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = ObsidianOnPrimary)
            ) {
                Text(Strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel, color = ObsidianTextSecondary)
            }
        },
        containerColor = theme.surfaceContainerLow
    )
}
