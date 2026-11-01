package org.fossify.phone.fragments

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.AttributeSet
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.getProperText
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.adapters.LightContactItem
import org.fossify.phone.adapters.LightContactsAdapter
import org.fossify.phone.databinding.FragmentContactsBinding
import org.fossify.phone.databinding.FragmentLettersLayoutBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.deduplicate
import org.fossify.phone.extensions.launchCreateNewContactIntent
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.LightContact
import kotlin.concurrent.thread

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()

    // Lightweight, fast-loading contacts for instant display
    private val lightContacts = ArrayList<LightContact>()
    private var lightAdapter: LightContactsAdapter? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentContactsBinding.bind(this).contactsFragment)
        innerBinding = LettersInnerBinding(binding)
        // RecyclerView perf tweaks
        binding.fragmentList.setHasFixedSize(true)
        binding.fragmentList.setItemViewCacheSize(20)
        binding.fragmentList.itemAnimator = null
    }

override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        binding.fragmentPlaceholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        binding.fragmentPlaceholder2.apply {
            text = context.getString(placeholderActionResId)
            underlineText()
            setOnClickListener {
                if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
                    activity?.launchCreateNewContactIntent()
                } else {
                    requestReadContactsPermission()
                }
            }
        }

        binding.horizontalAlphabetSlider.beGone()

        // Show fast skeletons immediately to avoid blank screen
        showLightSkeletons()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)
            fragmentPlaceholder.setTextColor(textColor)
            fragmentPlaceholder2.setTextColor(properPrimaryColor)

            horizontalAlphabetSlider.updateColors()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        // If contacts are already loaded and not forced to invalidate, update UI instantly without re-querying
        if (!invalidate && allContacts.isNotEmpty()) {
            gotContacts(allContacts)
            callback?.invoke()
            return
        }

        // Start ultra-fast lightweight load for instant UI
        loadLightContactsAsync()

        val privateCursor = context?.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts

            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }

            val deduplicated = allContacts.deduplicate()
            allContacts = deduplicated

            // Sync with MainActivity cache directly without triggering a redundant query
            (activity as? MainActivity)?.let { main ->
                main.runOnUiThread {
                    main.cachedContacts.clear()
                    main.cachedContacts.addAll(deduplicated)
                }
            }

            activity?.runOnUiThread {
                gotContacts(deduplicated)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        if (contacts.isEmpty()) {
            binding.apply {
                fragmentPlaceholder.beVisible()
                fragmentPlaceholder2.beVisible()
                fragmentList.beGone()
            }
        } else {
            binding.apply {
                fragmentPlaceholder.beGone()
                fragmentPlaceholder2.beGone()
                fragmentList.beVisible()
            }

            // Reuse existing adapter with DiffUtil instead of recreating viewholders
            val currentAdapter = binding.fragmentList.adapter as? ContactsAdapter
            if (currentAdapter != null) {
                currentAdapter.updateItems(contacts)
            } else {
                val newAdapter = ContactsAdapter(
                    activity = activity as SimpleActivity,
                    contacts = contacts,
                    recyclerView = binding.fragmentList,
                    refreshItemsListener = this,
                    itemClick = {
                        val contact = it as Contact
                        if (context.config.directOneTapCalling) {
                            (activity as? SimpleActivity)?.startCallWithConfirmationCheck(contact)
                        } else {
                            (binding.fragmentList.adapter as? ContactsAdapter)?.showContactQuickActions(contact)
                        }
                    },
                    profileIconClick = {
                        if (context.config.enableProfilePictureClick) {
                            activity?.startContactDetailsIntent(it as Contact)
                        }
                    }
                )
                binding.fragmentList.adapter = newAdapter
                lightAdapter = null
            }
        }
    } 

    private fun setupLetterFastScroller(contacts: ArrayList<Contact>) {
        binding.horizontalAlphabetSlider.beVisible()
        binding.horizontalAlphabetSlider.setupWithContacts(binding.fragmentList, contacts)
    }

