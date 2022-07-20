package io.xxlabs.messenger.backup.cloud.sftp.login.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.xxlabs.messenger.BuildConfig
import io.xxlabs.messenger.R
import io.xxlabs.messenger.backup.cloud.sftp.login.*
import io.xxlabs.messenger.support.appContext
import io.xxlabs.messenger.ui.dialog.info.InfoDialogUI
import io.xxlabs.messenger.ui.dialog.warning.WarningDialogUI
import kotlinx.coroutines.launch
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import timber.log.Timber

class SshLoginViewModel : ViewModel(), SshLoginListener, KnownHostsListener {

    // TODO: Implement
    private lateinit var knownHosts: KnownHostsDataSource

    private val ssh: SshClient = Ssh.apply {
        if (BuildConfig.DEBUG) {
            addHostKeyVerifier(PromiscuousVerifier())
        } else {
            addHostKeyVerifier(
                KnownHostsVerifier(knownHosts, this@SshLoginViewModel)
            )
        }
    }

    private val sshLogin: SshLoginUi = SshLogin(this, ssh)
    val sshLoginUi: LiveData<SshLoginUi> by ::_sshLoginUi
    private val _sshLoginUi = MutableLiveData(sshLogin)

    val loginSuccess: LiveData<SshCredentials?> by ::_loginSuccess
    private val _loginSuccess = MutableLiveData<SshCredentials?>(null)

    val loginError: LiveData<String?> by ::_loginError
    private val _loginError = MutableLiveData<String?>(null)

    override fun onLoginSuccess(credentials: SshCredentials) {
        _loginSuccess.postValue(credentials)
    }

    override fun onLoginError(message: String) {
        Timber.d("Sftp error: $message")
        _loginError.postValue(message)
    }

    override fun onUnknownHost(host: HostIdentity) {
        generateUnknownHostWarning(host)
    }

    private fun generateUnknownHostWarning(host: HostIdentity): WarningDialogUI {
        return with(host) {
            val infoDialogUi = InfoDialogUI.create(
                title = appContext().getString(R.string.ssh_unknown_host_title),
                body = "Could not verify `" + KeyType.fromKey(key)
                        + "` host key with fingerprint `" + SecurityUtils.getFingerprint(key)
                        + "` for `" + hostname
                        + "` on port " + port,
                spans = null,
                onDismissed = { refuseConnection() }
            )

            WarningDialogUI.create(
                infoDialogUI = infoDialogUi,
                buttonText = "Continue anyway",
                buttonOnClick = { addToWhitelist(host) }
            )
        }
    }

    private fun refuseConnection() {
        _loginError.value = "Could not verify host."
    }

    private fun addToWhitelist(host: HostIdentity) {
        knownHosts.add(host)
        viewModelScope.launch {
            sshLogin.onSubmitClicked()
        }
    }
}