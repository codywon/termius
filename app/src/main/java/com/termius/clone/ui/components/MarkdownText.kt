package com.termius.clone.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/**
 * 基于标准工业级库 Markwon 的原生 Markdown 富文本渲染组件
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    fontSizeSp: Float = 13f
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.create(context)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = fontSizeSp
                movementMethod = LinkMovementMethod.getInstance()
                if (textColor != Color.Unspecified) {
                    setTextColor(textColor.toArgb())
                }
                if (linkColor != Color.Unspecified) {
                    setLinkTextColor(linkColor.toArgb())
                }
                setLineSpacing(8f, 1.15f)
            }
        },
        update = { tv ->
            if (textColor != Color.Unspecified) {
                tv.setTextColor(textColor.toArgb())
            }
            if (linkColor != Color.Unspecified) {
                tv.setLinkTextColor(linkColor.toArgb())
            }
            tv.textSize = fontSizeSp
            markwon.setMarkdown(tv, markdown)
        },
        modifier = modifier
    )
}
