package com.termius.clone.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.HostEntity
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.ui.theme.*
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

    Scaffold(
        topBar = {
            Surface(color = ObsidianSurfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Bar (TermX Mobile Branding & Sync status)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Logo
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(ObsidianPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, ObsidianPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TermX Mobile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = ObsidianTextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(ObsidianPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Vault Synced & Secure",
                                    fontSize = 11.sp,
                                    color = ObsidianTextSecondary
                                )
                            }
                        }

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(ObsidianSurfaceContainerHigh, CircleShape)
                                .border(1.dp, ObsidianOutlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = ObsidianTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search hosts, clusters, IPs...", color = ObsidianTextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ObsidianTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSurfaceContainer,
                            unfocusedContainerColor = ObsidianSurfaceContainer,
                            focusedBorderColor = ObsidianPrimary,
                            unfocusedBorderColor = ObsidianOutlineVariant
                        )
                    )

                    // Tag Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterTags.forEach { tag ->
                            val count = if (tag == "All") hosts.size else hosts.count { it.groupName.equals(tag, ignoreCase = true) }
                            val isSelected = selectedTag == tag

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary) else null,
                                onClick = { selectedTag = tag }
                            ) {
                                Text(
                                    text = "$tag ($count)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ObsidianPrimary else ObsidianTextSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                containerColor = ObsidianPrimary,
                contentColor = ObsidianOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Host", modifier = Modifier.size(24.dp))
            }
        },
        containerColor = ObsidianBackground
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
                        Text("No hosts found", color = ObsidianTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap + button below to add your first SSH cluster host", color = ObsidianTextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredHosts, key = { it.id }) { host ->
                        StitchHostCard(
                            host = host,
                            onConnect = {
                                val identity = identities.find { it.id == host.identityId }
                                SessionManager.openSession(context, host, identity)
                                scope.launch {
                                    db.hostDao().updateLastConnected(host.id, System.currentTimeMillis())
                                }
                                onNavigateToTerminal()
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
}

@Composable
fun StitchHostCard(
    host: HostEntity,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenSftp: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val colorTag = try {
        Color(android.graphics.Color.parseColor(host.colorTag))
    } catch (e: Exception) {
        ObsidianPrimary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceContainerLow,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = host.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ObsidianTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Tag chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObsidianSurfaceContainerHigh
                    ) {
                        Text(
                            text = host.groupName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ObsidianTextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${host.username}@${host.hostname}:${host.port}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ObsidianTextSecondary
                )
            }

            // Quick Connect Button
            Surface(
                shape = CircleShape,
                color = ObsidianPrimary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.6f)),
                onClick = onConnect,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Connect",
                        tint = ObsidianPrimary,
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
                    modifier = Modifier.background(ObsidianSurfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Launch Terminal", color = ObsidianTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianPrimary) },
                        onClick = {
                            showMenu = false
                            onConnect()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open SFTP Explorer", color = ObsidianTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ObsidianSecondary) },
                        onClick = {
                            showMenu = false
                            onOpenSftp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Configuration", color = ObsidianTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ObsidianTextPrimary) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Host", color = ObsidianError) },
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
