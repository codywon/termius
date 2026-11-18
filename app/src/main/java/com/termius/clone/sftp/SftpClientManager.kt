package com.termius.clone.sftp

import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.data.security.KeyVaultManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.xfer.InMemoryDestFile
import net.schmizz.sshj.xfer.InMemorySourceFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SftpItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val mtime: Long,
    val permissions: String,
    val formattedSize: String = if (isDirectory) "" else formatSize(size),
    val formattedTime: String = formatDate(mtime)
)

private data class CachedDirectory(
    val items: List<SftpItem>,
    val fetchedAt: Long
)

class SftpClientManager {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null
    private var lastHost: HostEntity? = null
    private var lastIdentity: IdentityEntity? = null

    // 协程互斥锁：彻底杜绝 SFTP 单通道并发调用导致的 Socket Abort
    private val sftpMutex = Mutex()

    // 内存目录缓存 (路径 -> 缓存项)
    private val directoryCache = ConcurrentHashMap<String, CachedDirectory>()

    // 缓存新鲜期：15 秒内视为无需重复请求，60 秒内可用作 SWR 即时渲染
    companion object {
        const val FRESH_TTL_MS = 15_000L
        const val STALE_TTL_MS = 60_000L
        const val MAX_TEXT_FILE_BYTES = 2 * 1024 * 1024L
    }

    val isConnected: Boolean
        get() = sshClient?.isConnected == true && sftpClient != null

    private fun normalizePath(path: String): String = path.trimEnd('/').ifEmpty { "/" }

    private fun closeClientsQuietly() {
        try { sftpClient?.close() } catch (_: Throwable) {}
        try { sshClient?.disconnect() } catch (_: Throwable) {}
        try { sshClient?.close() } catch (_: Throwable) {}
        sftpClient = null
        sshClient = null
    }

    suspend fun connect(host: HostEntity, identity: IdentityEntity? = null) = withContext(Dispatchers.IO) {
        sftpMutex.withLock {
            directoryCache.clear()
            lastHost = host
            lastIdentity = identity
            connectInternal(host, identity)
        }
    }

    private fun connectInternal(host: HostEntity, identity: IdentityEntity?) {
        // 建立新连接前先安全关闭旧连接，杜绝连接与心跳线程泄漏
        closeClientsQuietly()

        com.termius.clone.TermiusApplication.setupBouncyCastle()
        val client = SSHClient()
        var newSftp: SFTPClient? = null
        try {
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 15_000
            client.timeout = 15_000
            client.connect(host.hostname, host.port)
            // 开启 SSH 心跳保活，防止移动网络 NAT 超时切断连接
            try {
                client.connection.keepAlive.setKeepAliveInterval(15)
            } catch (_: Exception) {}

            val username = if (host.authType == AuthType.IDENTITY_REF && identity != null) {
                identity.username
            } else {
                host.username
            }

            when (host.authType) {
                AuthType.PASSWORD -> {
                    val decryptedPassword = KeyVaultManager.decrypt(host.password)
                    client.authPassword(username, decryptedPassword)
                }
                AuthType.KEY -> {
                    val decryptedKey = KeyVaultManager.decrypt(host.privateKey)
                    val decryptedPassphrase = KeyVaultManager.decrypt(host.passphrase)
                    val keyProvider: KeyProvider = if (decryptedPassphrase.isNotEmpty()) {
                        client.loadKeys(decryptedKey, null, PasswordUtils.createOneOff(decryptedPassphrase.toCharArray()))
                    } else {
                        client.loadKeys(decryptedKey, null, null)
                    }
                    client.authPublickey(username, keyProvider)
                }
                AuthType.IDENTITY_REF -> {
                    if (identity != null) {
                        val decryptedKey = KeyVaultManager.decrypt(identity.privateKey)
                        if (decryptedKey.isNotEmpty()) {
                            val decryptedPassphrase = KeyVaultManager.decrypt(identity.passphrase)
                            val keyProvider: KeyProvider = if (decryptedPassphrase.isNotEmpty()) {
                                client.loadKeys(decryptedKey, null, PasswordUtils.createOneOff(decryptedPassphrase.toCharArray()))
                            } else {
                                client.loadKeys(decryptedKey, null, null)
                            }
                            client.authPublickey(username, keyProvider)
                        } else {
                            val decryptedPassword = KeyVaultManager.decrypt(identity.password)
                            client.authPassword(username, decryptedPassword)
                        }
                    } else {
                        val decryptedPassword = KeyVaultManager.decrypt(host.password)
                        client.authPassword(username, decryptedPassword)
                    }
                }
            }

            newSftp = client.newSFTPClient()
            sshClient = client
            sftpClient = newSftp
        } catch (e: Throwable) {
            try { newSftp?.close() } catch (_: Throwable) {}
            try { client.disconnect() } catch (_: Throwable) {}
            try { client.close() } catch (_: Throwable) {}
            throw e
        }
    }

