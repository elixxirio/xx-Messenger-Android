package io.xxlabs.messenger.backup.cloud.sftp.login

import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

interface KnownHostsDataSource {
    fun contains(host: HostIdentity): Boolean
    fun add(host: HostIdentity): Int
}

interface KnownHostsListener {
    fun onUnknownHost(host: HostIdentity)
}

data class HostIdentity(
    val hostname: String,
    val port: Int,
    val key: PublicKey?
)

/**
 * Prompts user to allow connection to a host with an unverified fingerprint.
 */
class KnownHostsVerifier(
    private val knownHosts: KnownHostsDataSource,
    private val hostsListener: KnownHostsListener
) : HostKeyVerifier {

    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        return with (HostIdentity(hostname ?: "", port, key)) {
            val verified = knownHosts.contains(this)
            if (!verified) hostsListener.onUnknownHost(this)

            verified
        }
    }
}