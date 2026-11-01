package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.util.TypedValue
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemContactSkeletonBinding
import org.fossify.phone.databinding.ItemContactWithoutNumberBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.models.LightContact

sealed class LightContactItem {
    data class Data(val contact: LightContact) : LightContactItem()
    data class Placeholder(val id: Int) : LightContactItem()
}

class LightContactsAdapter(
    private val activity: SimpleActivity,
    private val recyclerView: MyRecyclerView,
    private val itemClick: (LightContact) -> Unit,
    private val profileIconClick: ((LightContact) -> Unit)? = null
) : ListAdapter<LightContactItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_SKELETON = 1
    }

    var fontSize: Float = activity.getTextSize()

    object Diff : DiffUtil.ItemCallback<LightContactItem>() {
        override fun areItemsTheSame(oldItem: LightContactItem, newItem: LightContactItem): Boolean {
            return when {
                oldItem is LightContactItem.Data && newItem is LightContactItem.Data -> oldItem.contact.id == newItem.contact.id
                oldItem is LightContactItem.Placeholder && newItem is LightContactItem.Placeholder -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: LightContactItem, newItem: LightContactItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is LightContactItem.Data -> TYPE_ITEM
            is LightContactItem.Placeholder -> TYPE_SKELETON
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ITEM -> ItemVH(ItemContactWithoutNumberBinding.inflate(inflater, parent, false))
            else -> SkeletonVH(ItemContactSkeletonBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemVH -> holder.bind((getItem(position) as LightContactItem.Data).contact)
            is SkeletonVH -> holder.bind()
        }
    }

    inner class ItemVH(private val binding: ItemContactWithoutNumberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LightContact) {
            val textColor = activity.getProperTextColor()
            binding.apply {
                itemContactName.text = item.name
                itemContactName.setTextColor(textColor)
                itemContactName.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                if (item.number.isNotEmpty()) {
                    itemContactNumber.text = item.number
                    itemContactNumber.setTextColor(textColor.adjustAlpha(0.7f))
                    itemContactNumber.beVisible()
                } else {
                    itemContactNumber.beGone()
                }

                if (!activity.isDestroyed) {
                    SimpleContactsHelper(root.context).loadContactImage(item.photoUri ?: "", itemContactImage, item.name)
                }

                itemContactCallButton.setOnClickListener {
                    activity.startCallWithConfirmationCheck(item.number, item.name)
                }

                itemContactActionButton.setOnClickListener {
                    itemClick(item)
                }

                root.setOnClickListener {
                    itemClick(item)
                }

                if (profileIconClick != null && activity.config.enableProfilePictureClick) {
                    itemContactImage.setOnClickListener { profileIconClick.invoke(item) }
                } else {
                    itemContactImage.setOnClickListener(null)
                }
            }
        }
    }

    inner class SkeletonVH(private val binding: ItemContactSkeletonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            // Shimmer runs automatically
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder is ItemVH) {
            Glide.with(activity).clear(holder.itemView.findViewById<ImageView>(R.id.item_contact_image))
        }
    }
}
