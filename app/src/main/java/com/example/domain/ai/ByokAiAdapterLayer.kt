package com.example.domain.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Size
import com.example.BuildConfig
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.data.security.EncryptedMediaCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class PrivacyMode(val title: String, val subtitle: String) {
    LOCAL_ONLY(
        "Local-Only Strict",
        "Zero external network calls. Uses on-device Laplacian, dHash, and OCR heuristics."
    ),
    HYBRID_SMART_TIER(
        "Hybrid Smart Tier",
        "Local engine handles obvious blur/duplicates; downscaled cloud AI resolves ambiguous items."
    ),
    CLOUD_MULTIMODAL(
        "Full Cloud Multimodal",
        "Uses selected BYOK multimodal connection profile with encrypted local dHash caching."
    )
}

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val defaultEndpoint: String,
    val costPer1kTokensUsd: Double,
    val isOpenAiCompatible: Boolean
) {
    CUSTOM_OPENAI(
        id = "CUSTOM_OPENAI",
        displayName = "Custom (OpenAI-Compatible)",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gemini-2.0-flash",
            "claude-3-7-sonnet",
            "qwen2.5-vl-72b-instruct"
        ),
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        costPer1kTokensUsd = 0.00012,
        isOpenAiCompatible = true
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter",
        defaultModel = "google/gemini-2.0-flash-001",
        availableModels = listOf(
            "google/gemini-2.0-flash-001",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "qwen/qwen2.5-vl-72b-instruct"
        ),
        defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        costPer1kTokensUsd = 0.00012,
        isOpenAiCompatible = true
    ),
    GEMINI(
        id = "GEMINI",
        displayName = "Google Gemini",
        defaultModel = "gemini-3.5-flash",
        availableModels = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview"
        ),
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/",
        costPer1kTokensUsd = 0.00015,
        isOpenAiCompatible = false
    ),
    OPENAI(
        id = "OPENAI",
        displayName = "OpenAI Vision",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o"),
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        costPer1kTokensUsd = 0.00015,
        isOpenAiCompatible = true
    ),
    CLAUDE(
        id = "CLAUDE",
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-sonnet-latest",
        availableModels = listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        costPer1kTokensUsd = 0.0008,
        isOpenAiCompatible = false
    )
}

data class ByokConnectionProfile(
    val id: String = "profile_${UUID.randomUUID().toString().take(8)}",
    val profileName: String,
    val providerType: AiProviderType,
    val endpointUrl: String,
    val modelId: String,
    val apiKey: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("profileName", profileName)
        put("providerType", providerType.id)
        put("endpointUrl", endpointUrl)
        put("modelId", modelId)
        put("apiKey", apiKey)
    }

    companion object {
        fun fromJson(obj: JSONObject): ByokConnectionProfile {
            val providerId = obj.optString("providerType", AiProviderType.CUSTOM_OPENAI.id)
            val provider = runCatching { AiProviderType.valueOf(providerId) }
                .getOrDefault(AiProviderType.CUSTOM_OPENAI)
            return ByokConnectionProfile(
                id = obj.optString("id", "profile_${UUID.randomUUID().toString().take(8)}"),
                profileName = obj.optString("profileName", "Custom"),
                providerType = provider,
                endpointUrl = obj.optString("endpointUrl", provider.defaultEndpoint),
                modelId = obj.optString("modelId", provider.defaultModel),
                apiKey = obj.optString("apiKey", "")
            )
        }

        fun defaultStarterProfiles(): List<ByokConnectionProfile> = listOf(
            ByokConnectionProfile(
                id = "profile_custom_default",
                profileName = "Custom",
                providerType = AiProviderType.CUSTOM_OPENAI,
                endpointUrl = "https://api.openai.com/v1/chat/completions",
                modelId = "gpt-4o-mini",
                apiKey = ""
            ),
            ByokConnectionProfile(
                id = "profile_gemini_direct",
                profileName = "Google Gemini",
                providerType = AiProviderType.GEMINI,
                endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/",
                modelId = "gemini-3.5-flash",
                apiKey = ""
            ),
            ByokConnectionProfile(
                id = "profile_openrouter",
                profileName = "OpenRouter",
                providerType = AiProviderType.OPENROUTER,
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                modelId = "google/gemini-2.0-flash-001",
                apiKey = ""
            ),
            ByokConnectionProfile(
                id = "profile_openai_official",
                profileName = "OpenAI",
                providerType = AiProviderType.OPENAI,
                endpointUrl = "https://api.openai.com/v1/chat/completions",
                modelId = "gpt-4o-mini",
                apiKey = ""
            )
        )
    }
}

