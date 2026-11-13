package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.LanguageSpec
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.NeonAmethyst
import kotlin.math.sin

@Composable
fun GlowingWaveform(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 26
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val step = size.width / barCount
        for (i in 0 until barCount) {
            val amp = if (isActive) {
                0.22f + 0.72f * ((sin(phase + i * 0.52f) + 1f) / 2f)
            } else {
                0.15f + 0.12f * ((sin(i * 0.6f) + 1f) / 2f)
            }
            val barH = size.height * amp
            val x = i * step + step / 2f
            drawLine(
                brush = Brush.verticalGradient(
                    if (isActive) listOf(CyanPulse, ElectricViolet, NeonAmethyst)
                    else listOf(CharcoalBorder, ElectricViolet.copy(alpha = 0.4f))
                ),
                start = Offset(x, size.height / 2f - barH / 2f),
                end = Offset(x, size.height / 2f + barH / 2f),
                strokeWidth = (step * 0.45f).coerceIn(3f, 7f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun DuboraLogoHeader(
    badgeText: String = "AI STUDIO",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ElectricViolet, NeonAmethyst)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Dubora AI logo",
                tint = InkPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "DUBORA AI",
                style = MaterialTheme.typography.titleLarge,
                color = InkPrimary
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = LuminousIris
            )
        }
    }
}

@Composable
fun SearchableLanguageList(
    title: String,
    languages: List<LanguageSpec>,
    selectedCode: String,
    onSelect: (LanguageSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(languages, query) {
        if (query.isBlank()) languages
        else languages.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.nativeName.contains(query, ignoreCase = true) ||
                it.code.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = InkPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search language or ISO code...") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = InkSecondary)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = CharcoalBorder,
                focusedContainerColor = CharcoalElevated,
                unfocusedContainerColor = CharcoalElevated,
                focusedTextColor = InkPrimary,
                unfocusedTextColor = InkPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("language_search_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            items(filtered, key = { it.code }) { lang ->
                val isSelected = lang.code == selectedCode
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) ElectricViolet.copy(alpha = 0.22f) else CharcoalCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (isSelected) ElectricViolet else CharcoalBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(lang) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LuminousIris
                                )
                            }
                            Text(
                                text = "${lang.code.uppercase()} • ${lang.regionTag} • Gemini Live + Speaker ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(ElectricViolet),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "No supported languages match \"$query\" in the configured Gemini provider registry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, mins, secs)
    else "%02d:%02d".format(mins, secs)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        "%.2f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}

fun LanguageSpecLabel(code: String): String {
    val spec = LanguageCapabilityRegistry.findByCode(code)
    return "${spec.displayName} (${spec.code.uppercase()})"
}
