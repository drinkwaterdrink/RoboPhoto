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
import com.example.data.local.TokenUsageSource
import com.example.data.security.EncryptedMediaCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
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

enum class PrivacyMode(val title: String, val subtitle: String, val shortLabel: String) {
    LOCAL_ONLY(
        "Local-Only Strict",
        "Zero external network calls. Uses on-device Laplacian, dHash, and OCR heuristics.",
        "Local Only"
    ),
    HYBRID_SMART_TIER(
        "Hybrid Smart Tier",
        "Local engine handles obvious blur/duplicates; downscaled cloud AI resolves ambiguous items.",
        "Hybrid"
    ),
    CLOUD_MULTIMODAL(
        "Full Cloud Multimodal",
        "Uses selected BYOK multimodal connection profile with encrypted local dHash caching.",
        "Cloud"
    )
}

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val defaultEndpoint: String,
    val isOpenAiCompatible: Boolean
) {
    NANOGPT(
        id = "NANOGPT",
        displayName = "NanoGPT",
        defaultModel = "google/gemini-2.0-flash-001",
        availableModels = listOf(
            "google/gemini-2.0-flash-001",
            "gpt-4o-mini",
            "claude-3-5-sonnet-latest",
            "qwen/qwen2.5-vl-72b-instruct"
        ),
        defaultEndpoint = "https://nano-gpt.com/api/v1/chat/completions",
        isOpenAiCompatible = true
    ),
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
        isOpenAiCompatible = false
    ),
    OPENAI(
        id = "OPENAI",
        displayName = "OpenAI Vision",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o"),
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        isOpenAiCompatible = true
    ),
    CLAUDE(
        id = "CLAUDE",
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-sonnet-latest",
        availableModels = listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
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
    val maskedApiKey: String
        get() = EncryptedMediaCache.maskApiKey(apiKey)

    val isNanoGptProfile: Boolean
        get() = providerType == AiProviderType.NANOGPT ||
            endpointUrl.contains("nano-gpt.com", ignoreCase = true) ||
            profileName.contains("NanoGPT", ignoreCase = true)

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
                id = "profile_nanogpt",
                profileName = "NanoGPT",
                providerType = AiProviderType.NANOGPT,
                endpointUrl = "https://nano-gpt.com/api/v1/chat/completions",
                modelId = "google/gemini-2.0-flash-001",
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
            ),
            ByokConnectionProfile(
                id = "profile_claude_direct",
                profileName = "Anthropic Claude",
                providerType = AiProviderType.CLAUDE,
                endpointUrl = "https://api.anthropic.com/v1/messages",
                modelId = "claude-3-5-sonnet-latest",
                apiKey = ""
            )
        )
    }
}

/**
 * BYOK & Privacy configuration.
 * Pass 3 Section A: Contains ZERO spending budget or usage cap fields.
 * AI is never disabled because of an app-defined usage budget.
 */
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
    val skipPrivacyPreviewDialog: Boolean = false
) {
    val activeProfile: ByokConnectionProfile?
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

    val maskedCustomApiKey: String
        get() = EncryptedMediaCache.maskApiKey(customApiKey)

    val isNanoGptActive: Boolean
        get() = activeProvider == AiProviderType.NANOGPT ||
            customEndpointUrl.contains("nano-gpt.com", ignoreCase = true) ||
            activeProfileName.contains("NanoGPT", ignoreCase = true)
}

/**
 * Normalized subscription inclusion status for NanoGPT and provider catalogs (Pass 3 Section D).
 * Never guesses: if the API response does not explicitly establish subscription status,
 * it remains [UNKNOWN] ("Subscription status unknown") and is never labeled "Included".
 */
enum class SubscriptionInclusionStatus(val badgeText: String) {
    INCLUDED("Included"),
    NOT_INCLUDED("Paid / Per-Prompt"),
    UNKNOWN("Subscription status unknown")
}

/**
 * Provider-normalized model capability & subscription metadata (Pass 3 Sections D & E).
 */
data class DiscoveredAiModel(
    val id: String,
    val displayName: String,
    val supportsVision: Boolean,
    val visionReason: String,
    val provider: String = "",
    val pricing: String? = null,
    val subscriptionIncluded: Boolean? = null,
    val subscriptionReason: String = "Subscription status unknown",
    val inputModalities: List<String> = listOf("text"),
    val outputModalities: List<String> = listOf("text"),
    val supportsVideoInput: Boolean = false,
    val supportsStructuredJson: Boolean = true,
    val contextLength: Int? = null,
    val isAvailable: Boolean = true
) {
    val pricingLabel: String?
        get() = pricing

    val subscriptionStatus: SubscriptionInclusionStatus
        get() = when (subscriptionIncluded) {
            true -> SubscriptionInclusionStatus.INCLUDED
            false -> SubscriptionInclusionStatus.NOT_INCLUDED
            null -> SubscriptionInclusionStatus.UNKNOWN
        }

    val isConfirmedSubscriptionIncluded: Boolean
        get() = subscriptionIncluded == true
}

data class EndpointModelCatalogResult(
    val modelsEndpointUsed: String,
    val subscriptionEndpointUsed: String? = null,
    val allModels: List<DiscoveredAiModel>,
    val visionModels: List<DiscoveredAiModel>,
    val subscriptionIncludedModels: List<DiscoveredAiModel> = allModels.filter { it.isConfirmedSubscriptionIncluded },
    val servedFromCatalogCache: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionMetadataNote: String? = null
)

/**
 * Result of checking whether a user-selected model is compatible with photo inspection (Pass 3 Section E).
 */
data class ModelCapabilityValidation(
    val modelId: String,
    val supportsVision: Boolean,
    val visionReason: String,
    val subscriptionIncluded: Boolean?,
    val subscriptionReason: String,
    val warningMessage: String? = null,
    val suggestedVisionAlternatives: List<String> = emptyList()
)

/**
 * Structured result from "Test Connection" (Pass 3 Section G).
 */
data class AiConnectionTestResult(
    val connected: Boolean,
    val headline: String,
    val providerDisplayName: String,
    val modelCatalogStatus: String,
    val selectedModel: String,
    val selectedModelAvailable: Boolean,
    val visionSupported: Boolean,
    val visionStatusLabel: String,
    val subscriptionStatusLabel: String? = null,
    val authenticationValid: Boolean,
    val authStatusLabel: String,
    val latencyMs: Long = 0L,
    val errorDetail: String? = null
)

/**
 * Concise Privacy Preview displayed before sending a cloud AI batch (Pass 3 Section H).
 */
data class AiPrivacyPreview(
    val totalRequestedCount: Int,
    val networkUploadCount: Int,
    val alreadyCachedCount: Int,
    val providerName: String,
    val modelName: String,
    val maxDimensionPx: Int,
    val jpegQuality: Int,
    val sendsOriginalFiles: Boolean = false,
    val estimatedPayloadBytes: Long,
    val estimatedTokens: Int,
    val bulletPoints: List<String>
)

data class ParsedTokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val source: TokenUsageSource
)

