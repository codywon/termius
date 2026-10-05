package com.termius.clone.ai

import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.data.model.RiskLevel
import java.util.regex.Pattern

/**
 * 生产级 Linux 命令安全沙箱与高危拦截守卫 (DangerousActionGuard)
 * 严格执行 Human-in-the-Loop 人工审批防线：
 * 任何包含潜在不可逆损坏、数据抹除、停机、网络失联的高危指令，必须挂起并征得人工批准！
 */
object DangerousActionGuard {

    private val CRITICAL_PATTERNS = listOf(
        // 根目录与核心目录递归删除
        Pattern.compile("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)\s+.*(/|/\*|~|\$HOME|\.\.|\*)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)\s+.*(/etc|/var|/usr|/boot|/bin|/sbin|/lib|/sys|/dev)\b""", Pattern.CASE_INSENSITIVE),
        // 磁盘格式化与裸写
        Pattern.compile("""\bmkfs(\.[a-z0-9]+)?\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bdd\s+.*of=/dev/([a-z0-9]+)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bfdisk\s+/dev/""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bparted\s+.*mklabel""", Pattern.CASE_INSENSITIVE),
        // 关机与重启
        Pattern.compile("""\b(shutdown|reboot|poweroff|halt)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\binit\s+[06]\b""", Pattern.CASE_INSENSITIVE),
        // 灾难性提权/改权限
        Pattern.compile("""\bchmod\s+.*(-[a-zA-Z]*R[a-zA-Z]*)\s+(777|000)\s+(/|/\*|~)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bchown\s+.*(-[a-zA-Z]*R[a-zA-Z]*)\s+.*(/|/\*|~)\b""", Pattern.CASE_INSENSITIVE),
        // 终止系统进程
        Pattern.compile("""\bkill\s+-9\s+1\b""", Pattern.CASE_INSENSITIVE),
        // 数据库高危清空
        Pattern.compile("""\b(drop\s+database|truncate\s+table|drop\s+table)\b""", Pattern.CASE_INSENSITIVE),
        // Fork 炸弹
        Pattern.compile(""":\(\)\s*\{\s*:\|:&\s*\};:""")
    )

    private val HIGH_PATTERNS = listOf(
        // 普通目录递归删除 rm -rf
        Pattern.compile("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)\b""", Pattern.CASE_INSENSITIVE),
        // 清空防火墙与关闭网络防护
        Pattern.compile("""\biptables\s+(-F|-X|-t\s+nat\s+-F)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bufw\s+disable\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bsystemctl\s+(stop|disable)\s+firewalld\b""", Pattern.CASE_INSENSITIVE),
        // 用户与认证修改
        Pattern.compile("""\buserdel\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bpasswd\b""", Pattern.CASE_INSENSITIVE),
        // 强行清空日志或文件
        Pattern.compile("""\btruncate\s+-s\s*0\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bshred\s+""", Pattern.CASE_INSENSITIVE)
    )

    private val MEDIUM_PATTERNS = listOf(
        // 停止/重启核心生产服务
        Pattern.compile("""\bsystemctl\s+(stop|restart)\s+(docker|nginx|mysqld|mysql|mariadb|postgresql|ssh|sshd|kubelet)\b""", Pattern.CASE_INSENSITIVE),
        // 重写或直接覆盖系统配置文件
        Pattern.compile("""\b(>|>>)\s+/etc/"""),
        Pattern.compile("""\bsed\s+-i\s+.*(/etc/)""")
    )

    /**
     * 判定命令是否包含危险操作
     * @return 若危险，返回结构化的危险请求对象；若安全，返回 null
     */
    fun checkCommandRisk(command: String, hostLabel: String): DangerousActionRequest? {
        val trimmed = command.trim()

        // 1. CRITICAL
        for (pattern in CRITICAL_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.CRITICAL,
                    reason = "检测到灾难性破坏命令 (如全盘删除/磁盘格式化/关机/权限彻底破坏)",
                    impact = "执行后可能导致整个操作系统瘫痪、数据永久损毁或服务器彻底失联！"
                )
            }
        }

        // 2. HIGH
        for (pattern in HIGH_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.HIGH,
                    reason = "检测到高危变更操作 (如递归强制删除文件、重置防火墙或删除用户)",
                    impact = "执行后可能导致指定路径文件永久丢失、服务器端口暴露或认证失效。"
                )
            }
        }

        // 3. MEDIUM
        for (pattern in MEDIUM_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.MEDIUM,
                    reason = "检测到核心系统服务状态变更或 /etc 系统配置重写",
                    impact = "执行后可能导致远程 Web/数据库/容器服务短暂停机或配置生效风险。"
                )
            }
        }

        return null
    }
}
