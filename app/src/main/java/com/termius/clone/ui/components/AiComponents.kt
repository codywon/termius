package com.termius.clone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.data.model.RiskLevel
import com.termius.clone.ui.theme.LocalAppTheme

/**
 * 生产级 Linux 高危指令人工审批卡片 (Human-in-the-Loop Approval Card)
 * 沉稳、专业、去戏剧化 UI 味，严谨呈现特征与影响
 */
@Composable
fun DangerousActionApprovalCard(
    request: DangerousActionRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current

    val (badgeBg, badgeText, headerTitle) = when (request.riskLevel) {
        RiskLevel.CRITICAL -> Triple(Color(0xFFDC2626), "不可逆操作", "高危操作确认")
        RiskLevel.HIGH -> Triple(Color(0xFFEA580C), "高风险变更", "变更操作确认")
        RiskLevel.MEDIUM -> Triple(Color(0xFFD97706), "服务变更", "服务操作确认")
        RiskLevel.LOW -> Triple(Color(0xFF2563EB), "常规指令", "操作确认")
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceContainerLow),
        border = BorderStroke(1.dp, badgeBg.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar: 单一盾牌图标 + 沉稳标题 + 右侧不折行微徽标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = badgeBg,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = headerTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBg.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, badgeBg.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeBg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }

            // Target Host
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("目标主机:", fontSize = 11.5.sp, color = theme.textMuted)
                Text(
                    text = request.hostLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textSecondary
                )
            }

            // Command Box with Copy Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF181825),
                border = BorderStroke(0.6.dp, Color(0xFF313244)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF6C7086)
                    )
                    Text(
                        text = request.command,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFF38BA8),
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("command", request.command))
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制命令",
                            tint = Color(0xFFA6ADC8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // 风险特征与潜在影响提炼（紧凑沉稳，去除浮夸底色）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surfaceContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• 风险特征: ${request.reason}",
                    fontSize = 11.5.sp,
                    color = theme.textSecondary,
                    lineHeight = 16.sp
                )
                Text(
                    text = "• 潜在影响: ${request.impact}",
                    fontSize = 11.5.sp,
                    color = badgeBg,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textSecondary),
                    border = BorderStroke(0.8.dp, theme.outline.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拒绝执行", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = badgeBg)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批准执行", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * 深度思考链折叠卡片 (DeepSeek-R1 / QwQ 风格)
 */
@Composable
fun AiThinkingCard(
    reasoningContent: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    var isExpanded by remember { mutableStateOf(false) }

    if (reasoningContent.isBlank()) return

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceContainer.copy(alpha = 0.45f)),
        border = BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "思考过程 (${reasoningContent.length} 字符)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textSecondary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = theme.textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    HorizontalDivider(color = theme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = reasoningContent.trim(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
