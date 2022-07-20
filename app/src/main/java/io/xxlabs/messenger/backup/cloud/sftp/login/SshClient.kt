package io.xxlabs.messenger.backup.cloud.sftp.login

import io.xxlabs.messenger.BuildConfig
import io.xxlabs.messenger.R
import io.xxlabs.messenger.support.appContext
import io.xxlabs.messenger.ui.dialog.info.InfoDialogUI
import io.xxlabs.messenger.ui.dialog.warning.WarningDialogUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface SshClient {
    suspend fun connect(credentials: SshCredentials): SSHClient
    suspend fun disconnect()
}

object Ssh : SshClient, KnownHostsListener {

    private var client: SSHClient? = null
    private var cachedCredentials: SshCredentials? = null

    /**
     * Attempt an remote connection with the provided [SshCredentials].
     * Returns an [SSHClient] reference if successful.
     */
    override suspend fun connect(credentials: SshCredentials): SSHClient = suspendCoroutine { continuation ->
        client?.let {
            if (it.isConnectedWith(credentials)) {
                continuation.resume(it)
                return@suspendCoroutine
            }
        }

        try {
            // BouncyCastle is deprecated in Android P+
            SecurityUtils.setRegisterBouncyCastle(false)
            val ssh = SSHClient(Config).apply {
                if (BuildConfig.DEBUG) {
                    addHostKeyVerifier(PromiscuousVerifier())
                } else {
                    addHostKeyVerifier(KnownHostsVerifier())
                }
                connect(credentials.host, credentials.port.toInt())
            }

            try {
                ssh.authPassword(credentials.username, credentials.password)
            } catch (e: TransportException) {
                if (e.disconnectReason == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
                    Timber.d("Disconnected-- could not verify host identity.")
                }
                continuation.resumeWithException(Exception(""))
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                return@suspendCoroutine
            }

            client = ssh
            cachedCredentials = credentials
            continuation.resume(ssh)
            return@suspendCoroutine
        } catch (e: Exception) {
            continuation.resumeWithException(e)
            return@suspendCoroutine
        }
    }

    private fun SSHClient.isConnectedWith(credentials: SshCredentials): Boolean =
        isConnected && cachedCredentials == credentials

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                client?.disconnect()
            } catch (e: Exception) {
                Timber.d("Caught exception closing SSH: ${e.message}")
            } finally {
                client = null
            }
        }
    }

    override fun onUnknownHost(host: HostIdentity) {
        generateUnknownHostWarning(host)
    }

    private fun generateUnknownHostWarning(identity: HostIdentity): WarningDialogUI {
        return with(identity) {
            val infoDialogUi = InfoDialogUI.create(
                title = appContext().getString(R.string.ssh_unknown_host_title),
                body = "Could not verify `" + KeyType.fromKey(key)
                        + "` host key with fingerprint `" + SecurityUtils.getFingerprint(key)
                        + "` for `" + hostname
                        + "` on port " + port,
                spans = null,
                onDismissed = ::refuseConnection
            )

            WarningDialogUI.create(
                infoDialogUI = infoDialogUi,
                buttonText = "Continue anyway",
                buttonOnClick = ::addToWhitelist
            )
        }
    }


    private fun refuseConnection() {

    }

    private fun addToWhitelist() {
        // Save host, port and fingerprint to database or preferences.
        // Retry the connection.
    }
}