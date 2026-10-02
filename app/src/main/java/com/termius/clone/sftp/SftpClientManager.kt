package com.termius.clone.sftp

import com.termius.clone.data.model.AuthType
import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.IdentityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.File

data class SftpItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val mtime: Long,
    val permissions: String
)

class SftpClientManager {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    val isConnected: Boolean
        get() = sshClient?.isConnected == true && sftpClient != null

    suspend fun connect(host: HostEntity, identity: IdentityEntity? = null) = withContext(Dispatchers.IO) {
        com.termius.clone.TermiusApplication.setupBouncyCastle()
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host.hostname, host.port)

        val username = if (host.authType == AuthType.IDENTITY_REF && identity != null) {
            identity.username
        } else {
            host.username
        }

        when (host.authType) {
            AuthType.PASSWORD -> client.authPassword(username, host.password)
            AuthType.KEY -> {
                val keyProvider: KeyProvider = if (host.passphrase.isNotEmpty()) {
                    client.loadKeys(host.privateKey, null, PasswordUtils.createOneOff(host.passphrase.toCharArray()))
                } else {
                    client.loadKeys(host.privateKey, null, null)
                }
                client.authPublickey(username, keyProvider)
            }
            AuthType.IDENTITY_REF -> {
                if (identity != null && identity.privateKey.isNotEmpty()) {
                    val keyProvider: KeyProvider = if (identity.passphrase.isNotEmpty()) {
                        client.loadKeys(identity.privateKey, null, PasswordUtils.createOneOff(identity.passphrase.toCharArray()))
                    } else {
                        client.loadKeys(identity.privateKey, null, null)
                    }
                    client.authPublickey(username, keyProvider)
                } else {
                    client.authPassword(username, identity?.password ?: host.password)
                }
            }
        }

        sshClient = client
        sftpClient = client.newSFTPClient()
    }

    suspend fun listDirectory(path: String): List<SftpItem> = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        val remoteFiles: List<RemoteResourceInfo> = client.ls(path)

        remoteFiles.map { info ->
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
    }

    suspend fun downloadFile(remotePath: String, localFile: File) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.get(remotePath, localFile.absolutePath)
    }

    suspend fun uploadFile(localFile: File, remotePath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.put(localFile.absolutePath, remotePath)
    }

    suspend fun rename(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.rename(oldPath, newPath)
    }

    suspend fun deleteFile(remotePath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.rm(remotePath)
    }

    suspend fun deleteDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.rmdir(remotePath)
    }

    suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        client.mkdirs(remotePath)
    }

    suspend fun createEmptyFile(remotePath: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        val tempFile = File.createTempFile("sftp_new_", ".tmp")
        try {
            tempFile.writeText("")
            client.put(tempFile.absolutePath, remotePath)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun readTextFile(remotePath: String): String = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        val tempFile = File.createTempFile("sftp_read_", ".tmp")
        try {
            client.get(remotePath, tempFile.absolutePath)
            tempFile.readText(Charsets.UTF_8)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun writeTextFile(remotePath: String, content: String) = withContext(Dispatchers.IO) {
        val client = sftpClient ?: throw IllegalStateException("SFTP 客户端未连接")
        val tempFile = File.createTempFile("sftp_save_", ".tmp")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            client.put(tempFile.absolutePath, remotePath)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            sshClient?.disconnect()
            sshClient?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            sftpClient = null
            sshClient = null
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
