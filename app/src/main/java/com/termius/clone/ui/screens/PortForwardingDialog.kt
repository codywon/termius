package com.termius.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.termius.clone.data.model.TunnelRule
import com.termius.clone.data.model.TunnelType
import com.termius.clone.terminal.session.SshSession
import com.termius.clone.terminal.tunnel.TunnelManager
import com.termius.clone.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardingDialog(
    activeSession: SshSession?,
    onDismissRequest: () -> Unit
) {
    val rules by TunnelManager.rules.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(ObsidianPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "端口转发 (Port Forwarding)",
                                color = ObsidianTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (activeSession != null) "绑定至: ${activeSession.host.label}" else "未绑定活动 SSH 会话",
                                color = ObsidianTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tunnel Rules List
                if (rules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(ObsidianSurfaceContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShareLocation, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("暂无配置的隧道转发规则", color = ObsidianTextSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rules, key = { it.id }) { rule ->
                            TunnelItemCard(
                                rule = rule,
                                onToggle = {
                                    TunnelManager.toggleTunnel(rule.id, activeSession?.getClient())
                                },
                                onDelete = {
                                    TunnelManager.removeRule(rule.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianSecondary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ 新建规则", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                    ) {
                        Text("完成", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTunnelRuleDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newRule ->
                TunnelManager.addRule(newRule)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun TunnelItemCard(
    rule: TunnelRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (rule.isRunning) ObsidianPrimary.copy(alpha = 0.5f) else ObsidianOutlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (rule.isRunning) ObsidianPrimary else ObsidianTextMuted,
                                CircleShape
                            )
                    )
                    Text(
                        rule.name,
                        color = ObsidianTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tunnel Route representation
                Text(
                    text = "localhost:${rule.localPort} ➔ ${rule.remoteHost}:${rule.remotePort}",
                    color = if (rule.isRunning) ObsidianPrimary else ObsidianTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                if (rule.description.isNotEmpty()) {
                    Text(
                        rule.description,
                        color = ObsidianTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(
                    checked = rule.isRunning,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianOnPrimary,
                        checkedTrackColor = ObsidianPrimary,
                        uncheckedThumbColor = ObsidianTextMuted,
                        uncheckedTrackColor = ObsidianSurfaceContainerHigh
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun CreateTunnelRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (TunnelRule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var localPort by remember { mutableStateOf("8080") }
    var remoteHost by remember { mutableStateOf("127.0.0.1") }
    var remotePort by remember { mutableStateOf("80") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建端口转发规则", color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则别名") },
                    placeholder = { Text("如 Web Server") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it },
                        label = { Text("本地端口") },
                        placeholder = { Text("8080") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = remotePort,
                        onValueChange = { remotePort = it },
                        label = { Text("远程端口") },
                        placeholder = { Text("80") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    label = { Text("目标远程主机") },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明 (选填)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lPort = localPort.toIntOrNull() ?: 8080
                    val rPort = remotePort.toIntOrNull() ?: 80
                    val rule = TunnelRule(
                        name = if (name.isNotBlank()) name else "Tunnel $lPort",
                        type = TunnelType.LOCAL,
                        localPort = lPort,
                        remoteHost = if (remoteHost.isNotBlank()) remoteHost else "127.0.0.1",
                        remotePort = rPort,
                        isRunning = false,
                        description = description
                    )
                    onConfirm(rule)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = ObsidianOnPrimary)
            ) {
                Text("添加", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ObsidianTextSecondary)
            }
        },
        containerColor = ObsidianSurfaceContainerLow
    )
}