data class AiAdapterConfig(
    val activeProfileId: String = "profile_custom_default",
    val activeProfileName: String = "Custom",
    val profiles: List<ByokConnectionProfile> = ByokConnectionProfile.defaultStarterProfiles(),
    val activeProvider: AiProviderType = AiProviderType.CUSTOM_OPENAI,
    val selectedModel: String = "gpt-4o-mini",
    val customEndpointUrl: String = "https://api.openai.com/v1/chat/completions",
    val customApiKey: String = "",
    val privacyMode: PrivacyMode = PrivacyMode.HYBRID_SMART_TIER,
    val maxImageDimensionPx: Int = 512,
    val jpegQuality: Int = 75,
    val monthlyBudgetCapUsd: Double = 2.50
) {
    val activeProfile: ByokConnectionProfile?
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
}

data class DiscoveredAiModel(
    val id: String,
    val displayName: String,
    val supportsVision: Boolean,
    val visionReason: String,
    val pricingLabel: String? = null
)

data class EndpointModelCatalogResult(
    val modelsEndpointUsed: String,
    val allModels: List<DiscoveredAiModel>,
    val visionModels: List<DiscoveredAiModel>,
    val errorMessage: String? = null
)

data class MultimodalInspectionResult(
    val category: PhotoCategory,
    val screenshotSubType: ScreenshotSubType,
    val ocrText: String,
    val aiDescription: String,
    val semanticTags: String,
    val junkConfidence: Float,
    val junkReason: String,
    val sentimentalProtected: Boolean,
    val providerUsed: String,
    val auditLog: AiAuditLogEntity
)

