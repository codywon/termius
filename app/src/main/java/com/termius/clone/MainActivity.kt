package com.termius.clone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.termius.clone.ui.screens.MainScreen
import com.termius.clone.ui.screens.TerminalScreen
import com.termius.clone.ui.theme.LocalAppTheme
import com.termius.clone.ui.theme.TeamXTheme
import com.termius.clone.ui.theme.ThemeManager
import com.termius.clone.util.LanguageManager

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化主题与语言管理器
        ThemeManager.init(this)
        LanguageManager.init(this)

        // 沉浸式 Edge-to-Edge，状态栏由 Compose 统一精准控制
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 请求 Android 13+ 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            TeamXTheme {
                val theme = LocalAppTheme.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.background
                ) {
                    var currentRoute by remember { mutableStateOf("main") }

                    when (currentRoute) {
                        "main" -> MainScreen(
                            onNavigateToTerminal = { currentRoute = "terminal" }
                        )
                        "terminal" -> TerminalScreen(
                            onNavigateBack = { currentRoute = "main" }
                        )
                    }
                }
            }
        }
    }
}
