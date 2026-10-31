package org.fossify.phone.helpers

import org.fossify.phone.activities.CallActivity
import org.fossify.phone.activities.MainActivity
import java.util.concurrent.atomic.AtomicInteger

object AppVisibility {
    private val startedCount = AtomicInteger(0)

    @Volatile
    private var resumedActivityClassName: String? = null

    fun onActivityStarted() {
        startedCount.incrementAndGet()
    }

    fun onActivityStopped() {
        val newValue = startedCount.decrementAndGet()
        if (newValue < 0) {
            startedCount.set(0)
        }
    }

    fun onActivityResumed(activityClassName: String) {
        resumedActivityClassName = activityClassName
    }

    fun isAppInForeground(): Boolean {
        return startedCount.get() > 0
    }

    fun isOnCallScreenOrHomeScreen(): Boolean {
        if (!isAppInForeground()) return false
        val activity = resumedActivityClassName ?: return false
        return activity == CallActivity::class.java.name ||
            activity == MainActivity::class.java.name
    }
}