override fun onSearchClosed() {
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        when (val adapter = binding.fragmentList.adapter) {
            is ContactsAdapter -> adapter.updateItems(allContacts)
            is LightContactsAdapter -> adapter.submitList(lightContacts.map { LightContactItem.Data(it) })
        }
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val shouldNormalize = fixedText.normalizeString() == fixedText

        when (val adapter = binding.fragmentList.adapter) {
            is ContactsAdapter -> {
                val filtered = allContacts.filter { contact ->
                    getProperText(contact.getNameToDisplay(), shouldNormalize).contains(fixedText, true) ||
                        getProperText(contact.nickname, shouldNormalize).contains(fixedText, true) ||
                        (fixedText.toLongOrNull() != null && contact.doesContainPhoneNumber(fixedText, true)) ||
                        contact.emails.any { it.value.contains(fixedText, true) } ||
                        contact.addresses.any { getProperText(it.value, shouldNormalize).contains(fixedText, true) } ||
                        contact.IMs.any { it.value.contains(fixedText, true) } ||
                        getProperText(contact.notes, shouldNormalize).contains(fixedText, true) ||
                        getProperText(contact.organization.company, shouldNormalize).contains(fixedText, true) ||
                        getProperText(contact.organization.jobPosition, shouldNormalize).contains(fixedText, true) ||
                        contact.websites.any { it.contains(fixedText, true) }
                } as ArrayList

                filtered.sortBy {
                    val nameToDisplay = it.getNameToDisplay()
                    !getProperText(nameToDisplay, shouldNormalize).startsWith(fixedText, true) && !nameToDisplay.contains(fixedText, true)
                }

                binding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
                adapter.updateItems(filtered, fixedText)
                setupLetterFastScroller(filtered)
            }
            is LightContactsAdapter -> {
                val filtered = if (fixedText.isEmpty()) lightContacts else lightContacts.filter {
                    it.name.contains(fixedText, true) || it.number.contains(fixedText)
                }
                binding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
                adapter.submitList(filtered.map { LightContactItem.Data(it) })
            }
        }
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                binding.fragmentPlaceholder.text = context.getString(R.string.no_contacts_found)
                binding.fragmentPlaceholder2.text = context.getString(R.string.create_new_contact)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    val deduplicated = contacts.deduplicate()
                    allContacts = deduplicated
                    activity?.runOnUiThread {
                        gotContacts(deduplicated)
                    }
                }
            }
        }
    }
    private fun showLightSkeletons() {
        if (binding.fragmentList.adapter !is LightContactsAdapter) {
            lightAdapter = LightContactsAdapter(
                activity = activity as SimpleActivity,
                recyclerView = binding.fragmentList,
                itemClick = { item -> activity?.startCallWithConfirmationCheck(item.number, item.name) },
                profileIconClick = null
            )
            binding.fragmentList.adapter = lightAdapter
        }
        val placeholders = List(8) { idx -> LightContactItem.Placeholder(idx) }
        lightAdapter?.submitList(placeholders)
    }

    private fun loadLightContactsAsync() {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) return
        thread(name = "LightContactsLoader") {
            try {
                lightContacts.clear()
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                )
                val sortOrder = "LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}) ASC"
                val cursor = try {
                    context.contentResolver.query(uri, projection, null, null, sortOrder)
                } catch (_: SecurityException) {
                    null
                }
                var loaded = 0
                val seenContactIds = HashSet<Long>()
                val seenNameAndNumbers = HashSet<String>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val name = it.getString(1) ?: continue
                        val number = it.getString(2) ?: continue
                        val photo = it.getString(3)

                        // Avoid duplicate rows for the same contact ID
                        if (!seenContactIds.add(id)) continue

                        // Avoid duplicate rows across different accounts sharing name and phone digits
                        val digits = number.filter { ch -> ch.isDigit() }.let { d -> if (d.length > 9) d.takeLast(9) else d }
                        val key = "${name.trim().lowercase()}|$digits"
                        if (digits.isNotEmpty() && !seenNameAndNumbers.add(key)) continue

                        lightContacts.add(LightContact(id, name, number, photo))
                        loaded++
                        // Publish in small batches for smoothness
                        if (loaded <= 10 || loaded % 25 == 0) {
                            val snapshot = lightContacts.toList()
                            activity?.runOnUiThread {
                                if (binding.fragmentList.adapter !is LightContactsAdapter) {
                                    showLightSkeletons()
                                }
                                lightAdapter?.submitList(snapshot.map { LightContactItem.Data(it) })
                            }
                        }
                        if (loaded >= 5000) {
                            // Cap to avoid huge in-memory lists on low-end devices
                            break
                        }
                    }
                }
                // Final publish
                val snapshot = lightContacts.toList()
                activity?.runOnUiThread {
                    lightAdapter?.submitList(snapshot.map { LightContactItem.Data(it) })
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { binding.fragmentList.adapter = null } catch (_: Exception) {}
        lightAdapter = null
        lightContacts.clear()
    }
}
