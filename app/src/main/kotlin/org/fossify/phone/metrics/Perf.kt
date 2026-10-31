package org.fossify.phone.metrics

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object Perf {
    private const val TAG = "Perf"

    private var tStart: Long = 0L
    private val firstRowLogged = AtomicBoolean(false)
    private val firstNameLogged = AtomicBoolean(false)

    fun markStart() {
        tStart = System.currentTimeMillis()
        firstRowLogged.set(false)
        firstNameLogged.set(false)
        Log.d(TAG, "TTI start")
    }

    fun markFirstRow() {
        if (tStart == 0L) return
        if (firstRowLogged.compareAndSet(false, true)) {
            val dt = System.currentTimeMillis() - tStart
            Log.d(TAG, "TTFR (first row bound): ${dt}ms")
        }
    }

    fun markFirstName() {
        if (tStart == 0L) return
        if (firstNameLogged.compareAndSet(false, true)) {
            val dt = System.currentTimeMillis() - tStart
            Log.d(TAG, "TTFCP (first contact/name hydrated): ${dt}ms")
        }
    }
}
