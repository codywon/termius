package com.termius.clone

import com.termius.clone.util.ChatContentSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聊天正文净化器的回归用例。
 * 核心断言：任何二进制/超长 base64 都不得以原文形式留在正文里。
 */
class ChatContentSanitizerTest {

    // 真实 PNG 的魔数前缀（\x89PNG\r\n\x1a\n）+ 足量载荷
    private val pngPayload = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AGtQ0i/9QAAAABJRU5ErkJggg=="

    @Test
    fun plainChineseTextIsUntouched() {
        val source = "这台机器的磁盘快满了，建议先清理 /var/log 下的历史日志，然后再重启 nginx 服务。"
        val result = ChatContentSanitizer.sanitize(source)
        assertEquals(source, result.text)
        assertTrue(result.images.isEmpty())
        assertFalse(result.pendingTail)
    }

    @Test
    fun markdownImageWithDataUriBecomesToken() {
        val source = "这是架构图：\n\n![架构拓扑](data:image/png;base64,$pngPayload)\n\n请分析。"
        val result = ChatContentSanitizer.sanitize(source)

        assertTrue("正文不得残留 base64", !result.text.contains("iVBORw0KGgo"))
        assertTrue("应保留 alt 文本", result.text.contains("![架构拓扑](txcimg:0)"))
        assertEquals(1, result.images.size)
        assertTrue(result.images[0].startsWith("data:image/png;base64,"))
    }

    @Test
    fun bareDataUriBecomesImageToken() {
        val source = "图在这里 data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U" +
                "HR0aHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPDs0NDP/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIy"
        val result = ChatContentSanitizer.sanitize(source, isStreaming = false)

        assertFalse(result.text.contains("base64"))
        assertTrue(result.text.contains("txcimg:0"))
        assertEquals(1, result.images.size)
    }

    @Test
    fun svgDataUriIsFoldedNeverRendered() {
        val svg = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxzY3JpcHQ+YWxlcnQoMSk8L3NjcmlwdD48L3N2Zz4="
        val result = ChatContentSanitizer.sanitize("看看这个 $svg 图")

        assertFalse(result.text.contains("PHN2Zy"))
        assertTrue(result.text.contains("已折叠"))
        assertTrue("svg 绝不进图片通道", result.images.isEmpty())
    }

    @Test
    fun streamingPartialDataUriIsHeldBack() {
        val partial = "架构图：![拓扑](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEA"
        val result = ChatContentSanitizer.sanitize(partial, isStreaming = true)

        assertTrue(result.pendingTail)
        assertFalse("半截 base64 不得上屏", result.text.contains("iVBOR"))
        assertFalse("残缺的图片语法也不该留下", result.text.endsWith("]("))
    }

    @Test
    fun completeDataUriAtEndIsAcceptedWhenNotStreaming() {
        val complete = "![x](data:image/png;base64,$pngPayload)"
        val result = ChatContentSanitizer.sanitize(complete, isStreaming = false)

        assertFalse(result.pendingTail)
        assertEquals(1, result.images.size)
        assertTrue(result.text.contains("txcimg:0"))
    }

    @Test
    fun rawPngBase64RunBecomesImage() {
        val longPayload = pngPayload.repeat(6) // 远超 512 字符阈值
        val result = ChatContentSanitizer.sanitize("服务端返回：$longPayload")

        assertEquals(1, result.images.size)
        assertTrue(result.text.contains("txcimg:0"))
        assertFalse(result.text.contains("iVBORw0KGgo"))
    }

    @Test
    fun nonImageBase64RunIsFoldedNotDecoded() {
        // ZIP 头 PK\x03\x04 的 base64 前缀，非图片
        val longPayload = "UEsDBBQAAAAIAA" + "A".repeat(600)
        val result = ChatContentSanitizer.sanitize("附件内容：$longPayload")

        assertTrue(result.images.isEmpty())
        assertTrue(result.text.contains("已折叠"))
        assertFalse(result.text.contains("UEsDBBQ"))
    }

    @Test
    fun base64InsideCodeFenceIsFoldedNotTokenized() {
        val longPayload = pngPayload.repeat(6)
        val source = "示例数据：\n```\n$longPayload\n```\n以上是原始载荷。"
        val result = ChatContentSanitizer.sanitize(source)

        assertTrue("围栏内不转图片令牌", result.images.isEmpty())
        assertTrue(result.text.contains("已折叠"))
        assertFalse(result.text.contains("iVBORw0KGgo"))
    }

    @Test
    fun shortBase64LikeTokenIsNotTouched() {
        val source = "提交号 a3f9c1d2e4b5 与令牌 tok_9f8e7d6c5b4a 均保持原样。"
        val result = ChatContentSanitizer.sanitize(source)
        assertEquals(source, result.text)
    }
}
