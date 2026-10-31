package org.fossify.phone.extensions

import org.fossify.commons.models.contacts.Contact

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
