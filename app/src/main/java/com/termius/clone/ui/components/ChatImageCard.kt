package com.termius.clone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.ui.theme.LocalAppTheme
import com.termius.clone.util.ChatImageLoader
import com.termius.clone.util.Strings

/**
 * 聊天图片的统一状态。
 * 三个来源（用户附件 / 模型内联图 / 折叠后的裸 base64）共用同一套状态与卡片样式。
 */
sealed interface ChatImageState {
    data object Loading : ChatImageState
    data class Ready(val bitmap: ImageBitmap) : ChatImageState
    data class Unavailable(val detail: String? = null) : ChatImageState
}

/**
 * 异步加载一张聊天图片（磁盘/内存缓存 + 降采样解码，全部在 IO 线程）。
 */
@Composable
fun rememberChatImageState(source: String?, maxDimension: Int = 1024): ChatImageState {
    val context = LocalContext.current
    var state by remember(source, maxDimension) {
        mutableStateOf<ChatImageState>(if (source.isNullOrBlank()) ChatImageState.Unavailable() else ChatImageState.Loading)
    }
    LaunchedEffect(source, maxDimension) {
        val target = source
        if (target.isNullOrBlank()) {
            state = ChatImageState.Unavailable()
            return@LaunchedEffect
        }
        state = ChatImageState.Loading
        val bitmap = ChatImageLoader.load(context, target, maxDimension)
        state = if (bitmap != null) ChatImageState.Ready(bitmap) else ChatImageState.Unavailable()
    }
    return state
}

/**
 * 聊天图片卡片：用户附件与模型内联图片共用，保证样式与交互一致。
 *
 * @param caption  底部说明（文件名 · 大小 / 来源域名），可为空
 * @param onOpen   点击查看大图；为 null 时不显示全屏角标
 */
@Composable
fun ChatImageCard(
    state: ChatImageState,
    modifier: Modifier = Modifier,
    caption: String? = null,
    isUserBubble: Boolean = false,
    maxImageHeight: Dp = 220.dp,
    onOpen: (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(10.dp)
    val containerColor = if (isUserBubble) {
        Color.Black.copy(alpha = 0.18f)
    } else {
        theme.surfaceContainer.copy(alpha = 0.6f)
    }
    val borderColor = if (isUserBubble) {
        Color.White.copy(alpha = 0.25f)
    } else {
        theme.outline.copy(alpha = 0.25f)
    }

    val openable = onOpen != null && state is ChatImageState.Ready

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(0.6.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .then(if (openable) Modifier.clickable { onOpen?.invoke() } else Modifier)
    ) {
        Column {
            when (state) {
                is ChatImageState.Ready -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = maxImageHeight)
                ) {
                    Image(
                        bitmap = state.bitmap,
                        contentDescription = caption ?: "图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (openable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = if (Strings.isZh) "查看大图" else "View full size",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                is ChatImageState.Loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (isUserBubble) Color.White else theme.primary
                    )
                    Text(
                        text = if (Strings.isZh) "图片载入中…" else "Loading image…",
                        fontSize = 12.sp,
                        color = if (isUserBubble) Color.White.copy(alpha = 0.85f) else theme.textSecondary
                    )
                }

                is ChatImageState.Unavailable -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = if (isUserBubble) Color.White.copy(alpha = 0.85f) else theme.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (Strings.isZh) "图片无法解码" else "Image cannot be decoded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isUserBubble) Color.White else theme.textSecondary
                    )
                }
            }

            if (!caption.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = caption,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isUserBubble) Color.White.copy(alpha = 0.8f) else theme.textMuted
                    )
                }
            }
        }
    }
}
