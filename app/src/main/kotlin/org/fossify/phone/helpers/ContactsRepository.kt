package org.fossify.phone.helpers

import android.content.Context
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.extensions.deduplicate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unified High-Performance Contacts Repository.
 * Provides in-memory caching, startup preloading, and request deduplication
 * across ContactsFragment, FavoritesFragment, RecentsFragment, and DialpadActivity.
 */
object ContactsRepository {
    @Volatile
    private var inMemoryContacts: ArrayList<Contact>? = null

    @Volatile
    private var isFetching = false
    private val pendingCallbacks = CopyOnWriteArrayList<(ArrayList<Contact>) -> Unit>()
    private val lock = Any()

    fun getCachedContacts(): ArrayList<Contact>? {
        return inMemoryContacts
    }

    fun hasCachedContacts(): Boolean {
        return !inMemoryContacts.isNullOrEmpty()
    }

    fun invalidate() {
        synchronized(lock) {
            inMemoryContacts = null
        }
    }

    fun setCachedContacts(contacts: ArrayList<Contact>) {
        synchronized(lock) {
            inMemoryContacts = contacts
        }
    }

    /**
     * Preloads contacts in the background immediately during app launch.
     */
    fun preload(context: Context) {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) return
        if (hasCachedContacts() || isFetching) return
        loadContacts(context, forceRefresh = false, callback = null)
    }

    /**
     * Loads contacts with 0ms in-memory cache lookup and unified single-flight query.
     */
    fun loadContacts(
        context: Context,
        forceRefresh: Boolean = false,
        callback: ((ArrayList<Contact>) -> Unit)? = null
    ) {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            callback?.invoke(ArrayList())
            return
        }

        synchronized(lock) {
            if (!forceRefresh && !inMemoryContacts.isNullOrEmpty()) {
                val cached = ArrayList(inMemoryContacts!!)
                callback?.invoke(cached)
                return
            }

            if (callback != null) {
                pendingCallbacks.add(callback)
            }

            if (isFetching) {
                return
            }
            isFetching = true
        }

        ensureBackgroundThread {
            try {
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    ensureBackgroundThread {
                        val allContacts = ArrayList(contacts)

                        if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                            val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                            if (privateContacts.isNotEmpty()) {
                                allContacts.addAll(privateContacts)
                                allContacts.sort()
                            }
                        }

                        val deduplicated = allContacts.deduplicate()

                        synchronized(lock) {
                            inMemoryContacts = deduplicated
                            isFetching = false
                        }

                        while (pendingCallbacks.isNotEmpty()) {
                            val cb = pendingCallbacks.removeFirstOrNull() ?: break
                            try {
                                cb(deduplicated)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
                synchronized(lock) {
                    isFetching = false
                    pendingCallbacks.clear()
                }
            }
        }
    }
}