private data class ProviderCallOutcome(
    val succeeded: Boolean,
    val parsedJson: JSONObject,
    val tokenUsage: ParsedTokenUsage,
    val latencyMs: Long = 0L,
    val endpointUsed: String = "",
    val failureReason: String? = null
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
        }.let { existing ->
            if (existing.isEmpty()) {
                ByokConnectionProfile.defaultStarterProfiles()
            } else if (existing.none { it.providerType == AiProviderType.NANOGPT || it.id == "profile_nanogpt" }) {
                // Ensure NanoGPT profile is available in existing installations
                existing + ByokConnectionProfile(
                    id = "profile_nanogpt",
                    profileName = "NanoGPT",
                    providerType = AiProviderType.NANOGPT,
                    endpointUrl = "https://nano-gpt.com/api/v1/chat/completions",
                    modelId = "google/gemini-2.0-flash-001",
                    apiKey = ""
                )
            } else {
                existing
            }
        }

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
        val jpegQual = encryptedCache.peekWithoutStats("byok_jpeg_quality")?.toIntOrNull() ?: 75
        val skipPreview = encryptedCache.peekWithoutStats("byok_skip_privacy_preview") == "true"

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
            jpegQuality = jpegQual,
            skipPrivacyPreviewDialog = skipPreview
        )
    }

    suspend fun saveConfig(config: AiAdapterConfig) {
        val profilesArray = JSONArray().apply {
            config.profiles.forEach { put(it.toJson()) }
        }
        encryptedCache.remove("byok_budget_usd")
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
                "byok_jpeg_quality" to config.jpegQuality.toString(),
                "byok_skip_privacy_preview" to config.skipPrivacyPreviewDialog.toString()
            )
        )
    }

    /**
     * Resolves the API key: prioritizes the user's custom profile API key stored in
     * AES-256-GCM EncryptedMediaCache, then falls back to AI Studio Secrets Panel (BuildConfig).
     * Never logs or exposes the API key.
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
            AiProviderType.NANOGPT,
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
     * Derives the `/models` catalog URL from any user-entered base URL or chat completions URL.
     * Appends `?detailed=true` for NanoGPT endpoints so capabilities, modalities, and pricing
     * metadata are returned by the server.
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
     * Derives NanoGPT's dedicated subscription models catalog URL (`/api/subscription/v1/models?detailed=true`)
     * when the endpoint or provider is NanoGPT, or null for non-NanoGPT providers.
     */
    fun resolveNanoGptSubscriptionModelsUrl(
        rawEndpoint: String,
        provider: AiProviderType = AiProviderType.CUSTOM_OPENAI
    ): String? {
        val trimmed = rawEndpoint.trim().trimEnd('/')
        val isNanoGpt = provider == AiProviderType.NANOGPT || trimmed.contains("nano-gpt.com", ignoreCase = true)
        if (!isNanoGpt) return null

        val hostBase = if (trimmed.contains("nano-gpt.com", ignoreCase = true)) {
            val schemeAndHost = Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
                ?: "https://nano-gpt.com"
            schemeAndHost
        } else {
            "https://nano-gpt.com"
        }
        return "$hostBase/api/subscription/v1/models?detailed=true"
    }

    /**
     * Queries the provider's `/models` endpoint (and NanoGPT's `/api/subscription/v1/models?detailed=true`
     * when NanoGPT is active), normalizes capabilities and subscription inclusion, and caches the catalog
     * for fast reuse until "Refresh Models" (`forceRefresh = true`) is requested.
     */
    suspend fun fetchAvailableModelsFromEndpoint(
        rawEndpoint: String,
        provider: AiProviderType,
        apiKeyOverride: String = "",
        forceRefresh: Boolean = false
    ): EndpointModelCatalogResult = withContext(Dispatchers.IO) {
        val effectiveKey = resolveEffectiveApiKey(provider, apiKeyOverride)
        val primaryUrl = resolveModelsEndpointUrl(rawEndpoint, detailed = true)
        val subscriptionUrl = resolveNanoGptSubscriptionModelsUrl(rawEndpoint, provider)
        val cacheKey = "model_catalog_v3_${provider.id}_${primaryUrl.hashCode()}"

        if (!forceRefresh) {
            val cachedRaw = encryptedCache.peekWithoutStats(cacheKey)
            if (!cachedRaw.isNullOrBlank()) {
                runCatching {
                    val cachedObj = JSONObject(cachedRaw)
                    val ageMs = System.currentTimeMillis() - cachedObj.optLong("cachedAt", 0L)
                    if (ageMs in 0..900_000L) {
                        val bodyStr = cachedObj.optString("primaryBody", "")
                        val subIdsArr = cachedObj.optJSONArray("subscriptionIds")
                        val hasSubCatalog = cachedObj.optBoolean("hasSubscriptionCatalog", false)
                        val subIds = buildSet {
                            if (subIdsArr != null) {
                                for (i in 0 until subIdsArr.length()) {
                                    val id = subIdsArr.optString(i).trim()
                                    if (id.isNotEmpty()) add(id)
                                }
                            }
                        }
                        if (bodyStr.isNotBlank()) {
                            val parsedAll = parseModelsJsonAndDetectVision(
                                rawJson = bodyStr,
                                confirmedSubscriptionModelIds = if (hasSubCatalog) subIds else null
                            )
                            if (parsedAll.isNotEmpty()) {
                                return@withContext EndpointModelCatalogResult(
                                    modelsEndpointUsed = primaryUrl,
                                    subscriptionEndpointUsed = if (hasSubCatalog) subscriptionUrl else null,
                                    allModels = parsedAll,
                                    visionModels = parsedAll.filter { it.supportsVision },
                                    subscriptionIncludedModels = parsedAll.filter { it.isConfirmedSubscriptionIncluded },
                                    servedFromCatalogCache = true,
                                    subscriptionMetadataNote = buildSubscriptionCatalogNote(parsedAll, hasSubCatalog)
                                )
                            }
                        }
                    }
                }
            }
        }

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
                    401, 403 -> "Authentication required ($statusCode). Enter your API key and tap Refresh Models."
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

            // If NanoGPT is active, also query NanoGPT's official `/api/subscription/v1/models?detailed=true` catalog
            var confirmedSubIds: Set<String>? = null
            if (subscriptionUrl != null) {
                runCatching {
                    okHttpClient.newCall(buildGetRequest(subscriptionUrl)).execute().use { subResp ->
                        if (subResp.isSuccessful) {
                            val subBody = subResp.body?.string().orEmpty()
                            if (subBody.isNotBlank()) {
                                val subModels = parseModelsJsonAndDetectVision(
                                    rawJson = subBody,
                                    isFromSubscriptionCatalog = true
                                )
                                if (subModels.isNotEmpty()) {
                                    confirmedSubIds = subModels.map { it.id }.toSet()
                                }
                            }
                        }
                    }
                }
            }

            val parsedAll = parseModelsJsonAndDetectVision(
                rawJson = bodyStr,
                confirmedSubscriptionModelIds = confirmedSubIds
            )
            val parsedVision = parsedAll.filter { it.supportsVision }
            val parsedSub = parsedAll.filter { it.isConfirmedSubscriptionIncluded }

            if (parsedAll.isNotEmpty()) {
                val cachePayload = JSONObject().apply {
                    put("cachedAt", System.currentTimeMillis())
                    put("primaryBody", bodyStr)
                    put("hasSubscriptionCatalog", confirmedSubIds != null)
                    if (confirmedSubIds != null) {
                        val arr = JSONArray()
                        confirmedSubIds!!.forEach { arr.put(it) }
                        put("subscriptionIds", arr)
                    }
                }
                encryptedCache.put(cacheKey, cachePayload.toString())
            }

            EndpointModelCatalogResult(
                modelsEndpointUsed = primaryUrl,
                subscriptionEndpointUsed = if (confirmedSubIds != null) subscriptionUrl else null,
                allModels = parsedAll,
                visionModels = parsedVision,
                subscriptionIncludedModels = parsedSub,
                servedFromCatalogCache = false,
                errorMessage = if (parsedAll.isEmpty()) "No models found in response from $primaryUrl." else null,
                subscriptionMetadataNote = buildSubscriptionCatalogNote(parsedAll, confirmedSubIds != null)
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

    private fun buildSubscriptionCatalogNote(
        models: List<DiscoveredAiModel>,
        hasSubCatalog: Boolean
    ): String? {
        if (models.isEmpty()) return null
        val includedCount = models.count { it.subscriptionIncluded == true }
        val unknownCount = models.count { it.subscriptionIncluded == null }
        return when {
            includedCount > 0 && hasSubCatalog ->
                "$includedCount model(s) verified in NanoGPT subscription catalog."
            includedCount > 0 ->
                "$includedCount model(s) verified via NanoGPT subscription metadata."
            unknownCount == models.size ->
                "Subscription status unknown: endpoint response did not include subscription entitlement fields."
            else -> null
        }
    }

    /**
     * Parses OpenAI/NanoGPT/OpenRouter (`{ "data": [...] }`), Gemini (`{ "models": [...] }`), or
     * raw JSONArray model lists and normalizes Vision support, modalities, context length, pricing,
     * and NanoGPT subscription inclusion (Pass 3 Sections D & E).
     *
     * Never guesses subscription inclusion: if neither per-model subscription fields nor
     * [confirmedSubscriptionModelIds] / [isFromSubscriptionCatalog] establish inclusion,
     * `subscriptionIncluded` is set to `null` (`Subscription status unknown`).
     */
    fun parseModelsJsonAndDetectVision(
        rawJson: String,
        confirmedSubscriptionModelIds: Set<String>? = null,
        isFromSubscriptionCatalog: Boolean = false
    ): List<DiscoveredAiModel> {
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
            val ownedBy = obj.optString("owned_by")
                .ifBlank { obj.optString("provider") }
                .ifBlank { cleanId.substringBefore("/", "") }
                .trim()

            val (isVision, reason) = evaluateModelVisionSupport(obj, cleanId, displayName, description)
            val (inputMods, outputMods, hasVideoInput, hasStructuredJson, contextLen) = extractNormalizedCapabilities(
                obj = obj,
                isVision = isVision
            )

            val pricingObj = obj.optJSONObject("pricing")
            val promptPrice = pricingObj?.optString("prompt")?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            val unitStr = pricingObj?.optString("unit").orEmpty()
            val pricingLabel = when {
                promptPrice != null && unitStr.contains("million", ignoreCase = true) -> "$$promptPrice/1M"
                promptPrice != null -> "$$promptPrice/tok"
                pricingObj != null && (pricingObj.optString("prompt") == "0" || pricingObj.optDouble("prompt", -1.0) == 0.0) -> "Free / $0"
                else -> null
            }

            val (subIncluded, subReason) = evaluateSubscriptionInclusion(
                obj = obj,
                modelId = cleanId,
                pricingObj = pricingObj,
                confirmedSubscriptionModelIds = confirmedSubscriptionModelIds,
                isFromSubscriptionCatalog = isFromSubscriptionCatalog
            )

            results += DiscoveredAiModel(
                id = cleanId,
                displayName = displayName,
                supportsVision = isVision,
                visionReason = reason,
                provider = ownedBy,
                pricing = pricingLabel,
                subscriptionIncluded = subIncluded,
                subscriptionReason = subReason,
                inputModalities = inputMods,
                outputModalities = outputMods,
                supportsVideoInput = hasVideoInput,
                supportsStructuredJson = hasStructuredJson,
                contextLength = contextLen,
                isAvailable = !obj.optBoolean("deprecated", false) && !obj.optBoolean("disabled", false)
            )
        }

        return results.sortedWith(
            compareByDescending<DiscoveredAiModel> { it.isConfirmedSubscriptionIncluded }
                .thenByDescending { it.supportsVision }
                .thenBy { it.id.lowercase(Locale.US) }
        )
    }

    /**
     * Evaluates NanoGPT subscription inclusion using ONLY reliable metadata returned by the provider:
     * 1. Explicit per-model boolean or object fields (`subscription`, `subscription_included`, `subscriptionIncluded`,
     *    `free_with_subscription`, `included_in_subscription`, `included`, `entitlement`, `plan`, `tier`,
     *    `pricing.subscription_included`, `pricing.included`, `pricing.tier`).
     * 2. Membership in NanoGPT's `/api/subscription/v1/models` catalog when fetched.
     * 3. Otherwise returns `null to "Subscription status unknown"` — NEVER guesses or fabricates entitlement!
     */
    fun evaluateSubscriptionInclusion(
        obj: JSONObject,
        modelId: String,
        pricingObj: JSONObject? = obj.optJSONObject("pricing"),
        confirmedSubscriptionModelIds: Set<String>? = null,
        isFromSubscriptionCatalog: Boolean = false
    ): Pair<Boolean?, String> {
        // 1. Direct boolean keys on model object
        val directBooleanKeys = listOf(
            "subscription_included",
            "subscriptionIncluded",
            "free_with_subscription",
            "freeWithSubscription",
            "included_in_subscription",
            "includedInSubscription",
            "included"
        )
        for (key in directBooleanKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val rawVal = obj.opt(key)
                if (rawVal is Boolean) {
                    return if (rawVal) {
                        true to "Included ($key=true)"
                    } else {
                        false to "Not in subscription ($key=false)"
                    }
                }
            }
        }

        // 2. `subscription` field (can be Boolean, String, or JSONObject)
        if (obj.has("subscription") && !obj.isNull("subscription")) {
            when (val subVal = obj.opt("subscription")) {
                is Boolean -> {
                    return if (subVal) {
                        true to "Included (subscription=true)"
                    } else {
                        false to "Not in subscription (subscription=false)"
                    }
                }
                is JSONObject -> {
                    for (subKey in listOf("included", "free_with_subscription", "active", "is_included", "enabled")) {
                        if (subVal.has(subKey) && !subVal.isNull(subKey)) {
                            val flag = subVal.optBoolean(subKey, false)
                            return if (flag) {
                                true to "Included (subscription.$subKey=true)"
                            } else {
                                false to "Not in subscription (subscription.$subKey=false)"
                            }
                        }
                    }
                    val statusStr = subVal.optString("status")
                        .ifBlank { subVal.optString("tier") }
                        .ifBlank { subVal.optString("plan") }
                        .trim()
                        .lowercase(Locale.US)
                    if (statusStr in setOf("included", "subscription", "free", "active")) {
                        return true to "Included (subscription=$statusStr)"
                    }
                    if (statusStr in setOf("excluded", "paid", "none", "false", "pay_as_you_go")) {
                        return false to "Not in subscription (subscription=$statusStr)"
                    }
                }
                is String -> {
                    val lowerSub = subVal.trim().lowercase(Locale.US)
                    if (lowerSub in setOf("true", "included", "free", "subscription", "yes")) {
                        return true to "Included (subscription=$lowerSub)"
                    }
                    if (lowerSub in setOf("false", "excluded", "paid", "none", "no")) {
                        return false to "Not in subscription (subscription=$lowerSub)"
                    }
                }
            }
        }

        // 3. String classification fields (`entitlement`, `plan`, `tier`, `billing_tier`)
        for (field in listOf("entitlement", "plan", "tier", "billing_tier", "pricing_tier")) {
            if (obj.has(field) && !obj.isNull(field)) {
                val v = obj.optString(field, "").trim().lowercase(Locale.US)
                if (v in setOf("subscription", "included", "free_with_subscription", "subscription_included", "free")) {
                    return true to "Included ($field=$v)"
                }
                if (v in setOf("paid", "pay_per_prompt", "pay_as_you_go", "per_token", "premium_only", "excluded")) {
                    return false to "Not in subscription ($field=$v)"
                }
            }
        }

        // 4. `supported_service_tiers` array if it explicitly advertises `"subscription"`
        val tiersList = optStringList(obj, "supported_service_tiers")
        if (tiersList.contains("subscription") || tiersList.contains("subscription_included")) {
            return true to "Included (supported_service_tiers=subscription)"
        }

        // 5. `pricing` object classification
        if (pricingObj != null) {
            for (pKey in listOf("subscription_included", "subscriptionIncluded", "included", "free_with_subscription")) {
                if (pricingObj.has(pKey) && !pricingObj.isNull(pKey)) {
                    val flag = pricingObj.optBoolean(pKey, false)
                    return if (flag) {
                        true to "Included (pricing.$pKey=true)"
                    } else {
                        false to "Not in subscription (pricing.$pKey=false)"
                    }
                }
            }
            val pTier = pricingObj.optString("tier")
                .ifBlank { pricingObj.optString("plan") }
                .ifBlank { pricingObj.optString("classification") }
                .trim()
                .lowercase(Locale.US)
            if (pTier in setOf("subscription", "included", "free_with_subscription")) {
                return true to "Included (pricing.tier=$pTier)"
            }
            if (pTier in setOf("paid", "pay_per_prompt", "pay_as_you_go")) {
                return false to "Not in subscription (pricing.tier=$pTier)"
            }
        }

        // 6. NanoGPT `/api/subscription/v1/models` catalog membership
        if (isFromSubscriptionCatalog) {
            return true to "Included in NanoGPT /api/subscription/v1/models"
        }
        if (confirmedSubscriptionModelIds != null) {
            return if (modelId in confirmedSubscriptionModelIds) {
                true to "Included in NanoGPT /api/subscription/v1/models"
            } else {
                false to "Not listed in NanoGPT /api/subscription/v1/models"
            }
        }

        // 7. Cannot be reliably established -> DO NOT GUESS
        return null to "Subscription status unknown"
    }

    /**
     * Combines All Models / Vision Only / Subscription Included filters (Pass 3 Section D).
     * Models with `subscriptionIncluded == null` (Unknown) are NEVER included when
     * [subscriptionIncludedOnly] is true.
     */
    fun filterCatalogModels(
        models: List<DiscoveredAiModel>,
        visionOnly: Boolean,
        subscriptionIncludedOnly: Boolean,
        searchQuery: String = ""
    ): List<DiscoveredAiModel> {
        val cleanQuery = searchQuery.trim().lowercase(Locale.US)
        return models.filter { model ->
            val passesVision = !visionOnly || model.supportsVision
            val passesSubscription = !subscriptionIncludedOnly || model.subscriptionIncluded == true
            val passesSearch = cleanQuery.isEmpty() ||
                model.id.lowercase(Locale.US).contains(cleanQuery) ||
                model.displayName.lowercase(Locale.US).contains(cleanQuery) ||
                model.provider.lowercase(Locale.US).contains(cleanQuery) ||
                model.visionReason.lowercase(Locale.US).contains(cleanQuery)
            passesVision && passesSubscription && passesSearch
        }
    }

    /**
     * Validates whether [modelId] is suitable for photo/image inspection (Pass 3 Section E).
     * If the model does not support vision, returns a clear warning and compatible vision alternatives.
     */
    fun validateModelForPhotoInspection(
        modelId: String,
        catalogModels: List<DiscoveredAiModel> = emptyList(),
        provider: AiProviderType = AiProviderType.CUSTOM_OPENAI
    ): ModelCapabilityValidation {
        val cleanId = modelId.trim()
        val matchedInCatalog = catalogModels.firstOrNull { it.id.equals(cleanId, ignoreCase = true) }

        val (supportsVision, visionReason) = if (matchedInCatalog != null) {
            matchedInCatalog.supportsVision to matchedInCatalog.visionReason
        } else {
            evaluateModelVisionSupport(JSONObject(), cleanId, cleanId, "")
        }

        val subIncluded = matchedInCatalog?.subscriptionIncluded
        val subReason = matchedInCatalog?.subscriptionReason ?: "Subscription status unknown"

        if (supportsVision) {
            return ModelCapabilityValidation(
                modelId = cleanId,
                supportsVision = true,
                visionReason = visionReason,
                subscriptionIncluded = subIncluded,
                subscriptionReason = subReason,
                warningMessage = null,
                suggestedVisionAlternatives = emptyList()
            )
        }

        val alternatives = catalogModels
            .filter { it.supportsVision }
            .sortedByDescending { it.isConfirmedSubscriptionIncluded }
            .map { it.id }
            .ifEmpty { provider.availableModels }
            .take(4)

        return ModelCapabilityValidation(
            modelId = cleanId,
            supportsVision = false,
            visionReason = visionReason,
            subscriptionIncluded = subIncluded,
            subscriptionReason = subReason,
            warningMessage = "This model does not advertise image input. Select a vision model for photo inspection.",
            suggestedVisionAlternatives = alternatives
        )
    }

    private data class NormalizedCapsTuple(
        val inputModalities: List<String>,
        val outputModalities: List<String>,
        val supportsVideoInput: Boolean,
        val supportsStructuredJson: Boolean,
        val contextLength: Int?
    )

    private fun extractNormalizedCapabilities(
        obj: JSONObject,
        isVision: Boolean
    ): NormalizedCapsTuple {
        val archObj = obj.optJSONObject("architecture")
        val capObj = obj.optJSONObject("capabilities")

        val rawInputs = (optStringList(obj, "input_modalities") + optStringList(archObj, "input_modalities")).distinct()
        val inputModalities = if (rawInputs.isNotEmpty()) {
            rawInputs
        } else buildList {
            add("text")
            if (isVision) add("image")
            if (capObj?.optBoolean("video_input", false) == true) add("video")
        }

        val rawOutputs = (optStringList(obj, "output_modalities") + optStringList(archObj, "output_modalities")).distinct()
        val outputModalities = rawOutputs.ifEmpty { listOf("text") }

        val supportsVideo = capObj?.optBoolean("video_input", false) == true ||
            inputModalities.any { it.contains("video") }
        val supportsStructured = capObj?.optBoolean("structured_output", true) ?: true
        val contextLen = obj.optInt("context_length", obj.optInt("inputTokenLimit", -1)).takeIf { it > 0 }

        return NormalizedCapsTuple(
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            supportsVideoInput = supportsVideo,
            supportsStructuredJson = supportsStructured,
            contextLength = contextLen
        )
    }

    private fun evaluateModelVisionSupport(
        obj: JSONObject,
        modelId: String,
        displayName: String,
        description: String
    ): Pair<Boolean, String> {
        val lowerId = modelId.lowercase(Locale.US)
        val lowerName = displayName.lowercase(Locale.US)
        val lowerDesc = description.lowercase(Locale.US)

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

        val inputModalities = optStringList(obj, "input_modalities") +
            optStringList(archObj, "input_modalities")
        if (inputModalities.isNotEmpty()) {
            return if (inputModalities.any { it.contains("image") || it.contains("vision") || it.contains("multimodal") }) {
                true to "Vision Input (${inputModalities.joinToString("+")})"
            } else {
                false to "Text-Only Input"
            }
        }

        val modalityStr = (archObj?.optString("modality").orEmpty() + " " + obj.optString("modality").orEmpty())
            .trim()
            .lowercase(Locale.US)
        if (modalityStr.isNotEmpty()) {
            if (modalityStr.contains("image->text") || modalityStr.contains("image+text") || modalityStr.contains("text+image")) {
                return true to "Vision Modality ($modalityStr)"
            }
            if (modalityStr == "text->text" || modalityStr == "text") {
                return false to "Text-Only Modality"
            }
        }

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
                val item = arr.optString(i).trim().lowercase(Locale.US)
                if (item.isNotEmpty()) add(item)
            }
        }
    }

    /**
     * Extracts provider-reported token usage from:
     * - OpenAI / NanoGPT / OpenRouter (`usage`: `prompt_tokens` / `completion_tokens` / `total_tokens` or `input_tokens` / `output_tokens`)
     * - Anthropic Claude (`usage`: `input_tokens` / `output_tokens`)
     * - Google Gemini (`usageMetadata`: `promptTokenCount` / `candidatesTokenCount` / `totalTokenCount`)
     * Falling back to [fallbackEstimatedTokens] marked [TokenUsageSource.ESTIMATED] when unavailable.
     */
    fun extractTokenUsageFromProviderResponse(
        rawResponseRoot: JSONObject?,
        fallbackEstimatedTokens: Int
    ): ParsedTokenUsage {
        if (rawResponseRoot != null) {
            // 1. OpenAI / NanoGPT / OpenRouter / Anthropic Claude `usage` block (or nested `response.usage`)
            val usageObj = rawResponseRoot.optJSONObject("usage")
                ?: rawResponseRoot.optJSONObject("response")?.optJSONObject("usage")
            if (usageObj != null) {
                val prompt = usageObj.optInt(
                    "prompt_tokens",
                    usageObj.optInt("input_tokens", usageObj.optInt("promptTokenCount", -1))
                )
                val completion = usageObj.optInt(
                    "completion_tokens",
                    usageObj.optInt("output_tokens", usageObj.optInt("candidatesTokenCount", -1))
                )
                val total = usageObj.optInt(
                    "total_tokens",
                    usageObj.optInt("totalTokenCount", -1)
                )
                if (prompt >= 0 || completion >= 0 || total > 0) {
                    val safePrompt = prompt.coerceAtLeast(0)
                    val safeCompletion = completion.coerceAtLeast(0)
                    val safeTotal = if (total > 0) total else (safePrompt + safeCompletion).coerceAtLeast(1)
                    return ParsedTokenUsage(
                        promptTokens = safePrompt,
                        completionTokens = safeCompletion,
                        totalTokens = safeTotal,
                        source = TokenUsageSource.ACTUAL_FROM_PROVIDER
                    )
                }
            }

            // 2. Google Gemini `usageMetadata` block
            val geminiMeta = rawResponseRoot.optJSONObject("usageMetadata")
            if (geminiMeta != null) {
                val prompt = geminiMeta.optInt("promptTokenCount", geminiMeta.optInt("inputTokenCount", -1))
                val completion = geminiMeta.optInt(
                    "candidatesTokenCount",
                    geminiMeta.optInt("outputTokenCount", -1)
                )
                val total = geminiMeta.optInt("totalTokenCount", -1)
                if (prompt >= 0 || completion >= 0 || total > 0) {
                    val safePrompt = prompt.coerceAtLeast(0)
                    val safeCompletion = completion.coerceAtLeast(0)
                    val safeTotal = if (total > 0) total else (safePrompt + safeCompletion).coerceAtLeast(1)
                    return ParsedTokenUsage(
                        promptTokens = safePrompt,
                        completionTokens = safeCompletion,
                        totalTokens = safeTotal,
                        source = TokenUsageSource.ACTUAL_FROM_PROVIDER
                    )
                }
            }
        }

        val estimatedPrompt = (fallbackEstimatedTokens * 0.75).toInt().coerceAtLeast(1)
        val estimatedCompletion = (fallbackEstimatedTokens - estimatedPrompt).coerceAtLeast(1)
        return ParsedTokenUsage(
            promptTokens = estimatedPrompt,
            completionTokens = estimatedCompletion,
            totalTokens = fallbackEstimatedTokens.coerceAtLeast(1),
            source = TokenUsageSource.ESTIMATED
        )
    }

    /**
     * Builds a concise privacy preview before a cloud AI batch or single inspection is sent (Pass 3 Section H).
     */
    fun buildPrivacyPreview(
        photos: List<PhotoEntity>,
        config: AiAdapterConfig = loadConfig(),
        maxBatchSize: Int = 12
    ): AiPrivacyPreview {
        val batch = photos.take(maxBatchSize)
        val effectiveModel = config.selectedModel.trim().ifEmpty { config.activeProvider.defaultModel }
        val providerLabel = config.activeProfileName.ifBlank { config.activeProvider.displayName }

        var cachedCount = 0
        for (photo in batch) {
            val cacheKey = "ai_v2_${photo.dHash}_${photo.pHash}_${config.activeProfileId}_${effectiveModel}"
            if (encryptedCache.peekWithoutStats(cacheKey) != null) {
                cachedCount++
            }
        }

        val networkUploadCount = (batch.size - cachedCount).coerceAtLeast(0)
        // Estimate compressed JPEG size based on maxImageDimensionPx and jpegQuality
        val avgCompressedBytesPerItem = when {
            config.maxImageDimensionPx <= 512 -> 58_000L
            config.maxImageDimensionPx <= 768 -> 115_000L
            else -> 195_000L
        } * config.jpegQuality / 75L

        val totalEstimatedPayloadBytes = networkUploadCount * avgCompressedBytesPerItem
        val tokensPerItem = (260 + (avgCompressedBytesPerItem / 320).toInt()).coerceIn(280, 1400)
        val estimatedTokens = networkUploadCount * tokensPerItem

        val bullets = buildList {
            add("${batch.size} compressed preview${if (batch.size == 1) "" else "s"}${if (cachedCount > 0) " ($cachedCount already cached locally)" else ""}")
            add("Max ${config.maxImageDimensionPx} px (${config.jpegQuality}% JPEG quality)")
            add("No original-resolution files")
            add("OCR/metadata as needed")
        }

        return AiPrivacyPreview(
            totalRequestedCount = batch.size,
            networkUploadCount = networkUploadCount,
            alreadyCachedCount = cachedCount,
            providerName = providerLabel,
            modelName = effectiveModel,
            maxDimensionPx = config.maxImageDimensionPx,
            jpegQuality = config.jpegQuality,
            sendsOriginalFiles = false,
            estimatedPayloadBytes = totalEstimatedPayloadBytes,
            estimatedTokens = estimatedTokens,
            bulletPoints = bullets
        )
    }

    /**
     * Tests the active AI connection profile (Pass 3 Section G):
     * - Verifies model catalog reachability
     * - Checks whether the selected model is available and vision-capable
     * - Verifies authentication without burning significant tokens
     */
    suspend fun testConnection(
        config: AiAdapterConfig,
        catalogOverride: EndpointModelCatalogResult? = null
    ): AiConnectionTestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val providerLabel = config.activeProfileName.ifBlank { config.activeProvider.displayName }
        val modelId = config.selectedModel.trim().ifEmpty { config.activeProvider.defaultModel }
        val apiKey = resolveEffectiveApiKey(config.activeProvider, config.customApiKey)

        val catalog = catalogOverride ?: fetchAvailableModelsFromEndpoint(
            rawEndpoint = config.customEndpointUrl,
            provider = config.activeProvider,
            apiKeyOverride = config.customApiKey,
            forceRefresh = true
        )

        val capabilityCheck = validateModelForPhotoInspection(
            modelId = modelId,
            catalogModels = catalog.allModels,
            provider = config.activeProvider
        )

        val catalogOk = catalog.errorMessage == null && catalog.allModels.isNotEmpty()
        val modelFoundInCatalog = catalog.allModels.any { it.id.equals(modelId, ignoreCase = true) }
        val matchedModel = catalog.allModels.firstOrNull { it.id.equals(modelId, ignoreCase = true) }

        val authValid = apiKey.isNotBlank() && (catalogOk || config.activeProvider == AiProviderType.CLAUDE)
        val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val subLabel = if (config.isNanoGptActive) {
            when (matchedModel?.subscriptionIncluded) {
                true -> "Included with NanoGPT subscription"
                false -> "Paid / Per-prompt model"
                null -> "Subscription status unknown"
            }
        } else null

        val connected = authValid && (catalogOk || config.activeProvider == AiProviderType.CLAUDE)
        val headline = when {
            apiKey.isBlank() -> "API Key Required"
            !catalogOk && config.activeProvider != AiProviderType.CLAUDE -> "Catalog Unreachable"
            !capabilityCheck.supportsVision -> "Connected (Text-Only Model Selected)"
            else -> "Connected"
        }

        AiConnectionTestResult(
            connected = connected,
            headline = headline,
            providerDisplayName = providerLabel,
            modelCatalogStatus = if (catalogOk) "OK (${catalog.allModels.size} models)" else (catalog.errorMessage ?: "Unavailable"),
            selectedModel = modelId,
            selectedModelAvailable = modelFoundInCatalog || !catalogOk,
            visionSupported = capabilityCheck.supportsVision,
            visionStatusLabel = if (capabilityCheck.supportsVision) "supported (${capabilityCheck.visionReason})" else "not supported (${capabilityCheck.visionReason})",
            subscriptionStatusLabel = subLabel,
            authenticationValid = authValid,
            authStatusLabel = if (apiKey.isNotBlank()) "valid (${EncryptedMediaCache.maskApiKey(apiKey)})" else "missing API key",
            latencyMs = latency,
            errorDetail = if (apiKey.isBlank()) "Enter an API key to authenticate requests." else catalog.errorMessage
        )
    }

    /**
     * Inspects a photo using the Encrypted Local Cache first, then either the Local Forensics Engine
     * or the active BYOK Cloud Multimodal Profile with client-side image downscaling and real token tracking.
     *
     * Pass 3 Section A: Never blocks due to a spending budget.
     * Pass 3 Section E: Rejects non-vision models before sending any image payload.
     */
    suspend fun inspectPhotoWithPolicies(
        photo: PhotoEntity,
        requestType: String = "PHOTO_INSPECTION",
        knownCatalogModels: List<DiscoveredAiModel> = emptyList()
    ): MultimodalInspectionResult = withContext(Dispatchers.IO) {
        val config = loadConfig()
        val effectiveModel = config.selectedModel.trim().ifEmpty { config.activeProvider.defaultModel }
        val cacheKey = "ai_v2_${photo.dHash}_${photo.pHash}_${config.activeProfileId}_${effectiveModel}"

        // 1. Check Encrypted Cache by Perceptual Hashes (dHash + pHash) -> 0 new provider tokens!
        val cachedPayload = encryptedCache.get(cacheKey)
        if (cachedPayload != null) {
            runCatching {
                val obj = JSONObject(cachedPayload)
                return@withContext parseJsonToInspectionResult(
                    obj = obj,
                    photo = photo,
                    providerUsed = "${config.activeProfileName} (Encrypted Cache)",
                    providerType = config.activeProvider.id,
                    modelUsed = effectiveModel,
                    requestType = requestType,
                    downscaledDim = "${config.maxImageDimensionPx}px",
                    payloadBytes = 0,
                    tokenUsage = ParsedTokenUsage(
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0,
                        source = TokenUsageSource.LOCAL_CACHE_ZERO
                    ),
                    fromCache = true,
                    requestSucceeded = true,
                    latencyMs = 0L,
                    endpointUsed = "local://encrypted-cache"
                )
            }
        }

        // 2. Check Privacy Mode and API Key availability -> 0 network tokens when Local-Only!
        val apiKey = resolveEffectiveApiKey(config.activeProvider, config.customApiKey)
        val shouldUseCloud = config.privacyMode != PrivacyMode.LOCAL_ONLY && apiKey.isNotEmpty()

        if (!shouldUseCloud) {
            val localJson = buildLocalForensicInspectionJson(photo)
            encryptedCache.put(cacheKey, localJson.toString())
            val reasonTag = if (config.privacyMode == PrivacyMode.LOCAL_ONLY) {
                "Local-Only Privacy"
            } else {
                "Local On-Device Engine"
            }
            return@withContext parseJsonToInspectionResult(
                obj = localJson,
                photo = photo,
                providerUsed = reasonTag,
                providerType = "LOCAL",
                modelUsed = "on-device-forensics",
                requestType = "LOCAL_ANALYSIS",
                downscaledDim = "${config.maxImageDimensionPx}px (Local)",
                payloadBytes = 0,
                tokenUsage = ParsedTokenUsage(
                    promptTokens = 0,
                    completionTokens = 0,
                    totalTokens = 0,
                    source = TokenUsageSource.LOCAL_CACHE_ZERO
                ),
                fromCache = false,
                requestSucceeded = true,
                latencyMs = 1L,
                endpointUsed = "local://on-device"
            )
        }

        // 3. Capability Check (Pass 3 Section E): Reject non-vision models before sending image data!
        val capabilityValidation = validateModelForPhotoInspection(
            modelId = effectiveModel,
            catalogModels = knownCatalogModels,
            provider = config.activeProvider
        )
        if (!capabilityValidation.supportsVision) {
            val warning = capabilityValidation.warningMessage
                ?: "This model does not advertise image input. Select a vision model for photo inspection."
            return@withContext parseJsonToInspectionResult(
                obj = buildLocalForensicInspectionJson(photo),
                photo = photo,
                providerUsed = "${config.activeProfileName} (Vision Required)",
                providerType = config.activeProvider.id,
                modelUsed = effectiveModel,
                requestType = requestType,
                downscaledDim = "0px (Not Sent)",
                payloadBytes = 0,
                tokenUsage = ParsedTokenUsage(0, 0, 0, TokenUsageSource.LOCAL_CACHE_ZERO),
                fromCache = false,
                requestSucceeded = false,
                latencyMs = 0L,
                endpointUsed = "",
                failureNote = warning
            )
        }

        // 4. Downscale & Compress Image Payload
        val (base64Jpeg, byteLength, dimLabel) = downscaleAndEncodePhoto(
            photo = photo,
            maxDimension = config.maxImageDimensionPx,
            jpegQuality = config.jpegQuality
        )
        val fallbackEstimatedTokens = (260 + (byteLength / 320)).coerceIn(280, 1400)
        val requestStartMs = System.currentTimeMillis()

        val callOutcome = try {
            when (config.activeProvider) {
                AiProviderType.GEMINI -> callGeminiRestApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpointBase = config.customEndpointUrl,
                    apiKey = apiKey,
                    fallbackEstimatedTokens = fallbackEstimatedTokens
                )
                AiProviderType.NANOGPT,
                AiProviderType.OPENAI,
                AiProviderType.OPENROUTER,
                AiProviderType.CUSTOM_OPENAI -> callOpenAiCompatibleApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpoint = normalizeOpenAiEndpoint(config.customEndpointUrl),
                    apiKey = apiKey,
                    fallbackEstimatedTokens = fallbackEstimatedTokens
                )
                AiProviderType.CLAUDE -> callClaudeRestApi(
                    base64Jpeg = base64Jpeg,
                    photo = photo,
                    model = effectiveModel,
                    endpoint = config.customEndpointUrl.trim().ifEmpty { AiProviderType.CLAUDE.defaultEndpoint },
                    apiKey = apiKey,
                    fallbackEstimatedTokens = fallbackEstimatedTokens
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - requestStartMs).coerceAtLeast(1L)
            ProviderCallOutcome(
                succeeded = false,
                parsedJson = buildLocalForensicInspectionJson(photo),
                tokenUsage = ParsedTokenUsage(0, 0, 0, TokenUsageSource.ESTIMATED),
                latencyMs = elapsed,
                endpointUsed = sanitizeEndpointForAudit(config.customEndpointUrl),
                failureReason = "Network error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }

        if (callOutcome.succeeded) {
            encryptedCache.put(cacheKey, callOutcome.parsedJson.toString())
        }

        parseJsonToInspectionResult(
            obj = callOutcome.parsedJson,
            photo = photo,
            providerUsed = if (callOutcome.succeeded) {
                "${config.activeProfileName} ($effectiveModel)"
            } else {
                "${config.activeProfileName} (Fallback - Request Failed)"
            },
            providerType = config.activeProvider.id,
            modelUsed = effectiveModel,
            requestType = requestType,
            downscaledDim = dimLabel,
            payloadBytes = byteLength,
            tokenUsage = callOutcome.tokenUsage,
            fromCache = false,
            requestSucceeded = callOutcome.succeeded,
            latencyMs = callOutcome.latencyMs,
            endpointUsed = callOutcome.endpointUsed,
            failureNote = callOutcome.failureReason
        )
    }

    /**
     * Strips any query parameters (such as `?key=...`) from an endpoint URL before storing in audit logs.
     */
    fun sanitizeEndpointForAudit(rawUrl: String): String {
        return rawUrl.trim().substringBefore("?")
    }

    private fun callGeminiRestApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpointBase: String,
        apiKey: String,
        fallbackEstimatedTokens: Int
    ): ProviderCallOutcome {
        val startMs = System.currentTimeMillis()
        val safeModel = if (model.contains("1.5") || model.contains("2.0")) {
            "gemini-3.5-flash"
        } else {
            model.ifBlank { "gemini-3.5-flash" }
        }
        val cleanBase = endpointBase.trim().trimEnd('/')
            .ifEmpty { "https://generativelanguage.googleapis.com/v1beta/models" }
        val auditEndpoint = if (cleanBase.contains(":generateContent")) {
            cleanBase.substringBefore("?")
        } else {
            "$cleanBase/$safeModel:generateContent"
        }
        val url = "$auditEndpoint?key=$apiKey"

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
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (!response.isSuccessful) {
                return ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = ParsedTokenUsage(0, 0, 0, TokenUsageSource.ESTIMATED),
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Gemini HTTP ${response.code}"
                )
            }
            val rawBody = response.body?.string().orEmpty()
            val root = JSONObject(rawBody)
            val usage = extractTokenUsageFromProviderResponse(root, fallbackEstimatedTokens)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
            val extracted = extractJsonObject(text)
            return if (extracted != null) {
                ProviderCallOutcome(
                    succeeded = true,
                    parsedJson = extracted,
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint
                )
            } else {
                ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Gemini returned non-JSON response"
                )
            }
        }
    }

    private fun callOpenAiCompatibleApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpoint: String,
        apiKey: String,
        fallbackEstimatedTokens: Int
    ): ProviderCallOutcome {
        val startMs = System.currentTimeMillis()
        val auditEndpoint = sanitizeEndpointForAudit(endpoint)
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
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val root = JSONObject(response.body?.string().orEmpty())
                val usage = extractTokenUsageFromProviderResponse(root, fallbackEstimatedTokens)
                val content = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                val extracted = extractJsonObject(content)
                if (extracted != null) {
                    return ProviderCallOutcome(
                        succeeded = true,
                        parsedJson = extracted,
                        tokenUsage = usage,
                        latencyMs = latency,
                        endpointUsed = auditEndpoint
                    )
                }
            }
        }

        val fallbackRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(buildPayload(includeJsonFormat = false).toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(fallbackRequest).execute().use { response ->
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (!response.isSuccessful) {
                return ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = ParsedTokenUsage(0, 0, 0, TokenUsageSource.ESTIMATED),
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Provider HTTP ${response.code}"
                )
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val usage = extractTokenUsageFromProviderResponse(root, fallbackEstimatedTokens)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            val extracted = extractJsonObject(content)
            return if (extracted != null) {
                ProviderCallOutcome(
                    succeeded = true,
                    parsedJson = extracted,
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint
                )
            } else {
                ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Provider response did not contain valid JSON"
                )
            }
        }
    }

    private fun callClaudeRestApi(
        base64Jpeg: String,
        photo: PhotoEntity,
        model: String,
        endpoint: String,
        apiKey: String,
        fallbackEstimatedTokens: Int
    ): ProviderCallOutcome {
        val startMs = System.currentTimeMillis()
        val auditEndpoint = sanitizeEndpointForAudit(endpoint)
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
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (!response.isSuccessful) {
                return ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = ParsedTokenUsage(0, 0, 0, TokenUsageSource.ESTIMATED),
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Claude HTTP ${response.code}"
                )
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val usage = extractTokenUsageFromProviderResponse(root, fallbackEstimatedTokens)
            val text = root.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
            val extracted = extractJsonObject(text)
            return if (extracted != null) {
                ProviderCallOutcome(
                    succeeded = true,
                    parsedJson = extracted,
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint
                )
            } else {
                ProviderCallOutcome(
                    succeeded = false,
                    parsedJson = buildLocalForensicInspectionJson(photo),
                    tokenUsage = usage,
                    latencyMs = latency,
                    endpointUsed = auditEndpoint,
                    failureReason = "Claude returned non-JSON response"
                )
            }
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

    /**
     * Builds the multimodal forensic prompt using the single canonical enum lists from
     * [PhotoCategory.canonicalPromptValues] and [ScreenshotSubType.canonicalPromptValues].
     */
    fun buildMultimodalForensicPrompt(photo: PhotoEntity): String {
        val mediaTypeHint = if (photo.isVideo) "video frame (duration ${photo.formattedDuration})" else "photo"
        val validCategories = PhotoCategory.canonicalPromptValues()
        val validScreenshotSubTypes = ScreenshotSubType.canonicalPromptValues()
        return """
            Analyze this $mediaTypeHint for an intelligent photo & video library cleaner.
            Existing telemetry: title=${photo.title}, mediaType=${photo.mediaType}, sharpness=${photo.sharpnessScore}/100, exposure=${photo.exposureScore}/100.
            Return ONLY a JSON object with keys:
            - category: one of [$validCategories]
            - screenshotSubType: one of [$validScreenshotSubTypes]
            - ocrText: extracted visible text
            - aiDescription: concise 1-sentence description of the media
            - semanticTags: comma-separated lowercase search tags
            - junkConfidence: float between 0.0 and 1.0 indicating confidence it is safe to delete
            - junkReason: brief user-friendly explanation of why it should be kept or cleaned
            - sentimentalProtected: boolean true if it contains important records, pets, or best-shot memories
        """.trimIndent()
    }

    private fun buildLocalForensicInspectionJson(photo: PhotoEntity): JSONObject {
        val enhancedDescription = if (photo.aiDescription.isNotBlank()) {
            photo.aiDescription
        } else {
            "Analyzed on device (Sharpness ${photo.sharpnessScore}/100, Exposure ${photo.exposureScore}/100)."
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

    fun parseJsonToInspectionResult(
        obj: JSONObject,
        photo: PhotoEntity,
        providerUsed: String,
        modelUsed: String,
        downscaledDim: String,
        payloadBytes: Int,
        tokenUsage: ParsedTokenUsage,
        fromCache: Boolean,
        requestSucceeded: Boolean = true,
        failureNote: String? = null,
        providerType: String = "CUSTOM_OPENAI",
        requestType: String = "PHOTO_INSPECTION",
        latencyMs: Long = 0L,
        endpointUsed: String = ""
    ): MultimodalInspectionResult {
        val rawCategory = if (obj.has("category")) obj.optString("category") else photo.category
        val (resolvedCategory, categoryNote) = PhotoCategory.parseFromAi(rawCategory)
        val finalCategory = if (resolvedCategory == PhotoCategory.UNKNOWN && photo.categoryEnum != PhotoCategory.UNKNOWN && rawCategory.isBlank()) {
            photo.categoryEnum
        } else {
            resolvedCategory
        }

        val rawSubType = if (obj.has("screenshotSubType")) obj.optString("screenshotSubType") else photo.screenshotSubType
        val (resolvedSubType, subTypeNote) = ScreenshotSubType.parseFromAi(rawSubType)

        val ocr = obj.optString("ocrText", photo.ocrText)
        val desc = obj.optString("aiDescription", photo.aiDescription)
        val tags = obj.optString("semanticTags", photo.semanticTags)
        val conf = obj.optDouble("junkConfidence", photo.junkConfidence.toDouble()).toFloat().coerceIn(0f, 1f)
        val reason = obj.optString("junkReason", photo.junkReason)
        val protected = obj.optBoolean("sentimentalProtected", photo.sentimentalProtected)

        val schemaNotes = listOfNotNull(failureNote, categoryNote, subTypeNote)
            .joinToString(" • ")
        val summaryText = buildString {
            append("${(conf * 100).toInt()}% Junk Confidence • $reason")
            if (schemaNotes.isNotBlank()) {
                append(" [")
                append(schemaNotes)
                append("]")
            }
        }

        val log = AiAuditLogEntity(
            providerId = providerUsed,
            modelName = modelUsed,
            photoTitle = photo.title,
            downscaledDimension = downscaledDim,
            payloadBytes = payloadBytes,
            estimatedTokens = tokenUsage.totalTokens,
            estimatedCostUsd = 0.0,
            servedFromEncryptedCache = fromCache,
            verdictSummary = summaryText,
            promptTokens = tokenUsage.promptTokens,
            completionTokens = tokenUsage.completionTokens,
            totalTokens = tokenUsage.totalTokens,
            tokenUsageSource = tokenUsage.source.name,
            requestSucceeded = requestSucceeded,
            providerType = providerType,
            requestType = requestType,
            latencyMs = latencyMs,
            endpointUsed = endpointUsed
        )

        return MultimodalInspectionResult(
            category = finalCategory,
            screenshotSubType = resolvedSubType,
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
