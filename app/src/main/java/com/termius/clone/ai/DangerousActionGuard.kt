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
        // 根目录与核心系统目录递归删除
        Pattern.compile("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)\s+.*(/|/\*|~|${'$'}HOME|\.\.|\*)\b""", Pattern.CASE_INSENSITIVE),
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
        // 普通路径递归或强制删除 rm -rf / rm -f
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
     * 判定命令是否包含危险操作，并生成专业的风险描述与影响分析
     * 去除浮夸套话，精准呈现事实
     */
    fun checkCommandRisk(command: String, hostLabel: String): DangerousActionRequest? {
        val trimmed = command.trim()

        // 1. 优先针对最常见的删除操作做精准提炼
        val isRmRecursive = Pattern.compile("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)\b""", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()
        if (isRmRecursive) {
            val isSystemRoot = CRITICAL_PATTERNS[0].matcher(trimmed).find() || CRITICAL_PATTERNS[1].matcher(trimmed).find()
            return DangerousActionRequest(
                command = trimmed,
                hostLabel = hostLabel,
                riskLevel = if (isSystemRoot) RiskLevel.CRITICAL else RiskLevel.HIGH,
                reason = if (isSystemRoot) "包含系统核心目录递归强制删除操作" else "包含不可逆递归删除参数 (`rm -rf`)",
                impact = if (isSystemRoot) "根目录或系统核心文件将被清除，将导致操作系统损坏" else "目标路径文件与目录将被永久移除，无法撤销恢复"
            )
        }

        // 2. 磁盘格式化与裸写
        if (CRITICAL_PATTERNS[2].matcher(trimmed).find() || CRITICAL_PATTERNS[3].matcher(trimmed).find() ||
            CRITICAL_PATTERNS[4].matcher(trimmed).find() || CRITICAL_PATTERNS[5].matcher(trimmed).find()) {
            return DangerousActionRequest(
                command = trimmed,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.CRITICAL,
                reason = "检测到磁盘底层覆写或格式化指令 (`mkfs/dd/fdisk`)",
                impact = "目标块设备现有文件系统将被覆盖，现有数据将被清空"
            )
        }

        // 3. 关机与重启
        if (CRITICAL_PATTERNS[6].matcher(trimmed).find() || CRITICAL_PATTERNS[7].matcher(trimmed).find()) {
            return DangerousActionRequest(
                command = trimmed,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.CRITICAL,
                reason = "检测到系统关机或重启指令",
                impact = "主机将立即重启或关闭，现有网络连接与运行服务将中断"
            )
        }

        // 4. 其它 CRITICAL 命令
        for (pattern in CRITICAL_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.CRITICAL,
                    reason = "检测到系统关键权限修改或高风险破坏指令",
                    impact = "可能导致核心服务异常或主机权限体系受损"
                )
            }
        }

        // 5. 防火墙与网络变更
        if (HIGH_PATTERNS[1].matcher(trimmed).find() || HIGH_PATTERNS[2].matcher(trimmed).find() || HIGH_PATTERNS[3].matcher(trimmed).find()) {
            return DangerousActionRequest(
                command = trimmed,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.HIGH,
                reason = "检测到防火墙规则清空或防护服务停用",
                impact = "主机网络访问控制将被解除，对外端口可能无防护暴露"
            )
        }

        // 6. 其它 HIGH 命令
        for (pattern in HIGH_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.HIGH,
                    reason = "检测到高风险系统变更指令",
                    impact = "将修改系统核心用户、凭证或文件内容，影响后续运行"
                )
            }
        }

        // 7. MEDIUM 命令 (服务停止/重启、配置覆盖)
        for (pattern in MEDIUM_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return DangerousActionRequest(
                    command = trimmed,
                    hostLabel = hostLabel,
                    riskLevel = RiskLevel.MEDIUM,
                    reason = "检测到核心系统服务变更或 /etc 配置修改",
                    impact = "相关服务可能短暂停机或重新加载配置，影响正在处理的业务请求"
                )
            }
        }

        return null
    }
}
