package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AiAuditLogEntity
import com.example.data.local.PhotoEntity
import com.example.data.local.TriageStatus
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.ByokConnectionProfile
import com.example.domain.ai.EndpointModelCatalogResult
import com.example.domain.ai.PrivacyMode
import com.example.domain.rules.GoalCleanupPlan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineBlue
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaViewModel
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ByokAiHubSheet(
    currentConfig: AiAdapterConfig,
    totalSpendUsd: Double,
    encryptedCacheEntries: Int,
    encryptedCacheHitRate: Int,
    auditLogs: List<AiAuditLogEntity>,
    hasKeyForProvider: (AiProviderType) -> Boolean,
    onFetchEndpointModels: suspend (String, AiProviderType, String) -> EndpointModelCatalogResult,
    onSaveConfig: (AiAdapterConfig) -> Unit,
    onRunBatchAiPass: () -> Unit,
    onClearEncryptedCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var profiles by remember(currentConfig) {
        mutableStateOf(
            currentConfig.profiles.ifEmpty { ByokConnectionProfile.defaultStarterProfiles() }
        )
    }
    var activeProfileId by remember(currentConfig) {
        mutableStateOf(currentConfig.activeProfileId)
    }
    var profileName by remember(currentConfig) {
        mutableStateOf(currentConfig.activeProfileName)
    }
    var selectedProvider by remember(currentConfig) {
        mutableStateOf(currentConfig.activeProvider)
    }
    var selectedModel by remember(currentConfig) {
        mutableStateOf(currentConfig.selectedModel)
    }
    var customEndpoint by remember(currentConfig) {
        mutableStateOf(currentConfig.customEndpointUrl)
    }
    var customApiKey by remember(currentConfig) {
        mutableStateOf(currentConfig.customApiKey)
    }
    var showApiKeyPlaintext by remember { mutableStateOf(false) }

    // Live Endpoint Model Discovery & Vision-Only Dropdown State
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelCatalogResult by remember { mutableStateOf<EndpointModelCatalogResult?>(null) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var visionOnlyFilter by remember { mutableStateOf(true) }
    var modelDropdownSearchQuery by remember { mutableStateOf("") }

    fun triggerFetchModels(endpointToQuery: String = customEndpoint) {
        if (isFetchingModels) return
        coroutineScope.launch {
            isFetchingModels = true
            val res = onFetchEndpointModels(endpointToQuery, selectedProvider, customApiKey)
            modelCatalogResult = res
            isFetchingModels = false
            if (res.visionModels.isNotEmpty() || res.allModels.isNotEmpty()) {
                isModelDropdownExpanded = true
            }
        }
    }

    var selectedPrivacy by remember(currentConfig) {
        mutableStateOf(currentConfig.privacyMode)
    }
    var maxDimension by remember(currentConfig) {
        mutableIntStateOf(currentConfig.maxImageDimensionPx)
    }
    var budgetCapUsd by remember(currentConfig) {
        mutableDoubleStateOf(currentConfig.monthlyBudgetCapUsd)
    }

    fun selectProfile(profile: ByokConnectionProfile) {
        activeProfileId = profile.id
        profileName = profile.profileName
        selectedProvider = profile.providerType
        selectedModel = profile.modelId
        customEndpoint = profile.endpointUrl
        customApiKey = profile.apiKey
    }

    fun buildUpdatedProfilesList(): List<ByokConnectionProfile> {
        val editedProfile = ByokConnectionProfile(
            id = activeProfileId,
            profileName = profileName.trim().ifEmpty { "Custom" },
            providerType = selectedProvider,
            endpointUrl = customEndpoint.trim().ifEmpty { selectedProvider.defaultEndpoint },
            modelId = selectedModel.trim().ifEmpty { selectedProvider.defaultModel },
            apiKey = customApiKey.trim()
        )
        val exists = profiles.any { it.id == activeProfileId }
        return if (exists) {
            profiles.map { if (it.id == activeProfileId) editedProfile else it }
        } else {
            profiles + editedProfile
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = SpineViolet,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "BYOK Connection Profiles & AI Hub",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Custom OpenAI-compatible endpoints • Connection profiles • AES-256 keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // 1. Saved Connection Profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. Saved Connection Profiles",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val updatedExisting = buildUpdatedProfilesList()
                            val newId = "profile_${UUID.randomUUID().toString().take(8)}"
                            val defaultTitle = if (updatedExisting.none { it.profileName.equals("Custom", ignoreCase = true) }) {
                                "Custom"
                            } else {
                                "Custom ${updatedExisting.size + 1}"
                            }
                            val newProfile = ByokConnectionProfile(
                                id = newId,
                                profileName = defaultTitle,
                                providerType = AiProviderType.CUSTOM_OPENAI,
                                endpointUrl = "https://api.openai.com/v1/chat/completions",
                                modelId = "gpt-4o-mini",
                                apiKey = ""
                            )
                            profiles = updatedExisting + newProfile
                            selectProfile(newProfile)
                        },
                        modifier = Modifier.testTag("add_byok_profile_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Profile")
                    }
                    if (profiles.size > 1) {
                        IconButton(
                            onClick = {
                                val remaining = profiles.filterNot { it.id == activeProfileId }
                                if (remaining.isNotEmpty()) {
                                    profiles = remaining
                                    selectProfile(remaining.first())
                                }
                            },
                            modifier = Modifier.testTag("delete_byok_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete active profile",
                                tint = SpineCoral
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == activeProfileId
                    val liveTitle = if (isSelected) profileName.ifBlank { "Custom" } else profile.profileName
                    val liveModel = if (isSelected) selectedModel.ifBlank { profile.modelId } else profile.modelId
                    val hasCustomOrEnvKey = (if (isSelected) customApiKey.isNotBlank() else profile.apiKey.isNotBlank()) ||
                        hasKeyForProvider(if (isSelected) selectedProvider else profile.providerType)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            profiles = buildUpdatedProfilesList()
                            selectProfile(profile)
                        },
                        label = {
                            Text(
                                text = "$liveTitle ($liveModel)${if (hasCustomOrEnvKey) " • Key Ready" else ""}"
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = SpineEmerald
                        ),
                        modifier = Modifier.testTag("profile_chip_${profile.id}")
                    )
                }
            }

            // 2. Profile Editor: Name, Provider Type, Custom Endpoint, Model ID, API Key
            SpineCard(
                spineColor = SpineViolet,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "2. Configure & Rename Connection Profile",
                        style = MaterialTheme.typography.titleSmall,
                        color = SpineViolet
                    )

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { newName ->
                            profileName = newName
                            profiles = profiles.map {
                                if (it.id == activeProfileId) it.copy(profileName = newName.ifBlank { "Custom" }) else it
                            }
                        },
                        label = { Text("Profile Name (Rename Connection Profile)") },
                        placeholder = { Text("Custom") },
                        trailingIcon = {
                            if (profileName.isNotEmpty()) {
                                IconButton(onClick = { profileName = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear profile name",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_profile_name_input")
                    )

                    Text(
                        text = "Adapter Protocol / Service Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProviderType.entries.forEach { provider ->
                            val isSelected = selectedProvider == provider
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProvider = provider
                                    if (customEndpoint.isBlank() || AiProviderType.entries.any { it.defaultEndpoint == customEndpoint }) {
                                        customEndpoint = provider.defaultEndpoint
                                    }
                                    if (selectedModel.isBlank()) {
                                        selectedModel = provider.defaultModel
                                    }
                                },
                                label = { Text(provider.displayName) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SpineViolet.copy(alpha = 0.22f),
                                    selectedLabelColor = SpineViolet
                                ),
                                modifier = Modifier.testTag("provider_chip_${provider.id.lowercase()}")
                            )
                        }
                    }

                    // Custom Endpoint URL + Quick Presets
                    OutlinedTextField(
                        value = customEndpoint,
                        onValueChange = { customEndpoint = it },
                        label = {
                            Text(
                                if (selectedProvider.isOpenAiCompatible) {
                                    "OpenAI-Compatible Endpoint / Base URL"
                                } else {
                                    "Adapter Base URL / Endpoint"
                                }
                            )
                        },
                        placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                        supportingText = {
                            Text(
                                text = if (selectedProvider.isOpenAiCompatible) {
                                    "Enter any OpenAI-compatible base URL or /v1/chat/completions endpoint"
                                } else {
                                    "Provider REST endpoint URL"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_endpoint_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "NanoGPT" to "https://nano-gpt.com/api/v1/chat/completions",
                            "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
                            "OpenAI Default" to "https://api.openai.com/v1/chat/completions",
                            "Gemini REST" to "https://generativelanguage.googleapis.com/v1beta/models/"
                        ).forEach { (label, url) ->
                            FilterChip(
                                selected = customEndpoint.trim() == url,
                                onClick = {
                                    customEndpoint = url
                                    triggerFetchModels(url)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Custom API Key Input (placed before Model Fetch so authenticated /models endpoints work seamlessly)
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key for Profile (Encrypted AES-256 on Device)") },
                        placeholder = { Text("Paste your custom provider API key") },
                        singleLine = true,
                        visualTransformation = if (showApiKeyPlaintext) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showApiKeyPlaintext = !showApiKeyPlaintext }) {
                                    Icon(
                                        imageVector = if (showApiKeyPlaintext) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (showApiKeyPlaintext) "Hide API Key" else "Show API Key",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        supportingText = {
                            val statusText = when {
                                customApiKey.isNotBlank() -> "Encrypted in hardware-backed AES-256-GCM cache for this profile."
                                hasKeyForProvider(selectedProvider) -> "Using fallback key from AI Studio Secrets Panel (BuildConfig)."
                                else -> "Enter your API key above (optional for public /models catalogs like NanoGPT)."
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customApiKey.isNotBlank() || hasKeyForProvider(selectedProvider)) {
                                    SpineEmerald
                                } else {
                                    SpineAmber
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_api_key_input")
                    )

                    // Live Endpoint Vision-Model Discovery Button & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { triggerFetchModels(customEndpoint) },
                            enabled = !isFetchingModels,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fetch_endpoint_models_button")
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    color = SpineCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Querying /models...", color = SpineCyan)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Fetch available vision models",
                                    tint = SpineCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Fetch Vision Models from Endpoint",
                                    color = SpineCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        FilterChip(
                            selected = visionOnlyFilter,
                            onClick = { visionOnlyFilter = !visionOnlyFilter },
                            label = {
                                Text(
                                    text = if (visionOnlyFilter) "Vision Only ✓" else "All Models",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = SpineEmerald
                            ),
                            modifier = Modifier.testTag("toggle_vision_only_models")
                        )
                    }

                    // Custom Model ID Input + Expandable Vision Models Dropdown
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = { Text("Selected Vision Model ID (Tap arrow for dropdown or type)") },
                        placeholder = { Text("e.g. gpt-4o-mini, claude-3-7-sonnet, gemini-2.0-flash") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (modelCatalogResult == null && !isFetchingModels) {
                                        triggerFetchModels(customEndpoint)
                                    } else {
                                        isModelDropdownExpanded = !isModelDropdownExpanded
                                    }
                                },
                                modifier = Modifier.testTag("toggle_model_dropdown_button")
                            ) {
                                Icon(
                                    imageVector = if (isModelDropdownExpanded) {
                                        Icons.Filled.KeyboardArrowUp
                                    } else {
                                        Icons.Filled.ArrowDropDown
                                    },
                                    contentDescription = "Toggle available models dropdown",
                                    tint = SpineCyan
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_model_id_input")
                    )

                    // Discovered Endpoint Models Dropdown Card
                    modelCatalogResult?.let { catalog ->
                        val baseList = if (visionOnlyFilter) catalog.visionModels else catalog.allModels
                        val displayedModels = remember(baseList, modelDropdownSearchQuery) {
                            if (modelDropdownSearchQuery.isBlank()) {
                                baseList
                            } else {
                                val q = modelDropdownSearchQuery.trim().lowercase()
                                baseList.filter {
                                    it.id.lowercase().contains(q) ||
                                        it.displayName.lowercase().contains(q) ||
                                        it.visionReason.lowercase().contains(q)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(1.dp, SpineCyan.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("endpoint_models_dropdown_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isModelDropdownExpanded = !isModelDropdownExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (catalog.errorMessage != null) {
                                                "Endpoint Catalog Notice"
                                            } else if (visionOnlyFilter) {
                                                "Vision-Capable Models (${catalog.visionModels.size} of ${catalog.allModels.size} support images)"
                                            } else {
                                                "All Endpoint Models (${catalog.allModels.size} total)"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (catalog.errorMessage != null) SpineAmber else SpineCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = catalog.errorMessage ?: "Source: ${catalog.modelsEndpointUsed}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isModelDropdownExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.ArrowDropDown
                                        },
                                        contentDescription = null,
                                        tint = SpineCyan
                                    )
                                }

                                if (isModelDropdownExpanded && baseList.isNotEmpty()) {
                                    OutlinedTextField(
                                        value = modelDropdownSearchQuery,
                                        onValueChange = { modelDropdownSearchQuery = it },
                                        placeholder = {
                                            Text(
                                                "Filter ${baseList.size} models (e.g. 'gemini', 'claude', 'gpt', 'qwen')...",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = if (modelDropdownSearchQuery.isNotEmpty()) {
                                            {
                                                IconButton(onClick = { modelDropdownSearchQuery = "" }) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Clear filter",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("model_dropdown_search_input")
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        displayedModels.forEach { modelItem ->
                                            val isCurrent = selectedModel.equals(modelItem.id, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) {
                                                    SpineEmerald.copy(alpha = 0.18f)
                                                } else {
                                                    CharcoalSurface
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isCurrent) SpineEmerald else CardBorderSlate
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedModel = modelItem.id
                                                        isModelDropdownExpanded = false
                                                    }
                                                    .testTag("dropdown_model_item_${modelItem.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = modelItem.id,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = if (isCurrent) SpineEmerald else TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (!modelItem.displayName.equals(modelItem.id, ignoreCase = true)) {
                                                            Text(
                                                                text = modelItem.displayName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = TextSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        modelItem.pricingLabel?.let { price ->
                                                            TelemetryPill(
                                                                text = price,
                                                                accentColor = SpineAmber
                                                            )
                                                        }
                                                        TelemetryPill(
                                                            text = if (modelItem.supportsVision) "Vision" else "Text",
                                                            accentColor = if (modelItem.supportsVision) SpineEmerald else TextMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Built-In Vision Presets (or fetch live from endpoint above):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedProvider.availableModels.forEach { model ->
                            FilterChip(
                                selected = selectedModel == model,
                                onClick = { selectedModel = model },
                                label = { Text(model, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                                    selectedLabelColor = SpineEmerald
                                )
                            )
                        }
                    }
                }
            }

            // Security & Secrets Panel Guidance Card
            SpineCard(
                spineColor = SpineAmber,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = SpineAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Encrypted Local Key Vault & Secrets Notice",
                            style = MaterialTheme.typography.titleSmall,
                            color = SpineAmber
                        )
                    }
                    Text(
                        text = "Keys entered in Connection Profiles are encrypted locally with AES-256-GCM. You can also inject keys via the AI Studio Secrets panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.security_prototype_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // 3. Privacy Mode
            Text(
                text = "3. Privacy & Execution Mode",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrivacyMode.entries.forEach { mode ->
                    val active = selectedPrivacy == mode
                    SpineCard(
                        spineColor = if (active) SpineEmerald else CardBorderSlate,
                        containerColor = if (active) ElevatedSlate else CharcoalSurface,
                        onClick = { selectedPrivacy = mode },
                        modifier = Modifier.testTag("privacy_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (active) SpineEmerald else TextPrimary
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (active) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Selected Privacy Mode",
                                    tint = SpineEmerald
                                )
                            }
                        }
                    }
                }
            }

            // 4. Payload Downscaling & Cost Caps
            SpineCard(
                spineColor = SpineCyan,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Client-Side Downscaling & Spend Cap",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        TelemetryPill(
                            text = String.format(
                                Locale.US,
                                "Spent $%.4f / $%.2f",
                                totalSpendUsd,
                                budgetCapUsd
                            ),
                            accentColor = SpineCyan
                        )
                    }

                    Text(
                        text = "Pre-upload Resize Dimension: ${maxDimension}px JPEG (saves ~85% vision tokens)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(512, 768, 1024).forEach { dim ->
                            FilterChip(
                                selected = maxDimension == dim,
                                onClick = { maxDimension = dim },
                                label = { Text("${dim}px") }
                            )
                        }
                    }

                    Text(
                        text = String.format(Locale.US, "Monthly API Budget Cap: $%.2f USD", budgetCapUsd),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Slider(
                        value = budgetCapUsd.toFloat(),
                        onValueChange = { budgetCapUsd = it.toDouble() },
                        valueRange = 0.5f..20.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = SpineCyan,
                            activeTrackColor = SpineCyan
                        )
                    )
                }
            }

            // 5. Encrypted Cache Performance & Live API Inspection Audit Log
            SpineCard(
                spineColor = SpineEmerald,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = SpineEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Hardware AES-256-GCM Local Cache",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                        TelemetryPill(
                            text = "$encryptedCacheHitRate% Hit Rate ($encryptedCacheEntries entries)",
                            accentColor = SpineEmerald
                        )
                    }
                    Text(
                        text = "Perceptual hashes (dHash/pHash) and multimodal verdicts are encrypted on disk so re-scans and duplicate bursts consume 0 API tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRunBatchAiPass,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_batch_ai_button")
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Inspect Queue")
                        }
                        OutlinedButton(
                            onClick = onClearEncryptedCache,
                            modifier = Modifier.testTag("clear_encrypted_cache_button")
                        ) {
                            Text("Flush Cache")
                        }
                    }
                }
            }

            if (auditLogs.isNotEmpty()) {
                Text(
                    text = "Recent API & Cache Inspection Telemetry",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                auditLogs.take(5).forEach { log ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.photoTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                TelemetryPill(
                                    text = if (log.servedFromEncryptedCache) "CACHE HIT ($0.00)" else String.format(Locale.US, "$%.5f", log.estimatedCostUsd),
                                    accentColor = if (log.servedFromEncryptedCache) SpineEmerald else SpineViolet
                                )
                            }
                            Text(
                                text = "${log.providerId} • ${log.downscaledDimension} • ${log.verdictSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val finalProfiles = buildUpdatedProfilesList()
                    val cleanName = profileName.trim().ifEmpty { "Custom" }
                    val cleanModel = selectedModel.trim().ifEmpty { selectedProvider.defaultModel }
                    val cleanEndpoint = customEndpoint.trim().ifEmpty { selectedProvider.defaultEndpoint }
                    onSaveConfig(
                        AiAdapterConfig(
                            activeProfileId = activeProfileId,
                            activeProfileName = cleanName,
                            profiles = finalProfiles,
                            activeProvider = selectedProvider,
                            selectedModel = cleanModel,
                            customEndpointUrl = cleanEndpoint,
                            customApiKey = customApiKey.trim(),
                            privacyMode = selectedPrivacy,
                            maxImageDimensionPx = maxDimension,
                            monthlyBudgetCapUsd = budgetCapUsd
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_byok_config_button")
            ) {
                Text(
                    text = "Save Connection Profile & Activate",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF041E15),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalPlannerSheet(
    goalTargetMb: Int,
    goalPlan: GoalCleanupPlan?,
    onUpdateTargetMb: (Int) -> Unit,
    onExecutePlanToVault: (List<PhotoEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = SpineEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Cleanup Target Goal Planner",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Safest-first tiered optimizer • Zero sentimental loss",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            SpineCard(
                spineColor = SpineEmerald,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Recovery Goal",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        TelemetryPill(
                            text = "Goal: $goalTargetMb MB",
                            accentColor = SpineEmerald
                        )
                    }
                    Slider(
                        value = goalTargetMb.toFloat(),
                        onValueChange = { onUpdateTargetMb(it.toInt()) },
                        valueRange = 5f..55f,
                        colors = SliderDefaults.colors(
                            thumbColor = SpineEmerald,
                            activeTrackColor = SpineEmerald
                        ),
                        modifier = Modifier.testTag("goal_planner_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 25, 40, 55).forEach { preset ->
                            FilterChip(
                                selected = goalTargetMb == preset,
                                onClick = { onUpdateTargetMb(preset) },
                                label = { Text("Free $preset MB") }
                            )
                        }
                    }
                }
            }

            if (goalPlan != null) {
                Text(
                    text = "Safest Tiered Execution Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                goalPlan.tiers.forEach { tier ->
                    val tierColor = when (tier.tierNumber) {
                        1 -> SpineCoral
                        2 -> SpineAmber
                        else -> SpineCyan
                    }
                    SpineCard(
                        spineColor = tierColor,
                        containerColor = CharcoalSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tier.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                TelemetryPill(
                                    text = tier.riskBadge,
                                    accentColor = tierColor
                                )
                            }
                            Text(
                                text = tier.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "${tier.photos.size} items • Recoverable: ${LuminaViewModel.formatBytes(tier.bytesRecoverable)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = tierColor
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorderSlate)

                Button(
                    onClick = {
                        onExecutePlanToVault(goalPlan.selectedPhotos)
                        onDismiss()
                    },
                    enabled = goalPlan.selectedPhotos.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_goal_plan_button")
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFF041E15)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Stage ${goalPlan.selectedPhotos.size} Items (${LuminaViewModel.formatBytes(goalPlan.achievedBytes)}) to Vault",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF041E15),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoForensicsDetailSheet(
    photo: PhotoEntity,
    onTriage: (TriageStatus) -> Unit,
    onInspectWithAi: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRoboLabForensics by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${photo.folderName} • ${photo.width}×${photo.height} • ${LuminaViewModel.formatBytes(photo.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            if (photo.isVideo) {
                IntegratedVideoPlayer(
                    photo = photo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    autoPlay = false
                )
            } else {
                PhotoThumbnailView(
                    photo = photo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }

            // Human-Friendly Summary & Quality Card
            SpineCard(
                spineColor = SpineBlue,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Why This Item Was Flagged",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        ForensicPillBadge(
                            text = "Quality ${photo.overallQualityScore}/100",
                            accentColor = if (photo.overallQualityScore >= 70) SpineEmerald else SpineAmber
                        )
                    }
                    Text(
                        text = photo.humanFriendlyWhy,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    if (photo.aiDescription.isNotBlank()) {
                        Text(
                            text = photo.aiDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (photo.ocrText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElevatedSlate, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Detected Text: \"${photo.ocrText}\"",
                                style = MaterialTheme.typography.labelMedium,
                                color = SpineCyan
                            )
                        }
                    }
                }
            }

            // Expandable RoboLab / Technical Forensics Section (hidden by default)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRoboLabForensics = !showRoboLabForensics }
                    .testTag("toggle_robolab_forensics")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced / RoboLab Forensics",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Icon(
                            imageVector = if (showRoboLabForensics) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.ArrowDropDown
                            },
                            contentDescription = "Toggle RoboLab details",
                            tint = TextSecondary
                        )
                    }

                    if (showRoboLabForensics) {
                        HorizontalDivider(color = CardBorderSlate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ForensicPillBadge("Sharpness: ${photo.sharpnessScore}/100", SpineEmerald)
                            ForensicPillBadge("Exposure: ${photo.exposureScore}/100", SpineAmber)
                            ForensicPillBadge("Framing: ${photo.framingScore}/100", SpineBlue)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ForensicPillBadge("dHash: ${photo.dHash}", SpineCyan)
                            ForensicPillBadge("pHash: ${photo.pHash}", SpineViolet)
                        }
                        Text(
                            text = "Laplacian Variance: ${String.format(Locale.US, "%.1f", photo.laplacianVariance)} • Source: ${photo.analyzedByProvider}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "URI: ${photo.uriString}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onInspectWithAi(photo) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI Inspect")
                }
                Button(
                    onClick = {
                        onTriage(TriageStatus.KEEP)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Keep", color = Color(0xFF041E15), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onTriage(TriageStatus.TRASH_VAULT)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Review Bin", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TwoStepPermanentDeleteDialog(
    vaultPhotos: List<PhotoEntity>,
    totalRecoverableBytes: Long,
    onConfirmPermanentDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var safetyAcknowledged by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Permanently Delete ${vaultPhotos.size} Item(s)?",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This will permanently remove ${vaultPhotos.size} item(s) (${LuminaViewModel.formatBytes(totalRecoverableBytes)}) from your device storage. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElevatedSlate,
                    border = BorderStroke(
                        1.dp,
                        if (safetyAcknowledged) SpineCoral else CardBorderSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { safetyAcknowledged = !safetyAcknowledged }
                        .testTag("two_step_safety_checkbox")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (safetyAcknowledged) Icons.Filled.Check else Icons.Filled.Security,
                            contentDescription = null,
                            tint = if (safetyAcknowledged) SpineCoral else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "I have reviewed these ${vaultPhotos.size} items and want to delete them permanently.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmPermanentDelete,
                enabled = safetyAcknowledged,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpineCoral,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_permanent_delete_button")
            ) {
                Text("Delete Permanently", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
