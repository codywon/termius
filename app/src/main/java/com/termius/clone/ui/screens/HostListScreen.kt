package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.HostEntity
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.ui.components.AppUpdateDialog
import com.termius.clone.ui.components.UpdateUiState
import com.termius.clone.ui.theme.*
import com.termius.clone.util.AppUpdateManager
import com.termius.clone.util.Strings
import com.termius.clone.util.UpdateCheckResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNavigateToTerminal: () -> Unit,
    onNavigateToSftpForHost: (HostEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val hosts by db.hostDao().getAllHosts().collectAsState(initial = emptyList())
    val identities by db.identityDao().getAllIdentities().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("全部") }
    var hostToEdit by remember { mutableStateOf<HostEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    // 启动时静默检查更新
    LaunchedEffect(Unit) {
        val result = AppUpdateManager.checkUpdate(context, isManual = false)
        if (result is UpdateCheckResult.HasUpdate) {
            updateUiState = UpdateUiState.HasUpdate(result.info)
        }
    }

    // 动态提取用户实际存在的标签
    val existingTags = remember(hosts) {
        val tags = hosts.map { it.groupName.trim() }.filter { it.isNotEmpty() }.distinct()
        if (tags.isEmpty()) emptyList() else listOf("全部") + tags
    }

    val filteredHosts = remember(hosts, searchQuery, selectedTag) {
        hosts.filter { host ->
            val matchQuery = searchQuery.isBlank() ||
                host.label.contains(searchQuery, ignoreCase = true) ||
                host.hostname.contains(searchQuery, ignoreCase = true) ||
                host.groupName.contains(searchQuery, ignoreCase = true)

            val matchTag = if (selectedTag == "全部" || selectedTag == "All") true
            else host.groupName.equals(selectedTag, ignoreCase = true)

            matchQuery && matchTag
        }
    }

    val theme = LocalAppTheme.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = theme.surfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header Bar (对标 ConnectBot 图 1：极简沉稳)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Strings.appTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ObsidianTextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        // 检查更新按钮
                        IconButton(
                            onClick = {
                                scope.launch {
                                    Toast.makeText(context, if (Strings.isZh) "正在检查更新..." else "Checking...", Toast.LENGTH_SHORT).show()
                                    when (val result = AppUpdateManager.checkUpdate(context, isManual = true)) {
                                        is UpdateCheckResult.HasUpdate -> {
                                            updateUiState = UpdateUiState.HasUpdate(result.info)
                                        }
                                        is UpdateCheckResult.NoUpdate -> {
                                            Toast.makeText(context, if (Strings.isZh) "已是最新版本 (v${result.currentVersion})" else "Up to date (v${result.currentVersion})", Toast.LENGTH_SHORT).show()
                                        }
                                        is UpdateCheckResult.Error -> {
                                            Toast.makeText(context, if (Strings.isZh) "检查更新失败: ${result.message}" else "Failed: ${result.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = Strings.checkUpdates,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 搜索输入框 (紧凑极简)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text(Strings.searchHostsPlaceholder, color = ObsidianTextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceContainer,
                            unfocusedContainerColor = theme.surfaceContainer,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = ObsidianOutlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    // 标签横向过滤栏 (仅当用户设置了标签时显示，避免堆砌虚假分类)
                    if (existingTags.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingTags.forEach { tag ->
                                val isSelected = selectedTag == tag
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) theme.primary.copy(alpha = 0.18f) else theme.surfaceContainer,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                    onClick = { selectedTag = tag }
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) theme.primary else ObsidianTextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            // 对标 ConnectBot 图 1 悬浮加号按钮
            FloatingActionButton(
                onClick = {
                    hostToEdit = null
                    showEditDialog = true
                },
                containerColor = theme.primary,
                contentColor = if (theme.isDark) Color.Black else Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = Strings.addHost, modifier = Modifier.size(24.dp))
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredHosts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = ObsidianTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(Strings.noHostsTitle, color = ObsidianTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Strings.noHostsSubtitle, color = ObsidianTextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredHosts, key = { it.id }) { host ->
                        ConnectBotHostItem(
                            host = host,
                            onConnect = {
                                try {
                                    val identity = identities.find { it.id == host.identityId }
                                    SessionManager.openSession(context, host, identity)
                                    scope.launch {
                                        try {
                                            db.hostDao().updateLastConnected(host.id, System.currentTimeMillis())
                                        } catch (e: Throwable) {
                                            e.printStackTrace()
                                        }
                                    }
                                    onNavigateToTerminal()
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        context,
                                        "连接失败: ${e.localizedMessage ?: e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onEdit = {
                                hostToEdit = host
                                showEditDialog = true
                            },
                            onDuplicate = {
                                scope.launch {
                                    val copyHost = host.copy(
                                        id = 0L,
                                        label = "${host.label} (副本)",
                                        lastConnected = 0L
                                    )
                                    db.hostDao().insertHost(copyHost)
                                    Toast.makeText(context, if (Strings.isZh) "已复制主机" else "Host duplicated", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                scope.launch { db.hostDao().deleteHost(host) }
                            },
                            onOpenSftp = {
                                onNavigateToSftpForHost(host)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditHostDialog(
            hostToEdit = hostToEdit,
            identities = identities,
            onDismiss = { showEditDialog = false },
            onSave = { savedHost ->
                scope.launch {
                    if (savedHost.id == 0L) {
                        db.hostDao().insertHost(savedHost)
                    } else {
                        db.hostDao().updateHost(savedHost)
                    }
                    showEditDialog = false
                }
            }
        )
    }

    // 在线自动升级对话框
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
}

/**
 * 完全对标 ConnectBot 图 1 的原生扁平主机列表项：
 * - 左侧：圆形设备图标
 * - 中间：主机别名加粗 + ssh://user@host:port 连接地址
 * - 右侧：三点操作菜单
 * - 底部分割线
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectBotHostItem(
    host: HostEntity,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOpenSftp: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val colorTag = try {
        Color(android.graphics.Color.parseColor(host.colorTag))
    } catch (e: Exception) {
        theme.primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onConnect,
                    onLongClick = { showMenu = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧圆形图标 (参考 ConnectBot 图 1)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colorTag.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, colorTag.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Laptop,
                        contentDescription = null,
                        tint = colorTag,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = host.label.ifBlank { host.hostname },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ObsidianTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (host.groupName.isNotBlank() && host.groupName != "全部") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = host.groupName,
                                    fontSize = 10.sp,
                                    color = ObsidianTextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "ssh://${host.username}@${host.hostname}:${host.port}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ObsidianTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 右侧：三点操作菜单 (对标 ConnectBot 图 1)
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = ObsidianTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(theme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text(Strings.connect, color = ObsidianTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = theme.primary) },
                            onClick = {
                                showMenu = false
                                onConnect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SFTP", color = ObsidianTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ObsidianSecondary) },
                            onClick = {
                                showMenu = false
                                onOpenSftp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.edit, color = ObsidianTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ObsidianTextSecondary) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.copy, color = ObsidianTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianTextSecondary) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.delete, color = ObsidianError) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ObsidianError) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = ObsidianOutlineVariant.copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
    }
}
