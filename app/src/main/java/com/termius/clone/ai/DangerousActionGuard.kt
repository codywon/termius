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

    private const val REGEX_FLAGS = Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    private val LINE_CONTINUATION_REGEX = Regex("""\\\r?\n""")

    // 命令分隔起点与单命令内非分隔字符
    private const val CMD_START = """(?:^|[;\s|&`(])(?:sudo\s+)?"""
    private const val IN_CMD = """[^\n;|&]*?"""
    private const val RM_FLAG = """(?:-[a-zA-Z]*[rf][a-zA-Z]*|--force|--recursive)"""
    private const val REC_FLAG = """(?:-[a-zA-Z]*R[a-zA-Z]*|--recursive)"""
    private const val END_BOUND = """(?=$|[\s;|&)`"'])"""

    // 修复 \W\b 漏洞：使用前后空白/分隔符/行首行尾断言匹配 /, /*, ~, $HOME, .., *
    private const val ROOT_OR_WILDCARD_TARGET =
        """(?:^|[\s"'])(?:/\*?|~(?:/\*?)?|\$\{?HOME\}?(?:/\*?)?|\.\.(?:/\*?)?|\*)$END_BOUND"""
    private const val CORE_SYSTEM_DIRS =
        """(?:^|[\s"'])/(?:etc|var|usr|boot|bin|sbin|lib|lib64|sys|dev|proc|root)(?=$|/|\s|[;|&)`"'])"""
    private const val SENSITIVE_WRITE_PATHS =
        """(?:/etc/|/boot/|~/\.ssh/|\$\{?HOME\}?/\.ssh/|/root/\.ssh/|/var/spool/cron/|[^\s;|&"']*authorized_keys)"""

    private val RM_RECURSIVE_PATTERN = Pattern.compile(
        """${CMD_START}rm\b$IN_CMD$RM_FLAG$END_BOUND""",
        REGEX_FLAGS
    )

    private val RM_CRITICAL_PATTERNS = listOf(
        // 选项在前，根目录/通配符在后 (如 rm -rf /, rm -rf /*, rm -rf ~)
        Pattern.compile("""${CMD_START}rm\b$IN_CMD$RM_FLAG$IN_CMD$ROOT_OR_WILDCARD_TARGET""", REGEX_FLAGS),
        // 目标在前，选项在后 (如 rm / -rf, rm /* --force)
        Pattern.compile("""${CMD_START}rm\b$IN_CMD$ROOT_OR_WILDCARD_TARGET$IN_CMD$RM_FLAG$END_BOUND""", REGEX_FLAGS),
        // 系统核心目录 (任意顺序)
        Pattern.compile("""${CMD_START}rm\b$IN_CMD$RM_FLAG$IN_CMD$CORE_SYSTEM_DIRS""", REGEX_FLAGS),
        Pattern.compile("""${CMD_START}rm\b$IN_CMD$CORE_SYSTEM_DIRS$IN_CMD$RM_FLAG$END_BOUND""", REGEX_FLAGS)
    )

    private val DISK_PATTERNS = listOf(
        Pattern.compile("""\bmkfs(\.[a-z0-9]+)?\b""", REGEX_FLAGS),
        Pattern.compile("""\bdd\b$IN_CMD\bof=/dev/([a-z0-9]+)""", REGEX_FLAGS),
        Pattern.compile("""\bfdisk\s+/dev/""", REGEX_FLAGS),
        Pattern.compile("""\bparted\b$IN_CMD\bmklabel\b""", REGEX_FLAGS)
    )

    private val POWER_PATTERNS = listOf(
        Pattern.compile("""\b(shutdown|reboot|poweroff|halt)\b""", REGEX_FLAGS),
        Pattern.compile("""\binit\s+[06]\b""", REGEX_FLAGS)
    )

    private val CRITICAL_PATTERNS = listOf(
        // 灾难性提权/改权限 (兼容参数先后顺序，杜绝误判 /tmp/test)
        Pattern.compile("""${CMD_START}chmod\b$IN_CMD$REC_FLAG$IN_CMD(?:777|000)$IN_CMD$ROOT_OR_WILDCARD_TARGET""", REGEX_FLAGS),
        Pattern.compile("""${CMD_START}chmod\b$IN_CMD(?:777|000)$IN_CMD$REC_FLAG$IN_CMD$ROOT_OR_WILDCARD_TARGET""", REGEX_FLAGS),
        Pattern.compile("""${CMD_START}chown\b$IN_CMD$REC_FLAG$IN_CMD$ROOT_OR_WILDCARD_TARGET""", REGEX_FLAGS),
        Pattern.compile("""${CMD_START}chown\b$IN_CMD$ROOT_OR_WILDCARD_TARGET$IN_CMD$REC_FLAG$END_BOUND""", REGEX_FLAGS),
        // 终止系统 init 进程或全部进程 (kill -9 1 / kill -9 -1)
        Pattern.compile("""${CMD_START}kill\s+(?:-9|-KILL|-s\s+KILL|-s\s+9)\s+-?1$END_BOUND""", REGEX_FLAGS),
        // 数据库高危清空
        Pattern.compile("""\b(drop\s+database|truncate\s+table|drop\s+table)\b""", REGEX_FLAGS),
        // Fork 炸弹
        Pattern.compile(""":\(\)\s*\{\s*:\|:&\s*\};:""", REGEX_FLAGS)
    )

    private val FIREWALL_PATTERNS = listOf(
        Pattern.compile("""\biptables\s+(-F|-X|-t\s+nat\s+-F)$END_BOUND""", REGEX_FLAGS),
        Pattern.compile("""\bufw\s+disable\b""", REGEX_FLAGS),
        Pattern.compile("""\bsystemctl\s+(stop|disable)\s+firewalld\b""", REGEX_FLAGS)
    )

    private val SENSITIVE_WRITE_HIGH_PATTERNS = listOf(
        // 重定向写入高敏系统路径与 SSH 密钥/定时任务 (去掉 > 前错误的 \b)
        Pattern.compile("""(>|>>)\s*(?:/boot/|~/\.ssh/|\$\{?HOME\}?/\.ssh/|/root/\.ssh/|/var/spool/cron/|[^\s;|&"']*authorized_keys)""", REGEX_FLAGS),
        // tee 写入敏感系统目录与文件
        Pattern.compile("""${CMD_START}tee\b$IN_CMD$SENSITIVE_WRITE_PATHS""", REGEX_FLAGS),
        // 非 -rf 直接删除 /etc/, /boot/, authorized_keys
        Pattern.compile("""${CMD_START}(?:rm|unlink)\b$IN_CMD(?:/etc/|/boot/|[^\s;|&"']*authorized_keys)""", REGEX_FLAGS)
    )

    private const val SCRIPT_DESTRUCTIVE_KEYWORDS =
        """(?:shutil\.rmtree|os\.(?:remove|unlink|rmdir|system)|subprocess\.|\bunlink\b|rmSync|rmdirSync|child_process|execSync|\brm\s+-[a-z]*[rf]|/etc/|/boot/|authorized_keys|\bmkfs\b|\bshutdown\b|\breboot\b)"""

    private val REMOTE_EXEC_AND_SCRIPT_PATTERNS = listOf(
        // curl | sh, wget | bash, base64 -d | sh
        Pattern.compile("""\b(?:curl|wget|base64\s+(?:-d|--decode))\b(?:"[^"]*"|'[^']*'|[^\n|])*?\|\s*(?:sudo\s+)?(?:ba|z|da|k)?sh\b""", REGEX_FLAGS),
        // python / perl / ruby / node -c / -e 内联破坏调用 (限定在单条脚本参数引号内或未分隔子句内，绝不跨 && 误拦)
        Pattern.compile(
            """${CMD_START}(?:python[23]?|perl|ruby|node(?:js)?)\b$IN_CMD\s-[ce]\s+(?:"(?:\\.|[^"])*?$SCRIPT_DESTRUCTIVE_KEYWORDS|'(?:\\.|[^'])*?$SCRIPT_DESTRUCTIVE_KEYWORDS|[^\n;|&]*?$SCRIPT_DESTRUCTIVE_KEYWORDS)""",
            REGEX_FLAGS
        )
    )

    private val HIGH_PATTERNS = listOf(
        // find ... -delete
        Pattern.compile("""${CMD_START}find\b$IN_CMD\s-delete$END_BOUND""", REGEX_FLAGS),
        // 清空定时任务
        Pattern.compile("""${CMD_START}crontab\b$IN_CMD\s-r$END_BOUND""", REGEX_FLAGS),
        // 用户与认证修改
        Pattern.compile("""\buserdel\b""", REGEX_FLAGS),
        Pattern.compile("""\bpasswd\b""", REGEX_FLAGS),
        // 强行清空日志或文件
        Pattern.compile("""\btruncate\s+-s\s*0\b""", REGEX_FLAGS),
        Pattern.compile("""\bshred\s+""", REGEX_FLAGS)
    )

    private val MEDIUM_PATTERNS = listOf(
        // 停止/重启核心生产服务
        Pattern.compile("""\bsystemctl\s+(stop|restart)\s+(docker|nginx|mysqld|mysql|mariadb|postgresql|ssh|sshd|kubelet)\b""", REGEX_FLAGS),
        // 重写或直接覆盖 /etc/ 系统配置文件 (去掉 > 前错误的 \b)
        Pattern.compile("""(>|>>)\s*/etc/""", REGEX_FLAGS),
        Pattern.compile("""\bsed\s+-i\b$IN_CMD(?:/etc/|/boot/|authorized_keys)""", REGEX_FLAGS)
    )

    /**
     * 判定命令是否包含危险操作，并生成专业的风险描述与影响分析
     * 去除浮夸套话，精准呈现事实
     */
    fun checkCommandRisk(command: String, hostLabel: String): DangerousActionRequest? {
        val displayCommand = command.trim()
        if (displayCommand.isEmpty()) return null
        val normalized = LINE_CONTINUATION_REGEX.replace(command, " ").trim()

        // 1. 优先针对最常见的递归/强制删除操作做精准提炼
        if (RM_RECURSIVE_PATTERN.matcher(normalized).find()) {
            val isSystemRoot = RM_CRITICAL_PATTERNS.any { it.matcher(normalized).find() }
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = if (isSystemRoot) RiskLevel.CRITICAL else RiskLevel.HIGH,
                reason = if (isSystemRoot) "包含系统核心目录递归强制删除操作" else "包含不可逆递归删除参数 (`rm -rf`)",
                impact = if (isSystemRoot) "根目录或系统核心文件将被清除，将导致操作系统损坏" else "目标路径文件与目录将被永久移除，无法撤销恢复"
            )
        }

        // 2. 磁盘格式化与裸写
        if (DISK_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.CRITICAL,
                reason = "检测到磁盘底层覆写或格式化指令 (`mkfs/dd/fdisk`)",
                impact = "目标块设备现有文件系统将被覆盖，现有数据将被清空"
            )
        }

        // 3. 关机与重启
        if (POWER_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.CRITICAL,
                reason = "检测到系统关机或重启指令",
                impact = "主机将立即重启或关闭，现有网络连接与运行服务将中断"
            )
        }

        // 4. 其它 CRITICAL 命令
        if (CRITICAL_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.CRITICAL,
                reason = "检测到系统关键权限修改或高风险破坏指令",
                impact = "可能导致核心服务异常或主机权限体系受损"
            )
        }

        // 5. 防火墙与网络变更
        if (FIREWALL_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.HIGH,
                reason = "检测到防火墙规则清空或防护服务停用",
                impact = "主机网络访问控制将被解除，对外端口可能无防护暴露"
            )
        }

        // 6. 敏感路径重定向/tee 覆写与非 -rf 删核心文件
        if (SENSITIVE_WRITE_HIGH_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.HIGH,
                reason = "检测到对系统关键配置、引导区、SSH 密钥或定时任务的写入/删除操作",
                impact = "可能破坏系统引导、篡改认证授权或植入持久化任务"
            )
        }

        // 7. 管道执行脚本与内联脚本破坏调用
        if (REMOTE_EXEC_AND_SCRIPT_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.HIGH,
                reason = "检测到远程/解码脚本直接管道执行或内联脚本高危破坏调用",
                impact = "将绕过常规审查直接执行不可逆文件修改或系统变更"
            )
        }

        // 8. 其它 HIGH 命令 (find -delete, crontab -r, userdel, passwd, truncate, shred)
        if (HIGH_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.HIGH,
                reason = "检测到高风险系统变更或批量清理指令",
                impact = "将修改系统核心用户、定时任务、凭证或不可逆删除/清空文件"
            )
        }

        // 9. MEDIUM 命令 (服务停止/重启、/etc 配置覆盖)
        if (MEDIUM_PATTERNS.any { it.matcher(normalized).find() }) {
            return DangerousActionRequest(
                command = displayCommand,
                hostLabel = hostLabel,
                riskLevel = RiskLevel.MEDIUM,
                reason = "检测到核心系统服务变更或 /etc 配置修改",
                impact = "相关服务可能短暂停机或重新加载配置，影响正在处理的业务请求"
            )
        }

        return null
    }
}
