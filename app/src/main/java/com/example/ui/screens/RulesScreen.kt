package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CleanupRuleEntity
import com.example.data.local.PhotoEntity
import com.example.domain.rules.RuleEvaluationPreview
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.components.SpineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpineAmber
import com.example.ui.theme.SpineCoral
import com.example.ui.theme.SpineCyan
import com.example.ui.theme.SpineEmerald
import com.example.ui.theme.SpineViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@Composable
fun RulesScreen(
    uiState: LuminaUiState,
    onCreateRule: (String) -> Unit,
    onToggleRule: (CleanupRuleEntity) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onApproveAndExecuteRule: (List<PhotoEntity>, String) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var naturalPromptInput by remember { mutableStateOf("") }

    val examplePrompts = listOf(
        "Screenshots older than 90 days can be suggested for deletion unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers.",
        "Find screenshots older than six months that don't contain anything important",
        "Show me duplicate cannabis plant photos from August excluding best shots"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("rules_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Natural Language Rule Composer Card
        item {
            SpineCard(
                spineColor = SpineViolet,
                containerColor = CharcoalSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = SpineViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "AI Cleanup Rules Engine",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Define what 'junk' means in plain English; Lumina compiles executable database filters & safety exclusions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = naturalPromptInput,
                        onValueChange = { naturalPromptInput = it },
                        placeholder = {
                            Text(
                                "e.g., Screenshots older than 90 days unless they contain receipts, passwords, addresses, or confirmation numbers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpineViolet,
                            unfocusedBorderColor = CardBorderSlate,
                            focusedContainerColor = ElevatedSlate,
                            unfocusedContainerColor = ElevatedSlate
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("natural_rule_input")
                    )

                    // Quick Prompt Presets
                    Text(
                        text = "Tap a natural-language template:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        examplePrompts.forEachIndexed { idx, sample ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElevatedSlate,
                                border = BorderStroke(1.dp, CardBorderSlate),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { naturalPromptInput = sample }
                                    .testTag("preset_rule_chip_$idx")
                            ) {
                                Text(
                                    text = "\"$sample\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpineCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (naturalPromptInput.isNotBlank()) {
                                onCreateRule(naturalPromptInput)
                                naturalPromptInput = ""
                            }
                        },
                        enabled = naturalPromptInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SpineViolet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("compile_rule_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Compile into Reusable Cleanup Rule",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Compiled Rules & Live Database Previews
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Compiled Rules & Safety Previews",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                TelemetryPill(
                    text = "${uiState.ruleEvaluations.size} Rules",
                    accentColor = SpineViolet
                )
            }
        }

        items(uiState.ruleEvaluations, key = { it.rule.id }) { preview ->
            CompiledRulePreviewCard(
                preview = preview,
                onToggle = { onToggleRule(preview.rule) },
                onDelete = { onDeleteRule(preview.rule.id) },
                onExecuteToVault = {
                    onApproveAndExecuteRule(preview.matchedForCleanup, preview.rule.title)
                },
                onPhotoClick = onPhotoClick
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompiledRulePreviewCard(
    preview: RuleEvaluationPreview,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onExecuteToVault: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit
) {
    val rule = preview.rule
    val exclusions = rule.excludedKeywordsCsv.split(",").filter { it.isNotBlank() }
    val categories = rule.targetCategoriesCsv.split(",").filter { it.isNotBlank() }
    val required = rule.requiredKeywordsCsv.split(",").filter { it.isNotBlank() }

    SpineCard(
        spineColor = if (rule.isEnabled) SpineViolet else CardBorderSlate,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compiled_rule_card_${rule.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\"${rule.rawNaturalPrompt}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = SpineViolet)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Rule",
                            tint = TextMuted
                        )
                    }
                }
            }

            // Parsed AST Parameter Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (rule.minAgeDays > 0) {
                    TelemetryPill("Age > ${rule.minAgeDays}d", SpineAmber)
                }
                categories.forEach { cat ->
                    TelemetryPill("Category: $cat", SpineCyan)
                }
                if (rule.onlyNonBestDuplicates) {
                    TelemetryPill("Duplicates (Keep Best Shot)", SpineEmerald)
                }
                required.forEach { req ->
                    TelemetryPill("Match: '$req'", SpineViolet)
                }
                exclusions.forEach { exc ->
                    TelemetryPill("Unless: '$exc'", SpineEmerald)
                }
            }

            // Matched for Cleanup Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElevatedSlate,
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggested for Cleanup (${preview.matchedForCleanup.size} items)",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpineCoral
                        )
                        TelemetryPill(
                            text = "Recover ${LuminaViewModel.formatBytes(preview.totalRecoverableBytes)}",
                            accentColor = SpineCoral
                        )
                    }

                    if (preview.matchedForCleanup.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            preview.matchedForCleanup.forEach { photo ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable { onPhotoClick(photo) },
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    PhotoThumbnailView(
                                        photo = photo,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(68.dp),
                                        cornerRadius = 8.dp
                                    )
                                    Text(
                                        text = photo.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "All matching items have already been staged to the Quarantine Vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // Protected by Exclusions Strip (Receipts, Passwords, Confirmations, Best Shots)
            if (preview.protectedByExclusions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SpineEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SpineEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = SpineEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Protected by Rule Exclusions (${preview.protectedByExclusions.size} kept safe)",
                                style = MaterialTheme.typography.labelLarge,
                                color = SpineEmerald
                            )
                        }
                        preview.protectedByExclusions.take(4).forEach { (protectedPhoto, reason) ->
                            Text(
                                text = "• ${protectedPhoto.title} — $reason",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.clickable { onPhotoClick(protectedPhoto) }
                            )
                        }
                    }
                }
            }

            if (preview.matchedForCleanup.isNotEmpty()) {
                Button(
                    onClick = onExecuteToVault,
                    colors = ButtonDefaults.buttonColors(containerColor = SpineCoral),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("execute_rule_${rule.id}")
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Approve Rule & Stage ${preview.matchedForCleanup.size} Items (${LuminaViewModel.formatBytes(preview.totalRecoverableBytes)}) to Vault",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
