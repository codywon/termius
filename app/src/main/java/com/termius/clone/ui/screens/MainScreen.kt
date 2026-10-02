package com.termius.clone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.model.HostEntity
import com.termius.clone.terminal.session.SessionManager
import com.termius.clone.ui.theme.*

enum class MainTab(val title: String, val icon: ImageVector) {
    HOSTS("Hosts", Icons.Default.Dns),
    TERMINAL("Terminal", Icons.Default.Terminal),
    SFTP("SFTP", Icons.Default.FolderShared),
    SNIPPETS("Snippets", Icons.Default.Code),
    KEYCHAIN("Vault", Icons.Default.VpnKey)
}

@Composable
fun MainScreen(
    onNavigateToTerminal: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainTab.HOSTS) }
    var sftpHostTarget by remember { mutableStateOf<HostEntity?>(null) }

    val activeSessions by SessionManager.sessions.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                // Floating Active Terminal Session Capsule
                AnimatedVisibility(visible = activeSessions.isNotEmpty() && currentTab != MainTab.TERMINAL) {
                    Surface(
                        color = ObsidianSurfaceContainerHighest,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToTerminal
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(ObsidianPrimary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Active Terminal: ${activeSessions.size} connected session(s)",
                                color = ObsidianTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Resume >", color = ObsidianPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                NavigationBar(
                    containerColor = ObsidianSurfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 11.sp) },
                            selected = isSelected,
                            onClick = {
                                if (tab == MainTab.TERMINAL) {
                                    if (activeSessions.isNotEmpty()) {
                                        onNavigateToTerminal()
                                    } else {
                                        currentTab = MainTab.TERMINAL
                                    }
                                } else {
                                    if (tab != MainTab.SFTP) {
                                        sftpHostTarget = null
                                    }
                                    currentTab = tab
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianPrimary,
                                selectedTextColor = ObsidianPrimary,
                                unselectedIconColor = ObsidianTextMuted,
                                unselectedTextColor = ObsidianTextMuted,
                                indicatorColor = ObsidianPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.HOSTS -> HostListScreen(
                    onNavigateToTerminal = onNavigateToTerminal,
                    onNavigateToSftpForHost = { host ->
                        sftpHostTarget = host
                        currentTab = MainTab.SFTP
                    }
                )
                MainTab.TERMINAL -> {
                    if (activeSessions.isNotEmpty()) {
                        TerminalScreen(onNavigateBack = { currentTab = MainTab.HOSTS })
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ObsidianBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = ObsidianTextMuted, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No Active Terminal Session", color = ObsidianTextPrimary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Go to the Hosts tab and launch a connection", color = ObsidianTextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
                MainTab.SFTP -> SftpScreen(initialHost = sftpHostTarget)
                MainTab.SNIPPETS -> SnippetScreen()
                MainTab.KEYCHAIN -> KeychainScreen()
            }
        }
    }
}
