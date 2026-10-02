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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                "Port Forwarding Tunnels",
                                color = ObsidianTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (activeSession != null) "Binding to: ${activeSession.host.label}" else "No active SSH session bound",
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
                        Text("+ New Tunnel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                    ) {
                        Text("Done", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    Surface(
                        color = ObsidianSecondary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            rule.type.name,
                            color = ObsidianSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
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
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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
    var remotePort by remember { mutableStateOf("3000") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Tunnel Rule", color = ObsidianTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. NextJS Dev Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it },
                        label = { Text("Local Port") },
                        placeholder = { Text("8080") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = remotePort,
                        onValueChange = { remotePort = it },
                        label = { Text("Remote Port") },
                        placeholder = { Text("3000") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    label = { Text("Remote Host Target") },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Access web portal locally") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lPort = localPort.toIntOrNull() ?: 8080
                    val rPort = remotePort.toIntOrNull() ?: 3000
                    val ruleName = name.ifBlank { "Tunnel $lPort->$rPort" }
                    onConfirm(
                        TunnelRule(
                            name = ruleName,
                            type = TunnelType.LOCAL,
                            localPort = lPort,
                            remoteHost = remoteHost.ifBlank { "127.0.0.1" },
                            remotePort = rPort,
                            description = description
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
            ) {
                Text("Create Tunnel", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ObsidianTextMuted)
            }
        },
        containerColor = ObsidianSurfaceContainerLow
    )
}
