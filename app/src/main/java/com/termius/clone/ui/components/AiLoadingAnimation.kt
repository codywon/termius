package com.termius.clone.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.ui.theme.LocalAppTheme

/**
 * 1. Claude 风格多点公转轨迹动画 (Claude Orbiting Trajectory Spinner)
 * 3 颗点围绕圆心轨道平滑旋转，带有相位缩放与光晕透明度呼吸
 */
@Composable
fun ClaudeOrbitLoading(
    modifier: Modifier = Modifier,
    dotCount: Int = 3,
    size: Dp = 20.dp,
    dotSize: Dp = 4.dp,
    color: Color = Color.Unspecified
) {
    val theme = LocalAppTheme.current
    val effectiveColor = if (color != Color.Unspecified) color else theme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "orbit_transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val orbitRadius = (size.toPx() / 2f) - (dotSize.toPx() / 2f)
            val angleStep = 360f / dotCount

            for (i in 0 until dotCount) {
                val currentAngle = (rotation + i * angleStep) % 360f
                val rad = Math.toRadians(currentAngle.toDouble())
                val x = centerOffset.x + (orbitRadius * Math.cos(rad)).toFloat()
                val y = centerOffset.y + (orbitRadius * Math.sin(rad)).toFloat()

                val phase = currentAngle / 360f
                val scale = 0.75f + 0.45f * kotlin.math.sin(phase * Math.PI).toFloat().coerceIn(0f, 1f)
                val alpha = 0.35f + 0.65f * kotlin.math.sin(phase * Math.PI).toFloat().coerceIn(0f, 1f)

                drawCircle(
                    color = effectiveColor.copy(alpha = alpha),
                    radius = (dotSize.toPx() / 2f) * scale,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * 2. OpenAI / ChatGPT 经典弹性波浪三连点跳跃动画
 */
@Composable
fun OpenAiWaveDotsLoading(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    dotSize: Dp = 5.dp,
    spaceBetween: Dp = 4.dp
) {
    val theme = LocalAppTheme.current
    val effectiveColor = if (color != Color.Unspecified) color else theme.textPrimary

    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val delayMillis = i * 160
            val fraction by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0
                        1f at 350
                        0f at 700
                        0f at 1000
                    },
                    initialStartOffset = StartOffset(delayMillis),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot_$i"
            )

            val offsetY = -fraction * 4.dp.value
            val alpha = 0.35f + 0.65f * fraction

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = offsetY * density
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(effectiveColor)
            )
        }
    }
}

/**
 * 3. 正在执行工具动作/命令时的专业状态胶囊卡片（带轨迹旋转）
 */
@Composable
fun AiActionOrbitStatusCard(
    statusText: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.surfaceContainerLow,
        border = BorderStroke(0.6.dp, theme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClaudeOrbitLoading(
                size = 16.dp,
                dotSize = 3.2.dp,
                color = theme.primary
            )
            Text(
                text = statusText,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
