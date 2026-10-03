package com.termius.clone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.model.HostEntity
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings

enum class MainTab(val getTitle: () -> String, val icon: ImageVector) {
    HOSTS({ Strings.tabHosts }, Icons.Default.Dns),
    SFTP({ Strings.tabSftp }, Icons.Default.FolderShared),
    CHEATSHEET({ Strings.tabCheatsheet }, Icons.Default.MenuBook),
    SETTINGS({ Strings.tabSettings }, Icons.Default.Settings)
}

@Composable
fun MainScreen(
    onNavigateToTerminal: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainTab.HOSTS) }
    var sftpHostTarget by remember { mutableStateOf<HostEntity?>(null) }
    val theme = LocalAppTheme.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // 消除外层多余的状态栏 inset 嵌套，让顶部标题紧凑贴顶
        bottomBar = {
            NavigationBar(
                containerColor = theme.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val labelText = tab.getTitle()
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = labelText) },
                        label = { Text(labelText, fontSize = 11.sp) },
                        selected = isSelected,
                        onClick = {
                            if (tab != MainTab.SFTP) {
                                sftpHostTarget = null
                            }
                            currentTab = tab
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            unselectedIconColor = ObsidianTextMuted,
                            unselectedTextColor = ObsidianTextMuted,
                            indicatorColor = theme.primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentTab) {
                MainTab.HOSTS -> HostListScreen(
                    onNavigateToTerminal = onNavigateToTerminal,
                    onNavigateToSftpForHost = { host ->
                        sftpHostTarget = host
                        currentTab = MainTab.SFTP
                    }
                )
                MainTab.SFTP -> SftpScreen(initialHost = sftpHostTarget)
                MainTab.CHEATSHEET -> CommandCheatSheetScreen()
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