    private fun isNetworkDisconnection(e: Throwable): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val msg = cur.message?.lowercase() ?: ""
            if (cur is java.net.SocketException ||
                cur is net.schmizz.sshj.transport.TransportException ||
                cur is java.io.EOFException ||
                msg.contains("software caused connection abort") ||
                msg.contains("broken pipe") ||
                msg.contains("connection reset") ||
                msg.contains("socket closed") ||
                msg.contains("not connected")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    /**
     * 线程安全且具备网络自愈重试的 SFTP 执行模板
     */
    private suspend fun <T> withSftp(block: (SFTPClient) -> T): T = withContext(Dispatchers.IO) {
        sftpMutex.withLock {
            coroutineContext.ensureActive()
            val host = lastHost
            if (host != null && (sshClient?.isConnected != true || sftpClient == null)) {
                coroutineContext.ensureActive()
                closeClientsQuietly()
                connectInternal(host, lastIdentity)
            }

            val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
            try {
                block(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isNetworkDisconnection(e) && host != null) {
                    coroutineContext.ensureActive()
                    closeClientsQuietly()
                    connectInternal(host, lastIdentity)
                    val retryClient = sftpClient ?: throw e
                    block(retryClient)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * 获取指定路径的缓存列表（用于 SWR 模式即时秒开呈现）
     */
    fun getCachedItems(path: String): List<SftpItem>? {
        val normalized = normalizePath(path)
        val entry = directoryCache[normalized] ?: return null
        val age = System.currentTimeMillis() - entry.fetchedAt
        return if (age <= STALE_TTL_MS) entry.items else null
    }

    /**
     * 检查缓存是否处于绝对新鲜期（新鲜期内可直接跳过后台请求）
     */
    fun isCacheFresh(path: String): Boolean {
        val normalized = normalizePath(path)
        val entry = directoryCache[normalized] ?: return false
        return (System.currentTimeMillis() - entry.fetchedAt) <= FRESH_TTL_MS
    }

    /**
     * 精确清除目录缓存
     */
    fun invalidateCache(path: String? = null) {
        if (path == null) {
            directoryCache.clear()
        } else {
            val normalized = normalizePath(path)
            directoryCache.remove(normalized)
            directoryCache.remove("$normalized/")
            val parent = File(normalized).parent?.replace("\\", "/")?.let { normalizePath(it) }
            if (parent != null) {
                directoryCache.remove(parent)
                directoryCache.remove("$parent/")
            }
        }
    }

    /**
     * 列出目录项（支持缓存、互斥访问与网络自愈重连）
     */
    suspend fun listDirectory(path: String, forceRefresh: Boolean = false): List<SftpItem> = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(path)
        if (!forceRefresh) {
            val cached = getCachedItems(normalizedPath)
            if (cached != null && isCacheFresh(normalizedPath)) {
                return@withContext cached
            }
        }

        val remoteFiles: List<RemoteResourceInfo> = withSftp { client ->
            client.ls(normalizedPath)
        }

        val items = remoteFiles.map { info ->
            val isDir = info.attributes.mode.type == FileMode.Type.DIRECTORY
            SftpItem(
                name = info.name,
                path = info.path,
                isDirectory = isDir,
                size = info.attributes.size,
                mtime = info.attributes.mtime * 1000L,
                permissions = formatPermissions(isDir, info.attributes.mode.mask)
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        directoryCache[normalizedPath] = CachedDirectory(items, System.currentTimeMillis())
        items
    }

    suspend fun downloadFile(remotePath: String, localFile: File) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.get(remotePath, localFile.absolutePath)
        }
    }

    suspend fun downloadToStream(remotePath: String, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val dest = StreamDestFile(outputStream)
        withSftp { client ->
            client.get(remotePath, dest)
        }
    }

    suspend fun uploadFile(localFile: File, remotePath: String) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.put(localFile.absolutePath, remotePath)
        }
        invalidateCache(File(remotePath).parent)
    }

    suspend fun rename(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.rename(oldPath, newPath)
        }
        invalidateCache(File(oldPath).parent)
        invalidateCache(File(newPath).parent)
    }

    suspend fun deleteFile(remotePath: String) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.rm(remotePath)
        }
        invalidateCache(File(remotePath).parent)
    }

    suspend fun deleteDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.rmdir(remotePath)
        }
        invalidateCache(File(remotePath).parent)
        invalidateCache(remotePath)
    }

    suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        withSftp { client ->
            client.mkdirs(remotePath)
        }
        invalidateCache(File(remotePath).parent)
    }

    suspend fun createEmptyFile(remotePath: String) = withContext(Dispatchers.IO) {
        val fileName = File(remotePath).name.ifEmpty { "new_file" }
        val source = MemorySourceFile(fileName, ByteArray(0))
        withSftp { client ->
            client.put(source, remotePath)
        }
        invalidateCache(File(remotePath).parent)
    }

    suspend fun readTextFile(remotePath: String): String = withContext(Dispatchers.IO) {
        val dest = BoundedMemoryDestFile(MAX_TEXT_FILE_BYTES)
        withSftp { client ->
            val remoteSize = runCatching { client.stat(remotePath).size }.getOrNull()
            if (remoteSize != null && remoteSize > MAX_TEXT_FILE_BYTES) {
                throw IllegalStateException("文件过大（超过 ${MAX_TEXT_FILE_BYTES / (1024 * 1024)} MB），暂不支持在线编辑，请下载后查看")
            }
            client.get(remotePath, dest)
        }
        val bytes = dest.bytes
        val checkLen = minOf(bytes.size, 8192)
        for (i in 0 until checkLen) {
            if (bytes[i] == 0.toByte()) {
                throw IllegalStateException("检测到二进制文件（包含 NUL 字节），不支持以文本方式打开")
            }
        }
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun writeTextFile(remotePath: String, content: String) = withContext(Dispatchers.IO) {
        val fileName = File(remotePath).name.ifEmpty { "file" }
        val bytes = content.toByteArray(Charsets.UTF_8)
        val source = MemorySourceFile(fileName, bytes)
        withSftp { client ->
            client.put(source, remotePath)
        }
        invalidateCache(File(remotePath).parent)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        sftpMutex.withLock {
            try {
                directoryCache.clear()
                closeClientsQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                sftpClient = null
                sshClient = null
                lastHost = null
                lastIdentity = null
            }
        }
    }

    private fun formatPermissions(isDir: Boolean, mask: Int): String {
        val sb = java.lang.StringBuilder(if (isDir) "d" else "-")
        val rwx = charArrayOf(
            if ((mask and 0x100) != 0) 'r' else '-',
            if ((mask and 0x80) != 0) 'w' else '-',
            if ((mask and 0x40) != 0) 'x' else '-',
            if ((mask and 0x20) != 0) 'r' else '-',
            if ((mask and 0x10) != 0) 'w' else '-',
            if ((mask and 0x8) != 0) 'x' else '-',
            if ((mask and 0x4) != 0) 'r' else '-',
            if ((mask and 0x2) != 0) 'w' else '-',
            if ((mask and 0x1) != 0) 'x' else '-'
        )
        rwx.forEach { sb.append(it) }
        return sb.toString()
    }
}

