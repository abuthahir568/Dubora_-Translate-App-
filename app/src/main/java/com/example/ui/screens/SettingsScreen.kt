package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DubbingConfig
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.SubtitleMode
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.VoiceGenderMode
import com.example.ui.components.LanguagePickerCard
import com.example.ui.components.SearchableLanguageList
import com.example.ui.components.SliderRow
import com.example.ui.components.ToggleRow
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseAlert

@Composable
fun SettingsScreen(
    userDisplayName: String,
    userEmail: String,
    config: DubbingConfig,
    onUpdateConfig: ((DubbingConfig) -> DubbingConfig) -> Unit,
    onClearHistory: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingDefaultTarget by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = InkPrimary
            )
        }

        // Account Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = LuminousIris)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(userDisplayName, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                            Text(
                                userEmail.ifBlank { "Google Account Connected" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, CharcoalBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            tint = InkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", color = InkPrimary)
                    }
                }
            }
        }

        // Default Language & Voice
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Dubbing Preferences", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (pickingDefaultTarget) {
                        SearchableLanguageList(
                            title = "Default Target Language",
                            languages = LanguageCapabilityRegistry.targetLanguageOptions,
                            selectedCode = config.targetLangCode,
                            onSelect = { spec ->
                                onUpdateConfig { it.copy(targetLangCode = spec.code) }
                                pickingDefaultTarget = false
                            }
                        )
                    } else {
                        LanguagePickerCard(
                            label = "Default Target Language",
                            value = LanguageCapabilityRegistry.findByCode(config.targetLangCode).displayName,
                            code = config.targetLangCode.uppercase(),
                            onClick = { pickingDefaultTarget = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default AI Voice", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceGenderMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.voiceGenderMode == mode,
                                onClick = { onUpdateConfig { it.copy(voiceGenderMode = mode) } },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                    selectedLabelColor = InkPrimary
                                )
                            )
                        }
                    }

                    ToggleRow(
                        title = "Auto Dubbing on Movie Open",
                        subtitle = "Automatically launch Gemini live dubbing when starting playback",
                        checked = config.autoDubbing,
                        onCheckedChange = { c -> onUpdateConfig { it.copy(autoDubbing = c) } }
                    )
                }
            }
        }

        // Subtitle Appearance Studio
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subtitle Appearance", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Live preview box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalElevated)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = ObsidianBlack.copy(alpha = config.subtitleBgOpacity),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (config.subtitleMode == SubtitleMode.ORIGINAL || config.subtitleMode == SubtitleMode.DUAL) {
                                    Text(
                                        text = "We meet again at midnight.",
                                        fontSize = (config.subtitleFontSizeSp - 2f).coerceAtLeast(11f).sp,
                                        color = InkSecondary
                                    )
                                }
                                if (config.subtitleMode == SubtitleMode.AI_TRANSLATED || config.subtitleMode == SubtitleMode.DUAL) {
                                    Text(
                                        text = "Nos encontramos de nuevo a medianoche.",
                                        fontSize = config.subtitleFontSizeSp.sp,
                                        color = InkPrimary
                                    )
                                }
                                if (config.subtitleMode == SubtitleMode.OFF) {
                                    Text(
                                        text = "Subtitles hidden",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SliderRow(
                        label = "Subtitle Font Size",
                        valueText = "${config.subtitleFontSizeSp.toInt()} sp",
                        value = config.subtitleFontSizeSp,
                        range = 12f..24f,
                        onValueChange = { v -> onUpdateConfig { it.copy(subtitleFontSizeSp = v) } }
                    )
                    SliderRow(
                        label = "Subtitle Background Opacity",
                        valueText = "${(config.subtitleBgOpacity * 100).toInt()}%",
                        value = config.subtitleBgOpacity,
                        range = 0f..0.95f,
                        onValueChange = { v -> onUpdateConfig { it.copy(subtitleBgOpacity = v) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Subtitle Position", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SubtitlePosition.entries.forEach { pos ->
                            FilterChip(
                                selected = config.subtitlePosition == pos,
                                onClick = { onUpdateConfig { it.copy(subtitlePosition = pos) } },
                                label = { Text(pos.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                    selectedLabelColor = InkPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Data Usage & Privacy
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Network & Privacy", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    ToggleRow(
                        title = "Wi-Fi Only AI Processing",
                        subtitle = "Pause live Gemini audio chunk streaming on cellular networks",
                        checked = config.wifiOnlyProcessing,
                        onCheckedChange = { c -> onUpdateConfig { it.copy(wifiOnlyProcessing = c) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = CharcoalCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = CyanPulse)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Privacy Guarantee: Your video files remain strictly on your device and are never uploaded to cloud storage. Only small audio dialogue chunks are processed for translation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseAlert.copy(alpha = 0.16f),
                            contentColor = RoseAlert
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Dubbing History & Local Cache")
                    }
                }
            }
        }
    }
}
