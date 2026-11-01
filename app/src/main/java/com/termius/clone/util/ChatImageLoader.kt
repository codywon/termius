package com.termius.clone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 聊天图片加载器：统一处理三种来源，并把解码尺寸压到卡片大小。
 *
 * - `data:image/...;base64,...` / 裸 base64 → 直接解码（复用 ImageUtils，边界预读 + inSampleSize）
 * - `http(s)://...` → 下载到 `cacheDir/chat_images`，再按目标尺寸解码（用户明确要求"下载后内嵌"）
 * - 其它（相对路径等）→ 一律不加载，由调用方降级成文本
 *
 * 双缓存：内存 LruCache（按张数）+ 磁盘缓存（按 URL 哈希）。所有解码都在 IO 线程。
 */
object ChatImageLoader {

    /** 单张图下载上限：超过即放弃，避免移动网络与内存被拖垮 */
    private const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024

    private const val MEMORY_CACHE_COUNT = 24

    private const val DIR_NAME = "chat_images"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val memoryCache = object : LruCache<String, ImageBitmap>(MEMORY_CACHE_COUNT) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    /**
     * 加载并解码为卡片用位图；返回 null 表示"确实拿不到/解不出"，调用方显示降级占位。
     */
    suspend fun load(context: Context, source: String, maxDimension: Int = 1024): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (source.isBlank()) return@withContext null
            val cacheKey = "${source.hashCode()}@$maxDimension"
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val bitmap: ImageBitmap? = when {
                source.startsWith("data:", ignoreCase = true) || looksLikeRawBase64(source) ->
                    ImageUtils.decodeBase64ToImageBitmap(source, maxDimension)

                source.startsWith("http://", ignoreCase = true) ||
                        source.startsWith("https://", ignoreCase = true) -> {
                    val file = downloadToCache(context, source) ?: return@withContext null
                    decodeFile(file, maxDimension)
                }

                else -> null
            }

            if (bitmap != null) memoryCache.put(cacheKey, bitmap)
            bitmap
        }

    /** 预留给"清理缓存"入口使用（例如设置页清空图片缓存） */
    fun clearMemoryCache() = memoryCache.evictAll()

    fun clearDiskCache(context: Context) {
        runCatching { File(context.cacheDir, DIR_NAME).deleteRecursively() }
        memoryCache.evictAll()
    }

    // ---------------------------------------------------------------- 内部实现

    private fun looksLikeRawBase64(source: String): Boolean {
        if (source.length < 64) return false
        // 只看前 64 字符，避免对大载荷做全量遍历
        for (i in 0 until 64) {
            val c = source[i]
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
            if (!ok) return false
        }
        return true
    }

    private fun downloadToCache(context: Context, url: String): File? {
        val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        val target = File(dir, sha1(url) + ".img")
        if (target.exists() && target.length() > 0L) return target

        val tmp = File(dir, target.name + ".tmp")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TermX-Mobile")
                .header("Accept", "image/*")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return null

                var written = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (written > MAX_DOWNLOAD_BYTES) {
                                // 超出上限：丢弃，绝不把半张图塞进缓存
                                tmp.delete()
                                return null
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (written <= 0L) {
                    tmp.delete()
                    return null
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                target
            }
        } catch (_: Throwable) {
            runCatching { tmp.delete() }
            null
        }
    }

    /** 边界预读 + inSampleSize 降采样解码，避免整张大图进内存 */
    private fun decodeFile(file: File, maxDimension: Int): ImageBitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
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
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
