package com.termius.clone.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.HostEntity
import com.termius.clone.sftp.SftpClientManager
import com.termius.clone.sftp.SftpItem
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(
    initialHost: HostEntity? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val hosts by db.hostDao().getAllHosts().collectAsState(initial = emptyList())
    val identities by db.identityDao().getAllIdentities().collectAsState(initial = emptyList())

    var selectedHost by remember { mutableStateOf(initialHost) }
    var currentPath by remember { mutableStateOf("/") }
    var items by remember { mutableStateOf<List<SftpItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    // 弹窗与交互状态
    var showActionMenuFor by remember { mutableStateOf<SftpItem?>(null) }
    var editingItem by remember { mutableStateOf<SftpItem?>(null) }
    var editingContent by remember { mutableStateOf("") }
    var isSavingEdit by remember { mutableStateOf(false) }

    var renamingItem by remember { mutableStateOf<SftpItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var deletingItem by remember { mutableStateOf<SftpItem?>(null) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var createFileName by remember { mutableStateOf("") }

    var showHeaderMenu by remember { mutableStateOf(false) }

    val sftpManager = remember { SftpClientManager() }

    fun loadDir(path: String) {
        scope.launch {
            isLoading = true
            try {
                val list = sftpManager.listDirectory(path)
                items = list
                currentPath = path
            } catch (e: Exception) {
                Toast.makeText(context, if (Strings.isZh) "加载目录失败: ${e.message}" else "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    val uploadFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                try {
                    val fileName = queryFileName(context, uri) ?: "upload_${System.currentTimeMillis()}"
                    val targetRemotePath = if (currentPath.endsWith("/")) "$currentPath$fileName" else "$currentPath/$fileName"

                    withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException(if (Strings.isZh) "无法读取所选文件" else "Cannot open selected file")
                        val tempFile = File(context.cacheDir, fileName)
                        tempFile.outputStream().use { out ->
                            inputStream.copyTo(out)
                        }
                        try {
                            sftpManager.uploadFile(tempFile, targetRemotePath)
                        } finally {
                            tempFile.delete()
                        }
                    }
                    Toast.makeText(context, if (Strings.isZh) "文件上传成功: $fileName" else "Uploaded: $fileName", Toast.LENGTH_SHORT).show()
                    loadDir(currentPath)
                } catch (e: Exception) {
                    Toast.makeText(context, if (Strings.isZh) "上传失败: ${e.message}" else "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun connectToHost(host: HostEntity) {
        scope.launch {
            isLoading = true
            try {
                val identity = identities.find { it.id == host.identityId }
                sftpManager.connect(host, identity)
                isConnected = true
                selectedHost = host
                loadDir("/")
            } catch (e: Exception) {
                Toast.makeText(context, if (Strings.isZh) "SFTP 连接失败: ${e.message}" else "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                isConnected = false
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(initialHost) {
        if (initialHost != null) {
            connectToHost(initialHost)
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val theme = LocalAppTheme.current

    // 返回键分级拦截：编辑中退出编辑 ➜ 子目录返回上一级 ➜ 根目录断开连接
    BackHandler(enabled = isConnected) {
        if (editingItem != null) {
            keyboardController?.hide()
            editingItem = null
        } else if (currentPath != "/") {
            val parent = File(currentPath).parent ?: "/"
            loadDir(parent.replace("\\", "/"))
        } else {
            scope.launch {
                sftpManager.disconnect()
                isConnected = false
                selectedHost = null
                items = emptyList()
            }
        }
    }

    // 在线文件编辑全屏界面
    if (editingItem != null) {
        val targetItem = editingItem!!
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = theme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = theme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            editingItem = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = Strings.close, tint = ObsidianTextPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = targetItem.name,
                                color = ObsidianTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = targetItem.path,
                                color = ObsidianTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { keyboardController?.hide() }) {
                            Icon(Icons.Default.KeyboardHide, contentDescription = null, tint = ObsidianTextSecondary)
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                scope.launch {
                                    isSavingEdit = true
                                    try {
                                        sftpManager.writeTextFile(targetItem.path, editingContent)
                                        Toast.makeText(context, Strings.saveSuccess, Toast.LENGTH_SHORT).show()
                                        editingItem = null
                                        loadDir(currentPath)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, if (Strings.isZh) "保存失败: ${e.message}" else "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSavingEdit = false
                                    }
                                }
                            },
                            enabled = !isSavingEdit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = if (theme.isDark) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (isSavingEdit) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = if (theme.isDark) Color.Black else Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(Strings.save, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = ObsidianTextPrimary,
                        unfocusedTextColor = ObsidianTextPrimary,
                        cursorColor = theme.primary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = theme.surfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isConnected) {
                            IconButton(onClick = {
                                scope.launch {
                                    sftpManager.disconnect()
                                    isConnected = false
                                    selectedHost = null
                                    items = emptyList()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.close, tint = ObsidianTextPrimary)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (isConnected) 0.dp else 12.dp)
                        ) {
                            Text(
                                text = if (isConnected) (selectedHost?.label ?: "SFTP") else Strings.sftpTitle,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary,
                                fontSize = 17.sp
                            )
                            if (isConnected) {
                                Text(
                                    text = "${selectedHost?.username}@${selectedHost?.hostname}:${selectedHost?.port}",
                                    fontSize = 11.sp,
                                    color = ObsidianTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isConnected) {
                            // 新建/上传下拉菜单
                            Box {
                                IconButton(onClick = { showHeaderMenu = true }) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Actions", tint = theme.primary)
                                }
                                DropdownMenu(
                                    expanded = showHeaderMenu,
                                    onDismissRequest = { showHeaderMenu = false },
                                    modifier = Modifier.background(theme.surfaceContainerHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Strings.uploadFile, color = ObsidianTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = theme.primary) },
                                        onClick = {
                                            showHeaderMenu = false
                                            uploadFileLauncher.launch("*/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Strings.createFolder, color = ObsidianTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ObsidianSecondary) },
                                        onClick = {
                                            showHeaderMenu = false
                                            createFolderName = ""
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Strings.createFile, color = ObsidianTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = ObsidianTextPrimary) },
                                        onClick = {
                                            showHeaderMenu = false
                                            createFileName = ""
                                            showCreateFileDialog = true
                                        }
                                    )
                                }
                            }

                            // 刷新
                            IconButton(onClick = { loadDir(currentPath) }) {
                                Icon(Icons.Default.Refresh, contentDescription = Strings.refresh, tint = ObsidianTextPrimary)
                            }

                            // 断开连接
                            IconButton(onClick = {
                                scope.launch {
                                    sftpManager.disconnect()
                                    isConnected = false
                                    selectedHost = null
                                    items = emptyList()
                                }
                            }) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = Strings.disconnect, tint = ObsidianError)
                            }
                        }
                    }

                    // 分段式面包屑导航栏
                    if (isConnected) {
                        Surface(
                            color = theme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 根目录 /
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (currentPath == "/") theme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.clickable {
                                        if (currentPath != "/") loadDir("/")
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (currentPath == "/") theme.primary else ObsidianSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "/",
                                            fontSize = 12.sp,
                                            fontWeight = if (currentPath == "/") FontWeight.Bold else FontWeight.Medium,
                                            color = if (currentPath == "/") theme.primary else ObsidianTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                // 用户家目录快捷直达 (~)
                                val userHome = if (selectedHost?.username == "root") "/root" else "/home/${selectedHost?.username ?: "user"}"
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (currentPath == userHome) theme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.clickable {
                                        if (currentPath != userHome) loadDir(userHome)
                                    }
                                ) {
                                    Text(
                                        text = "~",
                                        fontSize = 13.sp,
                                        fontWeight = if (currentPath == userHome) FontWeight.Bold else FontWeight.Medium,
                                        color = if (currentPath == userHome) theme.primary else ObsidianTextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }

                                // 逐级目录分段
                                val segments = currentPath.split("/").filter { it.isNotEmpty() }
                                segments.forEachIndexed { index, seg ->
                                    Text(
                                        text = "/",
                                        color = ObsidianTextMuted.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 1.dp)
                                    )

                                    val isLast = index == segments.size - 1
                                    val targetPath = "/" + segments.take(index + 1).joinToString("/")

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isLast) theme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                        modifier = Modifier.clickable {
                                            if (!isLast) loadDir(targetPath)
                                        }
                                    ) {
                                        Text(
                                            text = seg,
                                            fontSize = 12.sp,
                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isLast) theme.primary else ObsidianTextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isConnected) {
                // 主机选择列表 (扁平对标 ConnectBot)
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = Strings.selectHostPrompt,
                        color = theme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
                    )

                    if (hosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(Strings.noHostsTitle, color = ObsidianTextMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(hosts, key = { it.id }) { host ->
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { connectToHost(host) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(theme.primary.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, theme.primary.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(host.label.ifBlank { host.hostname }, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary, fontSize = 15.sp)
                                            Text("sftp://${host.username}@${host.hostname}:${host.port}", color = ObsidianTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ObsidianTextMuted)
                                    }
                                }
                                HorizontalDivider(color = ObsidianOutlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            } else {
                // 远程文件列表
                Column(modifier = Modifier.fillMaxSize()) {
                    // 返回上级目录条目
                    if (currentPath != "/") {
                        Surface(
                            color = theme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parent = File(currentPath).parent ?: "/"
                                    loadDir(parent.replace("\\", "/"))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = ObsidianSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(Strings.parentDirectory, color = ObsidianSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = ObsidianOutlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                    }

                    if (items.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(Strings.emptyDirectory, color = ObsidianTextMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(items, key = { it.path }) { item ->
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (item.isDirectory) {
                                                    val next = if (currentPath.endsWith("/")) "$currentPath${item.name}" else "$currentPath/${item.name}"
                                                    loadDir(next)
                                                } else {
                                                    scope.launch {
                                                        isLoading = true
                                                        try {
                                                            editingContent = sftpManager.readTextFile(item.path)
                                                            editingItem = item
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, if (Strings.isZh) "无法读取该文件: ${e.message}" else "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                showActionMenuFor = item
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                            contentDescription = null,
                                            tint = if (item.isDirectory) ObsidianSecondary else ObsidianTextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                color = ObsidianTextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (item.isDirectory) {
                                                    "${item.permissions} • ${formatDate(item.mtime)}"
                                                } else {
                                                    "${formatSize(item.size)} • ${item.permissions} • ${formatDate(item.mtime)}"
                                                },
                                                color = ObsidianTextSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(onClick = { showActionMenuFor = item }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = ObsidianTextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = ObsidianOutlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = theme.primary)
                }
            }
        }
    }

    // 单项文件操作弹窗 (对标 ConnectBot 菜单样式)
    showActionMenuFor?.let { item ->
        AlertDialog(
            onDismissRequest = { showActionMenuFor = null },
            title = {
                Text(
                    text = item.name,
                    color = ObsidianTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!item.isDirectory) {
                        ListItem(
                            headlineContent = { Text(Strings.viewEditFile, color = ObsidianTextPrimary) },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = theme.primary) },
                            modifier = Modifier.clickable {
                                showActionMenuFor = null
                                scope.launch {
                                    isLoading = true
                                    try {
                                        editingContent = sftpManager.readTextFile(item.path)
                                        editingItem = item
                                    } catch (e: Exception) {
                                        Toast.makeText(context, if (Strings.isZh) "无法读取文件: ${e.message}" else "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        )

                        ListItem(
                            headlineContent = { Text(Strings.downloadToPhone, color = ObsidianTextPrimary) },
                            leadingContent = { Icon(Icons.Default.Download, contentDescription = null, tint = ObsidianSecondary) },
                            modifier = Modifier.clickable {
                                showActionMenuFor = null
                                scope.launch {
                                    isLoading = true
                                    try {
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                            ?: context.filesDir
                                        val localFile = File(downloadsDir, item.name)
                                        sftpManager.downloadFile(item.path, localFile)
                                        Toast.makeText(context, "${localFile.name}\n${Strings.downloadSuccess}", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, if (Strings.isZh) "下载失败: ${e.message}" else "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text(Strings.rename, color = ObsidianTextPrimary) },
                        leadingContent = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = ObsidianTertiary) },
                        modifier = Modifier.clickable {
                            showActionMenuFor = null
                            renamingItem = item
                            renameInput = item.name
                        }
                    )

                    ListItem(
                        headlineContent = { Text(Strings.copyRemotePath, color = ObsidianTextPrimary) },
                        leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianTextPrimary) },
                        modifier = Modifier.clickable {
                            showActionMenuFor = null
                            clipboardManager.setText(AnnotatedString(item.path))
                            Toast.makeText(context, if (Strings.isZh) "已复制路径: ${item.path}" else "Path copied: ${item.path}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    ListItem(
                        headlineContent = { Text(Strings.deleteItem, color = ObsidianError) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = ObsidianError) },
                        modifier = Modifier.clickable {
                            showActionMenuFor = null
                            deletingItem = item
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showActionMenuFor = null }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 重命名对话框
    renamingItem?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text(Strings.rename, color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (Strings.isZh) "输入新名称:" else "New name:", color = ObsidianTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isNotEmpty() && newName != targetItem.name) {
                            scope.launch {
                                isLoading = true
                                try {
                                    val parentDir = File(targetItem.path).parent ?: "/"
                                    val newRemotePath = if (parentDir.endsWith("/")) "$parentDir$newName" else "$parentDir/$newName"
                                    sftpManager.rename(targetItem.path, newRemotePath)
                                    Toast.makeText(context, if (Strings.isZh) "重命名成功" else "Renamed", Toast.LENGTH_SHORT).show()
                                    renamingItem = null
                                    loadDir(currentPath)
                                } catch (e: Exception) {
                                    Toast.makeText(context, if (Strings.isZh) "重命名失败: ${e.message}" else "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            renamingItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = if (theme.isDark) Color.Black else Color.White
                    )
                ) {
                    Text(Strings.confirm, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 删除确认对话框
    deletingItem?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(if (Strings.isZh) "确认删除" else "Confirm Delete", color = ObsidianError, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (Strings.isZh) "确定要永久删除 ${if (targetItem.isDirectory) "文件夹" else "文件"} \"${targetItem.name}\" 吗？此操作无法撤销。"
                    else "Delete ${if (targetItem.isDirectory) "folder" else "file"} \"${targetItem.name}\"? This cannot be undone.",
                    color = ObsidianTextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                if (targetItem.isDirectory) {
                                    sftpManager.deleteDirectory(targetItem.path)
                                } else {
                                    sftpManager.deleteFile(targetItem.path)
                                }
                                Toast.makeText(context, if (Strings.isZh) "已删除: ${targetItem.name}" else "Deleted: ${targetItem.name}", Toast.LENGTH_SHORT).show()
                                deletingItem = null
                                loadDir(currentPath)
                            } catch (e: Exception) {
                                Toast.makeText(context, if (Strings.isZh) "删除失败: ${e.message}" else "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianError, contentColor = Color.White)
                ) {
                    Text(Strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(Strings.createFolder, color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (Strings.isZh) "文件夹名称:" else "Folder name:", color = ObsidianTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = createFolderName,
                        onValueChange = { createFolderName = it },
                        singleLine = true,
                        placeholder = { Text(if (Strings.isZh) "文件夹名称" else "Folder name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = createFolderName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                isLoading = true
                                try {
                                    val newPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                                    sftpManager.createDirectory(newPath)
                                    Toast.makeText(context, if (Strings.isZh) "文件夹创建成功" else "Folder created", Toast.LENGTH_SHORT).show()
                                    showCreateFolderDialog = false
                                    loadDir(currentPath)
                                } catch (e: Exception) {
                                    Toast.makeText(context, if (Strings.isZh) "创建文件夹失败: ${e.message}" else "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = if (theme.isDark) Color.Black else Color.White
                    )
                ) {
                    Text(Strings.create, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }

    // 新建空白文件对话框
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(Strings.createFile, color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (Strings.isZh) "文件名称:" else "File name:", color = ObsidianTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = createFileName,
                        onValueChange = { createFileName = it },
                        singleLine = true,
                        placeholder = { Text("如 script.sh 或 config.json") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = createFileName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                isLoading = true
                                try {
                                    val newPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                                    sftpManager.createEmptyFile(newPath)
                                    Toast.makeText(context, if (Strings.isZh) "文件创建成功" else "File created", Toast.LENGTH_SHORT).show()
                                    showCreateFileDialog = false
                                    loadDir(currentPath)
                                } catch (e: Exception) {
                                    Toast.makeText(context, if (Strings.isZh) "创建文件失败: ${e.message}" else "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = if (theme.isDark) Color.Black else Color.White
                    )
                ) {
                    Text(Strings.create, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text(Strings.cancel, color = ObsidianTextSecondary)
                }
            },
            containerColor = theme.surfaceContainerLow
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
    return String.format(Locale.getDefault(), "%.1f %cB", bytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
