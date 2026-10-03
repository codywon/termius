package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.SnippetEntity
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings
import kotlinx.coroutines.launch

data class BuiltInCommand(
    val title: String,
    val category: String,
    val command: String,
    val description: String
)

private val BUILTIN_COMMANDS = listOf(
    // 系统资源
    BuiltInCommand("实时系统监控 (htop)", "系统资源", "htop", "高交互式进程与 CPU/内存实时资源监控"),
    BuiltInCommand("内存占用概览", "系统资源", "free -h", "以人类易读单位查看物理内存与 Swap 占用"),
    BuiltInCommand("磁盘空间与分区", "系统资源", "df -hT", "查看所有挂载磁盘的文件系统类型与剩余空间"),
    BuiltInCommand("内存最高前 10 进程", "系统资源", "ps aux --sort=-%mem | head -n 11", "快速揪出服务器上占用物理内存最大的 10 个进程"),
    BuiltInCommand("CPU 占用最高前 10 进程", "系统资源", "ps aux --sort=-%cpu | head -n 11", "快速揪出占用 CPU 最高的 10 个进程"),
    BuiltInCommand("系统负载与开机时间", "系统资源", "uptime", "查看系统已连续运行天数及 1/5/15 分钟平均负载"),

    // 网络端口
    BuiltInCommand("查看监听端口 (ss)", "网络端口", "ss -tulpn", "列出本机所有监听的 TCP/UDP 端口及对应程序 PID"),
    BuiltInCommand("排查指定端口占用", "网络端口", "lsof -i :8080", "查看哪个进程占用了 8080 端口"),
    BuiltInCommand("测试 HTTP 接口头与延迟", "网络端口", "curl -Iv -s https://example.com > /dev/null", "快速探测目标网站的 HTTP 状态码与握手过程"),
    BuiltInCommand("TCP 路由追踪", "网络端口", "traceroute -T -p 443 8.8.8.8", "利用 TCP 协议探测到目标节点各路由跳数及耗时"),
    BuiltInCommand("实时连接状态统计", "网络端口", "ss -s", "统计当前 TCP 建立连接数 (ESTAB)、TIME-WAIT 等"),

    // Docker 容器
    BuiltInCommand("容器状态一览", "Docker", "docker ps --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"", "紧凑格式列出当前所有运行中容器与端口映射"),
    BuiltInCommand("容器瞬时资源占用", "Docker", "docker stats --no-stream", "查看所有容器当前的 CPU、内存及网络 I/O 占用"),
    BuiltInCommand("实时追踪容器日志", "Docker", "docker logs --tail 100 -f <container_name>", "追踪指定容器最后 100 行动态实时日志"),
    BuiltInCommand("清理无用资源 (Prune)", "Docker", "docker system prune -a --volumes -f", "一键清理所有未被使用的废弃容器、镜像与数据卷"),
    BuiltInCommand("Compose 重启单个服务", "Docker", "docker compose restart <service_name>", "不影响其他容器的前提下快速重启单个服务"),

    // 服务管理
    BuiltInCommand("服务状态查看", "服务管理", "systemctl status <service>", "查看 systemd 服务的实时运行状态与最后日志"),
    BuiltInCommand("重启指定服务", "服务管理", "systemctl restart <service>", "优雅重启目标服务并应用新配置"),
    BuiltInCommand("查看服务专属日志", "服务管理", "journalctl -u <service> -n 50 --no-pager", "抓取指定服务最近 50 行运行日志"),
    BuiltInCommand("查看系统异常日志", "服务管理", "journalctl -xe --no-pager -n 50", "查看近期发生的系统级报错和崩溃堆栈"),
    BuiltInCommand("重载 Systemd 守护进程", "服务管理", "systemctl daemon-reload", "更新 .service 文件后重新加载服务配置"),

    // 文件与磁盘
    BuiltInCommand("当前目录体积前 10", "文件磁盘", "du -sh * 2>/dev/null | sort -rh | head -n 10", "排查当前目录下占用空间最大的文件或子文件夹"),
    BuiltInCommand("查找大于 100MB 大文件", "文件磁盘", "find / -type f -size +100M -exec ls -lh {} + 2>/dev/null", "全盘搜索占用超过 100MB 的大文件"),
    BuiltInCommand("清理系统旧系统日志", "文件磁盘", "journalctl --vacuum-size=200M", "安全将 systemd 日志体积压缩控制在 200MB 以内"),
    BuiltInCommand("日期打包压缩文件夹", "文件磁盘", "tar -czvf backup_\$(date +%F).tar.gz /data/app", "以当前日期命名将指定目录压缩为 tar.gz 包"),

    // 安全运维
    BuiltInCommand("防火墙状态 (UFW)", "安全运维", "ufw status verbose", "查看 Ubuntu/Debian 系统防火墙开关与放行规则"),
    BuiltInCommand("查看最近登录日志", "安全运维", "last -n 20", "检查最近 20 次登录的用户、终端和来源 IP"),
    BuiltInCommand("修复 SSH 密钥权限", "安全运维", "chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys", "一键修复 SSH 免密登录因权限过宽导致失效的问题"),
    BuiltInCommand("查看 Fail2ban 防爆破", "安全运维", "fail2ban-client status sshd", "查看当前被 Fail2ban 封禁的恶意 SSH 爆破 IP"),

    // Git 与项目运维
    BuiltInCommand("紧凑工作区状态", "Git运维", "git status -s", "简明扼要查看未提交与改动的文件"),
    BuiltInCommand("单行日志历史图谱", "Git运维", "git log --oneline -n 10 --graph", "以分支图形式查看最近 10 次提交记录"),
    BuiltInCommand("安全变基拉取代码", "Git运维", "git pull --rebase origin main", "保持提交树整洁地同步远程最新代码")
)

@Composable
fun CommandCheatSheetScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val userSnippets by db.snippetDao().getAllSnippets().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "全部", "系统资源", "网络端口", "Docker", "服务管理", "文件磁盘", "安全运维", "Git运维", "我的收藏"
    )

    // 组合内置命令与用户自定义命令
    val allItems = remember(userSnippets) {
        val userCommands = userSnippets.map {
            BuiltInCommand(
                title = it.title,
                category = "我的收藏",
                command = it.command,
                description = it.description.ifBlank { "用户自定义运维指令" }
            )
        }
        BUILTIN_COMMANDS + userCommands
    }

    val filteredList = remember(allItems, searchQuery, selectedCategory) {
        allItems.filter { item ->
            val matchCategory = if (selectedCategory == "全部") true else item.category == selectedCategory
            val matchQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.command.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val theme = LocalAppTheme.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 沉浸贴顶标题栏 (紧凑无缝)
            Surface(color = theme.surfaceContainerLow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(theme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Strings.cheatsheetTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ObsidianTextPrimary
                            )
                            Text(
                                text = Strings.cheatsheetSubtitle,
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )
                        }

                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = Strings.addCustomCommand, tint = theme.primary)
                        }
                    }

                    // 搜索框
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text(Strings.searchCommandsPlaceholder, color = ObsidianTextMuted, fontSize = 12.sp) },
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

                    // 分类 Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { category ->
                            val count = if (category == "全部") allItems.size else allItems.count { it.category == category }
                            val isSelected = selectedCategory == category

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surfaceContainer,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                onClick = { selectedCategory = category }
                            ) {
                                Text(
                                    text = "$category ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primary else ObsidianTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 命令列表
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("未找到相关运维指令", color = ObsidianTextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.title + it.command }) { cmd ->
                        CommandCard(
                            command = cmd,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(cmd.command))
                                Toast.makeText(context, "${Strings.copySuccess}: ${cmd.command}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // 自定义命令添加弹窗
    if (showAddDialog) {
        AddCommandDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, command, desc ->
                scope.launch {
                    db.snippetDao().insertSnippet(
                        SnippetEntity(
                            title = title,
                            command = command,
                            description = desc
                        )
                    )
                    showAddDialog = false
                    Toast.makeText(context, "已添加到自定义指令库", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun CommandCard(
    command: BuiltInCommand,
    onCopy: () -> Unit
) {
    val theme = LocalAppTheme.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类 Badge
                Surface(
                    color = theme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = command.category,
                        color = theme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = command.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ObsidianTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Command",
                        tint = theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (command.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.description,
                    fontSize = 11.sp,
                    color = ObsidianTextMuted,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 代码命令行展示
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ObsidianSurfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        color = theme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = command.command,
                        color = ObsidianTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCommandDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val theme = LocalAppTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.addCustomCommand, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("指令名称 (如: 快速重启后端)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("命令内容 (如: systemctl restart app)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("说明与备注 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && command.isNotBlank()) {
                        onSave(title.trim(), command.trim(), description.trim())
                    }
                },
                enabled = title.isNotBlank() && command.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = ObsidianOnPrimary)
            ) {
                Text("保存")
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
