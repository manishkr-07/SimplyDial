package org.fossify.phone.extensions

import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.PhoneNumber
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

/**
 * Deduplicate a list of contacts by aggregate contact ID, raw ID, and matching name + normalized phone number.
 * Merges phone numbers, photo URIs, starred status, and metadata so no contact data is lost.
 */
fun List<Contact>.deduplicate(): ArrayList<Contact> {
    if (isEmpty()) return ArrayList()

    val result = ArrayList<Contact>(size)
    val contactIdMap = HashMap<Int, Contact>()
    val rawIdMap = HashMap<Int, Contact>()
    val nameAndNumberMap = HashMap<String, Contact>()
    val numberOnlyMap = HashMap<String, Contact>()

    fun getCleanDigits(number: String?): String {
        if (number.isNullOrEmpty()) return ""
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    for (c in this) {
        val normName = c.getNameToDisplay().trim().lowercase()
        val cNumbers = c.phoneNumbers.map { getCleanDigits(it.normalizedNumber.ifEmpty { it.value }) }.filter { it.isNotEmpty() }

        var existing: Contact? = null

        // 1. Check aggregate contactId (highest confidence)
        if (c.contactId > 0) {
            existing = contactIdMap[c.contactId]
        }

        // 2. Check rawId
        if (existing == null && c.rawId > 0) {
            existing = rawIdMap[c.rawId]
        }

        // 3. Check matching display name AND phone number
        if (existing == null && normName.isNotEmpty()) {
            for (digits in cNumbers) {
                val key = "$normName|$digits"
                val match = nameAndNumberMap[key]
                if (match != null) {
                    existing = match
                    break
                }
            }
        }

        // 4. Check matching phone number if names are identical or one is empty/raw number
        if (existing == null) {
            for (digits in cNumbers) {
                if (digits.length >= 7) {
                    val match = numberOnlyMap[digits]
                    if (match != null) {
                        val matchName = match.getNameToDisplay().trim().lowercase()
                        if (matchName == normName || matchName.isEmpty() || normName.isEmpty() || matchName == digits || normName == digits) {
                            existing = match
                            break
                        }
                    }
                }
            }
        }

        // 5. If names are identical and neither contact has any phone numbers
        if (existing == null && normName.isNotEmpty() && cNumbers.isEmpty()) {
            val key = "$normName|"
            val match = nameAndNumberMap[key]
            if (match != null) {
                existing = match
            }
        }

        if (existing != null) {
            // Merge into existing contact

            // Preserve starred state (if any copy was starred, keep it starred)
            if (c.starred == 1 && existing.starred != 1) {
                existing.starred = 1
            }

            // Fill photoUri if existing was missing one
            if (existing.photoUri.isNullOrEmpty() && !c.photoUri.isNullOrEmpty()) {
                existing.photoUri = c.photoUri
            }

            // Merge contactId if existing was non-positive
            if (existing.contactId <= 0 && c.contactId > 0) {
                existing.contactId = c.contactId
            }

            // Merge phone numbers without duplicate numbers
            val existingDigits = existing.phoneNumbers.map { getCleanDigits(it.normalizedNumber.ifEmpty { it.value }) }.toMutableSet()
            for (phone in c.phoneNumbers) {
                val digits = getCleanDigits(phone.normalizedNumber.ifEmpty { phone.value })
                if (digits.isNotEmpty() && !existingDigits.contains(digits)) {
                    existing.phoneNumbers.add(phone)
                    existingDigits.add(digits)
                    if (normName.isNotEmpty()) {
                        nameAndNumberMap["$normName|$digits"] = existing
                    }
                    if (digits.length >= 7) {
                        numberOnlyMap[digits] = existing
                    }
                }
            }

            // Merge emails, notes if missing
            if (existing.notes.isNullOrEmpty() && !c.notes.isNullOrEmpty()) {
                existing.notes = c.notes
            }
            if (existing.emails.isEmpty() && c.emails.isNotEmpty()) {
                existing.emails.addAll(c.emails)
            }

            // Register merged contact with newly observed IDs
            if (c.contactId > 0 && !contactIdMap.containsKey(c.contactId)) {
                contactIdMap[c.contactId] = existing
            }
            if (c.rawId > 0 && !rawIdMap.containsKey(c.rawId)) {
                rawIdMap[c.rawId] = existing
            }
        } else {
            // New unique contact
            result.add(c)

            if (c.contactId > 0) {
                contactIdMap[c.contactId] = c
            }
            if (c.rawId > 0) {
                rawIdMap[c.rawId] = c
            }
            if (cNumbers.isEmpty() && normName.isNotEmpty()) {
                nameAndNumberMap["$normName|"] = c
            }
            for (digits in cNumbers) {
                if (normName.isNotEmpty()) {
                    nameAndNumberMap["$normName|$digits"] = c
                }
                if (digits.length >= 7 && !numberOnlyMap.containsKey(digits)) {
                    numberOnlyMap[digits] = c
                }
            }
        }
    }

    return result
}

/**
 * Get the first letter of a contact name, supporting both English and Hindi (Devanagari) characters
 */
fun Contact.getFirstLetter(): String {
    val name = getNameToDisplay()
    if (name.isEmpty()) return "#"
    
    val firstChar = name.first()
    
    return when {
        // English letters (A-Z, a-z) → uppercase
        firstChar.code in 'A'.code..'Z'.code -> firstChar.toString()
        firstChar.code in 'a'.code..'z'.code -> firstChar.uppercaseChar().toString()
        
        // Hindi Devanagari vowels (अ-औ, Unicode range: U+0905 to U+0914)
        firstChar.code in 0x0905..0x0914 -> firstChar.toString()
        
        // Hindi Devanagari consonants (क-ह, Unicode range: U+0915 to U+0939)
        firstChar.code in 0x0915..0x0939 -> firstChar.toString()
        
        // Hindi Devanagari additional characters (Unicode range: U+093A to U+097F)
        firstChar.code in 0x093A..0x097F -> firstChar.toString()
        
        // Numbers or special characters
        else -> "#"
    }
}

/**
 * Check if contact name starts with an English letter
 */
fun Contact.startsWithEnglishLetter(): Boolean {
    val firstLetter = getFirstLetter()
    if (firstLetter.isEmpty() || firstLetter == "#") return false
    val firstChar = firstLetter.first()
    return firstChar.code in 'A'.code..'Z'.code
}

/**
 * Check if contact name starts with a Hindi letter
 */
fun Contact.startsWithHindiLetter(): Boolean {
    val firstLetter = getFirstLetter()
    if (firstLetter.isEmpty() || firstLetter == "#") return false
    val firstChar = firstLetter.first()
    return firstChar.code in 0x0905..0x097F
}
