package io.xxlabs.messenger.ui.intro.registration.phone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import io.xxlabs.messenger.R
import io.xxlabs.messenger.data.data.Country
import io.xxlabs.messenger.databinding.FragmentRegistrationPhoneBinding
import io.xxlabs.messenger.di.utils.Injectable
import io.xxlabs.messenger.ui.dialog.info.InfoDialog
import io.xxlabs.messenger.ui.intro.registration.tfa.TwoFactorAuthCredentials
import io.xxlabs.messenger.ui.intro.registration.RegistrationFlowFragment
import io.xxlabs.messenger.ui.main.countrycode.CountryFullscreenDialog
import io.xxlabs.messenger.ui.main.countrycode.CountrySelectionListener

class RegistrationPhoneFragment : RegistrationFlowFragment(), Injectable {

    /* UI */

    private lateinit var binding: FragmentRegistrationPhoneBinding
    private val ui: PhoneRegistrationController by lazy { registrationViewModel }
    private lateinit var countryDialog: CountryFullscreenDialog

    private var dialogShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_registration_phone,
            container,
            false
        )
        initViewModels()
        initCountryDialog()
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    private fun initViewModels() {
        binding.ui = registrationViewModel
    }

    override fun onStart() {
        super.onStart()
        observeUI()
    }

    private fun initCountryDialog() {
        countryDialog = CountryFullscreenDialog.getInstance(
            object : CountrySelectionListener {
                override val onDismiss: () -> Unit = ::onCountryDialogDismissed
                override fun onItemSelected(country: Country) {
                    ui.onPhoneCountryCodeSelected(country)
                    countryDialog.dismiss()
                    dialogShown = false
                }
            }
        )
    }

    private fun onCountryDialogDismissed() {
        dialogShown = false
    }

    private fun observeUI() {
        ui.phoneInfoClicked.observe(viewLifecycleOwner) { show ->
            if (show) displayPhoneInfoDialog()
        }

        ui.phoneNavigateNextStep.observe(viewLifecycleOwner) { tfaCredentials ->
            tfaCredentials?.let { navigateNextStep(it) }
        }

        ui.phoneNavigateSkip.observe(viewLifecycleOwner) { skip ->
            if (skip) navigateSkip()
        }

        ui.phoneCountryCodeClicked.observe(viewLifecycleOwner) { clicked ->
            if (clicked && !dialogShown) selectCountry()
        }
    }

    private fun displayPhoneInfoDialog() {
        InfoDialog.newInstance(ui.phoneDialogUI)
            .show(requireActivity().supportFragmentManager, null)
        ui.onPhoneInfoHandled()
    }

    private fun navigateNextStep(tfaCredentials: TwoFactorAuthCredentials) {
        val directions = RegistrationPhoneFragmentDirections
            .actionRegistrationPhoneFragmentToRegistrationTfaFragment(tfaCredentials)
        findNavController().navigate(directions)
        ui.onPhoneNavigateHandled()
    }

    private fun navigateSkip() {
        onRegistrationComplete()
        ui.onPhoneNavigateHandled()
    }

    private fun selectCountry() {
        if (!countryDialog.isAdded) {
            dialogShown = true
            countryDialog.show(childFragmentManager, "countryCodeDialog")
        }
    }
}