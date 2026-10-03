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
    private const val PREFS_NAME = "teamx_language_prefs"
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
 * 界面多语言文本词典
 */
object Strings {
    private val isZh: Boolean get() = LanguageManager.currentLanguage == AppLanguage.ZH

    // 底部 Tab
    val tabHosts: String get() = if (isZh) "主机" else "Hosts"
    val tabSftp: String get() = if (isZh) "文件" else "SFTP"
    val tabCheatsheet: String get() = if (isZh) "速查" else "Cheatsheet"
    val tabSettings: String get() = if (isZh) "设置" else "Settings"

    // 主机列表页
    val appTitle: String get() = "TeamX Mobile"
    val searchHostsPlaceholder: String get() = if (isZh) "搜索主机、集群、IP..." else "Search hosts, clusters, IPs..."
    val allTags: String get() = if (isZh) "全部" else "All"
    val addHost: String get() = if (isZh) "添加主机" else "Add Host"
    val noHostsTitle: String get() = if (isZh) "暂无服务器资产" else "No Servers Configured"
    val noHostsSubtitle: String get() = if (isZh) "点击下方按钮添加首台云主机或集群节点" else "Tap below to add your first cloud or local server"
    val connect: String get() = if (isZh) "连接" else "Connect"
    val edit: String get() = if (isZh) "编辑" else "Edit"
    val delete: String get() = if (isZh) "删除" else "Delete"
    val synced: String get() = if (isZh) "已同步" else "Synced"
    val checkUpdates: String get() = if (isZh) "检查更新" else "Check Updates"

    // 运维速查手册页
    val cheatsheetTitle: String get() = if (isZh) "运维速查手册" else "DevOps Cheatsheet"
    val cheatsheetSubtitle: String get() = if (isZh) "生产环境常用 Linux & 项目运维指令" else "Linux & DevOps command references"
    val searchCommandsPlaceholder: String get() = if (isZh) "搜索运维指令、关键词或用途..." else "Search commands, tags, or usage..."
    val copySuccess: String get() = if (isZh) "已复制命令到剪贴板" else "Copied to clipboard"
    val categoryAll: String get() = if (isZh) "全部" else "All"
    val categorySystem: String get() = if (isZh) "系统资源" else "System"
    val categoryNetwork: String get() = if (isZh) "网络端口" else "Network"
    val categoryDocker: String get() = if (isZh) "Docker容器" else "Docker"
    val categoryServices: String get() = if (isZh) "服务管理" else "Services"
    val categoryDisk: String get() = if (isZh) "文件磁盘" else "Disk & Files"
    val categorySecurity: String get() = if (isZh) "安全运维" else "Security"
    val categoryGit: String get() = if (isZh) "Git部署" else "Git"
    val addCustomCommand: String get() = if (isZh) "添加自定义指令" else "Add Custom Command"

    // 设置页
    val settingsTitle: String get() = if (isZh) "全局设置" else "Settings"
    val sectionVault: String get() = if (isZh) "凭据与钥匙串" else "Credentials & Keychain"
    val sectionVaultDesc: String get() = if (isZh) "管理 SSH 私钥与主机密码凭据" else "Manage SSH keys and password credentials"
    val sectionAppearance: String get() = if (isZh) "外观与主题配色" else "Appearance & Themes"
    val sectionLanguage: String get() = if (isZh) "语言切换" else "Language"
    val sectionKeepalive: String get() = if (isZh) "连接与保活设置" else "Connection & Keepalive"
    val keepalivePing: String get() = if (isZh) "SSH 定时保活心跳" else "SSH Keepalive Heartbeat"
    val keepalivePingDesc: String get() = if (isZh) "定时发送探活数据包防止长连接被中间路由阻断" else "Periodically ping to prevent NAT timeouts"
    val wakeLockStatus: String get() = if (isZh) "后台常驻保护" else "Background WakeLock"
    val wakeLockStatusDesc: String get() = if (isZh) "息屏时保持 CPU 与 Wi-Fi 心跳，切回应用秒连" else "Keep CPU & Wi-Fi alive when screen off"
    val sectionAbout: String get() = if (isZh) "关于与更新" else "About & Updates"
    val poweredBy: String get() = "Powered by codywon"
    val currentVersionLabel: String get() = if (isZh) "当前版本" else "Current Version"
    val checkUpdateBtn: String get() = if (isZh) "检查新版本" else "Check for Updates"

    // 凭据弹窗
    val identitiesCount: String get() = if (isZh) "条已保存的身份凭据" else "saved identities"
    val manageVault: String get() = if (isZh) "管理保险库" else "Manage Vault"
}
