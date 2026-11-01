package com.termius.clone.util

/**
 * 聊天正文净化器：把「二进制 / 超长 base64」从文本排版层赶出去。
 *
 * 背景（真实缺陷）：
 * 1) 模型"想展示图片"时会输出裸 base64 或 ```data:image/png;base64,...```，这些是普通段落，
 *    会被 Markdown 原样排版成一屏乱码；
 * 2) 网关可能把 image part 摊平成文本（`{"type":"image_url","image_url":{"url":"data:..."}}`），
 *    整段 JSON 连 base64 一起变成正文；
 * 3) 流式渲染时 data URI 收到一半，剩下半截 base64 会被当文本上屏。
 *
 * 处理策略：
 * - `data:image/(png|jpeg|webp|gif);base64,...` → 摘成 `![alt](txcimg:N)` 令牌，正文不再承载载荷；
 * - 其它协议（尤其 svg）→ 折叠成一句人话，绝不渲染；
 * - 裸 base64（连续 ≥512 字符、无空白）→ 若魔数是 PNG/JPEG/GIF/WEBP 则转图片令牌，否则折叠成一句话；
 * - 流式模式下，尾部未收完的 data URI 直接截断（本帧不渲染），避免半截 base64 上屏。
 *
 * 纯 Kotlin 实现（只用 java.util.Base64 做魔数嗅探），不依赖 Android，可直接单测。
 */
object ChatContentSanitizer {

    /** 图片令牌前缀，形如 `txcimg:0` / `txcimg:1`，序号对应 [Result.images] 下标 */
    const val TOKEN_PREFIX = "txcimg:"

    /** 裸 base64 的最低折叠长度：太短的可能是哈希、令牌、合法编码片段，不动 */
    const val MIN_BARE_BASE64_RUN = 512

    private const val FENCE = "```"
    private const val MAX_PEEK_FOR_SYNTAX = 256

    private val ALLOWED_MIME = setOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif")

    private val DATA_URI_PREFIX = Regex("""data:([a-zA-Z0-9.+\-]+/[a-zA-Z0-9.+\-]+);base64,""", RegexOption.IGNORE_CASE)

