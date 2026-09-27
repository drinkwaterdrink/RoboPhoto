package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.PhotoEntity
import com.example.domain.rules.NaturalLanguageRuleEngine
import com.example.domain.rules.RuleEvaluationPreview
import com.example.ui.components.ForensicPillBadge
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    uiState: LuminaUiState,
    onCreateRuleFromPrompt: (String) -> Unit,
    onPreviewCleanupPlan: (String) -> Unit,
    onToggleRule: (CleanupRuleEntity) -> Unit,
    onExecuteRule: (CleanupRuleEntity) -> Unit,
    onDeleteRule: (CleanupRuleEntity) -> Unit,
    onOpenMatchesInReview: (CleanupRuleEntity) -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var naturalLanguageInput by remember { mutableStateOf("") }
    var selectedAutomationIdForPreview by remember { mutableStateOf<Long?>(null) }

    val parsedPreview = remember(naturalLanguageInput) {
        if (naturalLanguageInput.isNotBlank()) {
            NaturalLanguageRuleEngine.parseNaturalLanguageToRule(naturalLanguageInput)
        } else null
    }

    val livePreviewEvaluation = remember(parsedPreview, uiState.allPhotos) {
        if (parsedPreview != null) {
            NaturalLanguageRuleEngine.evaluateRule(parsedPreview, uiState.allPhotos)
        } else null
    }

    val examplePrompts = remember {
        listOf(
            "Find screenshots older than 6 months that probably aren't important",
            "Screenshots older than 90 days except receipts and banking",
            "Videos over 100MB older than 6 months",
            "Blurry photos except faces and favorites",
            "Keep burst best shot, move others to review bin"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("rules_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header (Pass 3 Section K: "Automations" user-facing terminology)
        item(key = "automations_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Automations",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Plain-English cleanup rules evaluated locally with automatic safety exclusions. Every action is reversible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // 2. Natural-Language Automation & Plan Builder Card
        item(key = "automation_builder_card") {
            Surface(
                color = CharcoalSurface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "New Automation or Cleanup Plan",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = naturalLanguageInput,
                        onValueChange = { naturalLanguageInput = it },
                        placeholder = {
                            Text(
                                "e.g., Find screenshots older than 6 months that probably aren't important...",
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nl_rule_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CardBorderSlate,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = ElectricBlue
                        ),
                        maxLines = 3
                    )

                    // Quick Prompt Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        examplePrompts.forEachIndexed { idx, example ->
                            Surface(
                                color = ElevatedSlate,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, CardBorderSlate),
                                modifier = Modifier
                                    .clickable { naturalLanguageInput = example }
                                    .testTag("nl_example_chip_$idx")
                            ) {
                                Text(
                                    text = example,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Live Plain-English & Safety Preview
                    if (parsedPreview != null && livePreviewEvaluation != null) {
                        Surface(
                            color = ElevatedSlate,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, KeepEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
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
                                        text = parsedPreview.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = KeepEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ForensicPillBadge(
                                        text = "${livePreviewEvaluation.matchingPhotos.size} matches • ${LuminaViewModel.formatBytes(livePreviewEvaluation.recoverableBytes)}",
                                        accentColor = ElectricBlue
                                    )
                                }

                                Text(
                                    text = parsedPreview.compiledRuleSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )

                                Text(
                                    text = "Safety Exclusions: ${formatSafetyExclusionsSummary(parsedPreview)} (${livePreviewEvaluation.protectedByExclusions.size} protected)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpineAmber
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onPreviewCleanupPlan(naturalLanguageInput)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("preview_nl_plan_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = ElectricBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Preview Plan",
                                            color = ElectricBlue,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onCreateRuleFromPrompt(naturalLanguageInput)
                                            naturalLanguageInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricBlue,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_nl_rule_button")
                                    ) {
                                        Text(
                                            text = "Save Automation",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Active Automations Header
        item(key = "active_automations_title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured Automations (${uiState.cleanupRules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                ForensicPillBadge(
                    text = "Tap card for preview",
                    accentColor = SpineViolet
                )
            }
        }

        // 4. Automation Cards
        items(
            items = uiState.cleanupRules,
            key = { it.id }
        ) { rule ->
            val eval = uiState.ruleEvaluations.find { it.rule.id == rule.id } ?: RuleEvaluationPreview(
                rule = rule,
                matchedForCleanup = emptyList(),
                protectedByExclusions = emptyList(),
                totalRecoverableBytes = 0L
            )
            val isExpanded = selectedAutomationIdForPreview == rule.id
            AutomationCard(
                rule = rule,
                evaluation = eval,
                isExpanded = isExpanded,
                onToggleExpand = {
                    selectedAutomationIdForPreview = if (isExpanded) null else rule.id
                },
                onToggleEnabled = { onToggleRule(rule) },
                onOpenMatchesInReview = { onOpenMatchesInReview(rule) },
                onExecute = { onExecuteRule(rule) },
                onDelete = { onDeleteRule(rule) },
                onInspectPhoto = onInspectPhoto
            )
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

private fun formatSafetyExclusionsSummary(rule: CleanupRuleEntity): String {
    val parts = mutableListOf("Favorites & Protected", "Best Shots")
    if (rule.safetyExclusionsList.isNotEmpty()) {
        parts.add(rule.safetyExclusionsList.take(5).joinToString(", "))
    }
    return parts.joinToString(" • ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationCard(
    rule: CleanupRuleEntity,
    evaluation: RuleEvaluationPreview,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onOpenMatchesInReview: () -> Unit,
    onExecute: () -> Unit,
    onDelete: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit
) {
    val isProtectRule = rule.isProtectionRule
    val accentColor = if (isProtectRule) KeepEmerald else ElectricBlue
    var showCompiledStructuredRule by remember { mutableStateOf(false) }

    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (rule.isEnabled) accentColor.copy(alpha = if (isExpanded) 0.6f else 0.3f) else CardBorderSlate
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("automation_card_${rule.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Name + Plain-English Rule + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isProtectRule) Icons.Filled.Shield else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Plain-English rule: \"${rule.naturalLanguagePrompt}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    ),
                    modifier = Modifier.testTag("rule_toggle_${rule.id}")
                )
            }

            // Key Metadata Badges: Status, Last evaluated, Current matches, Recoverable storage
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ForensicPillBadge(
                    text = if (rule.isEnabled) "Enabled" else "Disabled",
                    accentColor = if (rule.isEnabled) KeepEmerald else TextMuted
                )
                ForensicPillBadge(
                    text = "Evaluated: ${rule.formattedLastEvaluated}",
                    accentColor = TextSecondary
                )
                ForensicPillBadge(
                    text = "${evaluation.matchingPhotos.size} current matches",
                    accentColor = accentColor
                )
                if (!isProtectRule && evaluation.recoverableBytes > 0L) {
                    ForensicPillBadge(
                        text = "${LuminaViewModel.formatBytes(evaluation.recoverableBytes)} recoverable",
                        accentColor = SpineAmber
                    )
                }
            }

            // Safety Exclusions Summary
            Surface(
                color = ElevatedSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.fillMaxWidth()
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
                            text = "Safety exclusions",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeepEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatSafetyExclusionsSummary(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (evaluation.protectedByExclusions.isNotEmpty()) {
                        ForensicPillBadge(
                            text = "${evaluation.protectedByExclusions.size} protected",
                            accentColor = KeepEmerald
                        )
                    }
                }
            }

            // Expand / Collapse indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Hide detailed preview" else "Tap to inspect matches & rule details",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse preview" else "Expand preview",
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Detailed Preview Section (Shown when tapped/expanded)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (evaluation.matchingPhotos.isNotEmpty()) {
                        Text(
                            text = "Current Matching Items (${evaluation.matchingPhotos.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = evaluation.matchingPhotos.take(12),
                                key = { it.stableIdentityKey }
                            ) { photo ->
                                PhotoThumbnailView(
                                    photo = photo,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clickable { onInspectPhoto(photo) }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No current photos match this automation right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // Advanced: View Compiled Structured Rule Toggle
                    Surface(
                        color = ElevatedSlate,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompiledStructuredRule = !showCompiledStructuredRule }
                            .testTag("toggle_compiled_rule_${rule.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Code,
                                        contentDescription = null,
                                        tint = SpineViolet,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Advanced: Compiled Structured Rule",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpineViolet,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (showCompiledStructuredRule) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            AnimatedVisibility(visible = showCompiledStructuredRule) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = rule.compiledRuleSummary,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "targetCategories=${rule.targetCategoriesCsv.ifBlank { "ANY" }} | minAgeDays=${rule.minAgeDays} | minSizeBytes=${rule.minFileSizeBytes} | maxSharpness=${rule.maxSharpnessScore ?: "NONE"} | onlyNonBestDuplicates=${rule.onlyNonBestDuplicates}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons: Review Matches in Swipe Deck / Stage to Review Bin (Reversible) / Delete Automation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_rule_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete automation",
                                tint = DestructiveRed
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (evaluation.matchingPhotos.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onOpenMatchesInReview,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("review_rule_matches_${rule.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Swipe,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Review ${evaluation.matchingPhotos.size}",
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (!isProtectRule && evaluation.matchingPhotos.isNotEmpty() && rule.isEnabled) {
                                Button(
                                    onClick = onExecute,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("execute_rule_${rule.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Stage ${evaluation.matchingPhotos.size} to Bin",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
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
