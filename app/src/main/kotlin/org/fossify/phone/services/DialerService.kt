package org.fossify.phone.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.dialogs.ContactChoiceDialog

object DialerService {
    fun placeCall(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun promptUserToChoose(context: Context, candidates: List<ContactResolver.ResolvedContact>, onChoose: (ContactResolver.ResolvedContact) -> Unit) {
        // Limit to top 5 results
        val topCandidates = candidates.take(5)
        if (context is SimpleActivity) {
            ContactChoiceDialog(context, topCandidates, onChoose)
        } else {
            // Fallback to old implementation if not SimpleActivity
            val names = topCandidates.map { it.name + " — " + it.number }.toTypedArray()
            var selectedIndex = 0
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.choose_contact)
                .setSingleChoiceItems(names, selectedIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onChoose(topCandidates[selectedIndex])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
