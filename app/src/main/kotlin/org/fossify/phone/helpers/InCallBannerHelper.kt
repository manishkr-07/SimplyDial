package org.fossify.phone.helpers

import android.app.Activity
import android.content.Intent
import android.telecom.Call
import android.view.View
import android.widget.TextView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.getStateCompat

object InCallBannerHelper {

    fun setupBanner(activity: Activity, bannerView: View, bannerTextView: TextView, bannerActionView: TextView? = null) {
        val primaryCall = CallManager.getPrimaryCall()
        val state = primaryCall?.getStateCompat()

        val isCallActive = state in listOf(
            Call.STATE_ACTIVE,
            Call.STATE_HOLDING,
            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_RINGING
        )

        if (isCallActive && primaryCall != null) {
            getCallContact(activity.applicationContext, primaryCall) { callContact ->
                activity.runOnUiThread {
                    val name = if (callContact.name.isNotEmpty()) callContact.name else callContact.number.ifEmpty { activity.getString(R.string.unknown_caller) }
                    val stateText = when (state) {
                        Call.STATE_HOLDING -> activity.getString(R.string.call_on_hold)
                        Call.STATE_DIALING, Call.STATE_CONNECTING -> activity.getString(R.string.dialing)
                        Call.STATE_RINGING -> activity.getString(R.string.is_calling)
                        else -> activity.getString(R.string.ongoing_call)
                    }
                    bannerTextView.text = "🟢 $name • $stateText"
                    bannerView.beVisible()
                }
            }

            bannerView.setOnClickListener {
                val intent = CallActivity.getStartIntent(activity).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(intent)
            }
        } else {
            bannerView.beGone()
        }
    }
}

