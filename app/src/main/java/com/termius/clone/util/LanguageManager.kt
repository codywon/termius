package com.termius.clone.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val titleZh: String, val titleEn: String) {
    ZH("zh", "简体中文", "Simplified Chinese"),
    EN("en", "English", "English");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: ZH
    }
}

object LanguageManager {
    private const val PREFS_NAME = "termx_language_prefs"
    private const val KEY_LANG = "current_language_code"

    private var prefs: SharedPreferences? = null

    var currentLanguage by mutableStateOf(AppLanguage.ZH)
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedCode = prefs?.getString(KEY_LANG, AppLanguage.ZH.code) ?: AppLanguage.ZH.code
            currentLanguage = AppLanguage.fromCode(savedCode)
        }
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
        prefs?.edit()?.putString(KEY_LANG, language.code)?.apply()
    }
}

/**
 * 界面多语言文本词典 (遵循专业、地道、优雅的中文运维用语规范)
 */
object Strings {
    val isZh: Boolean get() = LanguageManager.currentLanguage == AppLanguage.ZH

    // 底部核心导航 Tab
    val tabHosts: String get() = if (isZh) "主机" else "Hosts"
    val tabSftp: String get() = if (isZh) "文件" else "SFTP"
    val tabCheatsheet: String get() = if (isZh) "速查" else "Cheatsheet"
    val tabSettings: String get() = if (isZh) "设置" else "Settings"

    // 主机列表页
    val appTitle: String get() = "TermX Mobile"
    val searchHostsPlaceholder: String get() = if (isZh) "搜索主机名、IP 地址、标签..." else "Search hosts, IPs, tags..."
    val allTags: String get() = if (isZh) "全部" else "All"
    val addHost: String get() = if (isZh) "添加主机" else "Add Host"
    val noHostsTitle: String get() = if (isZh) "暂无服务器" else "No Servers Configured"
    val noHostsSubtitle: String get() = if (isZh) "点击下方按钮添加云服务器或内网节点" else "Tap below to add your first server"
    val connect: String get() = if (isZh) "连接" else "Connect"
    val edit: String get() = if (isZh) "编辑" else "Edit"
    val delete: String get() = if (isZh) "删除" else "Delete"
    val synced: String get() = if (isZh) "本地就绪" else "Ready"
    val checkUpdates: String get() = if (isZh) "检查更新" else "Check Updates"

    // 运维命令速查页
    val cheatsheetTitle: String get() = if (isZh) "运维命令速查" else "Command Cheatsheet"
    val cheatsheetSubtitle: String get() = if (isZh) "常用 Linux 生产运维与项目管理命令" else "Common Linux & DevOps command references"
    val searchCommandsPlaceholder: String get() = if (isZh) "搜索命令名称、用途或关键词..." else "Search commands, tags, or usage..."
    val copySuccess: String get() = if (isZh) "命令已复制到剪贴板" else "Copied to clipboard"
    val categoryAll: String get() = if (isZh) "全部" else "All"
    val categorySystem: String get() = if (isZh) "系统监控" else "System"
    val categoryNetwork: String get() = if (isZh) "网络与端口" else "Network"
    val categoryDocker: String get() = if (isZh) "Docker 容器" else "Docker"
    val categoryServices: String get() = if (isZh) "服务管理" else "Services"
    val categoryDisk: String get() = if (isZh) "磁盘与文件" else "Disk & Files"
    val categorySecurity: String get() = if (isZh) "安全与防火墙" else "Security"
    val categoryGit: String get() = if (isZh) "Git 部署" else "Git"
    val addCustomCommand: String get() = if (isZh) "添加常用命令" else "Add Custom Command"

    // 全局设置页
    val settingsTitle: String get() = if (isZh) "设置" else "Settings"
    val settingsSubtitle: String get() = if (isZh) "偏好配置、配色主题与后台保活" else "Preferences, themes & keepalive"
    val sectionVault: String get() = if (isZh) "SSH 凭据管理" else "Credentials & Keys"
    val sectionVaultDesc: String get() = if (isZh) "统一管理服务器登录密码与 SSH 密钥" else "Manage server passwords and SSH keys"
    val sectionAppearance: String get() = if (isZh) "外观与配色主题" else "Appearance & Themes"
    val appearanceDesc: String get() = if (isZh) "选择终端界面配色方案 (即时生效)" else "Select terminal color theme"
    val sectionLanguage: String get() = if (isZh) "界面语言" else "Language"
    val sectionKeepalive: String get() = if (isZh) "连接与后台保活" else "Connection & Keepalive"
    val keepalivePing: String get() = if (isZh) "SSH 心跳保持" else "SSH Keepalive Heartbeat"
    val keepalivePingDesc: String get() = if (isZh) "定时发送心跳包，防止网络空闲时连接超时中断" else "Periodically ping to prevent timeouts"
    val wakeLockStatus: String get() = if (isZh) "后台服务常驻" else "Background Service"
    val wakeLockStatusDesc: String get() = if (isZh) "息屏时保持 CPU 与 Wi-Fi 活跃，防止后台网络断开" else "Keep CPU & Wi-Fi active when screen off"
    val runningStatus: String get() = if (isZh) "运行中" else "Running"

    // 电池后台优化
    val batteryOptimizationTitle: String get() = if (isZh) "电池后台优化" else "Battery Optimization"
    val batteryOptimizationDesc: String get() = if (isZh) "加入电池优化白名单，防止息屏被系统强制休眠" else "Exempt from battery optimization to keep SSH alive"
    val batteryOptimizedTag: String get() = if (isZh) "受限" else "Restricted"
    val batteryIgnoredTag: String get() = if (isZh) "无限制" else "Unrestricted"
    val batteryOptimizeBtn: String get() = if (isZh) "一键解除限制" else "Exempt from Optimization"
    val batteryGuideBtn: String get() = if (isZh) "保活指南" else "Keepalive Guide"

    val sectionAbout: String get() = if (isZh) "关于 TermX Mobile" else "About TermX Mobile"
    val poweredBy: String get() = "Powered by codywon"
    val currentVersionLabel: String get() = if (isZh) "当前版本" else "Current Version"
    val checkUpdateBtn: String get() = if (isZh) "检查新版本" else "Check for Updates"

    // 凭据弹窗
    val identitiesCount: String get() = if (isZh) "个已保存的登录凭据" else "saved credentials"
    val manageVault: String get() = if (isZh) "管理凭据" else "Manage Credentials"
    val viewLabel: String get() = if (isZh) "查看" else "View"
    val collapseLabel: String get() = if (isZh) "收起" else "Collapse"
    val newLabel: String get() = if (isZh) "新建" else "New"

    // 终端字体与手势
    val terminalFontSizeTitle: String get() = if (isZh) "终端字体大小" else "Terminal Font Size"
    val terminalFontSizeDesc: String get() = if (isZh) "调节代码字符字号，终端内亦支持双指捏合缩放" else "Adjust font size or pinch to zoom in terminal"
    val fontPreviewLabel: String get() = if (isZh) "字体效果预览" else "Font Preview"
    val scrollbackNotice: String get() = if (isZh) "支持双指/单指上下滑动翻看历史输出" else "Two-finger / one-finger swipe to view history"
}
