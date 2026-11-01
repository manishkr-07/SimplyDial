package org.fossify.phone.services

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Duration
import org.fossify.phone.BuildConfig

/**
 * Modernized Gemini Service powered by Google Gemini 3.8 Flash.
 *
 * Key features:
 * - Direct REST call to `gemini-3.8-flash` with secure `x-goog-api-key` header authentication.
 * - Native Structured JSON output enforcement (`response_mime_type: "application/json"` & `response_schema`).
 * - Strict schema validation and fallback parsing.
 * - Exponential backoff retry loop for transient 429 and 5xx network errors.
 * - Graceful offline ContactResolver fallback for zero-connectivity environments.
 */
object GeminiService {
    private const val TAG = "GeminiService"
    private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
    private const val MAX_CONTACT_CANDIDATES = 150
    private const val MAX_RETRIES = 3

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(15))
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(12))
            .writeTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class GeminiIntent(val intent: String?, val contact: String?, val number: String? = null)

    /**
     * Parses spoken user commands into an actionable intent using Gemini 3.8 Flash.
     * Falls back seamlessly to offline local fuzzy contact matching if network/key is unavailable.
     */
    suspend fun parseIntent(
        transcribedText: String,
        contacts: List<Pair<String, String>>,
        context: Context? = null
    ): GeminiIntent? = withContext(Dispatchers.IO) {
        val apiKey: String? = try {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            null
        }

        // If no API key configured, seamlessly use offline ContactResolver fallback
        if (apiKey == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Gemini API key not configured. Using offline ContactResolver fallback.")
            }
            return@withContext fallbackOfflineMatch(transcribedText, contacts, context)
        }

        val candidateContacts = contacts.take(MAX_CONTACT_CANDIDATES)

        // Build prompt with candidates list
        val contactsJson = candidateContacts.joinToString(",") {
            val safeName = it.first.replace("\"", "\\\"").replace("\n", " ").trim()
            val safeNum = it.second.replace("\"", "\\\"").trim()
            "{\"name\":\"$safeName\",\"number\":\"$safeNum\"}"
        }

        val prompt = buildString {
            append("You are an intelligent telephony voice dialer command parser.\n")
            append("User command: \"").append(transcribedText.replace("\"", "\\\"")).append("\"\n")
            append("Available contacts: [").append(contactsJson).append("]\n")
            append("Task: Determine the user intent ('call' or 'sms'), match the most relevant contact from the list, ")
            append("and return the matched name and phone number. If no suitable contact is found, set contact to null and number to null.")
        }

        val safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")

        // Structured JSON Output schema definition for Gemini 3.8 Flash
        val jsonPayload = buildString {
            append("{")
            append("\"contents\":[{\"parts\":[{\"text\":\"$safePrompt\"}]}],")
            append("\"generationConfig\":{")
            append("\"response_mime_type\":\"application/json\",")
            append("\"response_schema\":{")
            append("\"type\":\"OBJECT\",")
            append("\"properties\":{")
            append("\"intent\":{\"type\":\"STRING\",\"description\":\"User intent: 'call' or 'sms'\"},")
            append("\"contact\":{\"type\":\"STRING\",\"description\":\"Matched contact name from the list or null\"},")
            append("\"number\":{\"type\":\"STRING\",\"description\":\"Matched phone number or null\"}")
            append("},")
            append("\"required\":[\"intent\"]")
            append("}")
            append("}")
            append("}")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(GEMINI_ENDPOINT)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    val respStr = resp.body?.string().orEmpty()

                    if (!resp.isSuccessful) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Gemini API HTTP ${resp.code}: $respStr")
                        }
                        // Non-retryable client errors (Bad request, unauthorized, etc.)
                        if (resp.code in 400..499 && resp.code != 429) {
                            return@withContext fallbackOfflineMatch(transcribedText, candidateContacts, context)
                        }

                        // Retryable error (429 rate limit or 5xx server error)
                        if (attempt < MAX_RETRIES) {
                            val backoffMs = (250L * (1 shl (attempt - 1))) + (0..100).random()
                            delay(backoffMs)
                            return@use // continue retry loop
                        }
                        return@withContext fallbackOfflineMatch(transcribedText, candidateContacts, context)
                    }

                    // Validate and decode response
                    val parsedIntent = parseAndValidateResponse(respStr, candidateContacts, transcribedText)
                    if (parsedIntent != null) {
                        return@withContext parsedIntent
                    }
                }
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Gemini network attempt $attempt failed: ${e.message}")
                }
                if (attempt < MAX_RETRIES) {
                    val backoffMs = (250L * (1 shl (attempt - 1))) + (0..100).random()
                    delay(backoffMs)
                    continue
                }
            }
        }

        // Final fallback if all attempts exhausted
        return@withContext fallbackOfflineMatch(transcribedText, candidateContacts, context)
    }

    /**
     * Validates and extracts structured JSON from the Gemini response body.
     */
    private fun parseAndValidateResponse(
        responseBody: String,
        candidateContacts: List<Pair<String, String>>,
        originalText: String
    ): GeminiIntent? {
        try {
            val root = jsonParser.parseToJsonElement(responseBody).jsonObject

            // Check candidates array
            val candidates = root["candidates"] as? kotlinx.serialization.json.JsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts") as? kotlinx.serialization.json.JsonArray
            val textPart = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

            val targetJson = if (!textPart.isNullOrBlank()) {
                textPart
            } else {
                // Fallback: search root for JSON object containing intent
                val regex = Regex("(?s)\\{\\s*\"intent\".*?\\}")
                regex.find(responseBody)?.value
            }

            if (!targetJson.isNullOrBlank()) {
                val parsed = jsonParser.parseToJsonElement(targetJson).jsonObject
                val intent = parsed["intent"]?.jsonPrimitive?.content?.trim('"')?.lowercase()
                var contact = parsed["contact"]?.jsonPrimitive?.content?.trim('"')
                var number = parsed["number"]?.jsonPrimitive?.content?.trim('"')

                if (contact.equals("null", ignoreCase = true)) contact = null
                if (number.equals("null", ignoreCase = true)) number = null

                // If contact is provided without phone number, resolve locally from candidate pairs
                if (!contact.isNullOrBlank() && (number == null || number.isBlank())) {
                    val found = candidateContacts.firstOrNull { pair ->
                        pair.first.equals(contact, ignoreCase = true) ||
                            pair.first.contains(contact, ignoreCase = true)
                    }
                    if (found != null) {
                        number = found.second
                    }
                }

                if (intent != null) {
                    return GeminiIntent(intent, contact, number)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Error parsing Gemini structured JSON: ${e.message}")
            }
        }

        // Secondary heuristic fallback if model text mentions call
        if (responseBody.contains("call", ignoreCase = true)) {
            val words = originalText.split(" ")
            val contactName = words.drop(1).joinToString(" ").trim()
            val matchedNumber = candidateContacts.firstOrNull {
                it.first.contains(contactName, ignoreCase = true)
            }?.second

            return GeminiIntent("call", if (contactName.isNotEmpty()) contactName else null, matchedNumber)
        }

        return null
    }

    /**
     * Offline ContactResolver fallback when network is unavailable or API fails.
     */
    private fun fallbackOfflineMatch(
        transcribedText: String,
        contacts: List<Pair<String, String>>,
        context: Context?
    ): GeminiIntent? {
        val cleanQuery = transcribedText
            .replace("call", "", ignoreCase = true)
            .replace("please", "", ignoreCase = true)
            .replace("dial", "", ignoreCase = true)
            .trim()

        if (cleanQuery.isBlank()) return null

        // If Android Context is available, use ContactResolver with Levenshtein fuzzy matching
        if (context != null) {
            val matches = ContactResolver.findMatches(context, cleanQuery)
            val topMatch = matches.firstOrNull()
            if (topMatch != null) {
                return GeminiIntent("call", topMatch.name, topMatch.number)
            }
        }

        // Otherwise match against the supplied contact pairs
        val found = contacts.firstOrNull { pair ->
            pair.first.equals(cleanQuery, ignoreCase = true) ||
                pair.first.contains(cleanQuery, ignoreCase = true)
        }
        if (found != null) {
            return GeminiIntent("call", found.first, found.second)
        }

        return null
    }
}
