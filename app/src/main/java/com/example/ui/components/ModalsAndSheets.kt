package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.domain.ai.AiConnectionTestResult
import com.example.domain.ai.AiPrivacyPreview
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.AiUsageSummary
import com.example.domain.ai.AiWorkflowsAndUsageEngine
import com.example.domain.ai.ByokConnectionProfile
import com.example.domain.ai.DiscoveredAiModel
import com.example.domain.ai.EndpointModelCatalogResult
import com.example.domain.ai.NaturalLanguageCleanupPlan
import com.example.domain.ai.PrivacyMode
import com.example.domain.ai.SubscriptionInclusionStatus
import com.example.domain.ai.UsageRangeOption
import com.example.domain.rules.GoalCleanupPlan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
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
    todayUsageSummary: AiUsageSummary? = null,
    sevenDayUsageSummary: AiUsageSummary? = null,
    thirtyDayUsageSummary: AiUsageSummary? = null,
    totalProviderTokens: Long = 0L,
    securityStorageDescription: String = "",
    encryptedCacheEntries: Int,
    encryptedCacheHitRate: Int,
    auditLogs: List<AiAuditLogEntity>,
    hasKeyForProvider: (AiProviderType) -> Boolean,
    onFetchEndpointModels: suspend (String, AiProviderType, String, Boolean) -> EndpointModelCatalogResult,
    onTestConnection: (suspend (AiAdapterConfig, EndpointModelCatalogResult?) -> AiConnectionTestResult)? = null,
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
    var selectedPrivacy by remember(currentConfig) {
        mutableStateOf(currentConfig.privacyMode)
    }
    var maxDimension by remember(currentConfig) {
        mutableIntStateOf(currentConfig.maxImageDimensionPx)
    }
    var jpegQuality by remember(currentConfig) {
        mutableIntStateOf(currentConfig.jpegQuality)
    }
    var skipPrivacyPreview by remember(currentConfig) {
        mutableStateOf(currentConfig.skipPrivacyPreviewDialog)
    }

    var showApiKeyPlaintext by remember { mutableStateOf(false) }
    var showAdvancedMode by remember { mutableStateOf(false) }
    var selectedUsageRange by remember { mutableStateOf(UsageRangeOption.TODAY) }

    // Live Endpoint Model Discovery & Combinable Filters (Pass 3 Sections D & E)
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelCatalogResult by remember { mutableStateOf<EndpointModelCatalogResult?>(null) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var visionOnlyFilter by remember { mutableStateOf(true) }
    var subscriptionIncludedOnlyFilter by remember { mutableStateOf(false) }
    var modelDropdownSearchQuery by remember { mutableStateOf("") }

    // Connection Test State (Pass 3 Section G)
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<AiConnectionTestResult?>(null) }

    val isNanoGptActive = selectedProvider == AiProviderType.NANOGPT ||
        customEndpoint.contains("nano-gpt.com", ignoreCase = true) ||
        profileName.contains("NanoGPT", ignoreCase = true)

    fun buildCurrentDraftConfig(): AiAdapterConfig {
        val editedProfile = ByokConnectionProfile(
            id = activeProfileId,
            profileName = profileName.trim().ifEmpty { selectedProvider.displayName },
            providerType = selectedProvider,
            endpointUrl = customEndpoint.trim().ifEmpty { selectedProvider.defaultEndpoint },
            modelId = selectedModel.trim().ifEmpty { selectedProvider.defaultModel },
            apiKey = customApiKey.trim()
        )
        val updatedProfiles = if (profiles.any { it.id == activeProfileId }) {
            profiles.map { if (it.id == activeProfileId) editedProfile else it }
        } else {
            profiles + editedProfile
        }
        return AiAdapterConfig(
            activeProfileId = activeProfileId,
            activeProfileName = editedProfile.profileName,
            profiles = updatedProfiles,
            activeProvider = selectedProvider,
            selectedModel = editedProfile.modelId,
            customEndpointUrl = editedProfile.endpointUrl,
            customApiKey = editedProfile.apiKey,
            privacyMode = selectedPrivacy,
            maxImageDimensionPx = maxDimension,
            jpegQuality = jpegQuality,
            skipPrivacyPreviewDialog = skipPrivacyPreview
        )
    }

    fun selectProfile(profile: ByokConnectionProfile) {
        activeProfileId = profile.id
        profileName = profile.profileName
        selectedProvider = profile.providerType
        selectedModel = profile.modelId
        customEndpoint = profile.endpointUrl
        customApiKey = profile.apiKey
        connectionTestResult = null
    }

    fun triggerFetchModels(
        endpointToQuery: String = customEndpoint,
        forceRefresh: Boolean = false
    ) {
        if (isFetchingModels) return
        coroutineScope.launch {
            isFetchingModels = true
            val res = onFetchEndpointModels(
                endpointToQuery,
                selectedProvider,
                customApiKey,
                forceRefresh
            )
            modelCatalogResult = res
            isFetchingModels = false
            if (res.visionModels.isNotEmpty() || res.allModels.isNotEmpty()) {
                isModelDropdownExpanded = true
            }
        }
    }

    fun triggerConnectionTest() {
        if (isTestingConnection) return
        coroutineScope.launch {
            isTestingConnection = true
            val draft = buildCurrentDraftConfig()
            val catalog = onFetchEndpointModels(
                draft.customEndpointUrl,
                draft.activeProvider,
                draft.customApiKey,
                true
            )
            modelCatalogResult = catalog
            connectionTestResult = if (onTestConnection != null) {
                onTestConnection(draft, catalog)
            } else {
                val catalogOk = catalog.errorMessage == null && catalog.allModels.isNotEmpty()
                val keyReady = draft.customApiKey.isNotBlank() || hasKeyForProvider(draft.activeProvider)
                val matched = catalog.allModels.firstOrNull { it.id.equals(draft.selectedModel, ignoreCase = true) }
                val supportsVision = matched?.supportsVision ?: true
                AiConnectionTestResult(
                    connected = keyReady && (catalogOk || draft.activeProvider == AiProviderType.CLAUDE),
                    headline = if (keyReady) "Connected" else "API Key Required",
                    providerDisplayName = draft.activeProfileName,
                    modelCatalogStatus = if (catalogOk) "OK (${catalog.allModels.size} models)" else (catalog.errorMessage ?: "Unavailable"),
                    selectedModel = draft.selectedModel,
                    selectedModelAvailable = matched != null || !catalogOk,
                    visionSupported = supportsVision,
                    visionStatusLabel = if (supportsVision) "supported" else "not supported",
                    subscriptionStatusLabel = if (draft.isNanoGptActive) {
                        matched?.subscriptionStatus?.badgeText ?: "Subscription status unknown"
                    } else null,
                    authenticationValid = keyReady,
                    authStatusLabel = if (keyReady) "valid" else "missing API key"
                )
            }
            isTestingConnection = false
        }
    }

    // Evaluate capability & subscription status for the currently selected model
    val selectedCatalogModel: DiscoveredAiModel? = remember(modelCatalogResult, selectedModel) {
        modelCatalogResult?.allModels?.firstOrNull {
            it.id.equals(selectedModel.trim(), ignoreCase = true)
        }
    }

    val isSelectedModelVisionCapable: Boolean = remember(selectedCatalogModel, selectedModel) {
        if (selectedCatalogModel != null) {
            selectedCatalogModel.supportsVision
        } else {
            val lower = selectedModel.trim().lowercase(Locale.US)
            val textOnlyHints = listOf(
                "deepseek-r1", "deepseek-v3", "deepseek-chat", "deepseek-coder",
                "gpt-3.5", "o1-mini", "o3-mini", "codestral", "llama-3.1-", "llama-3.3-", "qwq"
            )
            textOnlyHints.none { lower.contains(it) }
        }
    }

    val compatibleVisionAlternatives: List<String> = remember(modelCatalogResult, selectedProvider, isNanoGptActive) {
        val fromCatalog = modelCatalogResult?.visionModels
            ?.sortedByDescending { it.isConfirmedSubscriptionIncluded }
            ?.map { it.id }
            .orEmpty()
        fromCatalog.ifEmpty { selectedProvider.availableModels }.take(4)
    }

    val effectiveTodayUsage = todayUsageSummary ?: remember(auditLogs, currentConfig) {
        AiWorkflowsAndUsageEngine.computeTodayUsageSummary(auditLogs, currentConfig)
    }
    val effective7dUsage = sevenDayUsageSummary ?: remember(auditLogs, currentConfig) {
        AiWorkflowsAndUsageEngine.computeUsageSummary(auditLogs, UsageRangeOption.LAST_7_DAYS, currentConfig)
    }
    val effective30dUsage = thirtyDayUsageSummary ?: remember(auditLogs, currentConfig) {
        AiWorkflowsAndUsageEngine.computeUsageSummary(auditLogs, UsageRangeOption.LAST_30_DAYS, currentConfig)
    }
    val activeUsageSummary = when (selectedUsageRange) {
        UsageRangeOption.TODAY -> effectiveTodayUsage
        UsageRangeOption.LAST_7_DAYS -> effective7dUsage
        UsageRangeOption.LAST_30_DAYS -> effective30dUsage
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
            // Header
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
                            text = "AI & Privacy Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Simple BYOK setup • Private on-device defaults • Auditable token tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // =====================================================================
            // LEVEL 1: SIMPLE MODE (Pass 3 Section F)
            // =====================================================================
            SpineCard(
                spineColor = SpineViolet,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 1. AI Provider Selector
                    Text(
                        text = "AI Provider",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
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
                                    val matchingSaved = profiles.firstOrNull { it.providerType == provider }
                                    if (matchingSaved != null) {
                                        selectProfile(matchingSaved)
                                    } else {
                                        selectedProvider = provider
                                        profileName = provider.displayName
                                        customEndpoint = provider.defaultEndpoint
                                        selectedModel = provider.defaultModel
                                        connectionTestResult = null
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

                    // 2. Model Selector + Combinable Filters (All Models / Vision Only / Subscription Included)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { triggerFetchModels(customEndpoint, forceRefresh = true) },
                            enabled = !isFetchingModels,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("refresh_models_button")
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    color = SpineCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Refreshing...", style = MaterialTheme.typography.labelSmall, color = SpineCyan)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Models",
                                    tint = SpineCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh Models", style = MaterialTheme.typography.labelSmall, color = SpineCyan)
                            }
                        }
                    }

                    // Filter Chips: All Models | Vision Only | Subscription Included (when NanoGPT is active)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !visionOnlyFilter && !subscriptionIncludedOnlyFilter,
                            onClick = {
                                visionOnlyFilter = false
                                subscriptionIncludedOnlyFilter = false
                            },
                            label = { Text("All Models", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpineCyan.copy(alpha = 0.20f),
                                selectedLabelColor = SpineCyan
                            ),
                            modifier = Modifier.testTag("filter_all_models_chip")
                        )
                        FilterChip(
                            selected = visionOnlyFilter,
                            onClick = { visionOnlyFilter = !visionOnlyFilter },
                            label = {
                                Text(
                                    text = if (visionOnlyFilter) "Vision Only ✓" else "Vision Only",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = SpineEmerald
                            ),
                            modifier = Modifier.testTag("toggle_vision_only_models")
                        )
                        if (isNanoGptActive) {
                            FilterChip(
                                selected = subscriptionIncludedOnlyFilter,
                                onClick = {
                                    subscriptionIncludedOnlyFilter = !subscriptionIncludedOnlyFilter
                                    if (modelCatalogResult == null && !isFetchingModels) {
                                        triggerFetchModels(customEndpoint, forceRefresh = false)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (subscriptionIncludedOnlyFilter) {
                                            "Subscription Included ✓"
                                        } else {
                                            "Subscription Included"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SpineViolet.copy(alpha = 0.24f),
                                    selectedLabelColor = SpineViolet
                                ),
                                modifier = Modifier.testTag("toggle_subscription_included_models")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = { Text("Selected Model") },
                        placeholder = { Text("e.g. google/gemini-2.0-flash-001, gpt-4o-mini") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (modelCatalogResult == null && !isFetchingModels) {
                                        triggerFetchModels(customEndpoint, forceRefresh = false)
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

                    // Capability & Subscription Status Pills for Selected Model
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryPill(
                            text = if (isSelectedModelVisionCapable) "✓ Vision capable" else "Text-only model",
                            accentColor = if (isSelectedModelVisionCapable) SpineEmerald else SpineAmber
                        )
                        if (isNanoGptActive) {
                            val subStatus = selectedCatalogModel?.subscriptionStatus ?: SubscriptionInclusionStatus.UNKNOWN
                            val pillText = when (subStatus) {
                                SubscriptionInclusionStatus.INCLUDED -> "✓ Included with NanoGPT subscription"
                                SubscriptionInclusionStatus.NOT_INCLUDED -> "Paid / Per-Prompt model"
                                SubscriptionInclusionStatus.UNKNOWN -> "Subscription status unknown"
                            }
                            val pillColor = when (subStatus) {
                                SubscriptionInclusionStatus.INCLUDED -> SpineEmerald
                                SubscriptionInclusionStatus.NOT_INCLUDED -> SpineAmber
                                SubscriptionInclusionStatus.UNKNOWN -> TextSecondary
                            }
                            TelemetryPill(
                                text = pillText,
                                accentColor = pillColor
                            )
                        }
                    }

                    // Capability-Aware Warning when a non-vision model is selected (Pass 3 Section E)
                    if (!isSelectedModelVisionCapable) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SpineAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, SpineAmber.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("non_vision_model_warning_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = SpineAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "This model does not advertise image input. Select a vision model for photo inspection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (compatibleVisionAlternatives.isNotEmpty()) {
                                    Text(
                                        text = "Compatible vision alternatives:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        compatibleVisionAlternatives.forEach { altModel ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { selectedModel = altModel },
                                                label = { Text(altModel, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = CharcoalSurface,
                                                    labelColor = SpineEmerald
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Discovered Endpoint Models Dropdown Card
                    modelCatalogResult?.let { catalog ->
                        val displayedModels = remember(
                            catalog.allModels,
                            visionOnlyFilter,
                            subscriptionIncludedOnlyFilter,
                            modelDropdownSearchQuery
                        ) {
                            val cleanQuery = modelDropdownSearchQuery.trim().lowercase(Locale.US)
                            catalog.allModels.filter { m ->
                                val passesVision = !visionOnlyFilter || m.supportsVision
                                val passesSub = !subscriptionIncludedOnlyFilter || m.subscriptionIncluded == true
                                val passesSearch = cleanQuery.isEmpty() ||
                                    m.id.lowercase(Locale.US).contains(cleanQuery) ||
                                    m.displayName.lowercase(Locale.US).contains(cleanQuery) ||
                                    m.provider.lowercase(Locale.US).contains(cleanQuery) ||
                                    m.visionReason.lowercase(Locale.US).contains(cleanQuery)
                                passesVision && passesSub && passesSearch
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
                                            } else {
                                                "Showing ${displayedModels.size} of ${catalog.allModels.size} Models"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (catalog.errorMessage != null) SpineAmber else SpineCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val subNote = catalog.subscriptionMetadataNote
                                        Text(
                                            text = catalog.errorMessage
                                                ?: subNote
                                                ?: "Source: ${catalog.modelsEndpointUsed}",
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

                                if (isModelDropdownExpanded) {
                                    OutlinedTextField(
                                        value = modelDropdownSearchQuery,
                                        onValueChange = { modelDropdownSearchQuery = it },
                                        placeholder = {
                                            Text(
                                                "Filter models (e.g. 'gemini', 'claude', 'gpt', 'qwen')...",
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

                                    if (displayedModels.isEmpty()) {
                                        Text(
                                            text = if (subscriptionIncludedOnlyFilter) {
                                                "No models matched 'Subscription Included'. ${catalog.subscriptionMetadataNote ?: "Enter your NanoGPT API key and tap Refresh Models to verify subscription entitlement."}"
                                            } else {
                                                "No models match current filter."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SpineAmber,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    } else {
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
                                                            // Display "Included" badge ONLY when verified (Pass 3 Section D)
                                                            if (modelItem.isConfirmedSubscriptionIncluded) {
                                                                TelemetryPill(
                                                                    text = "Included",
                                                                    accentColor = SpineViolet
                                                                )
                                                            }
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
                    }

                    // Quick Model Presets
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

                    // 3. API Key Input
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("••••••••••••") },
                        singleLine = true,
                        visualTransformation = if (showApiKeyPlaintext) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
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
                        },
                        supportingText = {
                            val storageDesc = securityStorageDescription.ifBlank {
                                "Encrypted locally with AES-256-GCM (excluded from cloud backup)."
                            }
                            val statusText = when {
                                customApiKey.isNotBlank() ->
                                    "Stored securely (${com.example.data.security.EncryptedMediaCache.maskApiKey(customApiKey)}): $storageDesc"
                                hasKeyForProvider(selectedProvider) ->
                                    "Using key from AI Studio Secrets Panel."
                                else ->
                                    "Enter your API key above (encrypted on device, never logged)."
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

                    // 4. [Test Connection] Button & Structured Result (Pass 3 Section G)
                    OutlinedButton(
                        onClick = { triggerConnectionTest() },
                        enabled = !isTestingConnection,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SpineCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_ai_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                color = SpineCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Testing Connection...", color = SpineCyan, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SpineCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Test Connection", color = SpineCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    connectionTestResult?.let { testRes ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElevatedSlate,
                            border = BorderStroke(
                                1.dp,
                                if (testRes.connected && testRes.visionSupported) SpineEmerald else SpineAmber
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connection_test_result_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = testRes.headline,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (testRes.connected) SpineEmerald else SpineAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (testRes.latencyMs > 0L) {
                                        TelemetryPill(
                                            text = "${testRes.latencyMs} ms",
                                            accentColor = SpineCyan
                                        )
                                    }
                                }
                                Text(
                                    text = "Provider: ${testRes.providerDisplayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Model catalog: ${testRes.modelCatalogStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Selected model: ${if (testRes.selectedModelAvailable) "available" else "not found in catalog"} (${testRes.selectedModel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Vision: ${testRes.visionStatusLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (testRes.visionSupported) SpineEmerald else SpineAmber
                                )
                                testRes.subscriptionStatusLabel?.let { subLabel ->
                                    Text(
                                        text = "Subscription: $subLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "Authentication: ${testRes.authStatusLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (testRes.authenticationValid) SpineEmerald else SpineAmber
                                )
                                testRes.errorDetail?.let { err ->
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpineAmber
                                    )
                                }
                            }
                        }
                    }

                    // 5. Privacy Selector (Hybrid / Local Only / Cloud)
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            PrivacyMode.HYBRID_SMART_TIER,
                            PrivacyMode.LOCAL_ONLY,
                            PrivacyMode.CLOUD_MULTIMODAL
                        ).forEach { mode ->
                            val active = selectedPrivacy == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) SpineEmerald.copy(alpha = 0.20f) else ElevatedSlate,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) SpineEmerald else CardBorderSlate
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPrivacy = mode }
                                    .testTag("privacy_mode_${mode.name.lowercase()}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = mode.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) SpineEmerald else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = selectedPrivacy.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // =====================================================================
            // TODAY'S AI USAGE REFERENCE PANEL (Pass 3 Section C)
            // Informational only — no spending budget or usage cap warnings!
            // =====================================================================
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
                        Column {
                            Text(
                                text = when (selectedUsageRange) {
                                    UsageRangeOption.TODAY -> "Today's AI Usage"
                                    UsageRangeOption.LAST_7_DAYS -> "7-Day AI Usage"
                                    UsageRangeOption.LAST_30_DAYS -> "30-Day AI Usage"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Informational reference • Resets at local midnight",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            UsageRangeOption.entries.forEach { rangeOpt ->
                                val isSel = selectedUsageRange == rangeOpt
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedUsageRange = rangeOpt },
                                    label = {
                                        Text(
                                            text = rangeOpt.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SpineCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = SpineCyan
                                    ),
                                    modifier = Modifier.testTag("usage_range_${rangeOpt.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "${activeUsageSummary.formattedTotalTokens} tokens",
                                style = MaterialTheme.typography.headlineSmall,
                                color = SpineCyan,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("today_usage_tokens_text")
                            )
                            Text(
                                text = "${activeUsageSummary.requestCount} request${if (activeUsageSummary.requestCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Input: ${String.format(Locale.US, "%,d", activeUsageSummary.inputTokens)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Output: ${String.format(Locale.US, "%,d", activeUsageSummary.outputTokens)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    HorizontalDivider(color = CardBorderSlate)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Provider: ${profileName.ifBlank { selectedProvider.displayName }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Model: ${selectedModel.ifBlank { selectedProvider.defaultModel }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TelemetryPill(
                            text = activeUsageSummary.sourceBreakdownLabel,
                            accentColor = if (activeUsageSummary.estimatedRequestCount == 0) SpineEmerald else SpineAmber
                        )
                    }

                    if (activeUsageSummary.cachedHitCount > 0 || activeUsageSummary.localAnalysisCount > 0) {
                        Text(
                            text = "0-token local savings: ${activeUsageSummary.cachedHitCount} cache hits • ${activeUsageSummary.localAnalysisCount} local-only analyses",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpineEmerald
                        )
                    }
                }
            }

            // =====================================================================
            // LEVEL 2: ADVANCED SETTINGS TOGGLE (Pass 3 Section F)
            // =====================================================================
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedMode = !showAdvancedMode }
                    .testTag("toggle_advanced_ai_settings")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = SpineCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Advanced",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Custom endpoint, profiles, downscale resolution, JPEG quality, cache & raw logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showAdvancedMode) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.ArrowDropDown
                        },
                        contentDescription = "Toggle Advanced AI Settings",
                        tint = SpineCyan
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedMode) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Saved Connection Profiles & Custom OpenAI-Compatible Profiles
                    SpineCard(
                        spineColor = SpineViolet,
                        containerColor = CharcoalSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Custom Connection Profiles",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val updatedExisting = buildCurrentDraftConfig().profiles
                                            val newId = "profile_${UUID.randomUUID().toString().take(8)}"
                                            val defaultTitle = "Custom ${updatedExisting.size + 1}"
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
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            profiles = buildCurrentDraftConfig().profiles
                                            selectProfile(profile)
                                        },
                                        label = { Text("$liveTitle ($liveModel)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                                            selectedLabelColor = SpineEmerald
                                        ),
                                        modifier = Modifier.testTag("profile_chip_${profile.id}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = profileName,
                                onValueChange = { newName ->
                                    profileName = newName
                                    profiles = profiles.map {
                                        if (it.id == activeProfileId) it.copy(profileName = newName.ifBlank { "Custom" }) else it
                                    }
                                },
                                label = { Text("Profile Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("byok_profile_name_input")
                            )

                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = { customEndpoint = it },
                                label = { Text("Custom Endpoint / Base URL") },
                                placeholder = { Text("https://nano-gpt.com/api/v1/chat/completions") },
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
                                    "OpenAI" to "https://api.openai.com/v1/chat/completions",
                                    "Gemini REST" to "https://generativelanguage.googleapis.com/v1beta/models/"
                                ).forEach { (label, url) ->
                                    FilterChip(
                                        selected = customEndpoint.trim() == url,
                                        onClick = {
                                            customEndpoint = url
                                            triggerFetchModels(url, forceRefresh = true)
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { triggerFetchModels(customEndpoint, forceRefresh = true) },
                                enabled = !isFetchingModels,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_endpoint_models_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Fetch model catalog",
                                    tint = SpineCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch Model Catalog from Endpoint", color = SpineCyan)
                            }
                        }
                    }

                    // Model Capability Diagnostics
                    selectedCatalogModel?.let { diag ->
                        SpineCard(
                            spineColor = SpineCyan,
                            containerColor = CharcoalSurface
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Model Capability Diagnostics",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "ID: ${diag.id} • Provider: ${diag.provider.ifBlank { selectedProvider.displayName }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Input Modalities: ${diag.inputModalities.joinToString(", ")} • Output: ${diag.outputModalities.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Vision: ${diag.visionReason} • Structured JSON: ${if (diag.supportsStructuredJson) "Supported" else "Fallback"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Subscription Metadata: ${diag.subscriptionReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (diag.isConfirmedSubscriptionIncluded) SpineEmerald else TextSecondary
                                )
                            }
                        }
                    }

                    // Downscale Resolution & JPEG Quality
                    SpineCard(
                        spineColor = SpineCyan,
                        containerColor = CharcoalSurface
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Downscale Resolution & Compression",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Max Dimension: ${maxDimension} px • JPEG Quality: ${jpegQuality}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(512, 768, 1024).forEach { dim ->
                                    FilterChip(
                                        selected = maxDimension == dim,
                                        onClick = { maxDimension = dim },
                                        label = { Text("${dim} px") }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(65, 75, 85, 92).forEach { qual ->
                                    FilterChip(
                                        selected = jpegQuality == qual,
                                        onClick = { jpegQuality = qual },
                                        label = { Text("${qual}% JPEG") }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Show Privacy Preview Before Cloud Batches",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Preview payload size, max resolution, and model before sending items.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = !skipPrivacyPreview,
                                    onCheckedChange = { showPreview -> skipPrivacyPreview = !showPreview },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpineEmerald,
                                        checkedTrackColor = SpineEmerald.copy(alpha = 0.35f)
                                    )
                                )
                            }
                        }
                    }

                    // Encrypted Cache & Audit Log
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
                                        text = "Encrypted AES-256-GCM Local Cache",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                }
                                TelemetryPill(
                                    text = "$encryptedCacheHitRate% Hit ($encryptedCacheEntries)",
                                    accentColor = SpineEmerald
                                )
                            }
                            Text(
                                text = securityStorageDescription.ifBlank {
                                    "Perceptual hashes and AI verdicts are cached locally with AES-256-GCM. Cache hits consume 0 provider tokens."
                                },
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
                                    Text("Inspect Batch")
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
                            text = "Audit Log & Raw Token Usage (${String.format(Locale.US, "%,d", totalProviderTokens)} lifetime tokens)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        auditLogs.take(8).forEach { log ->
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
                                        val tokenBadgeText = when {
                                            log.servedFromEncryptedCache -> "CACHE HIT (0 tok)"
                                            log.isLocalOnly -> "LOCAL (0 tok)"
                                            !log.requestSucceeded -> "FAILED (0 tok)"
                                            log.tokenUsageSource == "ACTUAL_FROM_PROVIDER" -> "✓ ${log.totalTokens} tok (actual)"
                                            else -> "~ ${log.totalTokens} tok (est.)"
                                        }
                                        val tokenBadgeColor = when {
                                            log.servedFromEncryptedCache || log.isLocalOnly -> SpineEmerald
                                            !log.requestSucceeded -> SpineCoral
                                            log.tokenUsageSource == "ACTUAL_FROM_PROVIDER" -> SpineCyan
                                            else -> SpineAmber
                                        }
                                        TelemetryPill(
                                            text = tokenBadgeText,
                                            accentColor = tokenBadgeColor
                                        )
                                    }
                                    Text(
                                        text = "${log.providerId} • In:${log.promptTokens} Out:${log.completionTokens} • ${log.downscaledDimension}${if (log.latencyMs > 0) " • ${log.latencyMs}ms" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = log.verdictSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSaveConfig(buildCurrentDraftConfig())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_byok_config_button")
            ) {
                Text(
                    text = "Save AI Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF041E15),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Concise Privacy Preview dialog shown before a cloud AI batch is sent (Pass 3 Section H).
 */
@Composable
fun AiPrivacyPreviewDialog(
    preview: AiPrivacyPreview,
    onConfirmAnalyze: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var rememberPreference by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = SpineEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Analyze ${preview.totalRequestedCount} item${if (preview.totalRequestedCount == 1) "" else "s"} with ${preview.providerName}?",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("ai_privacy_preview_dialog")
            ) {
                Text(
                    text = "Sent:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    preview.bulletPoints.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = CardBorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = preview.modelName,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Estimated payload:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = LuminaViewModel.formatBytes(preview.estimatedPayloadBytes),
                            style = MaterialTheme.typography.labelLarge,
                            color = SpineCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Estimated tokens: ~${String.format(Locale.US, "%,d", preview.estimatedTokens)} tokens (informational reference only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberPreference = !rememberPreference },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberPreference,
                        onCheckedChange = { rememberPreference = it },
                        colors = CheckboxDefaults.colors(checkedColor = SpineEmerald)
                    )
                    Text(
                        text = "Don't show again for this profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmAnalyze(rememberPreference) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("privacy_preview_analyze_button")
            ) {
                Text("Analyze", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("privacy_preview_cancel_button")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Natural-Language Cleanup Plan Sheet (Pass 3 Section J):
 * Converts a natural-language cleanup request into a structured, reversible preview with
 * candidate count, recoverable size, and automatic safety exclusions. Never immediately deletes!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaturalLanguageCleanupPlanSheet(
    plan: NaturalLanguageCleanupPlan,
    onReviewCandidates: (NaturalLanguageCleanupPlan) -> Unit,
    onSaveAutomation: (NaturalLanguageCleanupPlan) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val breakdown = plan.protectedBreakdown

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
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("natural_cleanup_plan_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cleanup Plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpineCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            SpineCard(
                spineColor = ElectricBlue,
                containerColor = CharcoalSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${plan.candidates.size} candidates",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = plan.plainEnglishSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    TelemetryPill(
                        text = LuminaViewModel.formatBytes(plan.recoverableBytes),
                        accentColor = KeepEmerald
                    )
                }
            }

            SpineCard(
                spineColor = SpineEmerald,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = SpineEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Protected automatically:",
                            style = MaterialTheme.typography.titleSmall,
                            color = SpineEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• ${breakdown.receiptsCount} receipts",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "• ${breakdown.confirmationNumbersCount} confirmation numbers",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "• ${breakdown.conversationsCount} conversations",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "• ${breakdown.credentialsCount} credential screenshots",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    if (breakdown.bestShotsAndFavoritesCount > 0) {
                        Text(
                            text = "• ${breakdown.bestShotsAndFavoritesCount} best shots & favorites",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSaveAutomation(plan) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SpineViolet),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cleanup_plan_save_automation_button")
                ) {
                    Text(
                        text = "Save Automation",
                        color = SpineViolet,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onReviewCandidates(plan) },
                    enabled = plan.candidates.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("cleanup_plan_review_button")
                ) {
                    Text(
                        text = "Review ${plan.candidates.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalPlannerSheet(
    goalTargetMb: Int,
    goalPlan: GoalCleanupPlan?,
    onUpdateTargetMb: (Int) -> Unit,
    onExecutePlanToVault: (List<PhotoEntity>) -> Unit,
    onReviewSelectedPhotos: (List<PhotoEntity>) -> Unit = onExecutePlanToVault,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomSlider by remember {
        mutableStateOf(goalTargetMb !in listOf(500, 1000, 5000))
    }
    var selectedTierNumbers by remember(goalPlan) {
        mutableStateOf(
            goalPlan?.tiers
                ?.filter { it.photos.isNotEmpty() }
                ?.map { it.tierNumber }
                ?.toSet()
                ?: setOf(1, 2, 3, 4)
        )
    }

    val chosenPhotos = remember(goalPlan, selectedTierNumbers) {
        goalPlan?.tiers
            ?.filter { it.tierNumber in selectedTierNumbers }
            ?.flatMap { it.photos }
            .orEmpty()
    }
    val chosenBytes = remember(chosenPhotos) {
        chosenPhotos.sumOf { it.fileSizeBytes }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBg,
        scrimColor = Color.Black.copy(alpha = 0.74f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("goal_planner_sheet"),
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
                            text = "How much space do you want to free?",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose a goal and select which proposed groups to review",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // Target Presets: 500 MB | 1 GB | 5 GB | Custom (Pass 4 Section O)
            SpineCard(
                spineColor = SpineEmerald,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Space Goal",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TelemetryPill(
                            text = if (goalTargetMb >= 1000) {
                                String.format(Locale.US, "Goal: %.1f GB", goalTargetMb / 1000.0)
                            } else {
                                "Goal: $goalTargetMb MB"
                            },
                            accentColor = SpineEmerald
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            500 to "500 MB",
                            1000 to "1 GB",
                            5000 to "5 GB"
                        )
                        presets.forEach { (mb, label) ->
                            FilterChip(
                                selected = !showCustomSlider && goalTargetMb == mb,
                                onClick = {
                                    showCustomSlider = false
                                    onUpdateTargetMb(mb)
                                },
                                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SpineEmerald.copy(alpha = 0.22f),
                                    selectedLabelColor = SpineEmerald
                                ),
                                modifier = Modifier.testTag("goal_preset_${mb}mb")
                            )
                        }
                        FilterChip(
                            selected = showCustomSlider,
                            onClick = { showCustomSlider = true },
                            label = { Text("Custom", fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpineCyan.copy(alpha = 0.22f),
                                selectedLabelColor = SpineCyan
                            ),
                            modifier = Modifier.testTag("goal_preset_custom")
                        )
                    }

                    if (showCustomSlider) {
                        Slider(
                            value = goalTargetMb.coerceIn(50, 5000).toFloat(),
                            onValueChange = { onUpdateTargetMb(it.toInt()) },
                            valueRange = 50f..5000f,
                            colors = SliderDefaults.colors(
                                thumbColor = SpineEmerald,
                                activeTrackColor = SpineEmerald
                            ),
                            modifier = Modifier.testTag("goal_planner_slider")
                        )
                    }
                }
            }

            if (goalPlan != null) {
                Text(
                    text = "Safest Proposed Plan (Tap groups to include or exclude)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                goalPlan.tiers.forEach { tier ->
                    val isTierSelected = tier.tierNumber in selectedTierNumbers
                    val tierColor = when (tier.tierNumber) {
                        1 -> SpineBlue
                        2 -> SpineCoral
                        3 -> SpineCyan
                        else -> SpineAmber
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isTierSelected) CharcoalSurface else CharcoalSurface.copy(alpha = 0.55f),
                        border = BorderStroke(
                            width = if (isTierSelected) 1.5.dp else 1.dp,
                            color = if (isTierSelected) tierColor else CardBorderSlate
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTierNumbers = if (isTierSelected) {
                                    selectedTierNumbers - tier.tierNumber
                                } else {
                                    selectedTierNumbers + tier.tierNumber
                                }
                            }
                            .testTag("goal_tier_card_${tier.tierNumber}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isTierSelected) tierColor else ElevatedSlate,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isTierSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF041E15),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = tier.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${tier.photos.size} items • ${tier.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = LuminaViewModel.formatBytes(tier.bytesRecoverable),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isTierSelected) tierColor else TextMuted,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExecutePlanToVault(chosenPhotos)
                            onDismiss()
                        },
                        enabled = chosenPhotos.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("execute_goal_plan_button")
                    ) {
                        Text(
                            text = "Stage to Bin (${LuminaViewModel.formatBytes(chosenBytes)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            onReviewSelectedPhotos(chosenPhotos)
                            onDismiss()
                        },
                        enabled = chosenPhotos.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = SpineEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(52.dp)
                            .testTag("review_goal_plan_button")
                    ) {
                        Text(
                            text = "Review ${chosenPhotos.size} Items",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF041E15),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoForensicsDetailSheet(
    photo: PhotoEntity,
    onTriage: (TriageStatus) -> Unit,
    onInspectWithAi: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRoboLabForensics by remember { mutableStateOf(false) }
    var showFullscreenPhoto by remember(photo.stableIdentityKey) { mutableStateOf(false) }

    val shortReasons = remember(photo) {
        AiWorkflowsAndUsageEngine.formatUserFacingBestShotExplanations(photo)
    }

    if (showFullscreenPhoto && !photo.isVideo) {
        FullscreenZoomablePhotoDialog(
            photo = photo,
            onDismiss = { showFullscreenPhoto = false }
        )
    }

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
                        .height(340.dp),
                    autoPlay = false,
                    showStoryboard = true
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    PhotoThumbnailView(
                        photo = photo,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clickable { showFullscreenPhoto = true }
                            .testTag("detail_sheet_photo_preview")
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBg.copy(alpha = 0.84f),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clickable { showFullscreenPhoto = true }
                            .testTag("detail_sheet_fullscreen_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "View full image",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Full Image / Zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Human-Friendly Summary & Quality Card (Pass 3 Section N: no dHash/pHash/Laplacian here)
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
                            text = "Summary & Quality",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        ForensicPillBadge(
                            text = "Quality ${photo.overallQualityScore}/100",
                            accentColor = if (photo.overallQualityScore >= 70) SpineEmerald else SpineAmber
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shortReasons.forEach { reasonChip ->
                            ForensicPillBadge(
                                text = reasonChip,
                                accentColor = SpineEmerald
                            )
                        }
                    }
                    Text(
                        text = photo.humanFriendlyWhy,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    if (photo.aiDescription.isNotBlank()) {
                        Text(
                            text = AiWorkflowsAndUsageEngine.sanitizeReasonForNormalUi(photo.aiDescription),
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

    AlertDialog(
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