class ByokAiAdapterLayer(
    private val context: Context,
    private val encryptedCache: EncryptedMediaCache
) {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun loadConfig(): AiAdapterConfig {
        val profilesJsonStr = encryptedCache.peekWithoutStats("byok_profiles_json")
        val loadedProfiles = if (!profilesJsonStr.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(profilesJsonStr)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let {
                            val parsed = ByokConnectionProfile.fromJson(it)
                            // Migrate legacy default title if present
                            if (parsed.id == "profile_nanogpt_custom" && parsed.profileName == "NanoGPT (Custom OpenAI)") {
                                add(parsed.copy(id = "profile_custom_default", profileName = "Custom"))
                            } else {
                                add(parsed)
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }.ifEmpty { ByokConnectionProfile.defaultStarterProfiles() }

        val rawActiveProfileId = encryptedCache.peekWithoutStats("byok_active_profile_id")
            ?: loadedProfiles.first().id
        val activeProfileId = if (rawActiveProfileId == "profile_nanogpt_custom") {
            "profile_custom_default"
        } else {
            rawActiveProfileId
        }
        val matchedProfile = loadedProfiles.find { it.id == activeProfileId } ?: loadedProfiles.first()

        val providerName = encryptedCache.peekWithoutStats("byok_provider") ?: matchedProfile.providerType.id
        val provider = runCatching { AiProviderType.valueOf(providerName) }.getOrDefault(matchedProfile.providerType)
        val model = encryptedCache.peekWithoutStats("byok_model")
            ?.takeIf { it.isNotBlank() } ?: matchedProfile.modelId
        val endpoint = encryptedCache.peekWithoutStats("byok_endpoint")
            ?.takeIf { it.isNotBlank() } ?: matchedProfile.endpointUrl
        val customApiKey = encryptedCache.peekWithoutStats("byok_custom_api_key")
            ?: matchedProfile.apiKey
        val rawProfileName = encryptedCache.peekWithoutStats("byok_active_profile_name")
            ?.takeIf { it.isNotBlank() } ?: matchedProfile.profileName
        val profileName = if (rawProfileName == "NanoGPT (Custom OpenAI)") "Custom" else rawProfileName

        val privacyName = encryptedCache.peekWithoutStats("byok_privacy") ?: PrivacyMode.HYBRID_SMART_TIER.name
        val privacy = runCatching { PrivacyMode.valueOf(privacyName) }.getOrDefault(PrivacyMode.HYBRID_SMART_TIER)
        val maxDim = encryptedCache.peekWithoutStats("byok_max_dim")?.toIntOrNull() ?: 512
        val budget = encryptedCache.peekWithoutStats("byok_budget_usd")?.toDoubleOrNull() ?: 2.50

        return AiAdapterConfig(
            activeProfileId = matchedProfile.id,
            activeProfileName = profileName,
            profiles = loadedProfiles,
            activeProvider = provider,
            selectedModel = model,
            customEndpointUrl = endpoint,
            customApiKey = customApiKey,
            privacyMode = privacy,
            maxImageDimensionPx = maxDim,
            monthlyBudgetCapUsd = budget
        )
    }

    suspend fun saveConfig(config: AiAdapterConfig) {
        val profilesArray = JSONArray().apply {
            config.profiles.forEach { put(it.toJson()) }
        }
        encryptedCache.putBatch(
            mapOf(
                "byok_active_profile_id" to config.activeProfileId,
                "byok_active_profile_name" to config.activeProfileName,
                "byok_profiles_json" to profilesArray.toString(),
                "byok_provider" to config.activeProvider.id,
                "byok_model" to config.selectedModel.trim(),
                "byok_endpoint" to config.customEndpointUrl.trim(),
                "byok_custom_api_key" to config.customApiKey.trim(),
                "byok_privacy" to config.privacyMode.name,
                "byok_max_dim" to config.maxImageDimensionPx.toString(),
                "byok_budget_usd" to config.monthlyBudgetCapUsd.toString()
            )
        )
    }

    /**
     * Resolves the API key: prioritizes the user's custom profile API key stored in
     * AES-256-GCM EncryptedMediaCache, then falls back to AI Studio Secrets Panel (BuildConfig).
     */
    fun resolveEffectiveApiKey(
        provider: AiProviderType,
        profileCustomKey: String = ""
    ): String {
        val trimmedCustom = profileCustomKey.trim()
        if (trimmedCustom.isNotEmpty()) {
            return trimmedCustom
        }
        val activeCfg = loadConfig()
        if (activeCfg.activeProvider == provider && activeCfg.customApiKey.isNotBlank()) {
            return activeCfg.customApiKey.trim()
        }
        val matchingProfileKey = activeCfg.profiles
            .firstOrNull { it.providerType == provider && it.apiKey.isNotBlank() }
            ?.apiKey
            ?.trim()
        if (!matchingProfileKey.isNullOrEmpty()) {
            return matchingProfileKey
        }

        val rawKey = when (provider) {
            AiProviderType.GEMINI -> BuildConfig.GEMINI_API_KEY
            AiProviderType.OPENAI -> BuildConfig.OPENAI_API_KEY
            AiProviderType.CLAUDE -> BuildConfig.ANTHROPIC_API_KEY
            AiProviderType.OPENROUTER,
            AiProviderType.CUSTOM_OPENAI -> BuildConfig.OPENROUTER_API_KEY.ifBlank { BuildConfig.OPENAI_API_KEY }
        }
        return if (
            rawKey.isBlank() ||
            rawKey.startsWith("MY_") ||
            rawKey == "null"
        ) {
            ""
        } else {
            rawKey.trim()
        }
    }

    fun hasConfiguredSecretKey(provider: AiProviderType): Boolean {
        return resolveEffectiveApiKey(provider).isNotEmpty()
    }

    /**
     * Normalizes user-entered OpenAI-compatible base URLs so custom OpenAI-compatible servers,
     * proxies, or gateways work whether the user enters a base URL or full path.
     */
    fun normalizeOpenAiEndpoint(rawEndpoint: String): String {
        val trimmed = rawEndpoint.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return "https://api.openai.com/v1/chat/completions"
        }
        return when {
            trimmed.endsWith("/chat/completions", ignoreCase = true) -> trimmed
            trimmed.endsWith("/v1", ignoreCase = true) -> "$trimmed/chat/completions"
            trimmed.endsWith("/api", ignoreCase = true) -> "$trimmed/v1/chat/completions"
            trimmed.contains("nano-gpt.com", ignoreCase = true) && !trimmed.contains("/api/v1", ignoreCase = true) ->
                "$trimmed/api/v1/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    /**
     * Derives the /models catalog URL from any user-entered base URL or chat completions URL.
     * Specifically appends `?detailed=true` for NanoGPT endpoints so vision capabilities,
     * input_modalities, and pricing metadata are returned by the server.
     */
    fun resolveModelsEndpointUrl(rawEndpoint: String, detailed: Boolean = true): String {
        val trimmed = rawEndpoint.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return "https://api.openai.com/v1/models"
        }
        if (trimmed.contains("generativelanguage.googleapis.com", ignoreCase = true)) {
            val base = trimmed.substringBefore(":generateContent").substringBefore("?")
            return if (base.endsWith("/models", ignoreCase = true)) base else "https://generativelanguage.googleapis.com/v1beta/models"
        }

        val withoutQuery = trimmed.substringBefore("?").trimEnd('/')
        val baseModelsUrl = when {
            withoutQuery.endsWith("/chat/completions", ignoreCase = true) ->
                withoutQuery.removeSuffix("/chat/completions").removeSuffix("/CHAT/COMPLETIONS") + "/models"
            withoutQuery.endsWith("/models", ignoreCase = true) ->
                withoutQuery
            withoutQuery.endsWith("/v1", ignoreCase = true) ->
                "$withoutQuery/models"
            withoutQuery.endsWith("/api", ignoreCase = true) ->
                "$withoutQuery/v1/models"
            withoutQuery.contains("nano-gpt.com", ignoreCase = true) && !withoutQuery.contains("/api/v1", ignoreCase = true) ->
                "$withoutQuery/api/v1/models"
            else ->
                "$withoutQuery/v1/models"
        }

        return if (detailed && baseModelsUrl.contains("nano-gpt.com", ignoreCase = true)) {
            "$baseModelsUrl?detailed=true"
        } else {
            baseModelsUrl
        }
    }

    /**
     * Queries the provider's `/models` endpoint (including NanoGPT `/api/v1/models?detailed=true`,
     * OpenRouter `/api/v1/models`, OpenAI `/v1/models`, or Gemini `/v1beta/models`), parses all models,
     * and filters down to Vision-capable models that can inspect images/video frames.
     */
    suspend fun fetchAvailableModelsFromEndpoint(
        rawEndpoint: String,
        provider: AiProviderType,
        apiKeyOverride: String = ""
    ): EndpointModelCatalogResult = withContext(Dispatchers.IO) {
        val effectiveKey = resolveEffectiveApiKey(provider, apiKeyOverride)
        val primaryUrl = resolveModelsEndpointUrl(rawEndpoint, detailed = true)

        fun buildGetRequest(url: String): Request {
            val finalUrl = if (url.contains("generativelanguage.googleapis.com") && effectiveKey.isNotEmpty() && !url.contains("key=")) {
                val sep = if (url.contains("?")) "&" else "?"
                "$url${sep}key=$effectiveKey"
            } else {
                url
            }
            val builder = Request.Builder()
                .url(finalUrl)
                .get()
                .addHeader("Accept", "application/json")
            if (effectiveKey.isNotEmpty() && !url.contains("generativelanguage.googleapis.com")) {
                builder.addHeader("Authorization", "Bearer $effectiveKey")
            }
            return builder.build()
        }

        try {
            var bodyStr = ""
            var statusCode = 0
            okHttpClient.newCall(buildGetRequest(primaryUrl)).execute().use { resp ->
                statusCode = resp.code
                if (resp.isSuccessful) {
                    bodyStr = resp.body?.string().orEmpty()
                }
            }

            // Fallback without ?detailed=true if a strict server rejected the query parameter
            if (bodyStr.isBlank() && primaryUrl.contains("?detailed=true")) {
                val fallbackUrl = resolveModelsEndpointUrl(rawEndpoint, detailed = false)
                okHttpClient.newCall(buildGetRequest(fallbackUrl)).execute().use { resp ->
                    statusCode = resp.code
                    if (resp.isSuccessful) {
                        bodyStr = resp.body?.string().orEmpty()
                    }
                }
            }

            if (bodyStr.isBlank()) {
                val errMsg = when (statusCode) {
                    401, 403 -> "Authentication required ($statusCode). Enter your API key below and tap Fetch Models again."
                    404 -> "Models endpoint not found at $primaryUrl (HTTP 404)."
                    else -> "Endpoint returned HTTP $statusCode from $primaryUrl."
                }
                return@withContext EndpointModelCatalogResult(
                    modelsEndpointUsed = primaryUrl,
                    allModels = emptyList(),
                    visionModels = emptyList(),
                    errorMessage = errMsg
                )
            }

            val parsedAll = parseModelsJsonAndDetectVision(bodyStr)
            val parsedVision = parsedAll.filter { it.supportsVision }

            EndpointModelCatalogResult(
                modelsEndpointUsed = primaryUrl,
                allModels = parsedAll,
                visionModels = parsedVision,
                errorMessage = if (parsedAll.isEmpty()) "No models found in response from $primaryUrl." else null
            )
        } catch (e: Exception) {
            EndpointModelCatalogResult(
                modelsEndpointUsed = primaryUrl,
                allModels = emptyList(),
                visionModels = emptyList(),
                errorMessage = "Could not reach $primaryUrl: ${e.localizedMessage ?: "Network error"}"
            )
        }
    }

    /**
     * Parses OpenAI/NanoGPT/OpenRouter (`{ "data": [...] }`), Gemini (`{ "models": [...] }`), or
     * raw JSONArray model lists and detects Vision / Image-Input support.
     */
    fun parseModelsJsonAndDetectVision(rawJson: String): List<DiscoveredAiModel> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        val itemsArray: JSONArray = runCatching {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("data")
                    ?: root.optJSONArray("models")
                    ?: root.optJSONArray("items")
                    ?: JSONArray()
            }
        }.getOrDefault(JSONArray())

        val results = mutableListOf<DiscoveredAiModel>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            val rawId = obj.optString("id")
                .ifBlank { obj.optString("model") }
                .ifBlank { obj.optString("name") }
                .trim()
            if (rawId.isEmpty()) continue
            val cleanId = rawId.removePrefix("models/")
            if (!seenIds.add(cleanId)) continue

            val displayName = obj.optString("name")
                .ifBlank { obj.optString("displayName") }
                .ifBlank { cleanId }
                .trim()
            val description = obj.optString("description", "")

            val (isVision, reason) = evaluateModelVisionSupport(obj, cleanId, displayName, description)

            // Optional pricing snippet if returned by NanoGPT ?detailed=true or OpenRouter
            val pricingObj = obj.optJSONObject("pricing")
            val promptPrice = pricingObj?.optString("prompt")?.takeIf { it.isNotBlank() && it != "0" }
            val pricingLabel = promptPrice?.let { "$$it/tok" }

            results += DiscoveredAiModel(
                id = cleanId,
                displayName = displayName,
                supportsVision = isVision,
                visionReason = reason,
                pricingLabel = pricingLabel
            )
        }

        return results.sortedWith(
            compareByDescending<DiscoveredAiModel> { it.supportsVision }
                .thenBy { it.id.lowercase() }
        )
    }

    private fun evaluateModelVisionSupport(
        obj: JSONObject,
        modelId: String,
        displayName: String,
        description: String
    ): Pair<Boolean, String> {
        val lowerId = modelId.lowercase()
        val lowerName = displayName.lowercase()
        val lowerDesc = description.lowercase()

        // 1. Exclude non-text-output generators (image generators, audio/TTS/STT, video gen, embeddings, moderation)
        val nonVisionGeneratorTokens = listOf(
            "dall-e", "flux", "stable-diffusion", "sdxl", "midjourney", "ideogram",
            "recraft", "whisper", "tts", "embedding", "text-embedding", "rerank",
            "moderation", "sora", "kling", "runway", "luma", "bge-", "e5-"
        )
        if (nonVisionGeneratorTokens.any { lowerId.contains(it) }) {
            return false to "Non-chat / Generator model"
        }

        val archObj = obj.optJSONObject("architecture")
        val outputModalities = optStringList(obj, "output_modalities") +
            optStringList(archObj, "output_modalities")
        if (outputModalities.isNotEmpty() && outputModalities.none { it.contains("text") }) {
            return false to "Outputs ${outputModalities.joinToString("/")} (not text)"
        }

        // 2. Check explicit input_modalities from NanoGPT ?detailed=true or OpenRouter
        val inputModalities = optStringList(obj, "input_modalities") +
            optStringList(archObj, "input_modalities")
        if (inputModalities.isNotEmpty()) {
            return if (inputModalities.any { it.contains("image") || it.contains("vision") || it.contains("multimodal") }) {
                true to "Vision Input (${inputModalities.joinToString("+")})"
            } else {
                false to "Text-Only Input"
            }
        }

        // 3. Check architecture.modality string (e.g. "text+image->text" vs "text->text")
        val modalityStr = (archObj?.optString("modality").orEmpty() + " " + obj.optString("modality").orEmpty())
            .trim()
            .lowercase()
        if (modalityStr.isNotEmpty()) {
            if (modalityStr.contains("image->text") || modalityStr.contains("image+text") || modalityStr.contains("text+image")) {
                return true to "Vision Modality ($modalityStr)"
            }
            if (modalityStr == "text->text" || modalityStr == "text") {
                return false to "Text-Only Modality"
            }
        }

        // 4. Check explicit capabilities object or array (NanoGPT / OpenAI-compatible detailed schema)
        val capObj = obj.optJSONObject("capabilities")
        if (capObj != null) {
            val hasExplicitVisionKey = capObj.has("vision") || capObj.has("supports_vision") ||
                capObj.has("image_input") || capObj.has("multimodal") || capObj.has("image")
            if (hasExplicitVisionKey) {
                val capVision = capObj.optBoolean("vision", false) ||
                    capObj.optBoolean("supports_vision", false) ||
                    capObj.optBoolean("image_input", false) ||
                    capObj.optBoolean("multimodal", false) ||
                    capObj.optBoolean("image", false)
                return if (capVision) {
                    true to "Vision Capability Verified"
                } else {
                    false to "No Vision Capability"
                }
            }
        }

        val capList = optStringList(obj, "capabilities")
        if (capList.any { it.contains("vision") || it.contains("image") || it.contains("multimodal") }) {
            return true to "Vision Capability Verified"
        }

        // 5. Direct boolean flags on model object
        if (obj.has("vision") || obj.has("supports_vision") || obj.has("supportsVision") || obj.has("image_input")) {
            val flag = obj.optBoolean("vision", false) ||
                obj.optBoolean("supports_vision", false) ||
                obj.optBoolean("supportsVision", false) ||
                obj.optBoolean("image_input", false)
            return if (flag) {
                true to "Vision Flag Enabled"
            } else {
                false to "Vision Flag False"
            }
        }

        // 6. Heuristic detection for endpoints that return minimal OpenAI `{"id": "..."}` objects
        val knownTextOnlyTokens = listOf(
            "deepseek-r1", "deepseek-v3", "deepseek-chat", "deepseek-coder", "deepseek-reasoner",
            "gpt-3.5", "o1-mini", "o3-mini", "qwen-2.5-coder", "qwen2.5-coder", "codestral",
            "llama-3.1-", "llama-3.3-", "qwq", "yi-large", "command-r", "nemotron"
        )
        if (knownTextOnlyTokens.any { lowerId.contains(it) } && !lowerId.contains("vision") && !lowerId.contains("-vl")) {
            return false to "Text-Only Model"
        }

        val knownVisionFamilies = listOf(
            "gpt-4o", "gpt-4-turbo", "gpt-4-vision", "gpt-4.1", "gpt-4.5", "gpt-5", "chatgpt-4o",
            "o1", "o3", "o4-mini",
            "gemini-1.5", "gemini-2.0", "gemini-2.5", "gemini-3", "gemini-pro-vision", "gemini-flash", "learnlm",
            "claude-3", "claude-3.5", "claude-3-5", "claude-3.7", "claude-3-7", "claude-4", "claude-sonnet-4", "claude-opus-4",
            "pixtral", "mistral-small-3.1", "mistral-medium-3",
            "qwen-vl", "qwen2-vl", "qwen2.5-vl", "qvq", "qwen-omni",
            "llama-3.2-11b-vision", "llama-3.2-90b-vision", "llama-4",
            "grok-2-vision", "grok-vision", "grok-3", "grok-4",
            "llava", "internvl", "molmo", "kimi-vl", "kimi-k2.5", "minicpm-v", "cogvlm",
            "yi-vl", "deepseek-vl", "phi-3-vision", "phi-3.5-vision", "phi-4-multimodal",
            "gemma-3-4b", "gemma-3-12b", "gemma-3-27b", "nova-lite", "nova-pro"
        )
        if (
            knownVisionFamilies.any { lowerId.contains(it) } ||
            lowerId.contains("vision") ||
            lowerId.contains("-vl") ||
            lowerId.contains("multimodal") ||
            lowerName.contains("vision") ||
            lowerDesc.contains("vision") ||
            lowerDesc.contains("multimodal") ||
            lowerDesc.contains("image input")
        ) {
            return true to "Multimodal Vision Model"
        }

        return false to "Text-Only Model"
    }

    private fun optStringList(obj: JSONObject?, key: String): List<String> {
        if (obj == null) return emptyList()
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim().lowercase()
                if (item.isNotEmpty()) add(item)
            }
        }
    }

    /**
     * Inspects a photo using the Encrypted Local Cache first, then either the Local Forensics Engine
     * or the active BYOK Cloud Multimodal Profile with client-side image downscaling and cost caps.
     */
    suspend fun inspectPhotoWithPolicies(
        photo: PhotoEntity,
        currentTotalSpendUsd: Double
    ): MultimodalInspectionResult = withContext(Dispatchers.IO) {
        val config = loadConfig()
        val effectiveModel = config.selectedModel.trim().ifEmpty { config.activeProvider.defaultModel }
        val cacheKey = "ai_v2_${photo.dHash}_${config.activeProfileId}_${effectiveModel}"

        // 1. Check Encrypted Cache by Perceptual Hash (dHash)
        val cachedPayload = encryptedCache.get(cacheKey)
        if (cachedPayload != null) {
            runCatching {
                val obj = JSONObject(cachedPayload)
                return@withContext parseJsonToInspectionResult(
                    obj = obj,
                    photo = photo,
                    providerUsed = "${config.activeProfileName} (Encrypted Cache)",
                    modelUsed = effectiveModel,
                    downscaledDim = "${config.maxImageDimensionPx}px",
                    payloadBytes = 0,
                    tokens = 0,
                    costUsd = 0.0,
                    fromCache = true
                )
            }
        }

        // 2. Check Privacy Mode, Budget Cap, and API Key availability
        val apiKey = resolveEffectiveApiKey(config.activeProvider, config.customApiKey)
        val withinBudget = currentTotalSpendUsd < config.monthlyBudgetCapUsd
        val shouldUseCloud = config.privacyMode != PrivacyMode.LOCAL_ONLY &&
            apiKey.isNotEmpty() &&
            withinBudget

        if (!shouldUseCloud) {
            val localJson = buildLocalForensicInspectionJson(photo)
            encryptedCache.put(cacheKey, localJson.toString())
            val reasonTag = when {
                config.privacyMode == PrivacyMode.LOCAL_ONLY -> "Local-Only Privacy"
                !withinBudget -> "Budget Cap Reached"
                else -> "Local On-Device Engine"
            }
            return@withContext parseJsonToInspectionResult(
                obj = localJson,
                photo = photo,
                providerUsed = reasonTag,
                modelUsed = effectiveModel,
                downscaledDim = "${config.maxImageDimensionPx}px (Local)",
                payloadBytes = 0,
                tokens = 0,
                costUsd = 0.0,
                fromCache = false
            )
        }

        // 3. Downscale & Compress Image Payload for Cost Control
        val (base64Jpeg, byteLength, dimLabel) = downscaleAndEncodePhoto(
            photo = photo,
            maxDimension = config.maxImageDimensionPx,
            jpegQuality = config.jpegQuality
        )
        val estimatedTokens = (260 + (byteLength / 320)).coerceIn(280, 1400)
        val estimatedCost = (estimatedTokens / 1000.0) * config.activeProvider.costPer1kTokensUsd

        val responseJson = try {
            when (config.activeProvider) {
                AiProviderType.GEMINI -> callGeminiRestApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpointBase = config.customEndpointUrl,
                    apiKey = apiKey
                )
                AiProviderType.OPENAI,
                AiProviderType.OPENROUTER,
                AiProviderType.CUSTOM_OPENAI -> callOpenAiCompatibleApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpoint = normalizeOpenAiEndpoint(config.customEndpointUrl),
                    apiKey = apiKey
                )
                AiProviderType.CLAUDE -> callClaudeRestApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpoint = config.customEndpointUrl.trim().ifEmpty { AiProviderType.CLAUDE.defaultEndpoint },
                    apiKey = apiKey
                )
            }
        } catch (_: Exception) {
            buildLocalForensicInspectionJson(photo)
        }

        encryptedCache.put(cacheKey, responseJson.toString())
        parseJsonToInspectionResult(
            obj = responseJson,
            photo = photo,
            providerUsed = "${config.activeProfileName} ($effectiveModel)",
            modelUsed = effectiveModel,
            downscaledDim = dimLabel,
            payloadBytes = byteLength,
            tokens = estimatedTokens,
            costUsd = estimatedCost,
            fromCache = false
        )
    }

    private fun callGeminiRestApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpointBase: String,
        apiKey: String
    ): JSONObject {
        val safeModel = if (model.contains("1.5") || model.contains("2.0")) {
            "gemini-3.5-flash"
        } else {
            model.ifBlank { "gemini-3.5-flash" }
        }
        val cleanBase = endpointBase.trim().trimEnd('/')
            .ifEmpty { "https://generativelanguage.googleapis.com/v1beta/models" }
        val url = if (cleanBase.contains(":generateContent")) {
            "$cleanBase?key=$apiKey"
        } else {
            "$cleanBase/$safeModel:generateContent?key=$apiKey"
        }

        val prompt = buildMultimodalForensicPrompt(photo)
        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .apply {
                                if (base64Jpeg.isNotEmpty()) {
                                    put(
                                        JSONObject().put(
                                            "inlineData",
                                            JSONObject()
                                                .put("mimeType", "image/jpeg")
                                                .put("data", base64Jpeg)
                                        )
                                    )
                                }
                            }
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json")
            )
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return buildLocalForensicInspectionJson(photo)
            val rawBody = response.body?.string().orEmpty()
            val root = JSONObject(rawBody)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
            return extractJsonObject(text) ?: buildLocalForensicInspectionJson(photo)
        }
    }

    private fun callOpenAiCompatibleApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpoint: String,
        apiKey: String
    ): JSONObject {
        val prompt = buildMultimodalForensicPrompt(photo)
        val contentArray = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            if (base64Jpeg.isNotEmpty()) {
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$base64Jpeg")
                        )
                )
            }
        }

        fun buildPayload(includeJsonFormat: Boolean): JSONObject = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", contentArray)
                )
            )
            if (includeJsonFormat) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val primaryRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(buildPayload(includeJsonFormat = true).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(primaryRequest).execute().use { response ->
            if (response.isSuccessful) {
                val root = JSONObject(response.body?.string().orEmpty())
                val content = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                return extractJsonObject(content) ?: buildLocalForensicInspectionJson(photo)
            }
        }

        // Fallback without response_format for custom OpenAI-compatible endpoints/models on NanoGPT
        val fallbackRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(buildPayload(includeJsonFormat = false).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(fallbackRequest).execute().use { response ->
            if (!response.isSuccessful) return buildLocalForensicInspectionJson(photo)
            val root = JSONObject(response.body?.string().orEmpty())
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            return extractJsonObject(content) ?: buildLocalForensicInspectionJson(photo)
        }
    }

    private fun callClaudeRestApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpoint: String,
        apiKey: String
    ): JSONObject {
        val prompt = buildMultimodalForensicPrompt(photo)
        val contentArr = JSONArray().apply {
            if (base64Jpeg.isNotEmpty()) {
                put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", base64Jpeg)
                        )
                )
            }
            put(JSONObject().put("type", "text").put("text", prompt))
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", contentArr)
                )
            )
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return buildLocalForensicInspectionJson(photo)
            val root = JSONObject(response.body?.string().orEmpty())
            val text = root.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
            return extractJsonObject(text) ?: buildLocalForensicInspectionJson(photo)
        }
    }

    /**
     * Safely extracts a JSONObject even if the model wraps JSON in markdown code fences or prose.
     */
    fun extractJsonObject(rawContent: String): JSONObject? {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return null
        runCatching { return JSONObject(trimmed) }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1)
            runCatching { return JSONObject(candidate) }
        }
        return null
    }

    private fun buildMultimodalForensicPrompt(photo: PhotoEntity): String {
        val mediaTypeHint = if (photo.isVideo) "video frame (duration ${photo.formattedDuration})" else "photo"
        return """
            Analyze this $mediaTypeHint for an intelligent photo & video library cleaner.
            Existing telemetry: title=${photo.title}, mediaType=${photo.mediaType}, sharpness=${photo.sharpnessScore}/100, exposure=${photo.exposureScore}/100, dHash=${photo.dHash}.
            Return ONLY a JSON object with keys:
            - category: one of [VIDEO, SCREENSHOT, RECEIPT, DOCUMENT, MEME, BLURRY, PET, PLANT, LANDSCAPE, SELFIE, DOWNLOAD]
            - screenshotSubType: one of [NONE, TEMPORARY_OTP, OLD_UI_CAPTURE, MEME_SOCIAL, CONFIRMATION_NUMBER, RECEIPT_INVOICE, IMPORTANT_CONVERSATION, PASSWORD_WIFI]
            - ocrText: extracted visible text
            - aiDescription: concise 1-sentence description of the media
            - semanticTags: comma-separated lowercase search tags
            - junkConfidence: float between 0.0 and 1.0 indicating confidence it is safe to delete
            - junkReason: brief explanation of why it should be kept or cleaned
            - sentimentalProtected: boolean true if it contains important records, pets, or best-shot memories
        """.trimIndent()
    }

    private fun buildLocalForensicInspectionJson(photo: PhotoEntity): JSONObject {
        val enhancedDescription = if (photo.aiDescription.isNotBlank()) {
            photo.aiDescription
        } else {
            "Verified by local forensics (Sharpness ${photo.sharpnessScore}/100, Exposure ${photo.exposureScore}/100, dHash ${photo.dHash.take(8)})."
        }
        return JSONObject().apply {
            put("category", photo.category)
            put("screenshotSubType", photo.screenshotSubType)
            put("ocrText", photo.ocrText)
            put("aiDescription", enhancedDescription)
            put("semanticTags", photo.semanticTags)
            put("junkConfidence", photo.junkConfidence.toDouble())
            put("junkReason", photo.junkReason)
            put("sentimentalProtected", photo.sentimentalProtected)
        }
    }

    private fun parseJsonToInspectionResult(
        obj: JSONObject,
        photo: PhotoEntity,
        providerUsed: String,
        modelUsed: String,
        downscaledDim: String,
        payloadBytes: Int,
        tokens: Int,
        costUsd: Double,
        fromCache: Boolean
    ): MultimodalInspectionResult {
        val cat = runCatching {
            PhotoCategory.valueOf(obj.optString("category", photo.category))
        }.getOrDefault(photo.categoryEnum)

        val sub = runCatching {
            ScreenshotSubType.valueOf(obj.optString("screenshotSubType", photo.screenshotSubType))
        }.getOrDefault(photo.screenshotSubTypeEnum)

        val ocr = obj.optString("ocrText", photo.ocrText)
        val desc = obj.optString("aiDescription", photo.aiDescription)
        val tags = obj.optString("semanticTags", photo.semanticTags)
        val conf = obj.optDouble("junkConfidence", photo.junkConfidence.toDouble()).toFloat().coerceIn(0f, 1f)
        val reason = obj.optString("junkReason", photo.junkReason)
        val protected = obj.optBoolean("sentimentalProtected", photo.sentimentalProtected)

        val log = AiAuditLogEntity(
            providerId = providerUsed,
            modelName = modelUsed,
            photoTitle = photo.title,
            downscaledDimension = downscaledDim,
            payloadBytes = payloadBytes,
            estimatedTokens = tokens,
            estimatedCostUsd = costUsd,
            servedFromEncryptedCache = fromCache,
            verdictSummary = "${(conf * 100).toInt()}% Junk Confidence • $reason"
        )

        return MultimodalInspectionResult(
            category = cat,
            screenshotSubType = sub,
            ocrText = ocr,
            aiDescription = desc,
            semanticTags = tags,
            junkConfidence = conf,
            junkReason = reason,
            sentimentalProtected = protected,
            providerUsed = providerUsed,
            auditLog = log
        )
    }

    private fun downscaleAndEncodePhoto(
        photo: PhotoEntity,
        maxDimension: Int,
        jpegQuality: Int
    ): Triple<String, Int, String> {
        return try {
            val uri = Uri.parse(photo.uriString)
            val bitmap = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content" -> {
                    runCatching {
                        context.contentResolver.loadThumbnail(uri, Size(maxDimension, maxDimension), null)
                    }.getOrNull() ?: if (photo.isVideo) {
                        extractVideoFrame(uri)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }
                photo.isVideo -> extractVideoFrame(uri)
                uri.scheme == "file" -> BitmapFactory.decodeFile(File(uri.path ?: "").absolutePath)
                uri.scheme == "content" -> context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                else -> null
            } ?: return Triple("", 0, "${maxDimension}px")

            val ratio = minOf(
                maxDimension.toFloat() / bitmap.width.coerceAtLeast(1),
                maxDimension.toFloat() / bitmap.height.coerceAtLeast(1),
                1f
            )
            val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(32)
            val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(32)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), baos)
            val bytes = baos.toByteArray()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Triple(b64, bytes.size, "${targetW}x${targetH}")
        } catch (_: Exception) {
            Triple("", 0, "${maxDimension}px")
        }
    }

    private fun extractVideoFrame(uri: Uri): Bitmap? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            frame
        }.getOrNull()
    }
}
