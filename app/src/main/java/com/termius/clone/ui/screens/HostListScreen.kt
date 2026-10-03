package com.termius.clone.ui.screens

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
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    var selectedTag by remember { mutableStateOf("All") }
    var hostToEdit by remember { mutableStateOf<HostEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    var updateUiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    // 启动时静默检查更新 (参考 mqtt-assistant-app)
    LaunchedEffect(Unit) {
        val result = AppUpdateManager.checkUpdate(context, isManual = false)
        if (result is UpdateCheckResult.HasUpdate) {
            updateUiState = UpdateUiState.HasUpdate(result.info)
        }
    }

    val filterTags = listOf("All", "Production", "Staging", "Docker", "AWS", "K8s")

    val filteredHosts = remember(hosts, searchQuery, selectedTag) {
        hosts.filter { host ->
            val matchQuery = searchQuery.isBlank() ||
                host.label.contains(searchQuery, ignoreCase = true) ||
                host.hostname.contains(searchQuery, ignoreCase = true) ||
                host.groupName.contains(searchQuery, ignoreCase = true)

            val matchTag = if (selectedTag == "All") true
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
                    // Header Bar (TeamX Mobile Branding & Sync status)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Logo
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(theme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, theme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Strings.appTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = ObsidianTextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(theme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = Strings.synced,
                                    fontSize = 11.sp,
                                    color = ObsidianTextSecondary
                                )
                            }
                        }

                        // Check for updates action button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
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
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = Strings.checkUpdates,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        placeholder = { Text(Strings.searchHostsPlaceholder, color = ObsidianTextMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceContainer,
                            unfocusedContainerColor = theme.surfaceContainer,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = ObsidianOutlineVariant
                        )
                    )

                    // Tag Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterTags.forEach { tag ->
                            val count = if (tag == "All") hosts.size else hosts.count { it.groupName.equals(tag, ignoreCase = true) }
                            val isSelected = selectedTag == tag

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surfaceContainer,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                onClick = { selectedTag = tag }
                            ) {
                                Text(
                                    text = "$tag ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primary else ObsidianTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    hostToEdit = null
                    showEditDialog = true
                },
                containerColor = theme.primary,
                contentColor = ObsidianOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = Strings.addHost, tint = ObsidianOnPrimary)
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
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(Strings.noHostsTitle, color = ObsidianTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Strings.noHostsSubtitle, color = ObsidianTextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredHosts, key = { it.id }) { host ->
                        TeamXHostCard(
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
                                    android.widget.Toast.makeText(
                                        context,
                                        "启动连接失败: ${e.localizedMessage ?: e.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onEdit = {
                                hostToEdit = host
                                showEditDialog = true
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeamXHostCard(
    host: HostEntity,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
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

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onConnect,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon with Color accent
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(colorTag.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, colorTag.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = colorTag,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = host.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ObsidianTextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (host.groupName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        // Tag chip
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.surfaceContainerHigh
                        ) {
                            Text(
                                text = host.groupName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = ObsidianTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${host.username}@${host.hostname}:${host.port}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ObsidianTextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Quick Connect Button
            Surface(
                shape = CircleShape,
                color = theme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                onClick = onConnect,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = Strings.connect,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // More Options Dropdown
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = ObsidianTextMuted)
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
}
