package com.termius.clone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 统一委托至现代标准 CommonMark + GFM 渲染引擎的 MarkdownText 组件
 * 彻底消除 Markwon 类冲突，保证全应用 Markdown 渲染体验一致
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    fontSizeSp: Float = 13f
) {
    MarkdownRenderer(
        content = markdown,
        modifier = modifier,
        isUser = false
    )
}
