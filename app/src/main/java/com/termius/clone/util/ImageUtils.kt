package com.termius.clone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * 生产级多模态图像处理与压缩转码工具 (TermX Vision Pipeline)
 * 1. 智能采样与等比缩放，严格限制最长边不超过 1920 像素，彻底规避 OOM；
 * 2. 高质量 JPEG 压缩并转为标准 Data URI Base64 格式，无缝兼容 OpenAI / Claude / Gemini / Qwen-VL 等多模态视觉大模型；
 * 3. 原生 Compose ImageBitmap 极速解码，零第三方外部库侵入。
 */
object ImageUtils {

    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif"
    )

    fun isImageFile(fileName: String, extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        if (IMAGE_EXTENSIONS.contains(ext)) return true
        val dotExt = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return IMAGE_EXTENSIONS.contains(dotExt)
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
            // 1. 仅读取边界获取尺寸
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

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

            // 3. 实际解码采样图像
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 节省 50% 内存
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

            // 5. 压缩为 JPEG 字节流
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            bitmap.recycle()

            // 6. Base64 编码
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将 Base64 或 Data URI 解码为 Compose ImageBitmap
     */
    fun decodeBase64ToImageBitmap(base64Data: String): ImageBitmap? {
        return try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
