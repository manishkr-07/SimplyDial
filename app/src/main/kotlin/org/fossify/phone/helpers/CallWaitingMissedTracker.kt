package org.fossify.phone.helpers

import org.fossify.phone.models.CallContact

object CallWaitingMissedTracker {
    @Volatile
    private var pendingMissedCallWaiting: CallContact? = null

    fun setPending(callContact: CallContact) {
        pendingMissedCallWaiting = callContact
    }

    fun consumePending(): CallContact? {
        val result = pendingMissedCallWaiting
        pendingMissedCallWaiting = null
        return result
    }

    fun clear() {
        pendingMissedCallWaiting = null
    }
}
