package one.mixin.android.ui.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.Scheme.HTTPS_PAY
import one.mixin.android.R
import one.mixin.android.databinding.FragmentQrBottomSheetBinding
import one.mixin.android.databinding.ViewQrBottomBinding
import one.mixin.android.extension.capture
import one.mixin.android.extension.generateQRCode
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.shareMedia
import one.mixin.android.extension.toast
import one.mixin.android.session.Session
import one.mixin.android.util.rxpermission.RxPermissions
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.toUser
import one.mixin.android.widget.BadgeCircleImageView.Companion.END_BOTTOM
import one.mixin.android.widget.BottomSheet
import java.io.File

@AndroidEntryPoint
class QrBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {
    companion object {
        const val TAG = "QrBottomSheetDialogFragment"
        const val ARGS_TYPE = "args_type"
        const val ARGS_CONTENT = "args_content"

        const val TYPE_MY_QR = 0
        const val TYPE_RECEIVE_QR = 1
        const val TYPE_MNEMONIC_QR = 2

        fun newInstance(
            content: String,
            type: Int,
        ) =
            QrBottomSheetDialogFragment().apply {
                arguments =
                    bundleOf(
                        ARGS_CONTENT to content,
                        ARGS_TYPE to type,
                    )
            }
    }

    private val content: String by lazy { requireArguments().getString(ARGS_CONTENT)!! }
    private val type: Int by lazy { requireArguments().getInt(ARGS_TYPE) }

    private val binding by viewBinding(FragmentQrBottomSheetBinding::inflate)

    @SuppressLint("RestrictedApi")
    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        contentView = binding.root
        (dialog as BottomSheet).setCustomView(contentView)
        binding.title.centerTitle()
        binding.title.leftIv.setOnClickListener { dismiss() }
        if (type == TYPE_MY_QR) {
            binding.title.titleTv.text = getString(R.string.My_QR_Code)
            binding.tipTv.text = getString(R.string.scan_code_add_me)
            binding.title.rightIv.setOnClickListener { showBottom() }
            binding.shareBtn.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!isAdded) return@launch
                    val path = binding.bottomLl.capture(requireContext()).getOrNull()
                    withContext(Dispatchers.Main) {
                        if (path.isNullOrBlank()) {
                            toast(getString(R.string.Share_error))
                        } else {
                            requireContext().shareMedia(false, File(path).toUri().toString())
                        }
                    }
                }
            }
        } else if (type == TYPE_RECEIVE_QR) {
            binding.title.titleTv.text = getString(R.string.Receive_Money)
            binding.tipTv.text = getString(R.string.transfer_qrcode_prompt)
            binding.title.rightIv.setOnClickListener { showBottom() }
            binding.shareBtn.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!isAdded) return@launch
                    val path = binding.bottomLl.capture(requireContext()).getOrNull()
                    withContext(Dispatchers.Main) {
                        if (path.isNullOrBlank()) {
                            toast(getString(R.string.Share_error))
                        } else {
                            requireContext().shareMedia(false, File(path).toUri().toString())
                        }
                    }
                }
            }
        } else if (type == TYPE_MNEMONIC_QR) {
            binding.title.titleTv.text = getString(R.string.Mnemonic_Phrase)
            binding.title.rightIv.isVisible = false
            binding.title.rightIv.isEnabled = false
            binding.idTv.isVisible = false
            binding.tipTv.isVisible = false
            binding.shareBtn.setText(R.string.Confirm)
            binding.shareBtn.setOnClickListener {
                dismissNow()
            }
            Session.getAccount()?.toUser()?.let { user->
                binding.badgeView.bg.setInfo(user.fullName, user.avatarUrl, user.userId)
            }
        }

        if (type == TYPE_MNEMONIC_QR) {
            binding.qr.post {
                val r = content.generateQRCode(binding.qr.width)
                binding.badgeView.layoutParams =
                    binding.badgeView.layoutParams.apply {
                        width = r.second
                        height = r.second
                    }
                binding.qr.setImageBitmap(r.first)
            }
        } else {
            bottomViewModel.findUserById(content).observe(
                this,
            ) { user ->
                if (user == null) {
                    bottomViewModel.refreshUser(content, true)
                } else {
                    binding.badgeView.bg.setInfo(user.fullName, user.avatarUrl, user.userId)
                    binding.idTv.text = getString(R.string.contact_mixin_id, user.identityNumber)
                    if (type == TYPE_RECEIVE_QR) {
                        binding.badgeView.badge.setImageResource(R.drawable.ic_contacts_receive_blue)
                        binding.badgeView.pos = END_BOTTOM
                    }
                    binding.qr.post {
                        Observable.create<Pair<Bitmap, Int>> { e ->
                            val account = Session.getAccount() ?: return@create
                            val code =
                                when (type) {
                                    TYPE_MY_QR -> account.codeUrl
                                    TYPE_RECEIVE_QR -> "$HTTPS_PAY/${user.userId}"
                                    else -> ""
                                }

                            val r = code.generateQRCode(binding.qr.width)
                            e.onNext(r)
                        }.subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .autoDispose(stopScope)
                            .subscribe(
                                { r ->
                                    binding.badgeView.layoutParams =
                                        binding.badgeView.layoutParams.apply {
                                            width = r.second
                                            height = r.second
                                        }
                                    binding.qr.setImageBitmap(r.first)
                                },
                                {
                                },
                            )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.idTv.updateLayoutParams<MarginLayoutParams> {
            width = MarginLayoutParams.MATCH_PARENT
        }
    }

    private fun showBottom() {
        val builder = BottomSheet.Builder(requireActivity())
        val view = View.inflate(ContextThemeWrapper(requireContext(), R.style.Custom), R.layout.view_qr_bottom, null)
        val viewBinding = ViewQrBottomBinding.bind(view)
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        viewBinding.save.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                RxPermissions(requireActivity())
                    .request(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .autoDispose(stopScope)
                    .subscribe(
                        { granted ->
                            if (granted) {
                                save()
                            } else {
                                requireContext().openPermissionSetting()
                            }
                        },
                        {
                            toast(R.string.Save_failure)
                        },
                    )
            } else {
                save()
            }
            bottomSheet.dismiss()
        }
        viewBinding.cancel.setOnClickListener { bottomSheet.dismiss() }
        bottomSheet.show()
    }

    private fun save() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!isAdded) return@launch
            val path = binding.bottomLl.capture(requireContext()).getOrNull()
            withContext(Dispatchers.Main) {
                if (path.isNullOrBlank()) {
                    toast(getString(R.string.Save_failure))
                } else {
                    toast(getString(R.string.Save_to, path))
                }
            }
        }
    }
}