    private fun isBase64Char(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='

    /**
     * @param text        净化后的正文（图片已变成 `![alt](txcimg:N)` 或折叠文案）
     * @param images      与 token 序号一一对应的图片载荷（data URI / http(s) URL / 裸 base64）
     * @param pendingTail 尾部有未收完的 data URI，本帧请勿渲染该片段
     * @param foldedCount 被折叠的非图片二进制块数量
     */
    data class Result(
        val text: String,
        val images: List<String> = emptyList(),
        val pendingTail: Boolean = false,
        val foldedCount: Int = 0
    )

    fun sanitize(raw: String, isStreaming: Boolean = false): Result {
        if (raw.isEmpty()) return Result(raw)
        if (raw.length < 16) return Result(raw)

        val out = StringBuilder(raw.length)
        val images = ArrayList<String>(2)
        var folded = 0
        var pending = false
        var inFence = false
        var i = 0

        while (i < raw.length) {
            // 1. 代码围栏切换：围栏内的 base64 只折叠成文字，不转图片令牌（否则会破坏代码块语义）
            if (raw.startsWith(FENCE, i)) {
                inFence = !inFence
                out.append(FENCE)
                i += FENCE.length
                continue
            }

            // 2. data URI
            if (!inFence && (raw[i] == 'd' || raw[i] == 'D')) {
                val prefixMatch = if (i + 5 <= raw.length && raw.regionMatches(i, "data:", 0, 5, ignoreCase = true)) {
                    DATA_URI_PREFIX.find(raw, i)?.takeIf { it.range.first == i }
                } else null
                if (prefixMatch != null) {
                    val mime = prefixMatch.groupValues[1].lowercase()
                    val uriStart = i
                    val payloadStart = prefixMatch.range.last + 1
                    var payloadEnd = payloadStart
                    while (payloadEnd < raw.length && isBase64Char(raw[payloadEnd])) payloadEnd++

                    val reachEnd = payloadEnd >= raw.length
                    if (reachEnd && isStreaming) {
                        // 流式未收完：把这一整段（连同可能存在的 ![alt]( 前缀）截掉
                        val cut = findSyntaxStart(out)
                        if (cut in 0..out.length) out.setLength(cut)
                        pending = true
                        break
                    }

                    val payload = raw.substring(uriStart, payloadEnd)
                    if (mime !in ALLOWED_MIME) {
                        // svg / pdf / 未知协议：折叠，绝不渲染
                        out.append(foldedChip(estimateBytes(payload)))
                        folded++
                    } else {
                        appendImageToken(out, images, payload)
                    }
                    i = payloadEnd
                    continue
                }
            }

            // 3. 裸 base64 长串（围栏内只折叠、围栏外可转图片）
            if (isBase64Char(raw[i]) && looksLikeBase64RunStart(raw, i)) {
                var end = i
                while (end < raw.length && isBase64Char(raw[end])) end++
                val run = raw.substring(i, end)
                if (run.length >= MIN_BARE_BASE64_RUN) {
                    if (!inFence && sniffImageMagic(run) != null) {
                        appendImageToken(out, images, run)
                    } else {
                        out.append(foldedChip(estimateBytes(run)))
                        folded++
                    }
                    i = end
                    continue
                }
            }

            out.append(raw[i])
            i++
        }

        return Result(
            text = out.toString(),
            images = images,
            pendingTail = pending,
            foldedCount = folded
        )
    }

    // ---------------------------------------------------------------- 内部工具

    /** 判断当前位置是否是 base64 长串的起点（前一个字符不能是 base64 字符） */
    private fun looksLikeBase64RunStart(raw: String, index: Int): Boolean {
        if (index > 0 && isBase64Char(raw[index - 1])) return false
        // 快速筛选：后续 64 个字符必须全部是 base64 字符，否则不可能是长载荷
        val limit = minOf(raw.length, index + 64)
        if (limit - index < 32) return false
        for (k in index until limit) {
            if (!isBase64Char(raw[k])) return false
        }
        return true
    }

    /** 追加图片令牌：若正处在 `![alt](` 的目标位置则原地替换，否则补一个完整的图片语法 */
    private fun appendImageToken(out: StringBuilder, images: ArrayList<String>, payload: String) {
        val index = images.size
        images.add(payload)
        val token = "$TOKEN_PREFIX$index"
        if (out.length >= 2 && out[out.length - 1] == '(' && out[out.length - 2] == ']') {
            out.append(token)
        } else {
            out.append("![图片 ${index + 1}]($token)")
        }
    }

    /** 流式截断时，把已被写入的 `![alt](` 前缀也一起撤销，避免留下残缺语法 */
    private fun findSyntaxStart(out: StringBuilder): Int {
        val text = out.toString()
        val open = text.lastIndexOf("![")
        if (open < 0 || text.length - open > MAX_PEEK_FOR_SYNTAX) return out.length
        val tail = text.substring(open)
        // 只认 `![...](` 这种"正在等待目标"的形态
        return if (tail.endsWith("](") || tail.endsWith("]")) open else out.length
    }

    private fun foldedChip(byteSize: Int): String =
        if (byteSize > 0) "[二进制数据 · ${formatBytes(byteSize)} · 已折叠]" else "[二进制数据 · 已折叠]"

    private fun estimateBytes(base64: String): Int = (base64.length * 3) / 4

    private fun formatBytes(bytes: Int): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** 魔数嗅探：只认常见光栅图片，避免把任意 base64 当图片去解码 */
    private fun sniffImageMagic(base64: String): String? {
        return try {
            val head = base64.take(24)
            val bytes = java.util.Base64.getDecoder().decode(head.padEnd(24, 'A'))
            if (bytes.size < 12) return null
            when {
                bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
                bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "image/gif"
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() -> "image/webp"
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
