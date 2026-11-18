package com.termius.clone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 聊天图片加载器：统一处理三种来源，并把解码尺寸压到卡片大小。
 *
 * - `data:image/...;base64,...` / 裸 base64 → 直接解码（复用 ImageUtils，边界预读 + inSampleSize）
 * - `http(s)://...` → 经 SSRF 校验后下载到 `cacheDir/chat_images`，再按目标尺寸解码（用户明确要求"下载后内嵌"）
 * - 其它（相对路径等）→ 一律不加载，由调用方降级成文本
 *
 * 双缓存：内存 LruCache（按字节大小容量 + SHA-1 Key）+ 磁盘缓存（按 URL SHA-1）。所有解码都在 IO 线程。
 */
object ChatImageLoader {

    /** 单张图下载上限：超过即放弃，避免移动网络与内存被拖垮 */
    private const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024

    /** 内存缓存容量：16 MiB */
    private const val MEMORY_CACHE_BYTES = 16 * 1024 * 1024

    private const val DIR_NAME = "chat_images"

    private val urlLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockForUrl(urlHash: String): Mutex {
        return urlLocks.computeIfAbsent(urlHash) { Mutex() }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (isBlockedHostNameOrLiteral(hostname)) {
                        throw IOException("已拦截内网或环回主机: $hostname")
                    }
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    if (addresses.any { isBlockedAddress(it) }) {
                        throw IOException("已拦截私网/环回/链路本地 IP 地址: $hostname")
                    }
                    return addresses
                }
            })
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                if (!isSafePublicHttpUrlSyntax(request.url.toString())) {
                    throw IOException("已拦截非公网或非法图片 URL")
                }
                chain.proceed(request)
            })
            .build()
    }

    private val memoryCache = object : LruCache<String, ImageBitmap>(MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * 4L
            return bytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }

    /**
     * 加载并解码为卡片用位图；返回 null 表示"确实拿不到/解不出"，调用方显示降级占位。
     */
    suspend fun load(context: Context, source: String, maxDimension: Int = 1024): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (source.isBlank()) return@withContext null
            val cacheKey = "${sha1(source)}@$maxDimension"
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

    // ---------------------------------------------------------------- SSRF 安全校验

    internal fun isSafePublicHttpUrlSyntax(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val rawHost = uri.host ?: return false
        val host = rawHost.trim().removeSurrounding("[", "]").trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty()) return false
        return !isBlockedHostNameOrLiteral(host)
    }

    internal fun isBlockedHostNameOrLiteral(rawHost: String): Boolean {
        val host = rawHost.trim().removeSurrounding("[", "]").trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty()) return true
        if (host == "localhost" ||
            host == "localhost.localdomain" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal")
        ) {
            return true
        }

        // 任何全数字/八进制/十六进制简写 IPv4 变形（如 2130706433、0x7f000001、127.1、0177.0.0.1）均拦截
        val dotParts = host.split('.')
        val allNumericLikeParts = dotParts.isNotEmpty() && dotParts.all { part ->
            part.isNotEmpty() && (part.all { it in '0'..'9' } || part.startsWith("0x"))
        }
        if (allNumericLikeParts) {
            if (dotParts.size != 4) return true
            if (dotParts.any { (it.length > 1 && it.startsWith('0')) || it.startsWith("0x") }) {
                return true
            }
            val octets = dotParts.map { it.toIntOrNull() ?: return true }
            if (octets.any { it !in 0..255 }) return true
            return isBlockedIpv4Octets(octets[0], octets[1], octets[2], octets[3])
        }

        // IPv6 字面量检测（InetAddress 对纯 IPv6 字面量不发起 DNS 请求）
        if (':' in host) {
            return try {
                val addr = InetAddress.getByName(host)
                isBlockedAddress(addr)
            } catch (_: Exception) {
                true
            }
        }
        return false
    }

    internal fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            return isBlockedIpv4Octets(
                bytes[0].toInt() and 0xFF,
                bytes[1].toInt() and 0xFF,
                bytes[2].toInt() and 0xFF,
                bytes[3].toInt() and 0xFF
            )
        }
        if (bytes.size == 16) {
            // 检查 IPv4-mapped IPv6 (::ffff:a.b.c.d)
            val isV4Mapped = (0..9).all { bytes[it].toInt() == 0 } &&
                (bytes[10].toInt() and 0xFF) == 0xFF &&
                (bytes[11].toInt() and 0xFF) == 0xFF
            if (isV4Mapped) {
                return isBlockedIpv4Octets(
                    bytes[12].toInt() and 0xFF,
                    bytes[13].toInt() and 0xFF,
                    bytes[14].toInt() and 0xFF,
                    bytes[15].toInt() and 0xFF
                )
            }
            // :: (全 0) 与 ::1
            val first15Zero = (0..14).all { bytes[it].toInt() == 0 }
            val lastByte = bytes[15].toInt() and 0xFF
            if (first15Zero && (lastByte == 0 || lastByte == 1)) return true

            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // fc00::/7 (Unique Local Address: fc00:: ~ fdff::)
            if ((b0 and 0xFE) == 0xFC) return true
            // fe80::/10 (Link-Local)
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
        }
        return false
    }

    private fun isBlockedIpv4Octets(b0: Int, b1: Int, b2: Int, b3: Int): Boolean {
        if (b0 == 0) return true                         // 0.0.0.0/8 ("this network")
        if (b0 == 10) return true                        // 10.0.0.0/8
        if (b0 == 127) return true                       // 127.0.0.0/8
        if (b0 == 169 && b1 == 254) return true          // 169.254.0.0/16
        if (b0 == 172 && b1 in 16..31) return true       // 172.16.0.0/12
        if (b0 == 192 && b1 == 168) return true          // 192.168.0.0/16
        if (b0 == 100 && b1 in 64..127) return true      // 100.64.0.0/10 (CGNAT)
        if (b0 >= 224) return true                       // Multicast / Reserved
        return false
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

    private suspend fun downloadToCache(context: Context, url: String): File? {
        if (!isSafePublicHttpUrlSyntax(url)) return null

        val urlHash = sha1(url)
        val mutex = lockForUrl(urlHash)
        return mutex.withLock {
            val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
            val target = File(dir, "$urlHash.img")
            if (target.exists() && target.length() > 0L) return@withLock target

            var tmp: File? = null
            try {
                val host = URI(url.trim()).host?.trim()?.removeSurrounding("[", "]")?.trimEnd('.')
                    ?: return@withLock null
                val resolved = InetAddress.getAllByName(host)
                if (resolved.isEmpty() || resolved.any { isBlockedAddress(it) }) {
                    return@withLock null
                }

                tmp = File.createTempFile(urlHash, ".tmp", dir)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TermX-Mobile")
                    .header("Accept", "image/*")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withLock null
                    val body = response.body ?: return@withLock null
                    if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withLock null

                    var written = 0L
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                written += read
                                if (written > MAX_DOWNLOAD_BYTES) {
                                    // 超出上限：丢弃，绝不把半张图塞进缓存
                                    return@withLock null
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (written <= 0L) {
                        return@withLock null
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                    }
                    target
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { tmp?.let { if (it.exists()) it.delete() } }
            }
        }
    }

    /** 边界预读 + inSampleSize 降采样解码，避免整张大图进内存 */
    private fun decodeFile(file: File, maxDimension: Int): ImageBitmap? {
        return try {
            if (maxDimension <= 0) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
            }

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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
