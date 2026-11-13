package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.DubbingConfig
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.SubtitleMode
import com.example.domain.model.TranslationQuality
import com.example.domain.model.VoiceGenderMode
import com.example.domain.model.VoiceStyle
import com.example.engine.ApiHealthCheckResult
import com.example.engine.DubbingErrorCategory
import com.example.engine.DubbingRuntimeState
import com.example.engine.PlatformBridgeStatus
import com.example.ui.StudioProbeState
import com.example.ui.components.GlowingWaveform
import com.example.ui.components.LanguagePickerCard
import com.example.ui.components.SearchableLanguageList
import com.example.ui.components.SliderRow
import com.example.ui.components.ToggleRow
import com.example.ui.theme.AmberBuffer
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseAlert

@Composable
fun AiDubbingScreen(
    config: DubbingConfig,
    runtimeState: DubbingRuntimeState,
    bridgeStatus: PlatformBridgeStatus,
    apiHealth: ApiHealthCheckResult,
    probeState: StudioProbeState,
    onUpdateConfig: ((DubbingConfig) -> DubbingConfig) -> Unit,
    onRunProbe: (String) -> Unit,
    onRetryProbe: () -> Unit,
    onRetryLiveDubbing: () -> Unit,
    onRefreshHealthCheck: () -> Unit,
    onPlayProbeAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var probeInput by remember {
        mutableStateOf("I thought we had more time before the storm hits the harbor.")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AI Dubbing Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Configure real-time Gemini speech recognition, speaker diarization, translation, and voice synthesis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }

        // Live pipeline card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = LuminousIris)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = runtimeState.stage.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                        }
                        Text(
                            text = "${runtimeState.segments.size} cues • ${runtimeState.detectedSpeakersCount} speakers",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GlowingWaveform(
                        isActive = runtimeState.stage.isBusy || runtimeState.isSpeakingNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = runtimeState.errorMessage ?: runtimeState.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (runtimeState.errorInfo != null) RoseAlert else InkSecondary
                    )
                    if (runtimeState.errorInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRetryLiveDubbing,
                            enabled = !runtimeState.isRetrying && !runtimeState.stage.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Live Dubbing")
                        }
                    }
                }
            }
        }

        // Language selectors
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language Pair", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (pickingSource) {
                        SearchableLanguageList(
                            title = "Select Original Language",
                            languages = LanguageCapabilityRegistry.sourceLanguageOptions,
                            selectedCode = config.sourceLangCode,
                            onSelect = { spec ->
                                onUpdateConfig { it.copy(sourceLangCode = spec.code) }
                                pickingSource = false
                            }
                        )
                    } else if (pickingTarget) {
                        SearchableLanguageList(
                            title = "Select Target Language",
                            languages = LanguageCapabilityRegistry.targetLanguageOptions,
                            selectedCode = config.targetLangCode,
                            onSelect = { spec ->
                                onUpdateConfig { it.copy(targetLangCode = spec.code) }
                                pickingTarget = false
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LanguagePickerCard(
                                label = "Original Dialogue",
                                value = LanguageCapabilityRegistry.findByCode(config.sourceLangCode).displayName,
                                code = config.sourceLangCode.uppercase(),
                                onClick = { pickingSource = true },
                                modifier = Modifier.weight(1f)
                            )
                            LanguagePickerCard(
                                label = "AI Target Language",
                                value = LanguageCapabilityRegistry.findByCode(config.targetLangCode).displayName,
                                code = config.targetLangCode.uppercase(),
                                onClick = { pickingTarget = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Voice & Mixing Controls
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Voice & Speaker Matching", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
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
                    Spacer(modifier = Modifier.height(10.dp))
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
                    SliderRow(
                        label = "Movie Chunk Window (3-hr Safe)",
                        valueText = "${config.lookaheadBufferSeconds.coerceIn(12, 30)}s",
                        value = config.lookaheadBufferSeconds.toFloat().coerceIn(12f, 30f),
                        range = 12f..30f,
                        onValueChange = { v -> onUpdateConfig { it.copy(lookaheadBufferSeconds = v.toInt()) } }
                    )
                }
            }
        }

        // Subtitles, Quality & Ducking
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio Mixing & Subtitles", style = MaterialTheme.typography.titleLarge, color = InkPrimary)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TranslationQuality.entries.forEach { q ->
                            FilterChip(
                                selected = config.translationQuality == q,
                                onClick = { onUpdateConfig { it.copy(translationQuality = q) } },
                                label = { Text(q.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                    selectedLabelColor = InkPrimary
                                )
                            )
                        }
                    }
                    Text(
                        text = "${config.translationQuality.modelId}: ${config.translationQuality.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPulse,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    ToggleRow(
                        title = "Preserve Original Background Music",
                        subtitle = "Retains soundtrack & ambient SFX between dubbed dialogue cues",
                        checked = config.preserveBackgroundMusic,
                        onCheckedChange = { c -> onUpdateConfig { it.copy(preserveBackgroundMusic = c) } }
                    )
                    ToggleRow(
                        title = "Reduce Original Dialogue (Ducking)",
                        subtitle = "Lowers original track volume while AI speaker speaks",
                        checked = config.reduceOriginalDialogue,
                        onCheckedChange = { c -> onUpdateConfig { it.copy(reduceOriginalDialogue = c) } }
                    )
                }
            }
        }

        // Live Gemini Dubbing Verification Probe
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = CyanPulse)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test Gemini Dubbing & Voice",
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Verify live Gemini translation + on-device voice synthesis before starting a movie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = probeInput,
                        onValueChange = { probeInput = it },
                        label = { Text("Sample dialogue line") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricViolet,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalElevated,
                            unfocusedContainerColor = CharcoalElevated,
                            focusedTextColor = InkPrimary,
                            unfocusedTextColor = InkPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("probe_dialogue_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onRunProbe(probeInput) },
                        enabled = probeState !is StudioProbeState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_dub_probe_button")
                    ) {
                        if (probeState is StudioProbeState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = InkPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (probeState.isRetrying) {
                                    "Retrying (${probeState.retryAttempt}/${probeState.maxRetries})..."
                                } else {
                                    "Translating & Synthesizing..."
                                }
                            )
                        } else {
                            Icon(Icons.Rounded.Translate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Translate & Speak Cue")
                        }
                    }

                    when (probeState) {
                        is StudioProbeState.Loading -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CharcoalCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (probeState.isRetrying) AmberBuffer.copy(alpha = 0.65f) else CharcoalBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("probe_loading_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = if (probeState.isRetrying) AmberBuffer else CyanPulse,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = probeState.statusMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = InkPrimary
                                        )
                                    }
                                    if (probeState.isRetrying) {
                                        LinearProgressIndicator(
                                            progress = {
                                                (probeState.retryAttempt.toFloat() / probeState.maxRetries.coerceAtLeast(1).toFloat())
                                                    .coerceIn(0.1f, 1f)
                                            },
                                            color = AmberBuffer,
                                            trackColor = CharcoalElevated,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        is StudioProbeState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CharcoalCard,
                                border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${probeState.result.diarized.speakerLabel} (${probeState.result.targetLangCode.uppercase()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldLive
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = probeState.result.translatedText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = InkPrimary
                                    )
                                    if (probeState.audioPath != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onPlayProbeAudio(probeState.audioPath) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalElevated),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CyanPulse)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Play Synthesized Voice", color = InkPrimary)
                                        }
                                    }
                                }
                            }
                        }

                        is StudioProbeState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CharcoalCard,
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("probe_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.WarningAmber,
                                            contentDescription = "Dubbing error",
                                            tint = RoseAlert,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = probeState.errorInfo.category.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = RoseAlert
                                        )
                                    }
                                    Text(
                                        text = probeState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkPrimary
                                    )
                                    Text(
                                        text = probeState.errorInfo.actionableHint,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkSecondary
                                    )
                                    Text(
                                        text = "API: ${probeState.errorInfo.apiName} • Model: ${probeState.errorInfo.modelId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPulse
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = onRetryProbe,
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("probe_retry_button")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Dubbing Request")
                                    }
                                }
                            }
                        }

                        StudioProbeState.Idle -> Unit
                    }
                }
            }
        }

        // Separate API Configuration, Quota & Media Bridge Health Card
        item {
            val runtimeErr = runtimeState.errorInfo ?: (probeState as? StudioProbeState.Error)?.errorInfo
            val quotaStatusText = when (runtimeErr?.category) {
                DubbingErrorCategory.QUOTA_EXCEEDED -> "Exhausted: ${runtimeErr.actionableHint}"
                DubbingErrorCategory.RATE_LIMITED_429 -> "Rate limited (429): Temporary request spike"
                DubbingErrorCategory.OVERLOADED_503 -> "Server overloaded (503): Temporary demand spike"
                else -> "Ready (no quota or rate-limit blocks detected)"
            }
            val configStatusText = if (apiHealth.isConfigHealthy && runtimeErr?.category != DubbingErrorCategory.CONFIGURATION_ERROR) {
                "Healthy (${apiHealth.appCheckMode})"
            } else {
                runtimeErr?.takeIf { it.category == DubbingErrorCategory.CONFIGURATION_ERROR }?.actionableHint
                    ?: apiHealth.configurationIssue
                    ?: "Configuration attention required"
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("API Config, Quota & Media Bridge", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                        Icon(
                            imageVector = if (apiHealth.isConfigHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = "API configuration status",
                            tint = if (apiHealth.isConfigHealthy) EmeraldLive else RoseAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text("AI API: ${apiHealth.apiProvider}", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                    Text("Active Model: ${apiHealth.activeModelId} (Fast tier: ${apiHealth.fallbackModelId})", style = MaterialTheme.typography.bodyMedium, color = CyanPulse)
                    Text("API Configuration: $configStatusText", style = MaterialTheme.typography.bodyMedium, color = if (apiHealth.isConfigHealthy) EmeraldLive else RoseAlert)
                    Text("Quota & Demand Status: $quotaStatusText", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    Text("Platform Bridge: ${bridgeStatus.platform} • ${bridgeStatus.playerEngine}", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    Text("Mixing: ${bridgeStatus.stemSeparationStatus}", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    OutlinedButton(
                        onClick = onRefreshHealthCheck,
                        border = BorderStroke(1.dp, CharcoalBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-check API Config & App Check", color = InkPrimary)
                    }
                }
            }
        }
    }
}
