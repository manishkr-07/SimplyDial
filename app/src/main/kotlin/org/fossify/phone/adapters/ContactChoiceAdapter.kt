package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemContactChoiceBinding
import org.fossify.phone.services.ContactResolver

class ContactChoiceAdapter(
    private val contacts: List<ContactResolver.ResolvedContact>,
    private val onContactClick: (ContactResolver.ResolvedContact) -> Unit
) : RecyclerView.Adapter<ContactChoiceAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemContactChoiceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.apply {
            contactName.text = contact.name
            contactNumber.text = contact.number

            // Set colors for better UI
            contactName.setTextColor(holder.itemView.context.getProperTextColor())
            contactNumber.setTextColor(holder.itemView.context.getProperTextColor())
            contactNumber.alpha = 0.6f

            // Avatar with photo or first letter
            if (contact.photoUri != null) {
                contactAvatar.setImageURI(contact.photoUri)
                contactAvatar.colorFilter = null
            } else {
                // Create circular bitmap with first letter
                val firstLetter = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawCircle(24f, 24f, 24f, android.graphics.Paint().apply {
                    color = holder.itemView.context.getProperPrimaryColor()
                    isAntiAlias = true
                })
                canvas.drawText(firstLetter, 24f, 32f, textPaint)
                contactAvatar.setImageBitmap(bitmap)
            }

            root.setOnClickListener {
                // Add haptic feedback for professional feel
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onContactClick(contact)
            }
        }
    }

    override fun getItemCount() = contacts.size
}
