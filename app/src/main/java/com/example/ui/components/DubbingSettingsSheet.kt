package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.domain.model.DubbingConfig
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.SubtitleMode
import com.example.domain.model.TranslationQuality
import com.example.domain.model.VoiceGenderMode
import com.example.domain.model.VoiceStyle
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubbingSettingsSheet(
    config: DubbingConfig,
    isDubbingActive: Boolean,
    onUpdateConfig: ((DubbingConfig) -> DubbingConfig) -> Unit,
    onStartOrApplyDubbing: () -> Unit,
    onStopDubbing: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        contentColor = InkPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = LuminousIris
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Dubbing Studio",
                            style = MaterialTheme.typography.headlineSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "Gemini Live Audio Pipeline & Voice Mixing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close dubbing settings", tint = InkSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pickingSource) {
                SearchableLanguageList(
                    title = "Original Movie Language",
                    languages = LanguageCapabilityRegistry.sourceLanguageOptions,
                    selectedCode = config.sourceLangCode,
                    onSelect = { spec ->
                        onUpdateConfig { it.copy(sourceLangCode = spec.code) }
                        pickingSource = false
                    }
                )
                return@ModalBottomSheet
            }

            if (pickingTarget) {
                SearchableLanguageList(
                    title = "Target AI Dubbing Language",
                    languages = LanguageCapabilityRegistry.targetLanguageOptions,
                    selectedCode = config.targetLangCode,
                    onSelect = { spec ->
                        onUpdateConfig { it.copy(targetLangCode = spec.code) }
                        pickingTarget = false
                    }
                )
                return@ModalBottomSheet
            }

            // Language pair selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LanguagePickerCard(
                    label = "Original Audio",
                    value = LanguageCapabilityRegistry.findByCode(config.sourceLangCode).displayName,
                    code = config.sourceLangCode.uppercase(),
                    onClick = { pickingSource = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_source_language")
                )
                LanguagePickerCard(
                    label = "AI Dub Into",
                    value = LanguageCapabilityRegistry.findByCode(config.targetLangCode).displayName,
                    code = config.targetLangCode.uppercase(),
                    onClick = { pickingTarget = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_target_language")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text("Voice Profile", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
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

            Spacer(modifier = Modifier.height(10.dp))

            Text("Voice Style", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceStyle.entries.forEach { style ->
                    FilterChip(
                        selected = config.voiceStyle == style,
                        onClick = { onUpdateConfig { it.copy(voiceStyle = style) } },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                            selectedLabelColor = InkPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SliderRow(
                label = "Voice Speed",
                valueText = "%.2fx".format(config.voiceSpeed),
                value = config.voiceSpeed,
                range = 0.75f..1.45f,
                onValueChange = { v -> onUpdateConfig { it.copy(voiceSpeed = v) } }
            )

            SliderRow(
                label = "Voice Pitch",
                valueText = "%.2f".format(config.voicePitch),
                value = config.voicePitch,
                range = 0.75f..1.35f,
                onValueChange = { v -> onUpdateConfig { it.copy(voicePitch = v) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Subtitles", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubtitleMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.subtitleMode == mode,
                        onClick = { onUpdateConfig { it.copy(subtitleMode = mode) } },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                            selectedLabelColor = InkPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Translation Quality & Latency", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TranslationQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = config.translationQuality == quality,
                        onClick = { onUpdateConfig { it.copy(translationQuality = quality) } },
                        label = { Text(quality.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                            selectedLabelColor = InkPrimary
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${config.translationQuality.modelId}: ${config.translationQuality.description}",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPulse
            )

            Spacer(modifier = Modifier.height(14.dp))

            ToggleRow(
                title = "Preserve Background Music & SFX",
                subtitle = "Uses smart audio ducking when dubbed dialogue speaks (stem isolation requires external Demucs server)",
                checked = config.preserveBackgroundMusic,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(preserveBackgroundMusic = checked) }
                }
            )

            ToggleRow(
                title = "Reduce Original Dialogue Volume",
                subtitle = "Ducks original movie audio to ${(config.originalDialogueDuckLevel * 100).toInt()}% during AI speech",
                checked = config.reduceOriginalDialogue,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(reduceOriginalDialogue = checked) }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isDubbingActive) {
                    Button(
                        onClick = {
                            onStopDubbing()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharcoalCard,
                            contentColor = InkPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("Stop AI Dub")
                    }
                }
                Button(
                    onClick = {
                        onStartOrApplyDubbing()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = InkPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("apply_ai_dub_button")
                ) {
                    Icon(Icons.Rounded.Translate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isDubbingActive) "Apply & Restart Dub" else "Start Live AI Dub")
                }
            }
        }
    }
}

@Composable
fun LanguagePickerCard(
    label: String,
    value: String,
    code: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CharcoalCard,
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                Text(code, style = MaterialTheme.typography.labelSmall, color = CyanPulse)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = LuminousIris)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = LuminousIris,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = InkPrimary,
                checkedTrackColor = ElectricViolet,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}
