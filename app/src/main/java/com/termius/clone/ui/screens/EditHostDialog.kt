package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.termius.clone.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

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

    var label by remember { mutableStateOf(hostToEdit?.label ?: "") }
    var hostname by remember { mutableStateOf(hostToEdit?.hostname ?: "") }
    var portText by remember { mutableStateOf(hostToEdit?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(hostToEdit?.username ?: "ubuntu") }
    // 默认认证方式设为 PASSWORD，避免用户未填私钥保存导致的连接失败
    var authType by remember { mutableStateOf(hostToEdit?.authType ?: AuthType.PASSWORD) }
    var password by remember { mutableStateOf(hostToEdit?.password ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var privateKey by remember { mutableStateOf(hostToEdit?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(hostToEdit?.passphrase ?: "") }
    var selectedIdentityId by remember { mutableStateOf(hostToEdit?.identityId) }
    var groupName by remember { mutableStateOf(hostToEdit?.groupName ?: "Production") }
    var selectedColor by remember { mutableStateOf(hostToEdit?.colorTag ?: "#67DF70") }

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var keepAliveSec by remember { mutableStateOf("30") }
    var isBackgroundKeepAlive by remember { mutableStateOf(true) }

    // Handshake 测试状态
    var isTestingHandshake by remember { mutableStateOf(false) }
    var handshakeResult by remember { mutableStateOf<String?>(null) }
    var handshakeSuccess by remember { mutableStateOf(false) }

    val presetColors = listOf("#67DF70", "#A2C9FF", "#D6ACFF", "#FABC45", "#FF6E6E")
    val presetTags = listOf("All", "Production", "Docker", "Staging", "AWS", "K8s")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                Surface(color = ObsidianSurfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ObsidianTextPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hostToEdit == null) "New Host Connection" else "Host Configuration",
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary,
                                fontSize = 17.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Obsidian Encrypted Session", fontSize = 11.sp, color = ObsidianTextSecondary)
                            }
                        }
                        Button(
                            onClick = {
                                val trimmedHost = hostname.trim()
                                if (trimmedHost.isBlank()) {
                                    Toast.makeText(context, "请输入主机 IP 或域名！", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                if (authType == AuthType.KEY && privateKey.isBlank()) {
                                    Toast.makeText(context, "当前选择 SSH Key 认证，请粘贴私钥或切换为 Password 认证！", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val port = portText.toIntOrNull() ?: 22
                                val finalLabel = if (label.isBlank()) trimmedHost else label.trim()
                                val host = (hostToEdit ?: HostEntity(
                                    label = finalLabel,
                                    hostname = trimmedHost
                                )).copy(
                                    label = finalLabel,
                                    hostname = trimmedHost,
                                    port = port,
                                    username = username.trim().ifBlank { "root" },
                                    authType = authType,
                                    password = password,
                                    privateKey = privateKey.trim(),
                                    passphrase = passphrase,
                                    identityId = selectedIdentityId,
                                    groupName = groupName,
                                    colorTag = selectedColor
                                )
                                onSave(host)
                            },
                            enabled = hostname.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Save", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            },
            containerColor = ObsidianBackground,
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Connection Profile
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connection Profile", fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontSize = 14.sp)
                        }

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Label / Alias") },
                            placeholder = { Text("e.g. Prod-API-Cluster-Node1") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianSurfaceContainer,
                                unfocusedContainerColor = ObsidianSurfaceContainer,
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianOutlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Color selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Color Tag:", fontSize = 12.sp, color = ObsidianTextSecondary)
                            presetColors.forEach { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(color, CircleShape)
                                        .clickable { selectedColor = hex }
                                        .then(
                                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                            else Modifier
                                        )
                                )
                            }
                        }

                        // Tags Selection
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cluster Group / Tags:", fontSize = 12.sp, color = ObsidianTextSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetTags.take(4).forEach { tag ->
                                    val isSelected = groupName == tag
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { groupName = tag },
                                        label = { Text(tag, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ObsidianPrimary.copy(alpha = 0.2f),
                                            selectedLabelColor = ObsidianPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Network & Address
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lan, contentDescription = null, tint = ObsidianSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Network & Address", fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontSize = 14.sp)
                        }

                        OutlinedTextField(
                            value = hostname,
                            onValueChange = { hostname = it.trim() },
                            label = { Text("Hostname / IP Address *") },
                            placeholder = { Text("192.168.1.100 or api.server.com") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipText = clipboard.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        hostname = clipText.trim()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = ObsidianSecondary)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianSurfaceContainer,
                                unfocusedContainerColor = ObsidianSurfaceContainer,
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianOutlineVariant
                            ),
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
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ObsidianSurfaceContainer,
                                    unfocusedContainerColor = ObsidianSurfaceContainer,
                                    focusedBorderColor = ObsidianPrimary,
                                    unfocusedBorderColor = ObsidianOutlineVariant
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Quick ports
                            listOf("22", "2222", "443").forEach { p ->
                                SuggestionChip(
                                    onClick = { portText = p },
                                    label = { Text(p, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }
                }

                // Section 3: Authentication & Credentials (密码与私钥)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Authentication & Credentials", fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontSize = 14.sp)
                        }

                        // Auth type chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = authType == AuthType.PASSWORD,
                                onClick = { authType = AuthType.PASSWORD },
                                label = { Text("Password", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = ObsidianPrimary
                                )
                            )
                            FilterChip(
                                selected = authType == AuthType.KEY,
                                onClick = { authType = AuthType.KEY },
                                label = { Text("SSH Key", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = ObsidianPrimary
                                )
                            )
                            FilterChip(
                                selected = authType == AuthType.IDENTITY_REF,
                                onClick = { authType = AuthType.IDENTITY_REF },
                                label = { Text("Vault Key", fontSize = 12.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            placeholder = { Text("root or ubuntu") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianSurfaceContainer,
                                unfocusedContainerColor = ObsidianSurfaceContainer,
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianOutlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        when (authType) {
                            AuthType.PASSWORD -> {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password",
                                                tint = ObsidianSecondary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ObsidianSurfaceContainer,
                                        unfocusedContainerColor = ObsidianSurfaceContainer,
                                        focusedBorderColor = ObsidianPrimary,
                                        unfocusedBorderColor = ObsidianOutlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AuthType.KEY -> {
                                OutlinedTextField(
                                    value = privateKey,
                                    onValueChange = { privateKey = it },
                                    label = { Text("Private Key (OpenSSH / PEM / Ed25519) *") },
                                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----\n...") },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val clipText = clipboard.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                privateKey = clipText.trim()
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste Key", tint = ObsidianPrimary)
                                        }
                                    },
                                    minLines = 3,
                                    maxLines = 6,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ObsidianSurfaceContainer,
                                        unfocusedContainerColor = ObsidianSurfaceContainer,
                                        focusedBorderColor = ObsidianPrimary,
                                        unfocusedBorderColor = ObsidianOutlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    label = { Text("Passphrase (Optional)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ObsidianSurfaceContainer,
                                        unfocusedContainerColor = ObsidianSurfaceContainer,
                                        focusedBorderColor = ObsidianPrimary,
                                        unfocusedBorderColor = ObsidianOutlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AuthType.IDENTITY_REF -> {
                                if (identities.isEmpty()) {
                                    Text("No identities saved in Keychain. Add one in the Keychain tab.", color = ObsidianTextMuted, fontSize = 12.sp)
                                } else {
                                    identities.forEach { ident ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selectedIdentityId == ident.id) ObsidianPrimary.copy(alpha = 0.15f) else ObsidianSurfaceContainer,
                                            border = if (selectedIdentityId == ident.id) androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary) else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedIdentityId = ident.id }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(ident.name, color = ObsidianTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("User: ${ident.username} • Type: ${ident.keyType}", color = ObsidianTextSecondary, fontSize = 11.sp)
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

                // Section 4: Advanced SSH Features Accordion
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = ObsidianTertiary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Advanced SSH & Keepalive", fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary, fontSize = 14.sp)
                            }
                            Icon(
                                if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = ObsidianTextSecondary
                            )
                        }

                        AnimatedVisibility(visible = isAdvancedExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Background Keepalive", color = ObsidianTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("Keep SSH session connected via Foreground Service", color = ObsidianTextSecondary, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = isBackgroundKeepAlive,
                                        onCheckedChange = { isBackgroundKeepAlive = it }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Heartbeat Keep-Alive:", color = ObsidianTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = keepAliveSec,
                                        onValueChange = { keepAliveSec = it.filter { c -> c.isDigit() } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.width(80.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("sec", color = ObsidianTextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Section 5: Test Connection Handshake Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (handshakeSuccess) ObsidianPrimary.copy(alpha = 0.15f) else ObsidianSurfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (handshakeSuccess) ObsidianPrimary else ObsidianOutlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                val trimmedTarget = hostname.trim()
                                scope.launch {
                                    isTestingHandshake = true
                                    handshakeResult = "Initiating SSH handshake with $trimmedTarget..."
                                    handshakeSuccess = false

                                    withContext(Dispatchers.IO) {
                                        try {
                                            com.termius.clone.TermiusApplication.setupBouncyCastle()
                                            val client = SSHClient()
                                            client.addHostKeyVerifier(PromiscuousVerifier())
                                            val start = System.currentTimeMillis()
                                            client.connect(trimmedTarget, portText.toIntOrNull() ?: 22)
                                            val ping = System.currentTimeMillis() - start
                                            client.disconnect()
                                            client.close()
                                            handshakeSuccess = true
                                            handshakeResult = "SSH Handshake Successful! (Ping: ${ping}ms OK)"
                                        } catch (e: Exception) {
                                            handshakeSuccess = false
                                            handshakeResult = "Handshake failed: ${e.message}"
                                        } finally {
                                            isTestingHandshake = false
                                        }
                                    }
                                }
                            },
                            enabled = hostname.isNotBlank() && !isTestingHandshake,
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingHandshake) {
                                CircularProgressIndicator(color = ObsidianPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing Handshake...", fontSize = 13.sp, color = ObsidianTextPrimary)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test SSH Handshake", fontSize = 13.sp, color = ObsidianTextPrimary)
                            }
                        }

                        if (handshakeResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = handshakeResult!!,
                                fontSize = 12.sp,
                                color = if (handshakeSuccess) ObsidianPrimary else ObsidianError,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 底部键盘额外防遮挡垫高区
                Spacer(modifier = Modifier.height(180.dp))
            }
        }
    }
}
