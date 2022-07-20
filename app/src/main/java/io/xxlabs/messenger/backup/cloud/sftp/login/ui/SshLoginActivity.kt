package io.xxlabs.messenger.backup.cloud.sftp.login.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.xxlabs.messenger.backup.cloud.sftp.login.SshCredentials
import io.xxlabs.messenger.databinding.ActivitySftpAuthBinding
import io.xxlabs.messenger.support.extensions.toast
import io.xxlabs.messenger.ui.dialog.warning.WarningDialog
import io.xxlabs.messenger.ui.dialog.warning.WarningDialogUI

class SshLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySftpAuthBinding
    private val sshViewModel: SshLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySftpAuthBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        observeUi()
    }

    private fun observeUi() {
        sshViewModel.sshLoginUi.observe(this) { ui ->
            ui?.let { binding.ui = it }
        }

        sshViewModel.loginSuccess.observe(this) { credentials ->
            credentials?.let { onLoginSuccess(credentials) }
        }

        sshViewModel.loginError.observe(this) { error ->
            error?.let { toast(error) }
        }

        sshViewModel.unknownHostWarning.observe(this) { warningUi ->
            warningUi?.let {
                showUnknownHostWarning(warningUi)
                sshViewModel.onUnknownHostWarningHandled()
            }
        }
    }

    private fun showUnknownHostWarning(warningUi: WarningDialogUI) {
        WarningDialog
            .newInstance(warningUi)
            .show(supportFragmentManager, null)
    }

    private fun onLoginSuccess(credentials: SshCredentials) {
        val intent = Intent(SFTP_AUTH_INTENT).apply {
            putExtra(EXTRA_SFTP_CREDENTIAL, credentials)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        const val SFTP_AUTH_INTENT = "sftp_auth"
        const val EXTRA_SFTP_CREDENTIAL = "sftp_credential"
    }
}