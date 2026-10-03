package com.termius.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            Surface(
                color = theme.surfaceContainerLow,
                shadowElevation = if (theme.isDark) 0.dp else 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = theme.divider.copy(alpha = 0.45f),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MainTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            val labelText = tab.getTitle()

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (tab != MainTab.SFTP) {
                                                sftpHostTarget = null
                                            }
                                            currentTab = tab
                                        }
                                        .background(
                                            if (isSelected) theme.primary.copy(alpha = if (theme.isDark) 0.22f else 0.12f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = labelText,
                                        tint = if (isSelected) theme.primary else theme.textMuted,
                                        modifier = Modifier.size(23.dp)
                                    )
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
