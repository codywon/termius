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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.HostEntity
import com.termius.clone.sftp.SftpClientManager
import com.termius.clone.sftp.SftpItem
import com.termius.clone.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    initialHost: HostEntity? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val hosts by db.hostDao().getAllHosts().collectAsState(initial = emptyList())
    val identities by db.identityDao().getAllIdentities().collectAsState(initial = emptyList())

    var selectedHost by remember { mutableStateOf(initialHost) }
    var currentPath by remember { mutableStateOf("/") }
    var items by remember { mutableStateOf<List<SftpItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    val sftpManager = remember { SftpClientManager() }

    fun loadDir(path: String) {
        scope.launch {
            isLoading = true
            try {
                val list = sftpManager.listDirectory(path)
                items = list
                currentPath = path
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load directory: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
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
                Toast.makeText(context, "SFTP Connection Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    Scaffold(
        topBar = {
            Surface(color = ObsidianSurfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(ObsidianSecondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, ObsidianSecondary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = ObsidianSecondary, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isConnected) "SFTP: ${selectedHost?.label}" else "SFTP File Explorer",
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isConnected) "${selectedHost?.username}@${selectedHost?.hostname}" else "Remote file manager",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (isConnected) {
                            IconButton(onClick = { loadDir(currentPath) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ObsidianTextPrimary)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    sftpManager.disconnect()
                                    isConnected = false
                                    selectedHost = null
                                    items = emptyList()
                                }
                            }) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect", tint = ObsidianError)
                            }
                        }
                    }

                    // Breadcrumb Path Bar
                    if (isConnected) {
                        Surface(color = ObsidianSurfaceContainer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentPath,
                                    color = ObsidianPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isConnected) {
                // Host selection list
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Select a host to launch SFTP:", color = ObsidianTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (hosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hosts configured yet", color = ObsidianTextMuted)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(hosts) { host ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianSurfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { connectToHost(host) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ObsidianSecondary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(host.label, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary, fontSize = 15.sp)
                                            Text("${host.username}@${host.hostname}:${host.port}", color = ObsidianTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ObsidianTextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // File List
                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentPath != "/") {
                        Surface(
                            color = ObsidianSurfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val parent = File(currentPath).parent ?: "/"
                                    loadDir(parent.replace("\\", "/"))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ObsidianSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(".. Parent Directory", color = ObsidianSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(items) { item ->
                            Surface(
                                color = ObsidianBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.isDirectory) {
                                            val next = if (currentPath.endsWith("/")) "$currentPath${item.name}" else "$currentPath/${item.name}"
                                            loadDir(next)
                                        } else {
                                            Toast.makeText(context, "${item.name} (${formatSize(item.size)})", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) ObsidianSecondary else ObsidianTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = ObsidianTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (item.isDirectory) "Directory" else "${formatSize(item.size)} • ${item.permissions}",
                                            color = ObsidianTextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = ObsidianOutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ObsidianPrimary)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
    return String.format(Locale.getDefault(), "%.1f %cB", bytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}
