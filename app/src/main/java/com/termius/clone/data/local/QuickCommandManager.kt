package com.termius.clone.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termius.clone.data.model.QuickCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

object QuickCommandManager {
    private const val PREFS_NAME = "teamx_quick_commands_prefs"
    private const val KEY_COMMANDS = "saved_commands"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _commands = MutableStateFlow<List<QuickCommand>>(emptyList())
    val commands: StateFlow<List<QuickCommand>> = _commands.asStateFlow()

    private val defaultCommands = listOf(
        QuickCommand(id = "ctrl_c", title = "Ctrl+C", subtitle = "中断/复制", command = "\u0003"),
        QuickCommand(id = "ctrl_v", title = "Ctrl+V", subtitle = "粘贴剪贴", command = "__CLIPBOARD_PASTE__"),
        QuickCommand(id = "ctrl_z", title = "Ctrl+Z", subtitle = "后台挂起", command = "\u001A"),
        QuickCommand(id = "ctrl_l", title = "Ctrl+L", subtitle = "快速清屏", command = "\u000C"),
        QuickCommand(id = "ctrl_d", title = "Ctrl+D", subtitle = "退出会话", command = "\u0004"),
        QuickCommand(id = "ctrl_a", title = "Ctrl+A", subtitle = "跳转行首", command = "\u0001"),
        QuickCommand(id = "ctrl_e", title = "Ctrl+E", subtitle = "跳转行尾", command = "\u0005"),
        QuickCommand(id = "ctrl_u", title = "Ctrl+U", subtitle = "整行清空", command = "\u0015"),
        QuickCommand(id = "ctrl_r", title = "Ctrl+R", subtitle = "历史搜索", command = "\u0012"),
        QuickCommand(id = "vim_wq", title = ":wq", subtitle = "保存退出", command = ":wq\n"),
        QuickCommand(id = "vim_q_force", title = ":q!", subtitle = "强制退出", command = ":q!\n"),
        QuickCommand(id = "sudo_repeat", title = "sudo !!", subtitle = "Root重试", command = "sudo !!\n"),
        QuickCommand(id = "docker_ps", title = "docker ps", subtitle = "容器状态", command = "docker ps\n"),
        QuickCommand(id = "git_status", title = "git status", subtitle = "仓库状态", command = "git status\n"),
        QuickCommand(id = "htop", title = "htop", subtitle = "性能监控", command = "htop\n"),
        QuickCommand(id = "df_h", title = "df -h", subtitle = "磁盘空间", command = "df -h\n"),
        QuickCommand(id = "free_m", title = "free -m", subtitle = "内存监控", command = "free -m\n"),
        QuickCommand(id = "tail_f", title = "tail -f", subtitle = "实时日志", command = "tail -f "),
        QuickCommand(id = "systemctl_status", title = "systemctl", subtitle = "服务状态", command = "systemctl status ")
    )

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadCommands()
        }
    }

    private fun loadCommands() {
        val sp = prefs ?: return
        val json = sp.getString(KEY_COMMANDS, null)
        if (json.isNullOrBlank()) {
            _commands.value = defaultCommands
            saveToDisk(_commands.value)
        } else {
            try {
                val type = object : TypeToken<List<QuickCommand>>() {}.type
                val list: List<QuickCommand> = gson.fromJson(json, type)
                if (list.isEmpty()) {
                    _commands.value = defaultCommands
                    saveToDisk(defaultCommands)
                } else {
                    val mutableList = list.toMutableList()
                    // 兼容老数据升级：确保拥有 Ctrl+V 与优化后的 Ctrl+C
                    val hasCtrlV = mutableList.any { it.id == "ctrl_v" || it.title.equals("Ctrl+V", ignoreCase = true) }
                    if (!hasCtrlV) {
                        val ctrlCIndex = mutableList.indexOfFirst { it.id == "ctrl_c" || it.title.equals("Ctrl+C", ignoreCase = true) }
                        val insertIndex = if (ctrlCIndex >= 0) ctrlCIndex + 1 else 0
                        mutableList.add(
                            insertIndex,
                            QuickCommand(id = "ctrl_v", title = "Ctrl+V", subtitle = "粘贴剪贴", command = "__CLIPBOARD_PASTE__")
                        )
                    }
                    val ctrlCIndex = mutableList.indexOfFirst { it.id == "ctrl_c" }
                    if (ctrlCIndex >= 0 && mutableList[ctrlCIndex].subtitle == "中断") {
                        mutableList[ctrlCIndex] = mutableList[ctrlCIndex].copy(subtitle = "中断/复制")
                    }
                    _commands.value = mutableList
                    saveToDisk(mutableList)
                }
            } catch (e: Exception) {
                _commands.value = defaultCommands
            }
        }
    }

    private fun saveToDisk(list: List<QuickCommand>) {
        val sp = prefs ?: return
        val json = gson.toJson(list)
        sp.edit().putString(KEY_COMMANDS, json).apply()
    }

    fun addCommand(title: String, subtitle: String, command: String) {
        val current = _commands.value.toMutableList()
        val item = QuickCommand(
            title = title.trim(),
            subtitle = subtitle.trim(),
            command = command,
            isCustom = true
        )
        current.add(item)
        _commands.value = current
        saveToDisk(current)
    }

    fun updateCommand(id: String, title: String, subtitle: String, command: String) {
        val current = _commands.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(
                title = title.trim(),
                subtitle = subtitle.trim(),
                command = command
            )
            _commands.value = current
            saveToDisk(current)
        }
    }

    fun deleteCommand(id: String) {
        val current = _commands.value.toMutableList()
        current.removeAll { it.id == id }
        _commands.value = current
        saveToDisk(current)
    }

    fun moveUp(index: Int) {
        if (index <= 0 || index >= _commands.value.size) return
        val current = _commands.value.toMutableList()
        Collections.swap(current, index, index - 1)
        _commands.value = current
        saveToDisk(current)
    }

    fun moveDown(index: Int) {
        if (index < 0 || index >= _commands.value.size - 1) return
        val current = _commands.value.toMutableList()
        Collections.swap(current, index, index + 1)
        _commands.value = current
        saveToDisk(current)
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _commands.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _commands.value = current
        saveToDisk(current)
    }

    fun swap(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _commands.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        Collections.swap(current, fromIndex, toIndex)
        _commands.value = current
        saveToDisk(current)
    }

    fun resetToDefaults() {
        _commands.value = defaultCommands
        saveToDisk(defaultCommands)
    }
}
