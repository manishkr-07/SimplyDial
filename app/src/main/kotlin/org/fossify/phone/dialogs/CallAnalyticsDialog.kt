package org.fossify.phone.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogCallAnalyticsBinding
import java.util.Calendar

class CallAnalyticsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogCallAnalyticsBinding? = null
    private val binding get() = _binding!!

    data class ContactStat(
        val name: String,
        val number: String,
        val totalCalls: Int,
        val totalDurationSeconds: Long
    )

    data class AnalyticsResult(
        val totalCalls: Int,
        val totalDurationSeconds: Long,
        val incomingCount: Int,
        val incomingDurationSeconds: Long,
        val outgoingCount: Int,
        val outgoingDurationSeconds: Long,
        val missedCount: Int,
        val missedCallbackRate: Int,
        val morningCalls: Int,
        val afternoonCalls: Int,
        val eveningCalls: Int,
        val nightCalls: Int,
        val topContacts: List<ContactStat>
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCallAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.analyticsCloseBtn.setOnClickListener { dismiss() }

        loadAnalytics()
    }

    private fun loadAnalytics() {
        binding.analyticsLoading.beVisible()
        binding.analyticsContent.beGone()
        binding.analyticsEmptyText.beGone()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = computeAnalytics()
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                binding.analyticsLoading.beGone()

                if (result == null || result.totalCalls == 0) {
                    binding.analyticsEmptyText.beVisible()
                } else {
                    displayResult(result)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayResult(res: AnalyticsResult) {
        binding.analyticsContent.beVisible()

        // Hero KPI
        val totalHours = res.totalDurationSeconds / 3600
        val totalMins = (res.totalDurationSeconds % 3600) / 60
        binding.analyticsTotalDuration.text = "${totalHours}h ${totalMins}m"
        binding.analyticsTotalCalls.text = "${res.totalCalls} calls in the last 30 days"

        // Incoming / Outgoing / Missed
        binding.analyticsIncomingCount.text = "${res.incomingCount}"
        binding.analyticsIncomingDuration.text = "${res.incomingDurationSeconds / 60}m"

        binding.analyticsOutgoingCount.text = "${res.outgoingCount}"
        binding.analyticsOutgoingDuration.text = "${res.outgoingDurationSeconds / 60}m"

        binding.analyticsMissedCount.text = "${res.missedCount}"
        binding.analyticsCallbackRate.text = "${res.missedCallbackRate}% back"

        // Peak Hours Distribution
        val totalForPeak = (res.morningCalls + res.afternoonCalls + res.eveningCalls + res.nightCalls).coerceAtLeast(1)
        val peakMax = maxOf(res.morningCalls, res.afternoonCalls, res.eveningCalls, res.nightCalls)
        val peakLabel = when (peakMax) {
            res.eveningCalls -> "Most active in the Evening (6 PM - 10 PM)"
            res.afternoonCalls -> "Most active in the Afternoon (12 PM - 6 PM)"
            res.morningCalls -> "Most active in the Morning (6 AM - 12 PM)"
            else -> "Most active at Night (10 PM - 6 AM)"
        }
        binding.analyticsPeakSummary.text = peakLabel

        // Set layout weights for peak bar
        (binding.analyticsBarMorning.layoutParams as? LinearLayout.LayoutParams)?.weight =
            (res.morningCalls.toFloat() / totalForPeak).coerceAtLeast(0.05f)
        (binding.analyticsBarAfternoon.layoutParams as? LinearLayout.LayoutParams)?.weight =
            (res.afternoonCalls.toFloat() / totalForPeak).coerceAtLeast(0.05f)
        (binding.analyticsBarEvening.layoutParams as? LinearLayout.LayoutParams)?.weight =
            (res.eveningCalls.toFloat() / totalForPeak).coerceAtLeast(0.05f)
        (binding.analyticsBarNight.layoutParams as? LinearLayout.LayoutParams)?.weight =
            (res.nightCalls.toFloat() / totalForPeak).coerceAtLeast(0.05f)

        binding.analyticsBarMorning.requestLayout()

        // Top Contacts
        binding.analyticsTopContactsContainer.removeAllViews()
        res.topContacts.forEachIndexed { index, contact ->
            val itemView = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_call_analytics_contact,
                binding.analyticsTopContactsContainer,
                false
            )

            itemView.findViewById<TextView>(R.id.contact_rank).text = "#${index + 1}"
            itemView.findViewById<TextView>(R.id.contact_name).text = contact.name.ifEmpty { contact.number }
            itemView.findViewById<TextView>(R.id.contact_number).text = contact.number
            val durationText = "${contact.totalDurationSeconds / 60}m"
            itemView.findViewById<TextView>(R.id.contact_stats).text = "${contact.totalCalls} calls • $durationText"

            binding.analyticsTopContactsContainer.addView(itemView)
        }
    }

    private fun computeAnalytics(): AnalyticsResult? {
        val context = context ?: return null
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val thirtyDaysAgo = calendar.timeInMillis

        var totalCalls = 0
        var totalDuration = 0L
        var incomingCount = 0
        var incomingDuration = 0L
        var outgoingCount = 0
        var outgoingDuration = 0L
        var missedCount = 0

        var morningCalls = 0
        var afternoonCalls = 0
        var eveningCalls = 0
        var nightCalls = 0

        val missedNumbers = mutableSetOf<String>()
        val returnedMissedNumbers = mutableSetOf<String>()

        val contactMap = mutableMapOf<String, Pair<String, Pair<Int, Long>>>() // number -> (name, (count, duration))

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME
                ),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(thirtyDaysAgo.toString()),
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                val cal = Calendar.getInstance()

                while (it.moveToNext()) {
                    val type = it.getInt(typeIdx)
                    val duration = it.getLong(durIdx)
                    val date = it.getLong(dateIdx)
                    val number = it.getString(numIdx) ?: "Unknown"
                    val name = it.getString(nameIdx) ?: ""

                    totalCalls++
                    totalDuration += duration

                    // Time of day
                    cal.timeInMillis = date
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    when (hour) {
                        in 6..11 -> morningCalls++
                        in 12..17 -> afternoonCalls++
                        in 18..21 -> eveningCalls++
                        else -> nightCalls++
                    }

                    when (type) {
                        CallLog.Calls.INCOMING_TYPE -> {
                            incomingCount++
                            incomingDuration += duration
                        }
                        CallLog.Calls.OUTGOING_TYPE -> {
                            outgoingCount++
                            outgoingDuration += duration
                            if (missedNumbers.contains(number)) {
                                returnedMissedNumbers.add(number)
                            }
                        }
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> {
                            missedCount++
                            missedNumbers.add(number)
                        }
                    }

                    // Contact aggregation
                    val current = contactMap[number]
                    if (current != null) {
                        val newCount = current.second.first + 1
                        val newDur = current.second.second + duration
                        val resolvedName = if (current.first.isNotEmpty()) current.first else name
                        contactMap[number] = Pair(resolvedName, Pair(newCount, newDur))
                    } else {
                        contactMap[number] = Pair(name, Pair(1, duration))
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        val topList = contactMap.entries
            .filter { it.key != "Unknown" && it.key.isNotEmpty() }
            .sortedByDescending { it.value.second.first }
            .take(5)
            .map {
                ContactStat(
                    name = it.value.first,
                    number = it.key,
                    totalCalls = it.value.second.first,
                    totalDurationSeconds = it.value.second.second
                )
            }

        val callbackRate = if (missedNumbers.isNotEmpty()) {
            ((returnedMissedNumbers.size.toFloat() / missedNumbers.size) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        return AnalyticsResult(
            totalCalls = totalCalls,
            totalDurationSeconds = totalDuration,
            incomingCount = incomingCount,
            incomingDurationSeconds = incomingDuration,
            outgoingCount = outgoingCount,
            outgoingDurationSeconds = outgoingDuration,
            missedCount = missedCount,
            missedCallbackRate = callbackRate,
            morningCalls = morningCalls,
            afternoonCalls = afternoonCalls,
            eveningCalls = eveningCalls,
            nightCalls = nightCalls,
            topContacts = topList
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            CallAnalyticsDialog().show(fragmentManager, "CallAnalyticsDialog")
        }
    }
}

