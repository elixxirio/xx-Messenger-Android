package io.xxlabs.messenger.backup.cloud.sftp.login.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.xxlabs.messenger.backup.cloud.sftp.login.SshCredentials
import io.xxlabs.messenger.databinding.ActivitySftpAuthBinding
import io.xxlabs.messenger.support.extensions.toast

class SshLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySftpAuthBinding
    private val sftpViewModel: SshLoginViewModel by viewModels()

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
        sftpViewModel.sshLoginUi.observe(this) { ui ->
            ui?.let { binding.ui = it }
        }

        sftpViewModel.loginSuccess.observe(this) { credentials ->
            credentials?.let { onLoginSuccess(credentials) }
        }

        sftpViewModel.loginError.observe(this) { error ->
            error?.let { toast(error) }
        }
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