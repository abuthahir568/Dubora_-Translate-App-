package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.LocalMovie
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseAlert

@Composable
fun DownloadsScreen(
    downloadedDubs: List<LocalMovie>,
    onPlayMovie: (LocalMovie) -> Unit,
    onClearDubCache: (LocalMovie) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Downloads & Dub Cache",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Offline AI-translated dialogue cues and synthesized voice tracks stored locally on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }

        if (downloadedDubs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ElectricViolet.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = CyanPulse,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Offline AI Dubs Cached Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When you watch a movie with AI Dub enabled, translated dialogue cues and WAV voice tracks are automatically cached here for instant offline playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }
            }
        } else {
            items(downloadedDubs, key = { it.id }) { movie ->
                val langName = movie.cachedDubLang?.let {
                    LanguageCapabilityRegistry.findByCode(it).displayName
                } ?: "AI Dub"

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(EmeraldLive.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = EmeraldLive
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$langName • ${formatDurationMs(movie.durationMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyanPulse
                            )
                        }
                        Button(
                            onClick = { onPlayMovie(movie) },
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("play_offline_dub_${movie.id}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = InkPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", color = InkPrimary)
                        }
                        IconButton(onClick = { onClearDubCache(movie) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete dub cache",
                                tint = RoseAlert
                            )
                        }
                    }
                }
            }
        }
    }
}
