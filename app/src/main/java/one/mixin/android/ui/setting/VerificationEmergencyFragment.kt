package one.mixin.android.ui.setting

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants.ARGS_USER
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.EmergencyPurpose
import one.mixin.android.api.request.EmergencyRequest
import one.mixin.android.crypto.CryptoPreference
import one.mixin.android.crypto.EdKeyPair
import one.mixin.android.crypto.PinCipher
import one.mixin.android.crypto.SignalProtocol
import one.mixin.android.crypto.generateEd25519KeyPair
import one.mixin.android.databinding.FragmentVerificationEmergencyBinding
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.getParcelableCompat
import one.mixin.android.extension.putInt
import one.mixin.android.extension.withArgs
import one.mixin.android.session.Session
import one.mixin.android.tip.TipBody
import one.mixin.android.tip.exception.TipNetworkException
import one.mixin.android.ui.common.PinCodeFragment
import one.mixin.android.ui.landing.LandingActivity.Companion.ARGS_PIN
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Account
import one.mixin.android.vo.User
import javax.inject.Inject

@AndroidEntryPoint
class VerificationEmergencyFragment : PinCodeFragment(R.layout.fragment_verification_emergency) {
    companion object {
        const val TAG = "VerificationEmergencyFragment"
        const val ARGS_VERIFICATION_ID = "args_verification_id"
        const val ARGS_FROM = "args_from"
        const val ARGS_IDENTITY_NUMBER = "args_identity_number"

        const val FROM_CONTACT = 0
        const val FROM_SESSION = 1

        fun newInstance(
            user: User? = null,
            pin: String? = null,
            verificationId: String? = null,
            from: Int,
            userIdentityNumber: String? = null,
        ) = VerificationEmergencyFragment().withArgs {
            putParcelable(ARGS_USER, user)
            putString(ARGS_PIN, pin)
            putString(ARGS_VERIFICATION_ID, verificationId)
            putInt(ARGS_FROM, from)
            putString(ARGS_IDENTITY_NUMBER, userIdentityNumber)
        }
    }

    private val user: User? by lazy { requireArguments().getParcelableCompat(ARGS_USER, User::class.java) }
    private val pin: String? by lazy { requireArguments().getString(ARGS_PIN) }
    private val verificationId by lazy { requireArguments().getString(ARGS_VERIFICATION_ID)!! }
    private val from by lazy { requireArguments().getInt(ARGS_FROM) }
    private val userIdentityNumber: String? by lazy { requireArguments().getString(ARGS_IDENTITY_NUMBER) }

    private val viewModel by viewModels<EmergencyViewModel>()

    private val binding by viewBinding(FragmentVerificationEmergencyBinding::bind)

    @Inject
    lateinit var pinCipher: PinCipher

    override fun getContentView() = binding.root

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.pinVerificationTitleTv.text =
            getString(R.string.setting_emergency_send_code, user?.identityNumber ?: userIdentityNumber)
    }

    override fun clickNextFab() {
        if (from == FROM_CONTACT) {
            createVerify()
        } else {
            loginVerify()
        }
    }

    override fun insertUser(u: User) {
        viewModel.insertUser(u)
    }

    private fun createVerify() =
        lifecycleScope.launch {
            showLoading()
            val code = binding.pinVerificationView.code()
            handleMixinResponse(
                invokeNetwork = {
                    viewModel.createVerifyEmergency(
                        verificationId,
                        EmergencyRequest(
                            user?.phone,
                            user?.identityNumber ?: userIdentityNumber,
                            pinCipher.encryptPin(requireNotNull(pin), TipBody.forEmergencyContactCreate(verificationId, code)),
                            code,
                            EmergencyPurpose.CONTACT.name,
                        ),
                    )
                },
                successBlock = { response ->
                    val a = response.data as Account
                    Session.storeAccount(a)
                    Session.setHasEmergencyContact(a.hasEmergencyContact)
                    activity?.supportFragmentManager?.findFragmentByTag(EmergencyContactFragment.TAG)?.let {
                        (it as? EmergencyContactFragment)?.setEmergencySet()
                    }

                    alertDialogBuilder()
                        .setMessage(
                            getString(
                                if (Session.hasEmergencyContact()) {
                                    R.string.Your_emergency_contact_has_been_changed
                                } else {
                                    R.string.setting_emergency_create_success
                                },
                            ),
                        )
                        .setPositiveButton(R.string.OK) { dialog, _ ->
                            if (requireActivity() is SettingActivity) {
                                requireActivity().finish()
                            }
                            parentFragmentManager.popBackStackImmediate()
                            parentFragmentManager.popBackStackImmediate()
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
                },
                doAfterNetworkSuccess = { hideLoading() },
                defaultErrorHandle = {
                    handleFailure(it)
                },
                defaultExceptionHandle = {
                    if (it is TipNetworkException) {
                        handleFailure(it.error)
                    } else {
                        handleError(it)
                    }
                },
            )
        }

    private fun loginVerify() =
        lifecycleScope.launch {
            showLoading()
            SignalProtocol.initSignal(requireContext().applicationContext)
            val sessionKey = generateEd25519KeyPair()
            handleMixinResponse(
                invokeNetwork = { viewModel.loginVerifyEmergency(verificationId, buildLoginEmergencyRequest(sessionKey)) },
                successBlock = { response ->
                    handleAccount(response, sessionKey) {
                        defaultSharedPreferences.putInt(PREF_LOGIN_FROM, FROM_EMERGENCY)
                    }
                },
                doAfterNetworkSuccess = { hideLoading() },
                defaultErrorHandle = {
                    handleFailure(it)
                },
                defaultExceptionHandle = {
                    handleError(it)
                },
            )
        }

    private fun buildLoginEmergencyRequest(sessionKey: EdKeyPair): EmergencyRequest {
        val registrationId = CryptoPreference.getLocalRegistrationId(requireContext())
        val sessionSecret = sessionKey.publicKey.base64Encode()
        return EmergencyRequest(
            phone = user?.phone,
            identityNumber = user?.identityNumber ?: userIdentityNumber,
            code = binding.pinVerificationView.code(),
            purpose = EmergencyPurpose.SESSION.name,
            sessionSecret = sessionSecret,
            registrationId = registrationId,
        )
    }
}
