package io.xxlabs.messenger.backup.cloud.sftp

import io.xxlabs.messenger.backup.bindings.AccountArchive
import io.xxlabs.messenger.backup.bindings.BACKUP_FILE_NAME
import io.xxlabs.messenger.backup.cloud.BACKUP_DIRECTORY_NAME
import io.xxlabs.messenger.backup.model.BackupSnapshot
import io.xxlabs.messenger.filetransfer.FileSize
import kotlinx.coroutines.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.FileSystemFile
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface SftpClient {
    suspend fun download(path: String): Pair<BackupSnapshot, AccountArchive>
    suspend fun upload(backup: File): FileSize
}

class SftpTransfer(private val credentials: SftpCredentials) : SftpClient {
    private val scope =  CoroutineScope(
        CoroutineName("SftpTransfer")
                + Job()
                + Dispatchers.Default
    )

    override suspend fun download(
        path: String
    ): Pair<BackupSnapshot, AccountArchive> = withContext(scope.coroutineContext) {
        val ssh = connect()
        try {
            val sftp = ssh.authenticate()
            val backupFile = fetchBackup(sftp, path)
            Pair(backupFile.snapshot(), backupFile.readData())
        } finally {
            ssh.disconnect()
        }
    }

    private suspend fun connect(): SSHClient = suspendCoroutine { continuation ->
        try {
            val ssh = SSHClient().apply {
                loadKnownHosts()
                connect(credentials.host, credentials.port.toInt())
            }
            continuation.resume(ssh)
        } catch(e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private suspend fun SSHClient.authenticate(): SFTPClient = suspendCoroutine { continuation ->
        try {
            authPassword(credentials.username, credentials.password)
            continuation.resume(newSFTPClient())
        } catch(e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private suspend fun fetchBackup(
        sftp: SFTPClient,
        path: String
    ): FileSystemFile = suspendCoroutine { continuation ->
        try {
            val backupFile = FileSystemFile(path)
            sftp.get(BACKUP_FILE_NAME, backupFile)
            continuation.resume(backupFile)
        } catch(e: Exception) {
            continuation.resumeWithException(e)
        } finally {
            sftp.close()
        }
    }

    override suspend fun upload(backup: File): FileSize = withContext(scope.coroutineContext) {
        val ssh = connect()
        try {
            val sftp = ssh.authenticate()
            with(FileSystemFile(backup)) {
                sftp.put(
                    this,
                    "/$BACKUP_DIRECTORY_NAME/$BACKUP_FILE_NAME"
                )
                FileSize(length)
            }
        } finally {
            ssh.disconnect()
        }
    }
}

private data class SftpBackupData(
    val name: String,
    override val date: Long,
    override val sizeBytes: Long
) : BackupSnapshot {

    companion object Factory {
        fun from(file: FileSystemFile) = with(file) {
            SftpBackupData(name, lastModifiedTime, length)
        }
    }
}

private fun FileSystemFile.snapshot(): BackupSnapshot = SftpBackupData.from(this)

private suspend fun FileSystemFile.readData(): AccountArchive = withContext(Dispatchers.IO) {
    inputStream.use {
        AccountArchive(inputStream.readBytes())
    }
}