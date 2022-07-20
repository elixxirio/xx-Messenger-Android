package io.xxlabs.messenger.backup.cloud.sftp.login

import io.xxlabs.messenger.R
import io.xxlabs.messenger.support.appContext
import io.xxlabs.messenger.ui.dialog.info.InfoDialogUI
import io.xxlabs.messenger.ui.dialog.warning.WarningDialogUI
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

interface KnownHostsDataSource {
    fun contains(host: HostIdentity): Boolean
}

data class HostIdentity(
    val hostname: String? = "",
    val port: Int = 22,
    val key: PublicKey?
)

/**
 * Prompts user to allow connection to a host with an unverified fingerprint.
 */
class KnownHostsVerifier(
    private val knownHosts: KnownHostsDataSource? = null
) : HostKeyVerifier {

    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        return with (HostIdentity(hostname, port, key)) {
            knownHosts?.contains(this) ?: false
        }
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
                buttonText = "I trust this host",
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