/**
 * 纯内存源文件传输，消除磁盘临时文件与闪存写入磨损
 */
private class MemorySourceFile(
    private val fileName: String,
    private val data: ByteArray
) : InMemorySourceFile() {
    override fun getName(): String = fileName
    override fun getLength(): Long = data.size.toLong()
    override fun getInputStream(): InputStream = ByteArrayInputStream(data)
}

/**
 * 带体积上限的内存目标文件接收，防止超大文本读取引发 OOM
 */
private class BoundedMemoryDestFile(
    private val maxBytes: Long
) : InMemoryDestFile() {
    private val stream = ByteArrayOutputStream()
    val bytes: ByteArray
        get() = stream.toByteArray()

    override fun getLength(): Long = stream.size().toLong()

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (stream.size() + 1L > maxBytes) {
                throw IllegalStateException("文件过大（超过 ${maxBytes / (1024 * 1024)} MB），暂不支持在线编辑，请下载后查看")
            }
            stream.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (stream.size().toLong() + len > maxBytes) {
                throw IllegalStateException("文件过大（超过 ${maxBytes / (1024 * 1024)} MB），暂不支持在线编辑，请下载后查看")
            }
            stream.write(b, off, len)
        }
    }

    override fun getOutputStream(append: Boolean): OutputStream = getOutputStream()
}

/**
 * 流式目标文件接收，直接写入 SAF 等外部 OutputStream
 */
private class StreamDestFile(
    private val targetStream: OutputStream
) : InMemoryDestFile() {
    override fun getLength(): Long = 0L

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = targetStream.write(b)
        override fun write(b: ByteArray) = targetStream.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = targetStream.write(b, off, len)
        override fun flush() = targetStream.flush()
        override fun close() {
            targetStream.flush()
        }
    }

    override fun getOutputStream(append: Boolean): OutputStream = getOutputStream()
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
    return String.format(Locale.getDefault(), "%.1f %cB", bytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

