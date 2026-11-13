package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.domain.model.AudioTrackMode
import com.example.domain.model.DubbingConfig
import com.example.domain.model.LocalMovie
import com.example.domain.model.SubtitleMode
import com.example.domain.model.SubtitlePosition
import com.example.engine.DubbingRuntimeState
import com.example.engine.GeminiDubbingEngine
import com.example.ui.components.DubbingSettingsSheet
import com.example.ui.components.GlowingWaveform
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.AmberBuffer
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseAlert
import kotlinx.coroutines.delay

@Composable
fun MoviePlayerScreen(
    movie: LocalMovie,
    config: DubbingConfig,
    runtimeState: DubbingRuntimeState,
    onAttachPlayer: (ExoPlayer) -> Unit,
    onUpdateConfig: ((DubbingConfig) -> DubbingConfig) -> Unit,
    onStartDubbing: () -> Unit,
    onRetryDubbing: () -> Unit,
    onSwitchToFastStream: () -> Unit = {},
    onStopDubbing: () -> Unit,
    onSwitchAudioTrack: (AudioTrackMode) -> Unit,
    onSetMasterVolume: (Float) -> Unit,
    onSavePosition: (String, Long) -> Unit,
    onClosePlayer: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(movie.lastPositionMs) }
    var totalDurationMs by remember { mutableLongStateOf(movie.durationMs.coerceAtLeast(1L)) }
    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }
    var showDubSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var masterVolume by remember { mutableFloatStateOf(1.0f) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(movie.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(movie.uriString)))
            prepare()
            if (movie.lastPositionMs > 0L) {
                seekTo(movie.lastPositionMs)
            }
            playWhenReady = true
        }
    }

    LaunchedEffect(exoPlayer) {
        onAttachPlayer(exoPlayer)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.duration > 0L) {
                    totalDurationMs = exoPlayer.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "Playback failed (${error.errorCodeName}): ${error.localizedMessage ?: "Unsupported video or audio codec."}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSavePosition(movie.id, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) {
                totalDurationMs = exoPlayer.duration
            }
            delay(250L)
        }
    }

    LaunchedEffect(showControls, isPlaying, isControlsLocked) {
        if (showControls && isPlaying && !isControlsLocked && !showDubSettingsSheet) {
            delay(4500L)
            showControls = false
        }
    }

    BackHandler {
        onClosePlayer(exoPlayer.currentPosition)
    }

    val activeSubtitleSegment = remember(runtimeState.segments, currentPosMs) {
        runtimeState.segments.firstOrNull { currentPosMs in it.startMs..it.endMs }
            ?: runtimeState.currentSegment
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isControlsLocked) {
                    showControls = !showControls
                } else {
                    showControls = true
                }
            }
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Live Subtitles Layer
        if (config.subtitleMode != SubtitleMode.OFF && activeSubtitleSegment != null) {
            val subtitleAlignment = when (config.subtitlePosition) {
                SubtitlePosition.TOP -> Alignment.TopCenter
                SubtitlePosition.BOTTOM -> Alignment.BottomCenter
                SubtitlePosition.CINEMA_BAR -> Alignment.BottomCenter
            }
            val bottomPad = when (config.subtitlePosition) {
                SubtitlePosition.TOP -> 78.dp
                SubtitlePosition.BOTTOM -> if (showControls) 146.dp else 36.dp
                SubtitlePosition.CINEMA_BAR -> if (showControls) 168.dp else 58.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(
                        horizontal = 24.dp,
                        vertical = bottomPad
                    ),
                contentAlignment = subtitleAlignment
            ) {
                Surface(
                    color = ObsidianBlack.copy(alpha = config.subtitleBgOpacity),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CharcoalBorder.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeSubtitleSegment.speakerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = LuminousIris
                        )
                        if (config.subtitleMode == SubtitleMode.ORIGINAL || config.subtitleMode == SubtitleMode.DUAL) {
                            Text(
                                text = activeSubtitleSegment.originalText,
                                fontSize = (config.subtitleFontSizeSp - 2f).coerceAtLeast(11f).sp,
                                color = InkSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (config.subtitleMode == SubtitleMode.AI_TRANSLATED || config.subtitleMode == SubtitleMode.DUAL) {
                            Text(
                                text = activeSubtitleSegment.translatedText,
                                fontSize = config.subtitleFontSizeSp.sp,
                                color = InkPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Buffering / Retry / Pipeline Status Pill
        if ((runtimeState.isDubbingActive || runtimeState.isRetrying) &&
            (runtimeState.isBufferingDub || runtimeState.isRetrying || runtimeState.stage.isBusy)
        ) {
            val pillBorderColor = if (runtimeState.isRetrying || runtimeState.isBufferingDub) {
                AmberBuffer.copy(alpha = 0.75f)
            } else {
                ElectricViolet.copy(alpha = 0.6f)
            }
            val pillText = when {
                runtimeState.isRetrying -> runtimeState.statusDetail
                runtimeState.isBufferingDub -> "Buffering synchronized AI dub..."
                else -> runtimeState.stage.label
            }
            Surface(
                color = ObsidianBlack.copy(alpha = 0.88f),
                shape = CircleShape,
                border = BorderStroke(1.dp, pillBorderColor),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 62.dp)
                    .testTag("player_dubbing_loading_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = if (runtimeState.isRetrying || runtimeState.isBufferingDub) AmberBuffer else CyanPulse,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelMedium,
                        color = InkPrimary
                    )
                }
            }
        }

        // Compact friendly error banner positioned above center video controls
        val activeError = playerError ?: runtimeState.errorMessage
        if (activeError != null) {
            val errInfo = runtimeState.errorInfo
            val canSwitchFast = playerError == null &&
                errInfo != null &&
                errInfo.modelId != GeminiDubbingEngine.FAST_MODEL_ID
            Surface(
                color = CharcoalCard.copy(alpha = 0.94f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.72f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 60.dp)
                    .widthIn(max = 520.dp)
                    .testTag("player_error_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "AI dubbing alert",
                                tint = RoseAlert,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (playerError != null) "Playback Error" else (errInfo?.category?.title ?: "AI Dubbing Error"),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                if (playerError != null) {
                                    playerError = null
                                } else {
                                    onStopDubbing()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("player_dismiss_error_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                tint = InkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = activeError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playerError == null && errInfo != null) {
                        Text(
                            text = "${errInfo.apiName} • ${errInfo.modelId} — ${errInfo.actionableHint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (playerError == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRetryDubbing,
                                enabled = !runtimeState.isRetrying && !runtimeState.stage.isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricViolet,
                                    contentColor = InkPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("player_retry_dub_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry AI Dub", maxLines = 1)
                            }
                            if (canSwitchFast) {
                                OutlinedButton(
                                    onClick = onSwitchToFastStream,
                                    enabled = !runtimeState.isRetrying && !runtimeState.stage.isBusy,
                                    border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("player_fast_stream_button")
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fast Stream", color = CyanPulse, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Locked state minimal unlock button
        if (isControlsLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 28.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ObsidianBlack.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, ElectricViolet),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { isControlsLocked = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock controls", tint = InkPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tap to Unlock Controls", style = MaterialTheme.typography.labelLarge, color = InkPrimary)
                    }
                }
            }
        } else {
            // Full Cinema Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianBlack.copy(alpha = 0.78f),
                                    Color.Transparent,
                                    ObsidianBlack.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .safeDrawingPadding()
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { onClosePlayer(exoPlayer.currentPosition) },
                                modifier = Modifier.testTag("player_back_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = InkPrimary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = movie.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (runtimeState.activeTrackMode == AudioTrackMode.AI_DUBBED) {
                                        "AI Dub: ${config.targetLangCode.uppercase()} • ${runtimeState.statusDetail}"
                                    } else {
                                        "Original Audio • ${movie.fileName}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (runtimeState.activeTrackMode == AudioTrackMode.AI_DUBBED) CyanPulse else InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Original <-> AI Dubbed quick toggle pill
                            Surface(
                                shape = CircleShape,
                                color = CharcoalCard.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, CharcoalBorder),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val next = if (runtimeState.activeTrackMode == AudioTrackMode.ORIGINAL) {
                                            AudioTrackMode.AI_DUBBED
                                        } else {
                                            AudioTrackMode.ORIGINAL
                                        }
                                        onSwitchAudioTrack(next)
                                    }
                                    .testTag("audio_mode_switch")
                            ) {
                                Text(
                                    text = if (runtimeState.activeTrackMode == AudioTrackMode.AI_DUBBED) "AI DUB ON" else "ORIGINAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (runtimeState.activeTrackMode == AudioTrackMode.AI_DUBBED) EmeraldLive else InkSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Prominent AI DUB button
                            Surface(
                                shape = CircleShape,
                                color = ElectricViolet,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showDubSettingsSheet = true }
                                    .testTag("player_ai_dub_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.GraphicEq,
                                        contentDescription = "AI Dub settings",
                                        tint = InkPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AI DUB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Center Transport Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(ObsidianBlack.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = InkPrimary)
                        }

                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.92f))
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = InkPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(totalDurationMs)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(ObsidianBlack.copy(alpha = 0.55f))
                        ) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = InkPrimary)
                        }
                    }

                    // Bottom Scrubber & Utility Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (runtimeState.isDubbingActive) {
                            GlowingWaveform(
                                isActive = runtimeState.isSpeakingNow || runtimeState.stage.isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                            )
                        }

                        if (showVolumeSlider) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = CyanPulse)
                                Spacer(modifier = Modifier.width(8.dp))
                                Slider(
                                    value = masterVolume,
                                    onValueChange = {
                                        masterVolume = it
                                        onSetMasterVolume(it)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyanPulse,
                                        activeTrackColor = CyanPulse
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatDurationMs(currentPosMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = InkPrimary
                            )
                            Slider(
                                value = currentPosMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                                onValueChange = { pos ->
                                    exoPlayer.seekTo(pos.toLong())
                                    currentPosMs = pos.toLong()
                                },
                                valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = LuminousIris,
                                    activeTrackColor = ElectricViolet,
                                    inactiveTrackColor = CharcoalElevated
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )
                            Text(
                                text = formatDurationMs(totalDurationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Volume",
                                        tint = if (showVolumeSlider) CyanPulse else InkPrimary
                                    )
                                }

                                Box {
                                    IconButton(onClick = { showSpeedMenu = true }) {
                                        Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = InkPrimary)
                                    }
                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false },
                                        containerColor = CharcoalCard
                                    ) {
                                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                            DropdownMenuItem(
                                                text = { Text("${spd}x", color = InkPrimary) },
                                                onClick = {
                                                    playbackSpeed = spd
                                                    exoPlayer.setPlaybackSpeed(spd)
                                                    showSpeedMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        val nextSub = when (config.subtitleMode) {
                                            SubtitleMode.OFF -> SubtitleMode.DUAL
                                            SubtitleMode.DUAL -> SubtitleMode.AI_TRANSLATED
                                            SubtitleMode.AI_TRANSLATED -> SubtitleMode.ORIGINAL
                                            SubtitleMode.ORIGINAL -> SubtitleMode.OFF
                                        }
                                        onUpdateConfig { it.copy(subtitleMode = nextSub) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (config.subtitleMode == SubtitleMode.OFF) {
                                            Icons.Rounded.ClosedCaptionOff
                                        } else {
                                            Icons.Rounded.ClosedCaption
                                        },
                                        contentDescription = "Toggle subtitles",
                                        tint = if (config.subtitleMode != SubtitleMode.OFF) LuminousIris else InkSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isControlsLocked = true }) {
                                    Icon(Icons.Rounded.Lock, contentDescription = "Lock controls", tint = InkPrimary)
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                    IconButton(
                                        onClick = {
                                            runCatching {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity.enterPictureInPictureMode(params)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.PictureInPictureAlt,
                                            contentDescription = "Picture-in-picture",
                                            tint = InkPrimary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isLandscapeFullscreen = !isLandscapeFullscreen
                                        activity?.requestedOrientation = if (isLandscapeFullscreen) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isLandscapeFullscreen) {
                                            Icons.Rounded.FullscreenExit
                                        } else {
                                            Icons.Rounded.Fullscreen
                                        },
                                        contentDescription = "Toggle fullscreen orientation",
                                        tint = InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDubSettingsSheet) {
        DubbingSettingsSheet(
            config = config,
            isDubbingActive = runtimeState.isDubbingActive,
            onUpdateConfig = onUpdateConfig,
            onStartOrApplyDubbing = onStartDubbing,
            onStopDubbing = onStopDubbing,
            onDismiss = { showDubSettingsSheet = false }
        )
    }
}
