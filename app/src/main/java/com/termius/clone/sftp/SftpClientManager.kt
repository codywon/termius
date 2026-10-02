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

    suspend fun connect(host: HostEntity, identity: IdentityEntity? = null) = withContext(Dispatchers.IO) {
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
                    client.loadKeys(host.privateKey, null, host.passphrase.toCharArray())
                } else {
                    client.loadKeys(host.privateKey, null, null)
                }
                client.authPublickey(username, keyProvider)
            }
            AuthType.IDENTITY_REF -> {
                if (identity != null && identity.privateKey.isNotEmpty()) {
                    val keyProvider: KeyProvider = if (identity.passphrase.isNotEmpty()) {
                        client.loadKeys(identity.privateKey, null, identity.passphrase.toCharArray())
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
                permissions = info.attributes.permissions.toString()
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

    suspend fun deleteFile(remotePath: String) = withContext(Dispatchers.IO) {
        sftpClient?.rm(remotePath)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            sshClient?.disconnect()
            sshClient?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
