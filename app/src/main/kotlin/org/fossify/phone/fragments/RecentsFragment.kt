package org.fossify.phone.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.provider.CallLog.Calls
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.databinding.FragmentRecentsBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.VisibleContactsEnricher
import org.fossify.phone.extensions.addAnimatedGradientRing
import org.fossify.phone.extensions.launchSendSMSIntent
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.metrics.Perf
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall

class RecentsFragment(
    context: Context, attributeSet: AttributeSet,
) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<CallLogItem>()
    private var recentsAdapter: RecentCallsAdapter? = null

    private var searchQuery: String? = null
    private var recentsHelper = RecentsHelper(context)
    private var currentFilter = FILTER_ALL
    private var hasLoadedData = false
    private var isFragmentVisible = false
    private var analyticsJob: Job? = null

    // Loading/backpressure guards
    private var isLoading = false

    // Visible contacts enrichment
    private lateinit var contactsEnricher: VisibleContactsEnricher
    private var enrichmentScheduled = false

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_MISSED = 1
        private const val FILTER_INCOMING = 2
        private const val FILTER_OUTGOING = 3
        private const val INITIAL_LOAD_LIMIT = 20  // Load a bit more initially to reduce perceived loading
        private const val INCREMENTAL_LOAD_SIZE = 50  // Fewer incremental refreshes to avoid UI churn
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
        binding.recentsList.setHasFixedSize(true)
        binding.recentsList.setItemViewCacheSize(20)
        binding.recentsList.itemAnimator = null
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }

        contactsEnricher = VisibleContactsEnricher(context)
        setupFilterChips()
        setupSwipeActions()
        setupScrollEnrichment()
        
        // Trigger initial load if this is the default visible tab
        post {
            if (!hasLoadedData && context.hasPermission(PERMISSION_READ_CALL_LOG)) {
                refreshItems(invalidate = false)
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        updateFilterChips()

        recentsAdapter?.apply {
            updateTextColor(textColor)
            initDrawables()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        if (invalidate) {
            allRecentCalls = emptyList()
            hasLoadedData = false
            recentsHelper.clearCache()
        }

        // Load data when requested (called when tab becomes visible)
        if (!hasLoadedData) {
            hasLoadedData = true
            isFragmentVisible = true
            
            // Initial load with very small batch (10 items) for instant display
            refreshCallLog(loadAll = false, isInitialLoad = true, preferFast = true) {
                // One follow-up load shortly after first paint
                postDelayed({
                    if (isFragmentVisible && allRecentCalls.filterIsInstance<RecentCall>().size < RecentsHelper.QUERY_LIMIT) {
                        refreshCallLog(loadAll = false, isInitialLoad = false, preferFast = true)
                    }
                }, 250)
            }
        } else if (invalidate) {
            // Force refresh (fast path)
            refreshCallLog(loadAll = false, isInitialLoad = false, preferFast = true)
        }
        
        callback?.invoke()
    }

    override fun onSearchClosed() {
        searchQuery = null
        val filtered = filterCalls(allRecentCalls)
        showOrHidePlaceholder(filtered.isEmpty())
        recentsAdapter?.updateItems(filtered)
    }

    override fun onSearchQueryChanged(text: String) {
        searchQuery = text
        updateSearchResult()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSearchResult() {
        ensureBackgroundThread {
            val fixedText = searchQuery!!.trim().replace("\\s+".toRegex(), " ")
            val recentCalls = allRecentCalls
                .filterIsInstance<RecentCall>()
                .filter {
                    it.name.contains(fixedText, true) || it.doesContainPhoneNumber(fixedText)
                }
                .sortedWith(
                    compareByDescending<RecentCall> { it.dayCode }
                        .thenByDescending { it.name.startsWith(fixedText, true) }
                        .thenByDescending { it.startTS }
                )

            prepareCallLog(recentCalls) {
                activity?.runOnUiThread {
                    showOrHidePlaceholder(recentCalls.isEmpty())
                    recentsAdapter?.updateItems(it, fixedText)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()
                refreshCallLog(preferFast = true)
            }
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        if (show && !binding.progressIndicator.isVisible()) {
            binding.recentsPlaceholder.beVisible()
        } else {
            binding.recentsPlaceholder.beGone()
        }
    }

    private fun gotRecents(recents: List<CallLogItem>) {
        if (!context.config.isSimpleMode) {
            binding.filterScrollView.beVisible()
            val missedCount = allRecentCalls.filterIsInstance<RecentCall>().count { it.type == Calls.MISSED_TYPE || it.type == Calls.REJECTED_TYPE }
            if (missedCount > 0) {
                binding.filterMissed.text = "${context.getString(R.string.filter_missed)} ($missedCount)"
            } else {
                binding.filterMissed.text = context.getString(R.string.filter_missed)
            }
            updateAnalyticsAndFrequentContacts()
        } else {
            binding.filterScrollView.beGone()
            binding.recentsAnalyticsCard.beGone()
            binding.frequentContactsScrollView.beGone()
        }
        // Hide progress indicator immediately when we have items to show
        if (recents.isNotEmpty()) {
            binding.progressIndicator.hide()
        }
        
        if (recents.isEmpty()) {
            binding.progressIndicator.hide()
            binding.apply {
                val placeholderRes = when (currentFilter) {
                    FILTER_MISSED -> R.string.no_missed_calls
                    FILTER_INCOMING -> R.string.no_incoming_calls
                    FILTER_OUTGOING -> R.string.no_outgoing_calls
                    else -> R.string.no_previous_calls
                }
                recentsPlaceholder.text = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
                    context.getString(placeholderRes)
                } else {
                    context.getString(R.string.could_not_access_the_call_history)
                }
                showOrHidePlaceholder(true)
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
            recentsAdapter?.updateItems(emptyList())
        } else {
            binding.apply {
                showOrHidePlaceholder(false)
                recentsPlaceholder2.beGone()
                recentsList.beVisible()
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                        gotRecents(filterCalls(allRecentCalls))
                    },
                    itemClick = {
                        val recentCall = it as RecentCall
                        activity?.startCallWithConfirmationCheck(recentCall.phoneNumber, recentCall.name)
                    },
                    profileIconClick = {
                        if (context.config.enableProfilePictureClick) {
                            val contact = findContactByCall(it as RecentCall)
                            if (contact != null) {
                                activity?.startContactDetailsIntent(contact)
                            }
                        }
                    }
                )

                binding.recentsList.adapter = recentsAdapter
                recentsAdapter?.updateItems(recents)

                // Correct any cached/wrong names quickly on first paint
                scheduleVisibleEnrichment(delayMs = 0)

                binding.recentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        // Defer heavy binds while scrolling; resume on idle
                        recentsAdapter?.updateFastBindMode(newState != RecyclerView.SCROLL_STATE_IDLE)
                    }
                })

                binding.recentsList.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                    override fun updateTop() = Unit
                    override fun updateBottom() {
                        if (!isLoading) refreshCallLog(preferFast = true)
                    }
                }
            } else {
                recentsAdapter?.updateItems(recents)

                // Keep current visible window accurate when list updates
                scheduleVisibleEnrichment(delayMs = 0)
            }

            // After first data, turn off fast bind after a short delay to populate details
            postDelayed({ recentsAdapter?.updateFastBindMode(false) }, 250)

            // Schedule contact enrichment for visible window
            scheduleVisibleEnrichment()
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, isInitialLoad: Boolean = false, preferFast: Boolean = true, callback: (() -> Unit)? = null) {
        if (isLoading) return
        isLoading = true
        // For very first load, show skeletons in the list and mark perf start
        if (allRecentCalls.isEmpty() && isInitialLoad) {
            Perf.markStart()
            showInitialSkeletons()
        }
        
        getRecentCalls(loadAll, isInitialLoad, preferFast) {
            allRecentCalls = it
            if (searchQuery.isNullOrEmpty()) {
                activity?.runOnUiThread { 
                    refreshFilteredCalls()
                    binding.progressIndicator.hide()
                }
            } else {
                updateSearchResult()
            }

            isLoading = false
            callback?.invoke()
            // After data refresh, enrich visible items
            scheduleVisibleEnrichment()
        }
    }

    private fun getRecentCalls(loadAll: Boolean, isInitialLoad: Boolean = false, preferFast: Boolean = true, callback: (List<CallLogItem>) -> Unit) {
        // Use smaller initial load limit for faster startup
        val existingCount = allRecentCalls.filterIsInstance<RecentCall>().size
        val queryCount = when {
            loadAll -> Int.MAX_VALUE
            isInitialLoad -> INITIAL_LOAD_LIMIT  // First load: only 10 items
            existingCount < 30 -> INCREMENTAL_LOAD_SIZE  // Second load: 20 items
            else -> 50  // Final load: remaining items
        }
        val existingRecentCalls = allRecentCalls.filterIsInstance<RecentCall>()

        if (!loadAll && preferFast) {
            recentsHelper.getRecentCallsFast(queryCount) { calls ->
                // Fast path: show immediately using cached call log fields; enrichment runs right after.
                callback(groupCallsByDate(calls))
            }
            return
        }

        with(recentsHelper) {
            if (context.config.groupSubsequentCalls) {
                getGroupedRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            } else {
                getRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            }
        }
    }

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<CallLogItem>) -> Unit) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }

        // Use cached contacts from MainActivity if available
        val mainActivity = activity as? MainActivity
        val cachedContacts = mainActivity?.cachedContacts
        
        ensureBackgroundThread {
            if (cachedContacts != null && cachedContacts.isNotEmpty()) {
                // Fast path: Use cached contacts
                val privateContacts = getPrivateContactsCached()
                val updatedCalls = updateNamesIfEmpty(
                    calls = maybeFilterPrivateCalls(calls, privateContacts),
                    contacts = cachedContacts,
                    privateContacts = privateContacts
                )

                callback(groupCallsByDate(updatedCalls))
            } else {
                // Slow path: Load contacts (but load only what we need)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    ensureBackgroundThread {
                        val privateContacts = getPrivateContactsCached()
                        val updatedCalls = updateNamesIfEmpty(
                            calls = maybeFilterPrivateCalls(calls, privateContacts),
                            contacts = contacts,
                            privateContacts = privateContacts
                        )

                        callback(groupCallsByDate(updatedCalls))
                    }
                }
            }
        }
    }
    
    // Cached version of getPrivateContacts
    private var cachedPrivateContacts: ArrayList<Contact>? = null
    private var privateContactsCacheTime = 0L
    
    private fun getPrivateContactsCached(): ArrayList<Contact> {
        val currentTime = System.currentTimeMillis()
        if (cachedPrivateContacts != null && (currentTime - privateContactsCacheTime) < 60000L) {
            return cachedPrivateContacts!!
        }
        
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        cachedPrivateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
        privateContactsCacheTime = currentTime
        return cachedPrivateContacts!!
    }

    private fun getPrivateContacts(): ArrayList<Contact> {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        return MyContactsContentProvider.getContacts(context, privateCursor)
    }

    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
        val ignoredSources = context.baseConfig.ignoredContactSources
        return if (SMT_PRIVATE in ignoredSources) {
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            calls.filterNot { it.phoneNumber in privateNumbers }
        } else {
            calls
        }
    }

    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
        if (calls.isEmpty()) return mutableListOf()

        // Pre-build lookup maps for faster access
        val privateNumbersMap = HashMap<String, String>()
        privateContacts.forEach { contact ->
            contact.phoneNumbers.forEach { phoneNumber ->
                privateNumbersMap[phoneNumber.value] = contact.getNameToDisplay()
                privateNumbersMap[phoneNumber.normalizedNumber] = contact.getNameToDisplay()
            }
        }
        
        val contactNumbersMap = HashMap<String, String>()
        contacts.filter { it.phoneNumbers.isNotEmpty() }.forEach { contact ->
            contact.phoneNumbers.forEach { phoneNumber ->
                contactNumbersMap[phoneNumber.normalizedNumber] = contact.getNameToDisplay()
            }
        }

        return calls.map { call ->
            if (call.phoneNumber == call.name) {
                val privateName = privateNumbersMap[call.phoneNumber]
                val contactName = contactNumbersMap[call.phoneNumber]

                when {
                    privateName != null -> withUpdatedName(call = call, name = privateName)
                    contactName != null -> withUpdatedName(call = call, name = contactName)
                    else -> call
                }
            } else {
                call
            }
        }
    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

    private fun groupCallsByDate(recentCalls: List<RecentCall>): MutableList<CallLogItem> {
        val callLog = mutableListOf<CallLogItem>()
        var lastDayCode = ""
        for (call in recentCalls) {
            val currentDayCode = call.dayCode
            if (currentDayCode != lastDayCode) {
                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }

            callLog += call
        }

        return callLog
    }

    private fun setupScrollEnrichment() {
        binding.recentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleVisibleEnrichment()
                }
            }
        })
    }

    private fun showInitialSkeletons() {
        binding.apply {
            showOrHidePlaceholder(false)
            recentsPlaceholder2.beGone()
            recentsList.beVisible()
        }
        if (binding.recentsList.adapter == null) {
            recentsAdapter = RecentCallsAdapter(
                activity = activity as SimpleActivity,
                recyclerView = binding.recentsList,
                refreshItemsListener = this,
                showOverflowMenu = true,
                itemDelete = { deleted ->
                    allRecentCalls = allRecentCalls.filter { it !in deleted }
                },
                itemClick = {
                    val recentCall = it as RecentCall
                    if (!context.config.isSimpleMode && context.config.showQuickActionsSheet && !context.config.directOneTapCalling) {
                        (activity as? org.fossify.phone.activities.SimpleActivity)?.let { act ->
                            org.fossify.phone.helpers.QuickActionsHelper.showRecentCallQuickActions(act, recentCall) {
                                refreshItems(invalidate = false)
                            }
                        }
                    } else {
                        activity?.startCallWithConfirmationCheck(recentCall.phoneNumber, recentCall.name)
                    }
                },
                profileIconClick = {
                    if (context.config.enableProfilePictureClick) {
                        val contact = findContactByCall(it as RecentCall)
                        if (contact != null) {
                            activity?.startContactDetailsIntent(contact)
                        }
                    }
                }
            )
            binding.recentsList.adapter = recentsAdapter
        }
        val placeholders = List(8) { idx -> CallLogItem.Placeholder(idx) }
        recentsAdapter?.updateItems(placeholders)
    }

    private fun scheduleVisibleEnrichment(delayMs: Long = 200) {
        if (enrichmentScheduled) return
        enrichmentScheduled = true
        // Debounce slightly to avoid firing during fast scroll
        postDelayed({
            enrichmentScheduled = false
            val lm = binding.recentsList.layoutManager as? LinearLayoutManager ?: return@postDelayed
            val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
            val last = lm.findLastVisibleItemPosition().coerceAtLeast(first)
            val window = (first..last)
                .mapNotNull { idx -> allRecentCalls.getOrNull(idx) as? RecentCall }
            val numbers = window.map { it.phoneNumber }
            if (numbers.isEmpty()) return@postDelayed
            contactsEnricher.enrich(numbers) { map ->
                // Apply on UI thread
                activity?.runOnUiThread { applyEnrichment(map) }
            }
        }, delayMs)
    }

    private fun applyEnrichment(map: Map<String, VisibleContactsEnricher.Enriched>) {
        if (map.isEmpty()) return
        var changed = false
        val updated = allRecentCalls.map { item ->
            if (item is RecentCall) {
                val key = item.phoneNumber.filter { it.isDigit() }.takeLast(15)
                val en = map[key]
                if (en != null) {
                    val newName = en.name ?: item.name
                    val newPhoto = en.photoUri ?: item.photoUri
                    if (newName != item.name || newPhoto != item.photoUri) {
                        changed = true
                        item.copy(
                            name = newName,
                            photoUri = newPhoto,
                            groupedCalls = item.groupedCalls?.map { gc ->
                                gc.copy(name = newName, photoUri = newPhoto)
                            }?.toMutableList()
                        )
                    } else item
                } else item
            } else item
        }
        if (changed) Perf.markFirstName()
        allRecentCalls = updated
        val filtered = filterCalls(updated)
        recentsAdapter?.updateItems(filtered)
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun setupFilterChips() {
        if (context.config.isSimpleMode) {
            binding.filterScrollView.beGone()
            return
        } else {
            binding.filterScrollView.beVisible()
        }

        val clickListener = View.OnClickListener { v ->
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val filter = when (v.id) {
                R.id.filter_missed -> FILTER_MISSED
                R.id.filter_incoming -> FILTER_INCOMING
                R.id.filter_outgoing -> FILTER_OUTGOING
                else -> FILTER_ALL
            }
            applyFilter(filter)
        }

        binding.filterAll.setOnClickListener(clickListener)
        binding.filterMissed.setOnClickListener(clickListener)
        binding.filterIncoming.setOnClickListener(clickListener)
        binding.filterOutgoing.setOnClickListener(clickListener)

        // Set initial state
        updateFilterChips()
    }

    private fun applyFilter(filterType: Int) {
        currentFilter = filterType
        updateFilterChips()
        refreshFilteredCalls()
    }

    private fun updateFilterChips() {
        val simpleAct = activity as? SimpleActivity
        val primaryColor = simpleAct?.getProperPrimaryColor() ?: context.getColor(R.color.color_primary)
        val surfaceColor = context.getColor(R.color.elevated_surface_color)
        val textColor = simpleAct?.getProperTextColor() ?: context.getColor(R.color.text_primary)
        val contrastColor = primaryColor.getContrastColor()
        val dividerColor = context.getColor(R.color.divider_color)

        val chips = listOf(
            binding.filterAll to (currentFilter == FILTER_ALL),
            binding.filterMissed to (currentFilter == FILTER_MISSED),
            binding.filterIncoming to (currentFilter == FILTER_INCOMING),
            binding.filterOutgoing to (currentFilter == FILTER_OUTGOING)
        )

        chips.forEach { (chip, isSelected) ->
            chip.isChecked = isSelected
            if (isSelected) {
                chip.chipBackgroundColor = ColorStateList.valueOf(primaryColor)
                chip.setTextColor(contrastColor)
                chip.chipStrokeWidth = 0f
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(surfaceColor)
                chip.setTextColor(textColor)
                chip.chipStrokeColor = ColorStateList.valueOf(dividerColor)
                chip.chipStrokeWidth = resources.displayMetrics.density * 1f
            }
        }
    }

    private fun refreshFilteredCalls() {
        val filteredCalls = filterCalls(allRecentCalls)
        gotRecents(filteredCalls)
    }

    private fun filterCalls(callLogItems: List<CallLogItem>): List<CallLogItem> {
        if (currentFilter == FILTER_ALL) {
            return callLogItems
        }

        val filteredRecentCalls = callLogItems
            .filterIsInstance<RecentCall>()
            .filter { call ->
                when (currentFilter) {
                    FILTER_MISSED -> call.type == Calls.MISSED_TYPE || call.type == Calls.REJECTED_TYPE
                    FILTER_INCOMING -> call.type == Calls.INCOMING_TYPE
                    FILTER_OUTGOING -> call.type == Calls.OUTGOING_TYPE
                    else -> true
                }
            }

        return groupCallsByDate(filteredRecentCalls)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isFragmentVisible = false
        try { binding.recentsList.adapter = null } catch (_: Exception) {}
        recentsAdapter = null
    }

    private var swipeHelperAttached = false

    private fun setupSwipeActions() {
        if (swipeHelperAttached) return
        val rv = binding.recentsList

        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.RIGHT or androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            private val callBgColor = ContextCompat.getColor(context, R.color.color_outgoing_call)
            private val smsBgColor = ContextCompat.getColor(context, R.color.color_primary)
            private val callIcon = ContextCompat.getDrawable(context, R.drawable.ic_phone_vector)
            private val smsIcon = ContextCompat.getDrawable(context, R.drawable.ic_message_vector)

            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Int {
                if (context.config.isSimpleMode) return 0
                val position = viewHolder.adapterPosition
                val item = recentsAdapter?.currentList?.getOrNull(position)
                return if (item is RecentCall) super.getSwipeDirs(recyclerView, viewHolder) else 0
            }

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = recentsAdapter?.currentList?.getOrNull(position) as? RecentCall
                recentsAdapter?.notifyItemChanged(position)

                if (item != null) {
                    viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    if (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                        activity?.startCallWithConfirmationCheck(item.phoneNumber, item.name)
                    } else if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                        (activity as? org.fossify.phone.activities.SimpleActivity)?.launchSendSMSIntent(item.phoneNumber)
                    }
                }
            }

            val cornerRadius = 16 * resources.displayMetrics.density
            val horizontalMargin = 12 * resources.displayMetrics.density
            val verticalMargin = 4 * resources.displayMetrics.density
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (dX > 0) {
                    bgPaint.color = callBgColor
                    val rect = android.graphics.RectF(
                        itemView.left.toFloat() + horizontalMargin,
                        itemView.top.toFloat() + verticalMargin,
                        itemView.left.toFloat() + dX,
                        itemView.bottom.toFloat() - verticalMargin
                    )
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

                    callIcon?.let { icon ->
                        val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + icon.intrinsicHeight
                        val iconLeft = itemView.left + iconMargin + horizontalMargin.toInt()
                        val iconRight = iconLeft + icon.intrinsicWidth
                        val swipeDistance = dX - horizontalMargin
                        if (swipeDistance > 0) {
                            val alpha = (swipeDistance / iconMargin.toFloat()).coerceIn(0f, 1f)
                            icon.alpha = (alpha * 255).toInt()
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            icon.setTint(android.graphics.Color.WHITE)
                            icon.draw(c)
                        }
                    }
                } else if (dX < 0) {
                    bgPaint.color = smsBgColor
                    val rect = android.graphics.RectF(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat() + verticalMargin,
                        itemView.right.toFloat() - horizontalMargin,
                        itemView.bottom.toFloat() - verticalMargin
                    )
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

                    smsIcon?.let { icon ->
                        val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + icon.intrinsicHeight
                        val iconRight = itemView.right - iconMargin - horizontalMargin.toInt()
                        val iconLeft = iconRight - icon.intrinsicWidth
                        val swipeDistance = -dX - horizontalMargin
                        if (swipeDistance > 0) {
                            val alpha = (swipeDistance / iconMargin.toFloat()).coerceIn(0f, 1f)
                            icon.alpha = (alpha * 255).toInt()
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            icon.setTint(android.graphics.Color.WHITE)
                            icon.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(rv)
        swipeHelperAttached = true
    }

    private fun updateAnalyticsAndFrequentContacts() {
        val recentCalls = allRecentCalls.filterIsInstance<RecentCall>()
        if (recentCalls.isEmpty()) {
            binding.recentsAnalyticsCard.beGone()
            binding.frequentContactsScrollView.beGone()
            return
        }

        analyticsJob?.cancel()
        analyticsJob = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.Default) {
            // 1. Today's Call Analytics
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val todayStartTS = (calendar.timeInMillis / 1000).toInt()

            val todayCalls = recentCalls.filter { it.startTS >= todayStartTS }
            val hasTodayCalls = todayCalls.isNotEmpty()
            val totalSeconds = if (hasTodayCalls) todayCalls.sumOf { it.duration.toLong() } else 0L
            val missedToday = if (hasTodayCalls) todayCalls.count { it.type == Calls.MISSED_TYPE || it.type == Calls.REJECTED_TYPE } else 0
            val todayCallsCount = todayCalls.size

            // 2. Top Frequent Contacts Carousel
            val frequentCalls = recentCalls
                .groupBy { if (it.name.isNotEmpty()) it.name else it.phoneNumber }
                .entries
                .sortedByDescending { it.value.size }
                .take(6)
                .map { it.value.first() }

            withContext(Dispatchers.Main) {
                if (!isActive) return@withContext

                if (hasTodayCalls) {
                    val formattedTime = (activity as? org.fossify.phone.activities.SimpleActivity)?.formatSecondsToShortTimeString(totalSeconds.toInt()) ?: "${totalSeconds / 60}m"
                    val missedPart = if (missedToday > 0) " • $missedToday missed" else ""
                    binding.recentsAnalyticsText.text = "Today: $todayCallsCount calls • $formattedTime talk time$missedPart"
                    binding.recentsAnalyticsCard.beVisible()
                } else {
                    binding.recentsAnalyticsCard.beGone()
                }

                if (frequentCalls.size >= 2) {
                    binding.frequentContactsHolder.removeAllViews()
                    val density = resources.displayMetrics.density
                    val itemWidth = (72 * density).toInt()

                    frequentCalls.forEach { call ->
                        val container = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = (4 * density).toInt()
                            }
                            isClickable = true
                            isFocusable = true
                            setBackgroundResource(R.drawable.contact_action_pill_bg)
                            val pad = (6 * density).toInt()
                            setPadding(pad, pad, pad, pad)
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start()
                                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                                }
                                false
                            }
                            setOnClickListener {
                                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                (activity as? org.fossify.phone.activities.SimpleActivity)?.let { act ->
                                    org.fossify.phone.helpers.QuickActionsHelper.showRecentCallQuickActions(act, call) {
                                        refreshItems(invalidate = false)
                                    }
                                }
                            }
                        }

                        val avatar = ImageView(context).apply {
                            val avatarSize = (44 * density).toInt()
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            org.fossify.commons.helpers.SimpleContactsHelper(context).loadContactImage(call.photoUri, this, call.name)
                            addAnimatedGradientRing()
                        }

                        val nameView = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = (4 * density).toInt()
                            }
                            val displayName = (if (call.name.isNotEmpty()) call.name else call.phoneNumber).split(" ").firstOrNull() ?: call.name
                            text = displayName
                            textSize = 11f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setTextColor(context.getColor(R.color.text_primary))
                            gravity = Gravity.CENTER
                        }

                        container.addView(avatar)
                        container.addView(nameView)
                        binding.frequentContactsHolder.addView(container)
                    }
                    binding.frequentContactsScrollView.beVisible()
                } else {
                    binding.frequentContactsScrollView.beGone()
                }
            }
        }
    }
}
