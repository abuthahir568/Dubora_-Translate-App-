package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.DubbingConfig
import com.example.domain.model.DubbingSessionRecord
import com.example.domain.model.LanguageCapabilityRegistry
import com.example.domain.model.LocalMovie
import com.example.engine.DubbingRuntimeState
import com.example.ui.CloudUiState
import com.example.ui.components.DuboraLogoHeader
import com.example.ui.components.GlowingWaveform
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.ObsidianBlack
import java.io.File

@Composable
fun HomeScreen(
    userName: String,
    movies: List<LocalMovie>,
    config: DubbingConfig,
    runtimeState: DubbingRuntimeState,
    cloudHistoryState: CloudUiState<List<DubbingSessionRecord>>,
    onOpenMoviePicker: () -> Unit,
    onPlayMovie: (LocalMovie) -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onNavigateToDubbing: () -> Unit,
    onNavigateToMovies: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continueWatching = movies.filter { it.lastPositionMs > 0L }
    val recentlyPlayed = movies.take(5)
    val shortcuts = LanguageCapabilityRegistry.shortcutLanguages

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DuboraLogoHeader(badgeText = "CINEMA DUBBING ENGINE")
                Surface(
                    shape = CircleShape,
                    color = CharcoalCard,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.clickable(onClick = onNavigateToDubbing)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (runtimeState.isDubbingActive) EmeraldLive else CyanPulse)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LanguageCapabilityRegistry.findByCode(config.targetLangCode).code.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkPrimary
                        )
                    }
                }
            }
        }

        // Welcome Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CharcoalSurface)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_dubbing_studio),
                    contentDescription = "Dubora AI cinema studio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(228.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianBlack.copy(alpha = 0.25f),
                                    ObsidianBlack.copy(alpha = 0.85f),
                                    CharcoalSurface
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Text(
                        text = "Welcome back, $userName",
                        style = MaterialTheme.typography.labelLarge,
                        color = LuminousIris
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Live AI Movie Dubbing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open any MP4, MOV, or MKV on your device to watch with real-time Gemini speech translation & speaker-matched voices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onOpenMoviePicker,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricViolet,
                                contentColor = InkPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_open_movie_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Movie")
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CharcoalCard.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, CharcoalBorder),
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onNavigateToDubbing)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Translate, contentDescription = null, tint = CyanPulse)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Studio", style = MaterialTheme.typography.labelLarge, color = InkPrimary)
                            }
                        }
                    }
                }
            }
        }

        // AI Processing Status Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CharcoalSurface,
                border = BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToDubbing)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = LuminousIris,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Processing Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (runtimeState.isDubbingActive) EmeraldLive.copy(alpha = 0.16f)
                            else CharcoalElevated
                        ) {
                            Text(
                                text = runtimeState.stage.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (runtimeState.isDubbingActive) EmeraldLive else InkSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GlowingWaveform(
                        isActive = runtimeState.stage.isBusy || runtimeState.isSpeakingNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = runtimeState.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }

        // Language Shortcuts
        item {
            Column {
                Text(
                    text = "Quick Dub Target",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shortcuts.forEach { lang ->
                        val selected = config.targetLangCode == lang.code
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTargetLanguage(lang.code) },
                            label = {
                                Text("${lang.displayName} • ${lang.nativeName}")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                selectedLabelColor = InkPrimary,
                                containerColor = CharcoalSurface,
                                labelColor = InkSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = CharcoalBorder,
                                selectedBorderColor = ElectricViolet
                            )
                        )
                    }
                }
            }
        }

        // Continue Watching
        if (continueWatching.isNotEmpty()) {
            item {
                SectionHeader(title = "Continue Watching", actionLabel = "All Movies", onAction = onNavigateToMovies)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueWatching, key = { it.id }) { movie ->
                        MoviePosterCard(movie = movie, onClick = { onPlayMovie(movie) })
                    }
                }
            }
        }

        // Recently Played
        item {
            SectionHeader(title = "Recently Played", actionLabel = "Library", onAction = onNavigateToMovies)
        }
        if (recentlyPlayed.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_cinema),
                            contentDescription = "No movies yet",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your cinema queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap Open Movie above to pick an MP4, MOV, or MKV from your phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentlyPlayed, key = { "recent_${it.id}" }) { movie ->
                        MoviePosterCard(movie = movie, onClick = { onPlayMovie(movie) })
                    }
                }
            }
        }

        // Recent Dubbing History (Cloud Synced)
        item {
            SectionHeader(title = "Recent Dubbing History", actionLabel = null, onAction = {})
        }
        when (cloudHistoryState) {
            is CloudUiState.Loading -> {
                item {
                    Text(
                        text = "Syncing dubbing history...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }
            }

            is CloudUiState.Error -> {
                item {
                    Text(
                        text = cloudHistoryState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }

            is CloudUiState.Success -> {
                val records = cloudHistoryState.data.take(6)
                if (records.isEmpty()) {
                    item {
                        Text(
                            text = "No cloud-synced AI dubbing sessions yet. Start AI Dub inside the player to log speaker & language history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }
                } else {
                    items(records, key = { it.id }) { session ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CharcoalSurface,
                            border = BorderStroke(1.dp, CharcoalBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ElectricViolet.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = LuminousIris
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = session.movieTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = InkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${session.sourceLang.uppercase()} -> ${session.targetLang.uppercase()} • ${session.segmentsProcessed} cues • ${formatDurationMs(session.lastPositionMs)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = InkSecondary
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = CharcoalElevated
                                ) {
                                    Text(
                                        text = session.status.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPulse,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = InkPrimary)
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = LuminousIris,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MoviePosterCard(
    movie: LocalMovie,
    onClick: () -> Unit
) {
    val progress = if (movie.durationMs > 0L) {
        (movie.lastPositionMs.toFloat() / movie.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(CharcoalElevated)
            ) {
                if (!movie.thumbnailPath.isNullOrBlank() && File(movie.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(movie.thumbnailPath),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_cinema),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${movie.title}",
                        tint = InkPrimary
                    )
                }
                if (movie.durationMs > 0L) {
                    Surface(
                        color = ObsidianBlack.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatDurationMs(movie.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = ElectricViolet,
                    trackColor = CharcoalElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (movie.lastPositionMs > 0L) {
                        "Resume ${formatDurationMs(movie.lastPositionMs)}"
                    } else {
                        movie.fileName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
