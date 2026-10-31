package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.extensions.hasPermission
import java.util.concurrent.ConcurrentHashMap

/**
 * Enriches phone numbers with contact display names and photo URIs.
 * - In-memory LRU with TTL to avoid repeated lookups
 * - Debounced, small-batch sequential lookups via PhoneLookup CONTENT_FILTER_URI
 */
class VisibleContactsEnricher(private val context: Context) {

    data class Enriched(val name: String?, val photoUri: String?)

    private val ttlMs: Long = 24 * 60 * 60 * 1000L
    private val maxEntries: Int = 1000

    private data class Entry(val value: Enriched, val updatedAt: Long)

    private val cache: LinkedHashMap<String, Entry> = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > maxEntries
        }
    }

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun enrich(numbers: List<String>, onResult: (Map<String, Enriched>) -> Unit) {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            onResult(emptyMap())
            return
        }
        // Normalize keys (keep raw digits end to support matching by suffix)
        val keys = numbers.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) {
            onResult(emptyMap())
            return
        }

        val now = System.currentTimeMillis()
        val result = HashMap<String, Enriched>()
        val missing = ArrayList<String>()

        synchronized(cache) {
            for (k in keys) {
                val e = cache[k]
                if (e != null && now - e.updatedAt < ttlMs) {
                    result[k] = e.value
                } else {
                    missing += k
                }
            }
        }

        if (missing.isEmpty()) {
            onResult(result)
            return
        }

        // Avoid duplicate concurrent lookups of the same numbers
        val toQuery = missing.filter { inFlight.add(it) }
        if (toQuery.isEmpty()) {
            onResult(result)
            return
        }

        ensureBackgroundThread {
            val lookedUp = HashMap<String, Enriched>()
            // Query sequentially (content provider lookups are cheap, keep pressure low)
            for (k in toQuery) {
                val enriched = querySingle(k)
                if (enriched != null) {
                    lookedUp[k] = enriched
                } else {
                    lookedUp[k] = Enriched(null, null)
                }
            }

            synchronized(cache) {
                val stamp = System.currentTimeMillis()
                for ((k, v) in lookedUp) {
                    cache[k] = Entry(v, stamp)
                }
            }
            inFlight.removeAll(toQuery)

            result.putAll(lookedUp)
            onResult(result)
        }
    }

    private fun querySingle(normalized: String): Enriched? {
        // Use PhoneLookup CONTENT_FILTER_URI for robust number matching
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalized))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.NUMBER
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                var bestName: String? = null
                var bestPhoto: String? = null
                while (c.moveToNext()) {
                    val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER)) ?: continue
                    // Prefer exact or suffix matches
                    val nn = normalize(number)
                    if (nn.endsWith(normalized)) {
                        bestName = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                        bestPhoto = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI))
                        break
                    }
                }
                return Enriched(bestName, bestPhoto)
            }
        } catch (_: SecurityException) {
            return null
        }
        return null
    }

    private fun normalize(n: String): String = n.filter { it.isDigit() }.takeLast(15)
}
