package com.termius.clone.ui.screens

import android.widget.Toast
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.termius.clone.util.LanguageManager
import com.termius.clone.util.Strings
import com.termius.clone.util.UpdateCheckResult
import kotlinx.coroutines.launch

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

    val theme = LocalAppTheme.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部沉浸式标题栏 (紧凑无缝贴顶)
            Surface(color = theme.surfaceContainerLow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(theme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Strings.settingsTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ObsidianTextPrimary
                        )
                        Text(
                            text = if (LanguageManager.currentLanguage == AppLanguage.ZH) "偏好、主题、保活与安全凭据" else "Preferences, themes, keepalive & credentials",
                            fontSize = 11.sp,
                            color = ObsidianTextSecondary
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 凭据与钥匙串 (Vault) 区块
                item {
                    SettingsCard(title = Strings.sectionVault, icon = Icons.Default.VpnKey) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${identities.size} ${Strings.identitiesCount}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ObsidianTextPrimary
                                    )
                                    Text(
                                        text = Strings.sectionVaultDesc,
                                        fontSize = 11.sp,
                                        color = ObsidianTextMuted
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { isVaultExpanded = !isVaultExpanded },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isVaultExpanded) "收起" else "查看",
                                            color = theme.primary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { showAddIdentityDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = theme.primary.copy(alpha = 0.2f),
                                            contentColor = theme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("新建", fontSize = 12.sp)
                                    }
                                }
                            }

                            // 展开的凭据列表
                            AnimatedVisibility(visible = isVaultExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (identities.isEmpty()) {
                                        Text(
                                            text = "暂无保存的凭据，点击右上角「新建」添加",
                                            fontSize = 12.sp,
                                            color = ObsidianTextMuted,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        identities.forEach { identity ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = ObsidianSurfaceContainerLowest,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
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
                                                            text = "用户: ${identity.username} • ${if (identity.privateKey.isNotEmpty()) "SSH Key (${identity.keyType})" else "密码认证"}",
                                                            fontSize = 10.sp,
                                                            color = ObsidianTextSecondary,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch { db.identityDao().deleteIdentity(identity) }
                                                        },
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
                        }
                    }
                }

                // 2. 外观与主题配色区块
                item {
                    SettingsCard(title = Strings.sectionAppearance, icon = Icons.Default.Palette) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "选择终端极客主题配色 (即时生效)",
                                fontSize = 11.sp,
                                color = ObsidianTextMuted,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppTheme.entries.forEach { appTheme ->
                                    val isSelected = theme == appTheme
                                    val title = if (LanguageManager.currentLanguage == AppLanguage.ZH) appTheme.titleZh else appTheme.titleEn

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) appTheme.primary.copy(alpha = 0.15f) else theme.surfaceContainerHigh,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) appTheme.primary else ObsidianOutlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { ThemeManager.setTheme(appTheme) }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // 主题颜色小圆圈
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(appTheme.primary, CircleShape)
                                                    .border(2.dp, ObsidianSurfaceContainerLowest, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) appTheme.primary else ObsidianTextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = ObsidianOutlineVariant, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // 终端字体大小调节
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Strings.terminalFontSizeTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ObsidianTextPrimary
                                    )
                                    Text(
                                        text = Strings.terminalFontSizeDesc,
                                        fontSize = 11.sp,
                                        color = ObsidianTextMuted
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = theme.primary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${ThemeManager.terminalFontSizeSp.toInt()} SP",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 字体快捷档位 Chips 与 +/- 微调
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { ThemeManager.setTerminalFontSize(ThemeManager.terminalFontSizeSp - 1f) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = theme.surfaceContainerHigh,
                                        contentColor = ObsidianTextPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                }

                                val fontPresets = listOf(11f, 13f, 15f, 17f, 20f)
                                fontPresets.forEach { size ->
                                    val isSelected = abs(ThemeManager.terminalFontSizeSp - size) < 0.4f
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surfaceContainerHigh,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                        onClick = { ThemeManager.setTerminalFontSize(size) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${size.toInt()} SP",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) theme.primary else ObsidianTextSecondary
                                            )
                                        }
                                    }
                                }

                                FilledTonalIconButton(
                                    onClick = { ThemeManager.setTerminalFontSize(ThemeManager.terminalFontSizeSp + 1f) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = theme.surfaceContainerHigh,
                                        contentColor = ObsidianTextPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 实时效果预览盒
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "${Strings.fontPreviewLabel}:",
                                        fontSize = 10.sp,
                                        color = ObsidianTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "root@teamx:~# docker ps\nweb-proxy   Up 18h   0.0.0.0:443",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = ThemeManager.terminalFontSizeSp.sp,
                                        lineHeight = (ThemeManager.terminalFontSizeSp * 1.35f).sp,
                                        color = theme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 语言切换区块 (解决卡片大小不对称与挤压折行问题)
                item {
                    SettingsCard(title = Strings.sectionLanguage, icon = Icons.Default.Language) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                val isSelected = LanguageManager.currentLanguage == lang
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.surfaceContainerHigh,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) theme.primary else ObsidianOutlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { LanguageManager.setLanguage(lang) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = lang.titleZh,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = ObsidianTextPrimary
                                            )
                                            Text(
                                                text = lang.titleEn,
                                                fontSize = 11.sp,
                                                color = ObsidianTextMuted
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { LanguageManager.setLanguage(lang) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = theme.primary,
                                                unselectedColor = ObsidianTextMuted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. 连接与保活设置区块
                item {
                    SettingsCard(title = Strings.sectionKeepalive, icon = Icons.Default.Bolt) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 保活心跳开关
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(Strings.keepalivePing, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ObsidianTextPrimary)
                                    Text(Strings.keepalivePingDesc, fontSize = 11.sp, color = ObsidianTextMuted)
                                }
                                Switch(
                                    checked = sshKeepaliveEnabled,
                                    onCheckedChange = { sshKeepaliveEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = theme.primary,
                                        checkedTrackColor = theme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            HorizontalDivider(color = ObsidianOutlineVariant, thickness = 0.5.dp)

                            // 后台常驻服务状态
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(theme.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(Strings.wakeLockStatus, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ObsidianTextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(Strings.wakeLockStatusDesc, fontSize = 11.sp, color = ObsidianTextMuted)
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = theme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "常驻保活中",
                                        fontSize = 11.sp,
                                        color = theme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. 关于与更新区块 (严格包含 Powered by codywon 与 TeamX Mobile)
                item {
                    SettingsCard(title = Strings.sectionAbout, icon = Icons.Default.Info) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(theme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .border(1.dp, theme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = theme.primary, modifier = Modifier.size(26.dp))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "TeamX Mobile",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Powered by codywon",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.primary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val versionName = remember { AppUpdateManager.getCurrentVersionName(context) }
                            Text(
                                text = "${Strings.currentVersionLabel}: v$versionName",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    if (isCheckingUpdate) return@Button
                                    isCheckingUpdate = true
                                    scope.launch {
                                        Toast.makeText(context, "正在测速检测新版本...", Toast.LENGTH_SHORT).show()
                                        when (val result = AppUpdateManager.checkUpdate(context, isManual = true)) {
                                            is UpdateCheckResult.HasUpdate -> {
                                                updateUiState = UpdateUiState.HasUpdate(result.info)
                                            }
                                            is UpdateCheckResult.NoUpdate -> {
                                                Toast.makeText(context, "已是最新版本 (v${result.currentVersion})", Toast.LENGTH_SHORT).show()
                                            }
                                            is UpdateCheckResult.Error -> {
                                                Toast.makeText(context, "检查更新失败: ${result.message}", Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {}
                                        }
                                        isCheckingUpdate = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = ObsidianOnPrimary)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isCheckingUpdate) "检测中..." else Strings.checkUpdateBtn, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // 在线自动升级对话框 (支持多镜像并发测速与最优路径流式下载)
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

    // 添加凭据对话框 (默认用户名设为 root)
    if (showAddIdentityDialog) {
        AddIdentityDialog(
            onDismiss = { showAddIdentityDialog = false },
            onSave = { identity ->
                scope.launch {
                    db.identityDao().insertIdentity(identity)
                    showAddIdentityDialog = false
                    Toast.makeText(context, "凭据已安全保存", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ObsidianTextPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun AddIdentityDialog(
    onDismiss: () -> Unit,
    onSave: (IdentityEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("root") } // 默认用户名 root
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(0) } // 0: Password, 1: Key
    val theme = LocalAppTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建身份与密钥凭据", fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("凭据标签 (如: 生产集群 Root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名 (默认: root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 认证方式 Tab
                TabRow(
                    selectedTabIndex = authMode,
                    containerColor = ObsidianSurfaceContainerLowest,
                    contentColor = theme.primary
                ) {
                    Tab(
                        selected = authMode == 0,
                        onClick = { authMode = 0 },
                        text = { Text("密码认证", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = authMode == 1,
                        onClick = { authMode = 1 },
                        text = { Text("SSH 私钥", fontSize = 12.sp) }
                    )
                }

                if (authMode == 0) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("SSH 密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("私钥内容 (OpenSSH / RSA / Ed25519)") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("密钥密码短语 Passphrase (选填)") },
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
                Text("保存凭据")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ObsidianTextSecondary)
            }
        },
        containerColor = theme.surfaceContainerLow
    )
}
