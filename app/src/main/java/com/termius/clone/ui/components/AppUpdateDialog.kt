package com.termius.clone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termius.clone.ui.theme.*
import com.termius.clone.util.AppUpdateManager
import com.termius.clone.util.UpdateInfo
import java.io.File
import java.util.Locale

/**
 * 更新 UI 状态密封类
 */
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class HasUpdate(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedText: String,
        val channelName: String
    ) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File, val info: UpdateInfo) : UpdateUiState()
    data class PermissionRequired(val apkFile: File, val info: UpdateInfo) : UpdateUiState()
    data class Error(val message: String, val info: UpdateInfo?) : UpdateUiState()
}

/**
 * 生产级现代化全自动在线更新对话框 (黑曜石极客风)
 */
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onIgnore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) {
        return
    }

    val context = LocalContext.current
    val currentVersion = AppUpdateManager.getCurrentVersionName(context)

    Dialog(
        onDismissRequest = {
            if (state !is UpdateUiState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateUiState.Downloading,
            dismissOnClickOutside = state !is UpdateUiState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, ObsidianOutlineVariant), RoundedCornerShape(20.dp)),
            color = ObsidianSurfaceContainerLow,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header: 图标与标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ObsidianPrimary.copy(alpha = 0.15f))
                            .border(1.dp, ObsidianPrimary.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (state) {
                                is UpdateUiState.Downloading -> Icons.Default.Download
                                is UpdateUiState.ReadyToInstall -> Icons.Default.SystemUpdate
                                is UpdateUiState.PermissionRequired -> Icons.Default.Security
                                is UpdateUiState.Error -> Icons.Default.ErrorOutline
                                else -> Icons.Default.RocketLaunch
                            },
                            contentDescription = null,
                            tint = ObsidianPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state) {
                                is UpdateUiState.Downloading -> "正在高速下载更新"
                                is UpdateUiState.ReadyToInstall -> "新版本已就绪"
                                is UpdateUiState.PermissionRequired -> "需要安装应用授权"
                                is UpdateUiState.Error -> "更新遇到问题"
                                else -> "发现新版本"
                            },
                            fontWeight = FontWeight.Bold,
                            color = ObsidianTextPrimary,
                            fontSize = 17.sp
                        )

                        val updateInfo = when (state) {
                            is UpdateUiState.HasUpdate -> state.info
                            is UpdateUiState.Downloading -> state.info
                            is UpdateUiState.ReadyToInstall -> state.info
                            is UpdateUiState.PermissionRequired -> state.info
                            is UpdateUiState.Error -> state.info
                            else -> null
                        }

                        if (updateInfo != null) {
                            Text(
                                text = "v$currentVersion ➔ v${updateInfo.versionName}",
                                fontSize = 12.sp,
                                color = ObsidianSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Body 内容区
                when (state) {
                    is UpdateUiState.HasUpdate -> {
                        // 版本日志卡片
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "发布日期: ${state.info.publishedAt.ifBlank { "最近" }}",
                                        fontSize = 11.sp,
                                        color = ObsidianTextMuted
                                    )
                                    if (state.info.fileSize > 0) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f MB", state.info.fileSize / (1024.0 * 1024.0)),
                                            fontSize = 11.sp,
                                            color = ObsidianTextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = ObsidianOutlineVariant, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = state.info.releaseNotes,
                                    fontSize = 13.sp,
                                    color = ObsidianTextPrimary,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!state.info.isManual) {
                                OutlinedButton(
                                    onClick = { onIgnore(state.info.tagName) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextMuted),
                                    border = BorderStroke(1.dp, ObsidianOutlineVariant)
                                ) {
                                    Text("跳过此版", fontSize = 13.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextMuted),
                                    border = BorderStroke(1.dp, ObsidianOutlineVariant)
                                ) {
                                    Text("稍后提醒", fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = { onStartDownload(state.info) },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即升级", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 优选测速通道展示
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(ObsidianPrimary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = state.channelName,
                                            fontSize = 11.sp,
                                            color = ObsidianSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 瞬时速率
                                    Text(
                                        text = state.speedText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 下载进度条
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = ObsidianPrimary,
                                    trackColor = ObsidianSurfaceContainerHighest
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val downloadedMb = state.downloadedBytes / (1024.0 * 1024.0)
                                    val totalMb = state.totalBytes / (1024.0 * 1024.0)
                                    Text(
                                        text = String.format(Locale.US, "%.1fMB / %.1fMB", downloadedMb, totalMb),
                                        fontSize = 11.sp,
                                        color = ObsidianTextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "已启用智能镜像测速与低速熔断保护，请稍候...",
                            fontSize = 11.sp,
                            color = ObsidianTextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("APK 安装包已下载校验完毕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                    Text("点击下方按钮立即唤起系统进行覆盖安装", fontSize = 11.sp, color = ObsidianTextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { onInstall(state.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即安装更新", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    is UpdateUiState.PermissionRequired -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("需要开启“允许安装未知应用”权限", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Android 安全机制要求对当前应用授予安装 APK 权限，开启后即可直接安装。", fontSize = 11.sp, color = ObsidianTextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val intent = AppUpdateManager.createInstallPermissionIntent(context)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("前往系统设置授权", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    is UpdateUiState.Error -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = BorderStroke(1.dp, ObsidianOutlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ObsidianError, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(state.message, fontSize = 12.sp, color = ObsidianTextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextMuted),
                                border = BorderStroke(1.dp, ObsidianOutlineVariant)
                            ) {
                                Text("关闭", fontSize = 13.sp)
                            }

                            if (state.info != null) {
                                Button(
                                    onClick = { AppUpdateManager.openInBrowser(context, state.info.downloadUrl) },
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceContainerHigh, contentColor = ObsidianTextPrimary)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("浏览器下载", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
