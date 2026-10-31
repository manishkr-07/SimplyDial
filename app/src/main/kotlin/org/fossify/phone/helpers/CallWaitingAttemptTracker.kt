package org.fossify.phone.helpers

object CallWaitingAttemptTracker {
    @Volatile
    private var pendingAttempt: Boolean = false

    fun markAttempted() {
        pendingAttempt = true
    }

    fun clear() {
        pendingAttempt = false
    }

    fun consumeIfPending(): Boolean {
        val result = pendingAttempt
        pendingAttempt = false
        return result
    }
}
