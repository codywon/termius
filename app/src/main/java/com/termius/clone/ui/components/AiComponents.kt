package com.termius.clone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.model.DangerousActionRequest
import com.termius.clone.data.model.RiskLevel
import com.termius.clone.ui.theme.LocalAppTheme

/**
 * 生产级高危命令人工审批卡片 (Human-in-the-Loop Approval Card)
 */
@Composable
fun DangerousActionApprovalCard(
    request: DangerousActionRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current

    val (badgeBg, badgeText, headerTitle) = when (request.riskLevel) {
        RiskLevel.CRITICAL -> Triple(Color(0xFFEF4444), "CRITICAL 灾难级破坏操作", "⚠️ 拦截到毁灭性系统指令")
        RiskLevel.HIGH -> Triple(Color(0xFFF97316), "HIGH 高风险变更操作", "⚠️ 拦截到高危变更指令")
        RiskLevel.MEDIUM -> Triple(Color(0xFFF59E0B), "MEDIUM 服务/配置变更", "🔔 核心服务或配置变更提醒")
        RiskLevel.LOW -> Triple(Color(0xFF3B82F6), "LOW 常规指令", "提示")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceContainerLow),
        border = BorderStroke(1.2.dp, badgeBg.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = badgeBg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = headerTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, badgeBg.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeBg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Target Host
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("目标主机:", fontSize = 12.sp, color = theme.textSecondary)
                Text(
                    text = request.hostLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.primary
                )
            }

            // Command Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181825))
                    .padding(10.dp)
            ) {
                Text(
                    text = request.command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    color = Color(0xFFF38BA8),
                    lineHeight = 17.sp
                )
            }

            // Reason & Impact
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg.copy(alpha = 0.08f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• 拦截原因: ${request.reason}",
                    fontSize = 11.5.sp,
                    color = theme.textPrimary,
                    lineHeight = 16.sp
                )
                Text(
                    text = "• 潜在影响: ${request.impact}",
                    fontSize = 11.5.sp,
                    color = badgeBg,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拒绝执行", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = badgeBg)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批准执行", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceContainer.copy(alpha = 0.6f)),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(15.dp)
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
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
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

/**
 * 工具动作状态胶囊 (正在执行命令 / 联网搜索等)
 */
@Composable
fun AiToolActionPill(
    actionText: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    if (actionText.isBlank()) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.primary.copy(alpha = 0.12f),
        border = BorderStroke(0.8.dp, theme.primary.copy(alpha = 0.35f)),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = theme.primary
            )
            Text(
                text = actionText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = theme.primary
            )
        }
    }
}
