package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@JsonClass(generateAdapter = true)
data class ScanResponse(
    val riskLevel: String,     // "SAFE", "SUSPICIOUS", "DANGEROUS"
    val riskScore: Int,        // 0 to 100
    val verdict: String,       // Detailed analysis feedback
    val recommendedAction: String // Steps user should take
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val scanResponseAdapter = moshi.adapter(ScanResponse::class.java)

    /**
     * Checks if the active API key is a placeholder or blank.
     */
    fun isApiKeyPlaceholder(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isEmpty() || 
               key == "MY_GEMINI_API_KEY" || 
               key.contains("PLACEHOLDER", ignoreCase = true)
    }

    /**
     * Performs a comprehensive scan on text (SMS, Email, or URL)
     */
    suspend fun scanContent(type: String, content: String): ScanResponse = withContext(Dispatchers.IO) {
        if (isApiKeyPlaceholder()) {
            Log.d(TAG, "API key is placeholder; executing local offline heuristic scanner.")
            return@withContext performLocalOfflineScan(type, content)
        }

        try {
            val key = BuildConfig.GEMINI_API_KEY
            val url = "$BASE_URL?key=$key"

            // Construct payload manually to avoid extra dependencies & maintain rigid schema control
            val prompt = """
                You are PhishGuard AI, an expert real-time mobile security assistant.
                Analyze this item for signs of phishing, scamming, credential theft, urgent financial requests, parcel delivery traps, suspicious subdomains, spoofing of known brands, or fraud.
                
                ITEM TYPE: $type
                ITEM CONTENT:
                "$content"
                
                Provide an output that matches this JSON schema strictly:
                {
                  "riskLevel": "SAFE" | "SUSPICIOUS" | "DANGEROUS",
                  "riskScore": integer between 0 and 100,
                  "verdict": "comprehensive explanation of your analysis and findings",
                  "recommendedAction": "clear advice on action to take"
                }
                
                Your response must be valid JSON matching this schema exactly.
            """.trimIndent()

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1) // Keep it factual and deterministic
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API error code: ${response.code}, body: $errBody")
                    // If network fails or quota runs out, fallback seamlessly to offline heuristic scan
                    return@withContext performLocalOfflineScan(type, content, isNetworkFailure = true, errMsg = "API Code ${response.code}")
                }

                val responseBodyStr = response.body?.string()
                if (responseBodyStr.isNullOrBlank()) {
                    return@withContext performLocalOfflineScan(type, content, isNetworkFailure = true, errMsg = "Empty response body")
                }

                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text")?.trim()

                if (rawText.isNullOrBlank()) {
                    return@withContext performLocalOfflineScan(type, content, isNetworkFailure = true, errMsg = "Malformed candidates content")
                }

                // Check and clean markdown blocks if the model somehow did not obey responseMimeType
                val jsonString = if (rawText.startsWith("```json")) {
                    rawText.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (rawText.startsWith("```")) {
                    rawText.substringAfter("```").substringBeforeLast("```").trim()
                } else {
                    rawText
                }

                val result = scanResponseAdapter.fromJson(jsonString)
                if (result != null) {
                    return@withContext result
                } else {
                    return@withContext performLocalOfflineScan(type, content, isNetworkFailure = true, errMsg = "JSON parse error")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network scanner Exception: ${e.message}", e)
            return@withContext performLocalOfflineScan(type, content, isNetworkFailure = true, errMsg = e.localizedMessage)
        }
    }

    /**
     * Dynamic Offline Heuristic Pattern Analyzer
     */
    private fun performLocalOfflineScan(
        type: String, 
        content: String, 
        isNetworkFailure: Boolean = false,
        errMsg: String? = null
    ): ScanResponse {
        val lowerContent = content.lowercase()
        var score = 0
        val reasons = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val hasUrl = lowerContent.contains("http://") || 
                     lowerContent.contains("https://") || 
                     lowerContent.contains("www.") ||
                     lowerContent.contains(".com/") ||
                     lowerContent.contains(".xyz") ||
                     lowerContent.contains(".click") ||
                     lowerContent.contains(".top")

        // 1. Analyze depending on type
        when (type) {
            "URL" -> {
                score += 30
                reasons.add("Heuristics flagged URL scan mode active.")
                
                if (lowerContent.contains("secure") || lowerContent.contains("login") || lowerContent.contains("verify") || lowerContent.contains("account") || lowerContent.contains("signin")) {
                    score += 25
                    reasons.add("URL contains credential-sensitive keywords (e.g. login, secure, verify).")
                }
                if (lowerContent.contains("bit.ly") || lowerContent.contains("tinyurl.com") || lowerContent.contains("t.co") || lowerContent.contains("is.gd")) {
                    score += 25
                    reasons.add("URL utilizes a known url-shortener service, heavily favored in social engineering to disguise landing pages.")
                }
                if (lowerContent.contains(".xyz") || lowerContent.contains(".info") || lowerContent.contains(".click") || lowerContent.contains(".top") || lowerContent.contains(".club")) {
                    score += 20
                    reasons.add("URL ends in high-risk, low-reputation top-level domains (TLDs) like .xyz, .click, or .top.")
                }
                if (lowerContent.contains("paypal") || lowerContent.contains("netflix") || lowerContent.contains("amazon") || lowerContent.contains("wellsfargo") || lowerContent.contains("chase") || lowerContent.contains("bankofamerica")) {
                    score += 30
                    reasons.add("Contains names of popular institutions. Attackers frequently spoof high-profile brands to steal sessions.")
                }
                if (!lowerContent.startsWith("https://") && (lowerContent.startsWith("http://") || hasUrl)) {
                    score += 20
                    reasons.add("Uses unencrypted HTTP protocol. Legitimate sites mandate modern HTTPS encryption.")
                }
            }
            "SMS", "EMAIL" -> {
                // Check urgent phishing topics
                val urgencyWords = listOf("urgent", "immediate", "act now", "suspended", "blocked", "restricted", "compromised", "unauthorized", "locked out")
                val financialWords = listOf("chase", "bank of america", "wells fargo", "paypal", "cashapp", "venmo", "transfer", "refund", "bitcoin", "crypto", "tax refund", "irs")
                val prizeWords = listOf("congratulations", "lottery", "won", "prize", "cash prize", "earned ${'$'}", "$", "gift card", "selected")
                val parcelWords = listOf("fedex", "ups", "usps", "delivery pending", "post office", "tracking", "package", "parcel", "shipment")

                var urgencyMatch = 0
                for (word in urgencyWords) {
                    if (lowerContent.contains(word)) urgencyMatch++
                }
                if (urgencyMatch > 0) {
                    score += (20 + (urgencyMatch * 5)).coerceAtMost(40)
                    reasons.add("High-urgency language detected ($urgencyMatch panic-triggers: ${urgencyWords.filter { lowerContent.contains(it) }.joinToString()}).")
                }

                var finMatch = 0
                for (word in financialWords) {
                    if (lowerContent.contains(word)) finMatch++
                }
                if (finMatch > 0) {
                    score += (25 + (finMatch * 5)).coerceAtMost(45)
                    reasons.add("Sensitive financial brand or action references discovered (${financialWords.filter { lowerContent.contains(it) }.joinToString()}).")
                }

                var prizeMatch = 0
                for (word in prizeWords) {
                    if (lowerContent.contains(word)) prizeMatch++
                }
                if (prizeMatch > 0) {
                    score += 35
                    reasons.add("Contains lottery, jackpot, or unsolicited winning keywords which are characteristic of classic advance-fee scams.")
                }

                var parcelMatch = 0
                for (word in parcelWords) {
                    if (lowerContent.contains(word)) parcelMatch++
                }
                if (parcelMatch > 0) {
                    score += 25
                    reasons.add("Contains shipping notification phrases mimicking USPS/FedEx package holds.")
                }

                if (hasUrl) {
                    score += 20
                    reasons.add("Message contains one or more website links ($content). Phishing thrives on embedding unverified links inside texts.")
                }
            }
        }

        // Limit score
        val finalScore = score.coerceIn(0, 100)

        val level = when {
            finalScore >= 70 -> "DANGEROUS"
            finalScore >= 35 -> "SUSPICIOUS"
            else -> "SAFE"
        }

        // Provide custom educational details depending on the severity
        when (level) {
            "DANGEROUS" -> {
                reasons.add("Critical threats triggered! Highly indicative of standard social engineering campaigns.")
                recommendations.add("Do not tap any links or dial numbers specified.")
                recommendations.add("Block the sender immediately.")
                recommendations.add("Delete this alert and the matching target message from your mailbox.")
            }
            "SUSPICIOUS" -> {
                reasons.add("Moderate probability of unwanted solicitation or fake brand association.")
                recommendations.add("Exercise caution before proceeding.")
                recommendations.add("Verify directly with original sources (e.g. log in to official app or brand portal via secure browser).")
            }
            "SAFE" -> {
                reasons.add("No immediate malicious syntax, domain threats, or high-urgency phish traps were identified.")
                recommendations.add("Although evaluated as low risk, always remain alert before entering sensitive codes or logging into external sites.")
            }
        }

        val verdictSuffix = if (isNetworkFailure) {
            " [Offline Guardian Active: $errMsg]"
        } else {
            " [Local Scanning Core]"
        }

        return ScanResponse(
            riskLevel = level,
            riskScore = finalScore,
            verdict = reasons.joinToString(" ") + verdictSuffix,
            recommendedAction = recommendations.joinToString(" ")
        )
    }
}
