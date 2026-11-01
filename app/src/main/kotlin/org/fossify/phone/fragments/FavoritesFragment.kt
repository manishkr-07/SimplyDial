package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import com.google.gson.Gson
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.Converters
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.FragmentFavoritesBinding
import org.fossify.phone.databinding.FragmentLettersLayoutBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.deduplicate
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.interfaces.RefreshItemsListener

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LettersInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentLettersLayoutBinding
    private var allContacts = ArrayList<Contact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentLettersLayoutBinding.bind(FragmentFavoritesBinding.bind(this).favoritesFragment)
        innerBinding = LettersInnerBinding(binding)
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
        binding.fragmentPlaceholder2.beGone()
        binding.horizontalAlphabetSlider.beGone()
        binding.fragmentList.setPaddingRelative(0, 0, 0, context.resources.getDimensionPixelSize(org.fossify.phone.R.dimen.recyclerview_fab_padding))
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.apply {
            fragmentPlaceholder.setTextColor(textColor)
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

            horizontalAlphabetSlider.updateColors()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        val mainCached = (activity as? MainActivity)?.cachedContacts
        if (!invalidate && !mainCached.isNullOrEmpty()) {
            filterAndDisplayFavorites(ArrayList(mainCached), callback)
            return
        }

        ensureBackgroundThread {
            val privateCursor = context?.getMyContactsCursor(favoritesOnly = true, withPhoneNumbersOnly = true)
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                ensureBackgroundThread {
                    allContacts = contacts

                    if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                        val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor).map {
                            it.copy(starred = 1)
                        }
                        if (privateContacts.isNotEmpty()) {
                            allContacts.addAll(privateContacts)
                            allContacts.sort()
                        }
                    }
                    filterAndDisplayFavorites(allContacts.deduplicate(), callback)
                }
            }
        }
    }

    fun refreshFromCached(contacts: ArrayList<Contact>) {
        if (activity == null || activity!!.isDestroyed || activity!!.isFinishing) return
        filterAndDisplayFavorites(contacts.deduplicate(), null)
    }

    private fun filterAndDisplayFavorites(contacts: ArrayList<Contact>, callback: (() -> Unit)?) {
        val deduplicated = contacts.deduplicate()
        val favorites = deduplicated.filter { it.starred == 1 } as ArrayList<Contact>

        allContacts = if (activity != null && activity!!.config.isCustomOrderSelected) {
            sortByCustomOrder(favorites)
        } else {
            favorites
        }

        activity?.runOnUiThread {
            gotContacts(allContacts)
            callback?.invoke()
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        binding.apply {
            if (contacts.isEmpty()) {
                fragmentPlaceholder.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentList.beVisible()

                updateListAdapter()
            }
        }
    }

    private fun updateListAdapter() {
        val viewType = context.config.viewType
        setViewType(viewType)

        val currAdapter = binding.fragmentList.adapter as ContactsAdapter?
        if (currAdapter == null) {
            ContactsAdapter(
                activity = activity as SimpleActivity,
                contacts = allContacts,
                recyclerView = binding.fragmentList,
                refreshItemsListener = this,
                viewType = viewType,
                showDeleteButton = false,
                enableDrag = true,
                itemClick = {
                    activity?.startCallWithConfirmationCheck(it as Contact)
                },
                profileIconClick = {
                    if (context.config.enableProfilePictureClick) {
                        activity?.startContactDetailsIntent(it as Contact)
                    }
                }).apply {
                binding.fragmentList.adapter = this

                onDragEndListener = {
                    val adapter = binding.fragmentList.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contacts
                        saveCustomOrderToPrefs(items)
                        setupLetterFastScroller(items)
                    }
                }

                onSpanCountListener = { newSpanCount ->
                    context.config.contactsGridColumnCount = newSpanCount
                }
            }

        } else {
            currAdapter.viewType = viewType
            currAdapter.updateItems(allContacts)
        }
    }

    fun columnCountChanged() {
        (binding.fragmentList.layoutManager as? MyGridLayoutManager)?.spanCount = context!!.config.contactsGridColumnCount
        binding.fragmentList.adapter?.apply {
            notifyItemRangeChanged(0, allContacts.size)
        }
    }

    private fun sortByCustomOrder(favorites: List<Contact>): ArrayList<Contact> {
        val favoritesOrder = activity!!.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        binding.horizontalAlphabetSlider.setupWithContacts(binding.fragmentList, contacts)
    }

    override fun onSearchClosed() {
        binding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val fixedText = text.trim().replace("\\s+".toRegex(), " ")
        val contacts = allContacts.filter {
            it.name.contains(fixedText, true) || (text.toLongOrNull() != null && it.doesContainPhoneNumber(fixedText))
        }.sortedByDescending {
            it.name.startsWith(fixedText, true)
        }.toMutableList() as ArrayList<Contact>

        binding.fragmentPlaceholder.beVisibleIf(contacts.isEmpty())
        (binding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts, fixedText)
        setupLetterFastScroller(contacts)
    }

    private fun setViewType(viewType: Int) {
        val spanCount = context.config.contactsGridColumnCount

        val layoutManager = if (viewType == VIEW_TYPE_GRID) {
            binding.horizontalAlphabetSlider.beGone()
            MyGridLayoutManager(context, spanCount)
        } else {
            binding.horizontalAlphabetSlider.beVisible()
            MyLinearLayoutManager(context)
        }
        binding.fragmentList.layoutManager = layoutManager
    }
}
