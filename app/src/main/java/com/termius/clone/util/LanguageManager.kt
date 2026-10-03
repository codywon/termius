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
 * 界面多语言文本词典 (去 AI 味、简练准确、专业干脆)
 */
object Strings {
    val isZh: Boolean get() = LanguageManager.currentLanguage == AppLanguage.ZH

    // 底部核心导航 Tab
    val tabHosts: String get() = if (isZh) "主机" else "Hosts"
    val tabSftp: String get() = if (isZh) "文件" else "SFTP"
    val tabCheatsheet: String get() = if (isZh) "速查" else "Cheatsheet"
    val tabSettings: String get() = if (isZh) "设置" else "Settings"

    // 通用按钮与提示
    val cancel: String get() = if (isZh) "取消" else "Cancel"
    val confirm: String get() = if (isZh) "确定" else "OK"
    val create: String get() = if (isZh) "创建" else "Create"
    val save: String get() = if (isZh) "保存" else "Save"
    val close: String get() = if (isZh) "关闭" else "Close"
    val edit: String get() = if (isZh) "编辑" else "Edit"
    val delete: String get() = if (isZh) "删除" else "Delete"
    val copy: String get() = if (isZh) "复制" else "Copy"
    val connect: String get() = if (isZh) "连接" else "Connect"
    val copySuccess: String get() = if (isZh) "已复制到剪贴板" else "Copied to clipboard"

    // 主机列表页
    val appTitle: String get() = "TermX Mobile"
    val searchHostsPlaceholder: String get() = if (isZh) "搜索主机..." else "Search hosts..."
    val allTags: String get() = if (isZh) "全部" else "All"
    val addHost: String get() = if (isZh) "添加主机" else "Add Host"
    val noHostsTitle: String get() = if (isZh) "暂无主机" else "No Hosts"
    val noHostsSubtitle: String get() = if (isZh) "点击右下角按钮添加服务器" else "Tap below to add a host"
    val synced: String get() = if (isZh) "本地就绪" else "Ready"
    val checkUpdates: String get() = if (isZh) "检查更新" else "Check Updates"

    // 运维命令速查页
    val cheatsheetTitle: String get() = if (isZh) "运维速查" else "Cheatsheet"
    val searchCommandsPlaceholder: String get() = if (isZh) "搜索命令..." else "Search commands..."
    val categoryAll: String get() = if (isZh) "全部" else "All"
    val categorySystem: String get() = if (isZh) "系统资源" else "System"
    val categoryNetwork: String get() = if (isZh) "网络端口" else "Network"
    val categoryDocker: String get() = if (isZh) "Docker" else "Docker"
    val categoryServices: String get() = if (isZh) "服务管理" else "Services"
    val categoryDisk: String get() = if (isZh) "文件磁盘" else "Disk"
    val categorySecurity: String get() = if (isZh) "安全运维" else "Security"
    val categoryGit: String get() = if (isZh) "Git 运维" else "Git"
    val addCustomCommand: String get() = if (isZh) "添加常用命令" else "Add Command"

    // 全局设置页
    val settingsTitle: String get() = if (isZh) "设置" else "Settings"
    val groupCredentials: String get() = if (isZh) "凭据管理" else "Credentials"
    val groupAppearance: String get() = if (isZh) "外观与界面" else "Appearance & UI"
    val groupConnection: String get() = if (isZh) "连接与保活" else "Connection & Keepalive"
    val groupAbout: String get() = if (isZh) "关于" else "About"

    val sectionVault: String get() = if (isZh) "SSH 凭据" else "SSH Credentials"
    val sectionAppearance: String get() = if (isZh) "应用主题" else "App Theme"
    val sectionTerminalTheme: String get() = if (isZh) "终端配色" else "Terminal Theme"
    val sectionLanguage: String get() = if (isZh) "界面语言" else "Language"
    val terminalFontSizeTitle: String get() = if (isZh) "终端字号" else "Font Size"

    // 保活设置
    val keepalivePing: String get() = if (isZh) "心跳保持" else "Keepalive Ping"
    val keepalivePingDesc: String get() = if (isZh) "定时发送空闲心跳包，防止连接超时断开" else "Send heartbeat packets to prevent timeout"
    val wakeLockStatus: String get() = if (isZh) "后台常驻服务" else "Background Service"
    val wakeLockStatusDesc: String get() = if (isZh) "锁屏时保持 CPU 与网络活跃" else "Keep CPU and network active when screen is locked"
    val runningStatus: String get() = if (isZh) "运行中" else "Running"

    // 电池后台优化
    val batteryOptimizationTitle: String get() = if (isZh) "电池优化" else "Battery Optimization"
    val batteryOptimizationDesc: String get() = if (isZh) "加入系统白名单，防止后台被系统杀掉" else "Whitelist from battery optimizations to prevent disconnection"
    val batteryOptimizedTag: String get() = if (isZh) "受限" else "Restricted"
    val batteryIgnoredTag: String get() = if (isZh) "无限制" else "Unrestricted"
    val batteryOptimizeBtn: String get() = if (isZh) "解除限制" else "Whitelist"
    val batteryGuideBtn: String get() = if (isZh) "保活说明" else "Guide"

    val poweredBy: String get() = "Powered by codywon"
    val currentVersionLabel: String get() = if (isZh) "当前版本" else "Version"
    val checkUpdateBtn: String get() = if (isZh) "检查更新" else "Check Updates"

    // 凭据相关
    val identitiesCount: String get() = if (isZh) "个已保存凭据" else "saved credentials"
    val manageVault: String get() = if (isZh) "管理凭据" else "Manage"
    val viewLabel: String get() = if (isZh) "查看" else "View"
    val collapseLabel: String get() = if (isZh) "收起" else "Collapse"
    val newLabel: String get() = if (isZh) "新建" else "New"

    // SFTP 远程文件管理
    val sftpTitle: String get() = if (isZh) "SFTP 文件管理" else "SFTP Manager"
    val selectHostPrompt: String get() = if (isZh) "选择服务器建立连接:" else "Select a server to connect:"
    val parentDirectory: String get() = if (isZh) ".. 返回上级目录" else ".. Parent Directory"
    val emptyDirectory: String get() = if (isZh) "目录为空" else "Directory is empty"
    val uploadFile: String get() = if (isZh) "上传文件" else "Upload File"
    val createFolder: String get() = if (isZh) "新建文件夹" else "New Folder"
    val createFile: String get() = if (isZh) "新建文件" else "New File"
    val refresh: String get() = if (isZh) "刷新" else "Refresh"
    val disconnect: String get() = if (isZh) "断开连接" else "Disconnect"
    val viewEditFile: String get() = if (isZh) "查看 / 编辑" else "View / Edit"
    val downloadToPhone: String get() = if (isZh) "下载到手机 (Downloads)" else "Download to Phone"
    val rename: String get() = if (isZh) "重命名" else "Rename"
    val copyRemotePath: String get() = if (isZh) "复制路径" else "Copy Path"
    val deleteItem: String get() = if (isZh) "删除" else "Delete"
    val saveSuccess: String get() = if (isZh) "保存成功" else "Saved successfully"
    val downloadSuccess: String get() = if (isZh) "下载成功，已保存至手机 Downloads 目录" else "Downloaded to phone Downloads folder"
}
