package com.termius.clone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

/**
 * 生产级多模态图像处理与压缩转码工具 (TermX Vision Pipeline)
 * 1. 类型识别四重保险：ContentResolver MIME → 文件头魔数嗅探 → 扩展名 → 实际可解码边界，
 *    彻底杜绝各家相册/云盘 Provider 缺失扩展名或返回 octet-stream 时把图片误判为二进制文本；
 * 2. 智能采样与等比缩放，严格限制最长边不超过 1920 像素，彻底规避 OOM；
 * 3. 高质量 JPEG 压缩并转为标准 Data URI Base64 格式，无缝兼容 OpenAI / Claude / Gemini / Qwen-VL；
 * 4. BitmapFactory 失败时用 ImageDecoder (API 28+) 兜底 HEIF/AVIF/渐进式 JPEG，保证真图必可转码；
 * 5. 支持按目标尺寸降采样解码，缩略图不再整张 1920px 大图进内存。
 */
object ImageUtils {

    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif", "avif", "jfif"
    )

    fun isImageFile(fileName: String, extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        if (IMAGE_EXTENSIONS.contains(ext)) return true
        return hasImageExtension(fileName)
    }

    /** 仅凭文件名后缀判定，供 UI 展示分支等无需访问 IO 的场景使用 */
    fun hasImageExtension(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        val dotExt = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return dotExt.isNotBlank() && IMAGE_EXTENSIONS.contains(dotExt)
    }

    /**
     * 文件头魔数嗅探（纯函数，零 IO，便于单元测试）：
     * 只覆盖「不会与纯文本混淆」的强签名：JPEG / PNG / GIF / WEBP / HEIF / AVIF。
     * BMP / TIFF 等弱签名交由解码边界探测兜底，避免把以 "BM" 开头的日志文本误判成图片。
     */
    fun looksLikeImageHeader(head: ByteArray): Boolean {
        if (head.size < 4) return false

        fun at(i: Int): Int = head[i].toInt() and 0xFF

        // JPEG: FF D8 FF
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return true
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return true
        // GIF: "GIF8"
        if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38) return true

        // RIFF....WEBP
        if (head.size >= 12 &&
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50
        ) return true

        // ISO-BMFF 家族：....ftyp + (heic/heix/hevc/heim/heis/mif1/msf1/avif/dash)
        if (head.size >= 12 &&
            at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70
        ) {
            val brand = String(head, 8, 4, Charsets.US_ASCII).lowercase(Locale.ROOT)
            if (brand in setOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif")) {
                return true
            }
        }
        return false
    }

    /**
     * 全方位智能探测指定 Uri 是否为图片（判定优先级：MIME → 文件头魔数 → 实际可解码边界 → 扩展名）
     * 任一层命中即认定图片，宁可走视觉通道，也绝不再把图片当纯文本展开成乱码。
     */
    fun isImageUri(context: Context, uri: Uri, fileName: String = ""): Boolean {
        // 1. 系统 Provider 声明的 MIME (最权威且最廉价)
        try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
            if (mimeType != null && (mimeType.startsWith("image/") || mimeType.contains("vnd.android.cursor.dir/image"))) {
                return true
            }
        } catch (_: Throwable) {}

        // 2. 文件头魔数嗅探：仅采信不会与文本混淆的强签名，零内存消耗（仅读前 16 字节）
        if (looksLikeImageHeader(readHeader(context, uri, 16))) return true

        // 3. 实际解码边界：能解出宽高就是真图（同时兼容 BMP / TIFF 等弱签名格式）
        if (readImageBounds(context, uri) != null) return true

        // 4. 文件名与 URI 路径扩展名
        if (hasImageExtension(fileName)) return true
        val lastSegment = uri.lastPathSegment ?: ""
        if (hasImageExtension(lastSegment)) return true

        return false
    }

    private fun readHeader(context: Context, uri: Uri, byteCount: Int): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                val buf = ByteArray(byteCount)
                var read = 0
                while (read < byteCount) {
                    val n = stream.read(buf, read, byteCount - read)
                    if (n <= 0) break
                    read += n
                }
                if (read <= 0) ByteArray(0) else buf.copyOf(read)
            } ?: ByteArray(0)
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    /** 读取图片宽高（BitmapFactory 优先，ImageDecoder 兜底 HEIF/AVIF）；解不出宽高即返回 null */
    private fun readImageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                return options.outWidth to options.outHeight
            }
        } catch (_: Throwable) {}

        // BitmapFactory 不支持 HEIF/AVIF 时退回 ImageDecoder 读取元信息
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                var size: Pair<Int, Int>? = null
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    size = info.size.width to info.size.height
                    // 目标尺寸压到 1×1，只取元信息，绝不真的把大图展开进内存
                    decoder.setTargetSize(1, 1)
                }
                size?.let { (w, h) -> if (w > 0 && h > 0) return w to h }
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * 读取图片 Uri 并压缩转码为标准 Base64 Data URI (如 data:image/jpeg;base64,...)
     * @param maxDimension 最长边最大像素 (默认 1920，保证架构图与监控文字极度清晰同时控制体积极小)
     * @param quality JPEG 压缩质量 (85 具备极高保真度)
     */
    fun compressAndEncodeImageUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1920,
        quality: Int = 85
    ): String? {
        return try {
            val bitmap = decodeBoundedBitmap(context, uri, maxDimension)
                ?: decodeWithImageDecoder(context, uri, maxDimension)
                ?: return null

            val outputStream = ByteArrayOutputStream()
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (!ok) return null

            val byteArray = outputStream.toByteArray()
            if (byteArray.isEmpty()) return null
            "data:image/jpeg;base64,${Base64.encodeToString(byteArray, Base64.NO_WRAP)}"
        } catch (_: Throwable) {
            null
        }
    }

    /** BitmapFactory 采样解码管线：先读边界算 inSampleSize，再解码并等比缩放到 maxDimension */
    private fun decodeBoundedBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        // 1. 仅读取边界获取尺寸
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // 2. 计算下采样率 inSampleSize 避免解码时内存暴涨
        var sampleSize = 1
        var maxSide = maxOf(origWidth, origHeight)
        while (maxSide / 2 >= maxDimension) {
            sampleSize *= 2
            maxSide /= 2
        }

        // 3. 实际解码采样图像 (RGB_565 节省 50% 内存)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // 4. 精确等比缩小至最长边不超过 maxDimension
        val currentMax = maxOf(bitmap.width, bitmap.height)
        if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax
            val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        return bitmap
    }

    /** ImageDecoder 兜底解码 (API 28+)：直接按目标尺寸解码，天然支持 HEIF/AVIF 与渐进式 JPEG */
    private fun decodeWithImageDecoder(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // 必须是软件位图：硬件位图无法再次 compress 成 JPEG
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (maxDimension > 0 && longest > maxDimension) {
                    val scale = maxDimension.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 将 Base64 或 Data URI 解码为 Compose ImageBitmap
     * @param maxDimension 大于 0 时按最长边降采样解码 (缩略图专用，避免整张 1920px 大图反复进内存)；0 表示原始尺寸
     */
    fun decodeBase64ToImageBitmap(base64Data: String, maxDimension: Int = 0): ImageBitmap? {
        return try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null

            if (maxDimension <= 0) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                return bitmap.asImageBitmap()
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / 2 >= maxDimension) {
                sampleSize *= 2
                maxSide /= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
