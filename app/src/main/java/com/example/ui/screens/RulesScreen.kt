package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.ui.components.ForensicPillBadge
import com.example.ui.components.PhotoThumbnailView
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DestructiveRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.KeepEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LuminaUiState
import com.example.ui.viewmodel.LuminaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    uiState: LuminaUiState,
    onCreateRule: (String) -> Unit,
    onToggleRule: (CleanupRuleEntity) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onExecuteRulePreview: (RuleEvaluationPreview) -> Unit,
    onOpenGoalPlanner: () -> Unit,
    onOpenByokSheet: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var promptInput by remember { mutableStateOf("") }

    val quickTemplates = listOf(
        "Delete screenshots older than 90 days",
        "Clean videos larger than 50 MB",
        "Remove blurry photos under 40 sharpness",
        "Never delete receipts or documents"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "automations_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automations",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Smart cleanup rules & storage goal planner",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenGoalPlanner,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderSlate),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("rules_goal_planner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Goal Plan", color = TextPrimary)
                    }

                    IconButton(
                        onClick = onOpenByokSheet,
                        modifier = Modifier.testTag("rules_ai_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "AI & RoboLab Settings",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // Create New Automation Card
        item(key = "automation_builder_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CardBorderSlate)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Create Smart Rule",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = {
                            Text(
                                text = "e.g., Move screenshots older than 60 days to Review Bin",
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ElevatedSlate,
                            unfocusedContainerColor = ElevatedSlate,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CardBorderSlate,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("natural_language_rule_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTemplates.forEach { preset ->
                            FilterChip(
                                selected = promptInput == preset,
                                onClick = { promptInput = preset },
                                label = {
                                    Text(
                                        text = preset,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ElevatedSlate,
                                    labelColor = TextSecondary,
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.18f),
                                    selectedLabelColor = ElectricBlue
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = promptInput == preset,
                                    borderColor = CardBorderSlate,
                                    selectedBorderColor = ElectricBlue.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                onCreateRule(promptInput)
                                promptInput = ""
                            }
                        },
                        enabled = promptInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_rule_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add Automation Rule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Rules List
        items(
            items = uiState.ruleEvaluations,
            key = { it.rule.id }
        ) { evaluation ->
            AutomationRuleCard(
                evaluation = evaluation,
                onToggle = { onToggleRule(evaluation.rule) },
                onDelete = { onDeleteRule(evaluation.rule.id) },
                onExecute = { onExecuteRulePreview(evaluation) },
                onInspectPhoto = onInspectPhoto
            )
        }
    }
}

@Composable
private fun AutomationRuleCard(
    evaluation: RuleEvaluationPreview,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onExecute: () -> Unit,
    onInspectPhoto: (PhotoEntity) -> Unit
) {
    val rule = evaluation.rule
    val isProtectRule = rule.isProtectionRule

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CardBorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        ForensicPillBadge(
                            text = if (isProtectRule) "Safety Guard" else "${evaluation.matchingPhotos.size} matches",
                            accentColor = if (isProtectRule) KeepEmerald else ElectricBlue
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\"${rule.naturalLanguagePrompt}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ElectricBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = ElevatedSlate
                    )
                )
            }

            if (evaluation.matchingPhotos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = evaluation.matchingPhotos.take(8),
                        key = { it.stableIdentityKey }
                    ) { photo ->
                        PhotoThumbnailView(
                            photo = photo,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { onInspectPhoto(photo) }
                        )
                    }
                }
            }

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
                        contentDescription = "Delete rule",
                        tint = TextMuted
                    )
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
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Move ${evaluation.matchingPhotos.size} (${LuminaViewModel.formatBytes(evaluation.recoverableBytes)}) to Bin",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
