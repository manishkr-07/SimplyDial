package org.fossify.phone.services

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactResolver {
    private const val TAG = "ContactResolver"

    data class ResolvedContact(val name: String, val number: String, val photoUri: android.net.Uri? = null)

    // Returns list of candidate matches (may be empty). Simple contains-based match.
    fun findMatches(context: Context, query: String): List<ResolvedContact> {
        val raw = getAllContacts(context)
        if (query.isBlank()) return raw
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                val photoUriString = it.getString(2)
                val photoUri = if (photoUriString != null) android.net.Uri.parse(photoUriString) else null
                raw.add(ResolvedContact(name, number, photoUri))
            }
        }

        // Score each contact by fuzzy similarity and return top matches
        val scored = raw.map { contact ->
            val score = similarityScore(contact.name, query)
            Pair(contact, score)
        }.filter { it.second > 0.1 } // filter out very low similarity
            .sortedByDescending { it.second }
            .map { it.first }

        Log.d(TAG, "Found ${scored.size} fuzzy matches for '$query'")
        return scored
    }

    // Return all contacts (name, number). Respects READ_CONTACTS permission and returns empty list if not granted.
    fun getAllContacts(context: Context): MutableList<ResolvedContact> {
        val raw = mutableListOf<ResolvedContact>()
        val permission = android.Manifest.permission.READ_CONTACTS
        if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted; returning empty contact list")
            return raw
        }

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                raw.add(ResolvedContact(name, number))
            }
        }
        return raw
    }

    // Compute a normalized similarity score between 0.0 and 1.0 based on Levenshtein distance
    private fun similarityScore(a: String, b: String): Double {
        val aa = a.lowercase().trim()
        val bb = b.lowercase().trim()
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val dist = levenshtein(aa, bb)
        val maxLen = maxOf(aa.length, bb.length)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val d = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
            }
        }
        return d[m][n]
    }
}
