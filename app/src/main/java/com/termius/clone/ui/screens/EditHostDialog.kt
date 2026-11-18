package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.data.security.KeyVaultManager
import com.termius.clone.ui.theme.*
import com.termius.clone.util.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHostDialog(
    hostToEdit: HostEntity? = null,
    identities: List<IdentityEntity>,
    onDismiss: () -> Unit,
    onSave: (HostEntity) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current

    var label by remember { mutableStateOf(hostToEdit?.label ?: "") }
    var hostname by remember { mutableStateOf(hostToEdit?.hostname ?: "") }
    var portText by remember { mutableStateOf(hostToEdit?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(hostToEdit?.username ?: "root") }
    var authType by remember { mutableStateOf(hostToEdit?.authType ?: AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var selectedIdentityId by remember { mutableStateOf(hostToEdit?.identityId) }
    var groupName by remember { mutableStateOf(hostToEdit?.groupName ?: "") }
    var selectedColor by remember { mutableStateOf(hostToEdit?.colorTag ?: "#67DF70") }

    var isCredentialsReady by remember(hostToEdit?.id) {
        mutableStateOf(
            hostToEdit == null || (
                hostToEdit.password.isEmpty() &&
                hostToEdit.privateKey.isEmpty() &&
                hostToEdit.passphrase.isEmpty()
            )
        )
    }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(hostToEdit?.id) {
        if (hostToEdit != null && !isCredentialsReady) {
            val (decPass, decKey, decPhrase) = withContext(Dispatchers.IO) {
                Triple(
                    runCatching { KeyVaultManager.decryptForUi(hostToEdit.password) }.getOrDefault(""),
                    runCatching { KeyVaultManager.decryptForUi(hostToEdit.privateKey) }.getOrDefault(""),
                    runCatching { KeyVaultManager.decryptForUi(hostToEdit.passphrase) }.getOrDefault("")
                )
            }
            password = decPass
            privateKey = decKey
            passphrase = decPhrase
            isCredentialsReady = true
        }
    }

    var isTestingHandshake by remember { mutableStateOf(false) }
    var handshakeResult by remember { mutableStateOf<String?>(null) }
    var handshakeSuccess by remember { mutableStateOf(false) }

    val presetColors = listOf("#67DF70", "#A2C9FF", "#D6ACFF", "#FABC45", "#FF6E6E")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                Surface(color = theme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.close, tint = ObsidianTextPrimary)
                        }
                        Text(
                            text = if (hostToEdit == null) {
                                if (Strings.isZh) "添加主机" else "Add Host"
                            } else {
                                if (Strings.isZh) "编辑主机" else "Edit Host"
                            },
                            fontWeight = FontWeight.Bold,
                            color = ObsidianTextPrimary,
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmedHost = hostname.trim()
                                if (trimmedHost.isBlank()) {
                                    Toast.makeText(context, if (Strings.isZh) "请输入主机 IP 或域名" else "Please enter hostname/IP", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val port = portText.toIntOrNull()
                                if (port == null || port !in 1..65535) {
                                    Toast.makeText(context, if (Strings.isZh) "请输入有效端口号 (1-65535)" else "Invalid port (1-65535)", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (authType == AuthType.KEY && privateKey.isBlank()) {
                                    Toast.makeText(context, if (Strings.isZh) "请粘贴 SSH 私钥或切换为密码认证" else "Please provide SSH private key", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                if (authType == AuthType.IDENTITY_REF && selectedIdentityId == null) {
                                    Toast.makeText(context, if (Strings.isZh) "请选择一个已保存的凭据" else "Please select a saved credential", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val finalLabel = if (label.isBlank()) trimmedHost else label.trim()
                                val targetUser = username.trim().ifBlank { "root" }
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val (encPass, encKey, encPhrase) = withContext(Dispatchers.IO) {
                                            Triple(
                                                if (authType == AuthType.PASSWORD) KeyVaultManager.encrypt(password) else "",
                                                if (authType == AuthType.KEY) KeyVaultManager.encrypt(privateKey.trim()) else "",
                                                if (authType == AuthType.KEY) KeyVaultManager.encrypt(passphrase) else ""
                                            )
                                        }
                                        val host = (hostToEdit ?: HostEntity(
                                            label = finalLabel,
                                            hostname = trimmedHost
                                        )).copy(
                                            label = finalLabel,
                                            hostname = trimmedHost,
                                            port = port,
                                            username = targetUser,
                                            authType = authType,
                                            password = encPass,
                                            privateKey = encKey,
                                            passphrase = encPhrase,
                                            identityId = if (authType == AuthType.IDENTITY_REF) selectedIdentityId else null,
                                            groupName = groupName.trim(),
                                            colorTag = selectedColor
                                        )
                                        onSave(host)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = hostname.isNotBlank() && isCredentialsReady && !isSaving,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(Strings.save, color = if (theme.isDark) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            },
            containerColor = theme.background,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 主机信息
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (Strings.isZh) "主机信息" else "Host Info", fontWeight = FontWeight.Bold, color = theme.primary, fontSize = 13.sp)

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(if (Strings.isZh) "别名 (选填，如: 生产服务器)" else "Label / Alias") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text(if (Strings.isZh) "标签分组 (选填，如: Docker / 阿里云)" else "Group Tag (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 标记颜色
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(if (Strings.isZh) "图标颜色:" else "Color Tag:", fontSize = 12.sp, color = ObsidianTextSecondary)
                            presetColors.forEach { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(color, CircleShape)
                                        .clickable { selectedColor = hex }
                                        .then(
                                            if (isSelected) Modifier.border(2.dp, theme.primary, CircleShape)
                                            else Modifier
                                        )
                                )
                            }
                        }
                    }
                }

                // 网络地址
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (Strings.isZh) "网络地址" else "Network", fontWeight = FontWeight.Bold, color = theme.primary, fontSize = 13.sp)

                        OutlinedTextField(
                            value = hostname,
                            onValueChange = { hostname = it.trim() },
                            label = { Text(if (Strings.isZh) "主机地址 (IP 或域名) *" else "Hostname / IP *") },
                            placeholder = { Text(if (Strings.isZh) "例如 192.168.1.100 或 server.com" else "e.g. 192.168.1.100 or server.com") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipText = clipboard.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        hostname = clipText.trim()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = theme.primary)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter { c -> c.isDigit() } },
                                label = { Text(if (Strings.isZh) "端口" else "Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )

                            listOf("22", "2222", "8022").forEach { p ->
                                SuggestionChip(
                                    onClick = { portText = p },
                                    label = { Text(p, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }
                }

                // 认证信息
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (Strings.isZh) "认证方式" else "Authentication", fontWeight = FontWeight.Bold, color = theme.primary, fontSize = 13.sp)

                        // 认证方式选择
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = authType == AuthType.PASSWORD,
                                onClick = { authType = AuthType.PASSWORD },
                                label = { Text(if (Strings.isZh) "密码认证" else "Password", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = theme.primary
                                )
                            )
                            FilterChip(
                                selected = authType == AuthType.KEY,
                                onClick = { authType = AuthType.KEY },
                                label = { Text(if (Strings.isZh) "SSH 密钥" else "SSH Key", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = theme.primary
                                )
                            )
                            FilterChip(
                                selected = authType == AuthType.IDENTITY_REF,
                                onClick = { authType = AuthType.IDENTITY_REF },
                                label = { Text(if (Strings.isZh) "从凭据库选择" else "Saved Identity", fontSize = 12.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(if (Strings.isZh) "用户名 (默认: root)" else "Username") },
                            placeholder = { Text("root") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        when (authType) {
                            AuthType.PASSWORD -> {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(if (Strings.isZh) "登录密码" else "Password") },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = ObsidianTextSecondary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AuthType.KEY -> {
                                OutlinedTextField(
                                    value = privateKey,
                                    onValueChange = { privateKey = it },
                                    label = { Text(if (Strings.isZh) "私钥内容 (OpenSSH / PEM / Ed25519) *" else "Private Key *") },
                                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----\n...") },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val clipText = clipboard.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                privateKey = clipText.trim()
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = theme.primary)
                                        }
                                    },
                                    minLines = 3,
                                    maxLines = 6,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    label = { Text(if (Strings.isZh) "密码短语 Passphrase (选填)" else "Passphrase (Optional)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AuthType.IDENTITY_REF -> {
                                if (identities.isEmpty()) {
                                    Text(if (Strings.isZh) "凭据库中暂无保存的凭据，请在「设置」中添加" else "No saved credentials found in Settings.", color = ObsidianTextMuted, fontSize = 12.sp)
                                } else {
                                    identities.forEach { ident ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selectedIdentityId == ident.id) theme.primary.copy(alpha = 0.15f) else theme.surfaceContainerHigh,
                                            border = if (selectedIdentityId == ident.id) androidx.compose.foundation.BorderStroke(1.dp, theme.primary) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedIdentityId = ident.id }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(ident.name, color = ObsidianTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("用户: ${ident.username} • ${ident.keyType}", color = ObsidianTextSecondary, fontSize = 11.sp)
                                                }
                                                RadioButton(
                                                    selected = selectedIdentityId == ident.id,
                                                    onClick = { selectedIdentityId = ident.id }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 连接握手测试
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (handshakeSuccess) theme.primary.copy(alpha = 0.12f) else theme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, if (handshakeSuccess) theme.primary else ObsidianOutlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                val trimmedTarget = hostname.trim()
                                val targetPort = portText.toIntOrNull()
                                if (targetPort == null || targetPort !in 1..65535) {
                                    handshakeSuccess = false
                                    handshakeResult = if (Strings.isZh) "端口号无效 (1-65535)" else "Invalid port (1-65535)"
                                    return@Button
                                }

                                val targetUser = username.trim().ifBlank { "root" }
                                val selectedIdentity = if (authType == AuthType.IDENTITY_REF) {
                                    identities.find { it.id == selectedIdentityId }
                                } else null

                                if (authType == AuthType.KEY && privateKey.isBlank()) {
                                    handshakeSuccess = false
                                    handshakeResult = if (Strings.isZh) "请先提供 SSH 私钥内容" else "Please provide SSH private key"
                                    return@Button
                                }
                                if (authType == AuthType.IDENTITY_REF && selectedIdentity == null) {
                                    handshakeSuccess = false
                                    handshakeResult = if (Strings.isZh) "请先选择一个已保存的凭据" else "Please select a saved credential"
                                    return@Button
                                }

                                scope.launch {
                                    isTestingHandshake = true
                                    handshakeResult = if (Strings.isZh) "正在连接并验证 $trimmedTarget..." else "Connecting & authenticating $trimmedTarget..."
                                    handshakeSuccess = false

                                    val (ok, msg) = withContext(Dispatchers.IO) {
                                        var client: SSHClient? = null
                                        try {
                                            com.termius.clone.TermiusApplication.setupBouncyCastle()
                                            client = SSHClient().apply {
                                                addHostKeyVerifier(PromiscuousVerifier())
                                                connectTimeout = 8_000
                                                timeout = 8_000
                                            }
                                            val start = System.currentTimeMillis()
                                            client.connect(trimmedTarget, targetPort)

                                            val effectiveUser = if (authType == AuthType.IDENTITY_REF && selectedIdentity != null) {
                                                selectedIdentity.username.ifBlank { targetUser }
                                            } else {
                                                targetUser
                                            }

                                            when (authType) {
                                                AuthType.PASSWORD -> client.authPassword(effectiveUser, password)
                                                AuthType.KEY -> {
                                                    val trimmedKey = privateKey.trim()
                                                    val keyProvider = if (passphrase.isNotEmpty()) {
                                                        client.loadKeys(trimmedKey, null, PasswordUtils.createOneOff(passphrase.toCharArray()))
                                                    } else {
                                                        client.loadKeys(trimmedKey, null, null)
                                                    }
                                                    client.authPublickey(effectiveUser, keyProvider)
                                                }
                                                AuthType.IDENTITY_REF -> {
                                                    val ident = selectedIdentity!!
                                                    val decKey = KeyVaultManager.decrypt(ident.privateKey)
                                                    if (decKey.isNotEmpty()) {
                                                        val decPhrase = KeyVaultManager.decrypt(ident.passphrase)
                                                        val keyProvider = if (decPhrase.isNotEmpty()) {
                                                            client.loadKeys(decKey, null, PasswordUtils.createOneOff(decPhrase.toCharArray()))
                                                        } else {
                                                            client.loadKeys(decKey, null, null)
                                                        }
                                                        client.authPublickey(effectiveUser, keyProvider)
                                                    } else {
                                                        val decPass = KeyVaultManager.decrypt(ident.password)
                                                        client.authPassword(effectiveUser, decPass)
                                                    }
                                                }
                                            }

                                            if (!client.isAuthenticated) {
                                                false to (if (Strings.isZh) "SSH 认证失败，请检查凭据" else "SSH authentication failed")
                                            } else {
                                                val ping = System.currentTimeMillis() - start
                                                true to (if (Strings.isZh) "连接与认证成功 (网络延迟: ${ping}ms)" else "Connected & Authenticated! (${ping}ms)")
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            false to (if (Strings.isZh) "连接失败: ${e.message}" else "Failed: ${e.message}")
                                        } finally {
                                            try { client?.disconnect() } catch (_: Throwable) {}
                                            try { client?.close() } catch (_: Throwable) {}
                                        }
                                    }

                                    handshakeSuccess = ok
                                    handshakeResult = msg
                                    isTestingHandshake = false
                                }
                            },
                            enabled = hostname.isNotBlank() && isCredentialsReady && !isTestingHandshake,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceContainerHigh),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingHandshake) {
                                CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (Strings.isZh) "正在测试连接..." else "Testing...", fontSize = 13.sp, color = ObsidianTextPrimary)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (Strings.isZh) "测试 SSH 连接" else "Test Connection", fontSize = 13.sp, color = ObsidianTextPrimary)
                            }
                        }

                        if (handshakeResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = handshakeResult!!,
                                fontSize = 12.sp,
                                color = if (handshakeSuccess) theme.primary else ObsidianError,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
