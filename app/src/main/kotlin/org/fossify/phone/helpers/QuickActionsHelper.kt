package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogContactQuickActionsBinding
import org.fossify.phone.dialogs.ShowGroupedCallsDialog
import org.fossify.phone.dialogs.VideoCallOptionsDialog
import org.fossify.phone.extensions.addAnimatedGradientRing
import org.fossify.phone.extensions.launchSendSMSIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.models.RecentCall

object QuickActionsHelper {

    fun showRecentCallQuickActions(
        activity: SimpleActivity,
        call: RecentCall,
        onRefresh: (() -> Unit)? = null
    ) {
        if (activity.isDestroyed || activity.isFinishing) return

        val binding = DialogContactQuickActionsBinding.inflate(LayoutInflater.from(activity))
        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(binding.root)

        val matchingContact = (activity as? MainActivity)?.cachedContacts?.find {
            (call.name.isNotEmpty() && it.name == call.name) || it.doesHavePhoneNumber(call.phoneNumber)
        }

        binding.apply {
            // Avatar
            val photoUri = matchingContact?.photoUri ?: call.photoUri
            SimpleContactsHelper(activity).loadContactImage(photoUri, contactSheetAvatar, call.name)
            contactSheetAvatar.addAnimatedGradientRing()

            // Name & Number/Time
            contactSheetName.text = call.name.ifEmpty { call.phoneNumber }

            val callTypeText = when (call.type) {
                CallLog.Calls.OUTGOING_TYPE -> activity.getString(R.string.outgoing_call)
                CallLog.Calls.INCOMING_TYPE -> activity.getString(R.string.incoming_call)
                CallLog.Calls.MISSED_TYPE -> activity.getString(R.string.missed_call)
                else -> activity.getString(R.string.call)
            }
            val timeText = call.startTS.formatDateOrTime(activity, false, false, false)
            val durationText = if (call.duration > 0) " • ${activity.formatSecondsToShortTimeString(call.duration)}" else ""
            contactSheetNumber.text = "${call.phoneNumber} • $callTypeText • $timeText$durationText"

            // Call
            contactActionCall.setOnClickListener {
                dialog.dismiss()
                activity.startCallWithConfirmationCheck(call.phoneNumber, call.name)
            }

            // SMS
            contactActionSms.setOnClickListener {
                dialog.dismiss()
                activity.launchSendSMSIntent(call.phoneNumber)
            }

            // Video Call
            contactActionVideo.setOnClickListener {
                dialog.dismiss()
                VideoCallOptionsDialog(
                    activity = activity,
                    contactName = call.name.ifEmpty { call.phoneNumber },
                    contactNumber = call.phoneNumber
                )
            }

            // Call History / Grouped Calls
            contactActionHistory.setOnClickListener {
                dialog.dismiss()
                val recentCalls = call.groupedCalls ?: listOf(call)
                ShowGroupedCallsDialog(activity, recentCalls)
            }

            // Details / Add Contact
            contactActionDetails.setOnClickListener {
                dialog.dismiss()
                if (matchingContact != null) {
                    activity.startContactDetailsIntent(matchingContact)
                } else {
                    Intent().apply {
                        action = Intent.ACTION_INSERT_OR_EDIT
                        type = "vnd.android.cursor.item/contact"
                        putExtra(KEY_PHONE, call.phoneNumber)
                        activity.launchActivityIntent(this)
                    }
                }
            }

            // Copy Number
            contactActionCopy.setOnClickListener {
                dialog.dismiss()
                activity.copyToClipboard(call.phoneNumber)
            }

            // WhatsApp Direct Chat
            contactActionWhatsapp.setOnClickListener {
                dialog.dismiss()
                openWhatsApp(activity, call.phoneNumber)
            }

            // Share Contact
            contactActionShare.setOnClickListener {
                dialog.dismiss()
                val shareText = if (call.name.isNotEmpty()) "${call.name}: ${call.phoneNumber}" else call.phoneNumber
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
            }

            // Additional phone numbers if contact matched
            val additionalNumbers = matchingContact?.phoneNumbers?.filter { it.normalizedNumber != call.phoneNumber && it.normalizedNumber.isNotEmpty() } ?: emptyList()
            if (additionalNumbers.isNotEmpty()) {
                contactNumbersSection.beVisible()
                contactNumbersList.removeAllViews()
                val density = activity.resources.displayMetrics.density
                additionalNumbers.forEach { phoneNum ->
                    val numRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.contact_action_pill_bg)
                        val pad = (10 * density).toInt()
                        setPadding(pad, pad, pad, pad)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            activity.startCallWithConfirmationCheck(phoneNum.normalizedNumber, call.name)
                        }
                    }

                    val infoLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val numTextView = TextView(activity).apply {
                        text = phoneNum.normalizedNumber
                        textSize = 14f
                        setTextColor(activity.getProperTextColor())
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val labelText = activity.getPhoneNumberTypeText(phoneNum.type, phoneNum.label)
                    val labelTextView = TextView(activity).apply {
                        text = if (labelText.isNotEmpty()) labelText else "Other"
                        textSize = 12f
                        setTextColor(activity.getProperTextColor().adjustAlpha(0.7f))
                    }

                    infoLayout.addView(numTextView)
                    infoLayout.addView(labelTextView)

                    val callIcon = ImageView(activity).apply {
                        setImageResource(R.drawable.ic_phone_vector)
                        val iconSize = (20 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        setColorFilter(ContextCompat.getColor(activity, R.color.color_primary))
                    }

                    numRow.addView(infoLayout)
                    numRow.addView(callIcon)
                    contactNumbersList.addView(numRow, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    })
                }
            } else {
                contactNumbersSection.beGone()
            }

            // Block Number
            contactActionBlock.text = activity.getString(R.string.block_number)
            contactActionBlock.setTextColor(ContextCompat.getColor(activity, R.color.color_missed_call))
            contactActionBlock.setOnClickListener {
                dialog.dismiss()
                val question = String.format(activity.getString(R.string.block_confirmation), call.phoneNumber)
                ConfirmationDialog(activity, question) {
                    activity.addBlockedNumber(call.phoneNumber)
                    onRefresh?.invoke()
                }
            }
        }

        dialog.show()
    }

    private fun openWhatsApp(activity: Activity, rawNumber: String) {
        val cleanNumber = rawNumber.filter { it.isDigit() }
        if (cleanNumber.isNotEmpty()) {
            try {
                val waIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                    `package` = "com.whatsapp"
                }
                activity.startActivity(waIntent)
            } catch (e: Exception) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber"))
                    activity.startActivity(browserIntent)
                } catch (e2: Exception) {
                    activity.toast("Could not open WhatsApp")
                }
            }
        } else {
            activity.toast("No valid number found")
        }
    }
}
