package com.termius.clone.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termius.clone.data.model.AiAgentConfig
import com.termius.clone.ui.theme.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极致简洁的大模型连接配置弹窗 (参考 MQTT 助手极简设计哲学)：
 * 仅保留 Base URL、API Key、模型名称 (支持动态从 /v1/models 拉取) 与必要的高级参数，
 * 拒绝繁复死板的固定模型预置列表，保障极高自由度与轻量体验。
 */
@Composable
fun AiSettingsDialog(
    initialConfig: AiAgentConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (AiAgentConfig) -> Unit
) {
    val theme = LocalAppTheme.current
    val coroutineScope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var customPrompt by remember { mutableStateOf(initialConfig.customPrompt) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var modelFetchMessage by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    var temperature by remember { mutableDoubleStateOf(initialConfig.temperature) }
    var maxTokens by remember { mutableIntStateOf(initialConfig.maxTokens) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(0.8.dp, theme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI 模型服务配置",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = theme.textMuted
                        )
                    }
                }

                HorizontalDivider(
                    color = theme.outline.copy(alpha = 0.2f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Input Fields (Scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Base URL
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "接口基础地址 (Base URL)",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                        )
                        SettingInputField(
                            value = baseUrl,
                            placeholder = "https://api.deepseek.com",
                            onValueChange = { baseUrl = it }
                        )
                    }

                    // 2. API Key
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "API Key",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceContainer)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = theme.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    color = theme.textPrimary
                                ),
                                singleLine = true,
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                cursorBrush = SolidColor(theme.primary),
                                decorationBox = { inner ->
                                    if (apiKey.isEmpty()) {
                                        Text(
                                            text = "sk-xxxxxxxxxxxxxxxx",
                                            style = TextStyle(fontSize = 12.sp, color = theme.textMuted)
                                        )
                                    }
                                    inner()
                                }
                            )
                            IconButton(
                                onClick = { isApiKeyVisible = !isApiKeyVisible },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "显隐",
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 3. Model Name & Dynamic Fetch
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "模型名称 (Model)",
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textSecondary)
                            )
                            Box {
                                TextButton(
                                    onClick = {
                                        if (baseUrl.isBlank()) {
                                            modelFetchMessage = "请先输入 Base URL"
                                            return@TextButton
                                        }
                                        isFetchingModels = true
                                        modelFetchMessage = ""
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val clean = baseUrl.trim().trimEnd('/')
                                                val endpoint = if (clean.endsWith("/v1")) "$clean/models" else "$clean/v1/models"
                                                val url = URL(endpoint)
                                                val conn = (url.openConnection() as HttpURLConnection).apply {
                                                    requestMethod = "GET"
                                                    connectTimeout = 8000
                                                    readTimeout = 12000
                                                    if (apiKey.isNotBlank()) {
                                                        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                                                    }
                                                }
                                                val code = conn.responseCode
                                                if (code in 200..299) {
                                                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                                                    val json = JSONObject(body)
                                                    val dataArray = json.optJSONArray("data") ?: json.optJSONArray("models")
                                                    val list = mutableListOf<String>()
                                                    if (dataArray != null) {
                                                        for (i in 0 until dataArray.length()) {
                                                            val item = dataArray.opt(i)
                                                            if (item is JSONObject) {
                                                                val id = item.optString("id", "").ifBlank { item.optString("name", "") }
                                                                if (id.isNotBlank()) list.add(id)
                                                            } else if (item is String && item.isNotBlank()) {
                                                                list.add(item)
                                                            }
                                                        }
                                                    }
                                                    fetchedModels = list
                                                    if (list.isNotEmpty()) {
                                                        showModelDropdown = true
                                                        modelFetchMessage = "成功获取 ${list.size} 个模型"
                                                    } else {
                                                        modelFetchMessage = "未能解析到模型列表"
                                                    }
                                                } else {
                                                    modelFetchMessage = "拉取失败 (HTTP $code)"
                                                }
                                            } catch (e: Exception) {
                                                modelFetchMessage = "连接出错: ${e.message}"
                                            } finally {
                                                isFetchingModels = false
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = theme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text("拉取列表", fontSize = 11.sp, color = theme.primary, fontWeight = FontWeight.SemiBold)
                                }

                                DropdownMenu(
                                    expanded = showModelDropdown && fetchedModels.isNotEmpty(),
                                    onDismissRequest = { showModelDropdown = false },
                                    modifier = Modifier.background(theme.surfaceContainerLow)
                                ) {
                                    fetchedModels.take(25).forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = theme.textPrimary) },
                                            onClick = {
                                                modelName = m
                                                showModelDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        SettingInputField(
                            value = modelName,
                            placeholder = "deepseek-chat",
                            onValueChange = { modelName = it }
                        )

                        if (modelFetchMessage.isNotEmpty()) {
                            Text(
                                text = modelFetchMessage,
                                style = TextStyle(fontSize = 10.5.sp, color = if (modelFetchMessage.startsWith("成功")) theme.primary else MaterialTheme.colorScheme.error)
                            )
                        }
                    }

                    // 4. 高级参数 (折叠)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.surfaceContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "高级运行参数",
                            style = TextStyle(fontSize = 11.5.sp, color = theme.textSecondary, fontWeight = FontWeight.SemiBold)
                        )
                        IconButton(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = theme.textMuted
                            )
                        }
                    }

                    if (showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 自定义 System Prompt
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("自定义追加提示词 (可选)", fontSize = 11.sp, color = theme.textMuted)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceContainer)
                                        .padding(8.dp)
                                ) {
                                    BasicTextField(
                                        value = customPrompt,
                                        onValueChange = { customPrompt = it },
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = TextStyle(fontSize = 11.5.sp, color = theme.textPrimary),
                                        cursorBrush = SolidColor(theme.primary),
                                        decorationBox = { inner ->
                                            if (customPrompt.isEmpty()) {
                                                Text("例如：优先使用英文排查，日志截取最近 20 行...", fontSize = 11.sp, color = theme.textMuted)
                                            }
                                            inner()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, theme.outline.copy(alpha = 0.35f))
                    ) {
                        Text("取消", color = theme.textSecondary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val newConfig = initialConfig.copy(
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim(),
                                modelName = modelName.trim().ifBlank { "deepseek-chat" },
                                customPrompt = customPrompt.trim(),
                                temperature = temperature,
                                maxTokens = maxTokens
                            )
                            onSaveConfig(newConfig)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                    ) {
                        Text("保存配置", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceContainer)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = theme.textPrimary
            ),
            singleLine = true,
            cursorBrush = SolidColor(theme.primary),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(fontSize = 12.sp, color = theme.textMuted)
                    )
                }
                inner()
            }
        )
    }
}
