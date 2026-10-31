package org.fossify.phone.dialogs

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactChoiceAdapter
import org.fossify.phone.databinding.DialogContactChoicesBinding
import org.fossify.phone.services.ContactResolver

class ContactChoiceDialog(
    val activity: SimpleActivity,
    val candidates: List<ContactResolver.ResolvedContact>,
    val onChoose: (ContactResolver.ResolvedContact) -> Unit
) {
    private val binding = DialogContactChoicesBinding.inflate(LayoutInflater.from(activity))
    private var dialog: AlertDialog? = null
    private lateinit var adapter: ContactChoiceAdapter

    init {
        binding.apply {
            dialogTitle.text = activity.getString(R.string.choose_contact)
            dialogTitle.setTextColor(activity.getProperTextColor())

            adapter = ContactChoiceAdapter(candidates) { selectedContact ->
                // Call selected contact directly
                onChoose(selectedContact)
                dialog?.dismiss()
            }

            contactsRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
            contactsRecycler.adapter = adapter

            // X button to dismiss dialog
            closeDialogButton.setOnClickListener {
                // Add haptic feedback on cancel button press
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                dialog?.dismiss()
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.choose_contact) { alertDialog ->
                    dialog = alertDialog
                    // Add fade-in animation for professional feel
                    val fadeIn = android.view.animation.AnimationUtils.loadAnimation(activity, android.R.anim.fade_in)
                    fadeIn.duration = 300
                    binding.root.startAnimation(fadeIn)
                }
            }
    }
}
