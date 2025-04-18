package one.mixin.android.ui.wallet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import one.mixin.android.R
import one.mixin.android.databinding.ItemSelectableUserBinding
import one.mixin.android.extension.toast
import one.mixin.android.ui.wallet.MultiSelectTokenListBottomSheetDialogFragment
import one.mixin.android.vo.Recipient
import one.mixin.android.vo.UserItem

class SelectableUserAdapter(private val selectedUsers: MutableList<Recipient>) : ListAdapter<UserItem, SelectableUserAdapter.SearchUserViewHolder>(UserItem.DIFF_CALLBACK) {
    var callback: WalletSearchUserCallback? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SearchUserViewHolder {
        return SearchUserViewHolder(ItemSelectableUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(
        holder: SearchUserViewHolder,
        position: Int,
    ) {
        getItem(position)?.let { holder.bind(it, selectedUsers, callback) }
    }

    inner class SearchUserViewHolder(val binding: ItemSelectableUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            user: UserItem,
            selectedRecipients: MutableList<Recipient>,
            callback: WalletSearchUserCallback? = null,
        ) {
            binding.normal.setName(user)
            binding.mixinIdTv.text = user.identityNumber
            binding.avatar.setInfo(user.fullName, user.avatarUrl, user.id)
            binding.cb.isChecked = selectedRecipients.contains(user)
            binding.cb.isClickable = false
            itemView.setOnClickListener {
                if (!binding.cb.isChecked && selectedRecipients.size >= MultiSelectTokenListBottomSheetDialogFragment.LIMIT) {
                    toast(binding.root.context.getString(R.string.Select_LIMIT, MultiSelectTokenListBottomSheetDialogFragment.LIMIT))
                    return@setOnClickListener
                }
                binding.cb.isChecked = !binding.cb.isChecked
                callback?.onUserClick(user)
                notifyItemChanged(adapterPosition)
            }
        }
    }
}

interface WalletSearchUserCallback {
    fun onUserClick(user: UserItem)
}

