package org.fossify.phone.services

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import org.fossify.phone.BuildConfig

object GeminiService {
    private const val TAG = "GeminiService"
    private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private const val MAX_CONTACT_CANDIDATES = 150

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(15))
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(15))
            .build()
    }

    @Serializable
    data class GeminiIntent(val intent: String?, val contact: String?, val number: String? = null)

    /**
     * Calls Google Gemini 1.5 Flash via REST API with secure header-based authentication.
     * The API key is sent via the 'x-goog-api-key' HTTP header to prevent query parameter exposure.
     * Full request bodies and contact data are strictly shielded from system Logcat to preserve privacy.
     */
    suspend fun parseIntent(transcribedText: String, contacts: List<Pair<String, String>>): GeminiIntent? = withContext(Dispatchers.IO) {
        val apiKey: String? = try { BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() } } catch (e: Throwable) { null }
        if (apiKey == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Gemini API key is unconfigured. Using local matcher fallback.")
            }
            return@withContext null
        }

        // Limit candidates to prevent large network payloads and protect user bandwidth
        val candidateContacts = contacts.take(MAX_CONTACT_CANDIDATES)

        // Build a strict JSON-only prompt asking the model to extract intent and match contact
        val contactsJson = candidateContacts.joinToString(",") {
            val safeName = it.first.replace("\"", "\\\"").replace("\n", " ")
            val safeNum = it.second.replace("\"", "\\\"")
            "{\"name\":\"$safeName\",\"number\":\"$safeNum\"}"
        }

        val prompt = buildString {
            append("You are an extractor.\n")
            append("User command: \"").append(transcribedText.replace("\"", "\\\"")).append("\"\n")
            append("Available contacts: [").append(contactsJson).append("]\n")
            append("Respond with EXACTLY one JSON object and nothing else, with the keys: `intent`, `contact`, `number`.\n")
            append("If you cannot find a contact, set contact to null and number to null.\n")
            append("Example output: {\"intent\":\"call\",\"contact\":\"Mom\",\"number\":\"+12345\"}\n")
            append("Do not include any surrounding explanation or additional text.\n")
        }

        val safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonPayload = "{\"contents\":[{\"parts\":[{\"text\":\"$safePrompt\"}]}]}"

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonPayload.toRequestBody(mediaType)

        val maxAttempts = 2
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(GEMINI_ENDPOINT)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", apiKey) // Secure header authentication
                    .post(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    val respStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "Gemini API returned error code ${resp.code}")
                        }
                        if (resp.code in 400..499) return@withContext null
                        if (attempt < maxAttempts) {
                            delay(250L * attempt)
                            continue
                        }
                        return@withContext null
                    }

                    // Extract JSON object containing intent key
                    val regex = Regex("(?s)\\{\\s*\"intent\".*?\\}")
                    val match = regex.find(respStr)
                    if (match != null) {
                        val jsonText = match.value
                        try {
                            val parsed = Json.parseToJsonElement(jsonText).jsonObject
                            val intent = parsed["intent"]?.toString()?.trim('"')
                            val contact = parsed["contact"]?.toString()?.trim('"')
                            var number = parsed["number"]?.toString()?.trim('"')

                            // If model returned a name without number, resolve locally from contacts
                            if (!contact.isNullOrBlank() && (number == null || number.isBlank())) {
                                val found = candidateContacts.firstOrNull { pair ->
                                    pair.first.equals(contact, ignoreCase = true) || pair.first.contains(contact, ignoreCase = true)
                                }
                                if (found != null) {
                                    number = found.second
                                }
                            }

                            return@withContext GeminiIntent(intent, contact, number)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.w(TAG, "Could not decode model response JSON")
                            }
                            return@withContext null
                        }
                    }

                    // Fallback heuristic if keyword "call" is detected
                    if (respStr.contains("call", true)) {
                        val words = transcribedText.split(" ")
                        val contact = words.drop(1).joinToString(" ").trim()
                        return@withContext GeminiIntent("call", if (contact.isNotEmpty()) contact else null)
                    }
                }
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Network attempt $attempt failed: ${e.message}")
                }
                if (attempt < maxAttempts) {
                    delay(250L * attempt)
                    continue
                }
            }
        }

        return@withContext null
    }
}
