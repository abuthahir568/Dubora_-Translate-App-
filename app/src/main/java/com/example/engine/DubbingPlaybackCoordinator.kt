package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.LocalLibraryRepository
import com.example.domain.model.AudioTrackMode
import com.example.domain.model.DubSegment
import com.example.domain.model.DubbingConfig
import com.example.domain.model.DubbingPipelineStage
import com.example.domain.model.LocalMovie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class DubbingRuntimeState(
    val stage: DubbingPipelineStage = DubbingPipelineStage.IDLE,
    val statusDetail: String = "Select AI Dub to stream translated dialogue",
    val activeTrackMode: AudioTrackMode = AudioTrackMode.ORIGINAL,
    val isDubbingActive: Boolean = false,
    val isSpeakingNow: Boolean = false,
    val activeSpeakerLabel: String? = null,
    val currentSegment: DubSegment? = null,
    val segments: List<DubSegment> = emptyList(),
    val processedUntilMs: Long = 0L,
    val isBufferingDub: Boolean = false,
    val isRetrying: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 3,
    val activeModelId: String = GeminiDubbingEngine.DEFAULT_MODEL_ID,
    val errorMessage: String? = null,
    val errorInfo: DubbingErrorInfo? = null,
    val detectedSpeakersCount: Int = 0
)

class DubbingPlaybackCoordinator(
    private val context: Context,
    private val mediaBridge: MediaBridge,
    private val translationEngine: TranslationEngine,
    private val voiceEngine: VoiceGenerationEngine,
    private val localRepo: LocalLibraryRepository,
    private val scope: CoroutineScope
) : AudioSynchronizationEngine, AudioMixingEngine {

    private val _state = MutableStateFlow(DubbingRuntimeState())
    val state: StateFlow<DubbingRuntimeState> = _state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var dubMediaPlayer: MediaPlayer? = null
    private var lastPlayedSegmentId: String? = null
    private val playedSegmentIds = mutableSetOf<String>()
    private var pausedMidDub: Boolean = false
    private var lastSyncPosMs: Long = -1L
    private var pipelineJob: Job? = null
    private var syncJob: Job? = null
    private val knownSpeakers = mutableMapOf<String, String>()
    private val coveredRanges = mutableListOf<LongRange>()
    private var currentConfig: DubbingConfig = DubbingConfig()
    private var currentMovie: LocalMovie? = null
    private var activePipelineKey: Pair<String, String>? = null
    private var originalMasterVolume: Float = 1.0f

    fun attachPlayer(player: ExoPlayer) {
        exoPlayer = player
    }

    fun setMasterVolume(volume: Float) {
        originalMasterVolume = volume.coerceIn(0f, 1f)
        val st = _state.value
        applyMixState(
            mode = st.activeTrackMode,
            isDubSpeaking = st.isSpeakingNow,
            preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
            reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
            duckLevel = currentConfig.originalDialogueDuckLevel
        )
    }

    fun updateConfig(config: DubbingConfig) {
        currentConfig = config
        val st = _state.value
        applyMixState(
            mode = st.activeTrackMode,
            isDubSpeaking = st.isSpeakingNow,
            preserveBackgroundMusic = config.preserveBackgroundMusic,
            reduceOriginalDialogue = config.reduceOriginalDialogue,
            duckLevel = config.originalDialogueDuckLevel
        )
    }

    fun switchTrackMode(mode: AudioTrackMode) {
        if (mode == AudioTrackMode.ORIGINAL) {
            stopDubbedPlayback()
        }
        _state.value = _state.value.copy(
            activeTrackMode = mode,
            isSpeakingNow = false
        )
        applyMixState(
            mode = mode,
            isDubSpeaking = false,
            preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
            reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
            duckLevel = currentConfig.originalDialogueDuckLevel
        )
    }

    suspend fun loadCachedSegmentsForMovie(movie: LocalMovie, targetLangCode: String) {
        val cached = sanitizeAndDeoverlapSegments(localRepo.getCachedSegments(movie.id, targetLangCode))
        coveredRanges.clear()
        playedSegmentIds.clear()
        val maxEnd = cached.maxOfOrNull { it.endMs } ?: 0L
        _state.value = _state.value.copy(
            segments = cached,
            processedUntilMs = maxEnd,
            detectedSpeakersCount = cached.map { it.speakerId }.distinct().size,
            stage = if (cached.isNotEmpty()) DubbingPipelineStage.READY_TO_PLAY else DubbingPipelineStage.IDLE,
            statusDetail = if (cached.isNotEmpty()) {
                "${cached.size} dubbed cues loaded offline"
            } else {
                "Select AI Dub to start real-time translation"
            }
        )
    }

    fun retryLiveDubbing() {
        val movie = currentMovie ?: return
        if (pipelineJob?.isActive == true && _state.value.stage != DubbingPipelineStage.ERROR) {
            return
        }
        startLiveDubbing(movie, currentConfig, forceRestart = true, clearPreviousCache = false)
    }

    fun startLiveDubbing(
        movie: LocalMovie,
        config: DubbingConfig,
        forceRestart: Boolean = false,
        clearPreviousCache: Boolean = false
    ) {
        if (!forceRestart &&
            pipelineJob?.isActive == true &&
            currentMovie?.id == movie.id &&
            _state.value.stage != DubbingPipelineStage.ERROR
        ) {
            return
        }

        val nextKey = movie.id to config.targetLangCode
        val isSameTargetPipeline = activePipelineKey == nextKey
        activePipelineKey = nextKey
        currentMovie = movie
        currentConfig = config
        pipelineJob?.cancel()
        syncJob?.cancel()
        stopDubbedPlayback()
        lastPlayedSegmentId = null
        playedSegmentIds.clear()
        pausedMidDub = false
        lastSyncPosMs = -1L
        if (!isSameTargetPipeline || clearPreviousCache) {
            coveredRanges.clear()
            knownSpeakers.clear()
            _state.value = _state.value.copy(
                segments = emptyList(),
                processedUntilMs = 0L,
                detectedSpeakersCount = 0
            )
        }

        val health = GeminiConfigValidator.inspect(context, config.translationQuality.modelId)
        if (!health.isConfigHealthy) {
            val configErr = DubbingErrorInfo(
                category = DubbingErrorCategory.CONFIGURATION_ERROR,
                apiName = GeminiErrorClassifier.API_NAME,
                modelId = config.translationQuality.modelId,
                userMessage = health.configurationIssue ?: "Firebase AI Logic configuration check failed.",
                actionableHint = "Verify FirebaseApp initialization and App Check setup before retrying.",
                technicalDetail = "${health.appCheckMode}: ${health.configurationIssue}"
            )
            _state.value = _state.value.copy(
                stage = DubbingPipelineStage.ERROR,
                isDubbingActive = false,
                isRetrying = false,
                retryAttempt = 0,
                activeModelId = config.translationQuality.modelId,
                errorMessage = configErr.userMessage,
                errorInfo = configErr,
                statusDetail = configErr.category.title
            )
            return
        }

        if (config.wifiOnlyProcessing && !isOnWifi()) {
            val wifiErr = DubbingErrorInfo(
                category = DubbingErrorCategory.TIMEOUT_OR_NETWORK,
                apiName = GeminiErrorClassifier.API_NAME,
                modelId = config.translationQuality.modelId,
                userMessage = "Wi-Fi only mode is enabled in Settings.",
                actionableHint = "Connect to Wi-Fi or disable 'Wi-Fi Only Processing' in Settings, then tap Retry.",
                technicalDetail = "Active network is metered or offline"
            )
            _state.value = _state.value.copy(
                stage = DubbingPipelineStage.ERROR,
                isDubbingActive = false,
                isRetrying = false,
                retryAttempt = 0,
                activeModelId = config.translationQuality.modelId,
                errorMessage = wifiErr.userMessage,
                errorInfo = wifiErr,
                statusDetail = "Wi-Fi required"
            )
            return
        }

        _state.value = _state.value.copy(
            activeTrackMode = AudioTrackMode.AI_DUBBED,
            isDubbingActive = true,
            isRetrying = false,
            retryAttempt = 0,
            activeModelId = config.translationQuality.modelId,
            errorMessage = null,
            errorInfo = null,
            stage = DubbingPipelineStage.LISTENING,
            statusDetail = "Preparing fast AI dubbing stream..."
        )

        // Start sync loop tracking ExoPlayer clock (supports seeking, pause/resume, and smooth cue chaining)
        syncJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                val posMs = player?.currentPosition ?: 0L
                val isPlaying = player?.isPlaying == true
                val st = _state.value

                // Detect scrubbing / seeking (forward jump > 2.5s or rewind > 400ms)
                val userSeeked = lastSyncPosMs >= 0L && (posMs < lastSyncPosMs - 400L || posMs > lastSyncPosMs + 2_500L)
                lastSyncPosMs = posMs
                if (userSeeked) {
                    stopDubbedPlayback()
                    pausedMidDub = false
                    lastPlayedSegmentId = null
                    playedSegmentIds.clear()
                    if (_state.value.isSpeakingNow) {
                        _state.value = _state.value.copy(isSpeakingNow = false)
                        applyMixState(
                            mode = _state.value.activeTrackMode,
                            isDubSpeaking = false,
                            preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
                            reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
                            duckLevel = currentConfig.originalDialogueDuckLevel
                        )
                    }
                }

                val active = findActiveSegment(st.segments, posMs)

                if (st.activeTrackMode == AudioTrackMode.AI_DUBBED) {
                    val buffering = st.isDubbingActive &&
                        isPlaying &&
                        !isPositionCovered(posMs, st.segments) &&
                        resolveMovieDurationMs(movie) > posMs + 1500L

                    if (!isPlaying) {
                        if (st.isSpeakingNow && !pausedMidDub) {
                            pauseDubbedPlayback()
                            pausedMidDub = true
                        }
                        _state.value = _state.value.copy(
                            currentSegment = active,
                            activeSpeakerLabel = active?.speakerLabel,
                            isBufferingDub = false
                        )
                    } else {
                        // Resuming mid-sentence after video pause
                        if (pausedMidDub && st.isSpeakingNow) {
                            resumeDubbedPlayback()
                            pausedMidDub = false
                            applyMixState(
                                mode = AudioTrackMode.AI_DUBBED,
                                isDubSpeaking = true,
                                preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
                                reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
                                duckLevel = currentConfig.originalDialogueDuckLevel
                            )
                        }

                        // Only start next dubbed cue when previous dubbed cue has finished (zero mid-word chop)
                        val nextCue = if (!st.isSpeakingNow) {
                            findNextUnplayedCue(st.segments, posMs, playedSegmentIds)
                        } else {
                            null
                        }

                        if (nextCue != null) {
                            val audioPath = nextCue.synthesizedAudioPath
                            playedSegmentIds.add(nextCue.id)
                            lastPlayedSegmentId = nextCue.id
                            if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
                                val videoPlaybackRate = player?.playbackParameters?.speed ?: 1.0f
                                _state.value = _state.value.copy(
                                    currentSegment = active ?: nextCue,
                                    isSpeakingNow = true,
                                    activeSpeakerLabel = nextCue.speakerLabel,
                                    isBufferingDub = false
                                )
                                applyMixState(
                                    mode = AudioTrackMode.AI_DUBBED,
                                    isDubSpeaking = true,
                                    preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
                                    reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
                                    duckLevel = currentConfig.originalDialogueDuckLevel
                                )
                                playDubbedFile(
                                    filePath = audioPath,
                                    volume = originalMasterVolume,
                                    speed = videoPlaybackRate
                                ) {
                                    pausedMidDub = false
                                    _state.value = _state.value.copy(isSpeakingNow = false)
                                    applyMixState(
                                        mode = _state.value.activeTrackMode,
                                        isDubSpeaking = false,
                                        preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
                                        reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
                                        duckLevel = currentConfig.originalDialogueDuckLevel
                                    )
                                }
                            } else {
                                _state.value = _state.value.copy(
                                    currentSegment = active ?: nextCue,
                                    activeSpeakerLabel = nextCue.speakerLabel,
                                    isBufferingDub = buffering
                                )
                            }
                        } else if (!st.isSpeakingNow) {
                            _state.value = _state.value.copy(
                                currentSegment = active,
                                activeSpeakerLabel = active?.speakerLabel,
                                isBufferingDub = buffering
                            )
                        } else {
                            _state.value = _state.value.copy(
                                currentSegment = active ?: _state.value.currentSegment,
                                activeSpeakerLabel = (active ?: _state.value.currentSegment)?.speakerLabel
                            )
                        }
                    }
                }
                delay(140L)
            }
        }

        // Start seek-aware incremental lookahead pipeline (handles both 10s clips and 3-hour movies)
        pipelineJob = scope.launch {
            try {
                val uri = Uri.parse(movie.uriString)
                if (clearPreviousCache) {
                    localRepo.clearSegmentsForLanguage(movie.id, config.targetLangCode)
                } else {
                    val cached = sanitizeAndDeoverlapSegments(
                        localRepo.getCachedSegments(movie.id, config.targetLangCode)
                    )
                    if (cached.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            segments = cached,
                            processedUntilMs = cached.maxOf { it.endMs },
                            detectedSpeakersCount = cached.map { it.speakerId }.distinct().size
                        )
                    }
                }

                // Wait briefly for ExoPlayer metadata if movie duration wasn't resolved during import
                var waitChecks = 0
                while (movie.durationMs <= 0L && (exoPlayer?.duration ?: 0L) <= 0L && waitChecks < 10 && isActive) {
                    delay(150L)
                    waitChecks++
                }

                val steadyChunkMs = config.lookaheadBufferSeconds.coerceIn(12, 30) * 1000L
                val totalDurationMs = resolveMovieDurationMs(movie)

                while (isActive) {
                    val currentPos = (exoPlayer?.currentPosition ?: 0L).coerceIn(0L, totalDurationMs)
                    val window = nextUncoveredWindow(
                        currentPosMs = currentPos,
                        totalDurationMs = totalDurationMs,
                        steadyChunkMs = steadyChunkMs,
                        cachedSegments = _state.value.segments
                    )

                    if (window == null) {
                        val allDone = coveredRangesTotalMs(totalDurationMs) >= totalDurationMs - 1000L
                        _state.value = _state.value.copy(
                            stage = DubbingPipelineStage.READY_TO_PLAY,
                            statusDetail = if (allDone) {
                                "All movie dialogue dubbed & synchronized"
                            } else {
                                "Lookahead buffer ready ahead of playhead"
                            }
                        )
                        if (allDone) {
                            localRepo.markDubCached(movie.id, config.targetLangCode, isDownloaded = true)
                        }
                        delay(700L)
                        continue
                    }

                    val (nextStart, windowEnd) = window
                    val isImmediatePlayhead = nextStart <= currentPos + 2000L

                    _state.value = _state.value.copy(
                        stage = DubbingPipelineStage.DETECTING_DIALOGUE,
                        statusDetail = "Extracting audio ${formatMs(nextStart)} – ${formatMs(windowEnd)}"
                    )

                    val chunkResult = mediaBridge.extractAudioChunk(
                        uri = uri,
                        movieId = movie.id,
                        windowStartMs = nextStart,
                        windowEndMs = windowEnd
                    )
                    if (chunkResult.isFailure) {
                        val ex = chunkResult.exceptionOrNull()
                        val errInfo = GeminiErrorClassifier.classify(
                            ex ?: IllegalStateException("Unable to decode audio track for chunk extraction."),
                            _state.value.activeModelId
                        )
                        _state.value = _state.value.copy(
                            stage = DubbingPipelineStage.ERROR,
                            isDubbingActive = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            activeTrackMode = AudioTrackMode.ORIGINAL,
                            errorMessage = errInfo.userMessage,
                            errorInfo = errInfo,
                            statusDetail = errInfo.category.title
                        )
                        applyMixState(
                            mode = AudioTrackMode.ORIGINAL,
                            isDubSpeaking = false,
                            preserveBackgroundMusic = config.preserveBackgroundMusic,
                            reduceOriginalDialogue = config.reduceOriginalDialogue,
                            duckLevel = config.originalDialogueDuckLevel
                        )
                        return@launch
                    }

                    val chunk = chunkResult.getOrThrow()
                    if (!chunk.hasAudioSignal || chunk.wavBytes.isEmpty()) {
                        // Skip silent/music-less gap without consuming AI quota
                        recordCoveredRange(nextStart, windowEnd)
                        _state.value = _state.value.copy(
                            processedUntilMs = maxOf(_state.value.processedUntilMs, windowEnd),
                            stage = DubbingPipelineStage.READY_TO_PLAY,
                            statusDetail = "Skipped silent window (${formatMs(nextStart)}–${formatMs(windowEnd)})"
                        )
                        continue
                    }

                    val activeModel = _state.value.activeModelId
                    _state.value = _state.value.copy(
                        stage = DubbingPipelineStage.TRANSLATING,
                        isRetrying = false,
                        retryAttempt = 0,
                        statusDetail = "Translating ${formatMs(nextStart)}–${formatMs(windowEnd)} via $activeModel..."
                    )

                    val translatedResult = translationEngine.recognizeDiarizeAndTranslateChunk(
                        chunk = chunk,
                        config = config,
                        knownSpeakers = knownSpeakers,
                        onRetry = { progress ->
                            val statusText = if (!isImmediatePlayhead && _state.value.segments.isNotEmpty()) {
                                "Buffering next scene (${progress.attempt}/${progress.maxRetries})..."
                            } else {
                                progress.userStatusMessage
                            }
                            _state.value = _state.value.copy(
                                stage = DubbingPipelineStage.RETRYING,
                                isRetrying = true,
                                retryAttempt = progress.attempt,
                                maxRetries = progress.maxRetries,
                                activeModelId = progress.modelId,
                                statusDetail = statusText
                            )
                        }
                    )
                    if (translatedResult.isFailure) {
                        val err = translatedResult.exceptionOrNull()
                        val errInfo = (err as? DubbingApiException)?.errorInfo
                            ?: GeminiErrorClassifier.classify(
                                err ?: IllegalStateException("Gemini AI dubbing request failed."),
                                _state.value.activeModelId
                            )
                        _state.value = _state.value.copy(
                            stage = DubbingPipelineStage.ERROR,
                            isDubbingActive = false,
                            isRetrying = false,
                            retryAttempt = 0,
                            isBufferingDub = false,
                            isSpeakingNow = false,
                            activeTrackMode = AudioTrackMode.ORIGINAL,
                            errorMessage = errInfo.userMessage,
                            errorInfo = errInfo,
                            statusDetail = errInfo.category.title
                        )
                        applyMixState(
                            mode = AudioTrackMode.ORIGINAL,
                            isDubSpeaking = false,
                            preserveBackgroundMusic = config.preserveBackgroundMusic,
                            reduceOriginalDialogue = config.reduceOriginalDialogue,
                            duckLevel = config.originalDialogueDuckLevel
                        )
                        return@launch
                    }

                    _state.value = _state.value.copy(
                        isRetrying = false,
                        retryAttempt = 0,
                        errorMessage = null,
                        errorInfo = null
                    )

                    val rawSegments = translatedResult.getOrThrow()
                    val synthesizedList = mutableListOf<DubSegment>()

                    if (rawSegments.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            stage = DubbingPipelineStage.GENERATING_VOICE,
                            statusDetail = "Synthesizing ${rawSegments.size} speaker cue(s)..."
                        )
                        for (seg in rawSegments) {
                            val cueSlotMs = (seg.endMs - seg.startMs).coerceAtLeast(800L)
                            val ttsResult = voiceEngine.synthesizeSegment(
                                movieId = movie.id,
                                segmentId = seg.id,
                                text = seg.translatedText,
                                targetLangCode = config.targetLangCode,
                                speakerId = seg.speakerId,
                                speakerGender = seg.speakerGender,
                                voiceGenderMode = config.voiceGenderMode,
                                voiceSpeed = config.voiceSpeed,
                                voicePitch = config.voicePitch,
                                voiceStyle = config.voiceStyle,
                                targetMaxDurationMs = cueSlotMs
                            )
                            val synth = ttsResult.getOrNull()
                            synthesizedList.add(
                                seg.copy(
                                    synthesizedAudioPath = synth?.filePath,
                                    synthesizedDurationMs = synth?.durationMs ?: cueSlotMs
                                )
                            )
                        }
                        localRepo.saveSegments(synthesizedList)
                        localRepo.markDubCached(movie.id, config.targetLangCode, isDownloaded = false)
                    }

                    recordCoveredRange(nextStart, windowEnd)

                    val retainedExisting = _state.value.segments.filterNot { seg ->
                        seg.startMs >= nextStart && seg.startMs < windowEnd
                    }
                    val merged = sanitizeAndDeoverlapSegments(retainedExisting + synthesizedList)

                    _state.value = _state.value.copy(
                        segments = merged,
                        processedUntilMs = maxOf(_state.value.processedUntilMs, windowEnd),
                        detectedSpeakersCount = merged.map { it.speakerId }.distinct().size,
                        stage = DubbingPipelineStage.READY_TO_PLAY,
                        statusDetail = "Ready (${merged.size} dubbed cues • ${formatMs(windowEnd)} buffered)"
                    )

                    // Pace back-to-back chunk requests to prevent per-minute / concurrency 429 bursts
                    if (windowEnd < totalDurationMs) {
                        delay(if (isImmediatePlayhead) 1_600L else 2_000L)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live dubbing pipeline failed", e)
                val errInfo = GeminiErrorClassifier.classify(e, _state.value.activeModelId)
                _state.value = _state.value.copy(
                    stage = DubbingPipelineStage.ERROR,
                    isDubbingActive = false,
                    isRetrying = false,
                    retryAttempt = 0,
                    activeTrackMode = AudioTrackMode.ORIGINAL,
                    errorMessage = errInfo.userMessage,
                    errorInfo = errInfo,
                    statusDetail = errInfo.category.title
                )
            }
        }
    }

    private fun resolveMovieDurationMs(movie: LocalMovie): Long {
        val playerDur = exoPlayer?.duration?.takeIf { it > 0L }
        return (playerDur ?: movie.durationMs).coerceAtLeast(3_000L)
    }

    private fun recordCoveredRange(startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        coveredRanges.add(startMs until endMs)
        // Merge overlapping/adjacent ranges for fast O(ranges) lookup across 3-hour movies
        val sorted = coveredRanges.sortedBy { it.first }
        coveredRanges.clear()
        for (range in sorted) {
            val prev = coveredRanges.lastOrNull()
            if (prev == null || range.first > prev.last + 1L) {
                coveredRanges.add(range)
            } else {
                coveredRanges[coveredRanges.lastIndex] = prev.first..maxOf(prev.last, range.last)
            }
        }
    }

    private fun isPositionCovered(positionMs: Long, segments: List<DubSegment>): Boolean {
        if (coveredRanges.any { positionMs in it }) return true
        return segments.any { positionMs in it.startMs..it.endMs }
    }

    private fun coveredRangesTotalMs(totalDurationMs: Long): Long {
        return coveredRanges.sumOf { (minOf(it.last + 1L, totalDurationMs) - it.first).coerceAtLeast(0L) }
    }

    private fun nextUncoveredWindow(
        currentPosMs: Long,
        totalDurationMs: Long,
        steadyChunkMs: Long,
        cachedSegments: List<DubSegment>
    ): Pair<Long, Long>? {
        // Short clip (< 30s, e.g., 10s video): translate the entire video in 1 call for instant speed
        if (totalDurationMs <= 30_000L) {
            val start = 0L
            if (coveredRanges.any { start in it && totalDurationMs - 1L in it }) {
                return null
            }
            return start to totalDurationMs
        }

        // Look ahead up to 2 steady windows (~48–60s) from current playhead (even after seeking)
        val lookaheadHorizonMs = (currentPosMs + steadyChunkMs * 2L).coerceAtMost(totalDurationMs)
        var cursor = (currentPosMs / 1000L) * 1000L

        while (cursor < lookaheadHorizonMs && cursor < totalDurationMs) {
            val covering = coveredRanges.firstOrNull { cursor in it }
            if (covering != null) {
                cursor = covering.last + 1L
                continue
            }

            // Skip regions already populated in offline Room cache (supports forward & backward seek)
            val overlappingCached = cachedSegments.firstOrNull {
                it.endMs > cursor && it.startMs <= cursor + 1_500L
            }
            if (overlappingCached != null) {
                val advanceTo = maxOf(cursor + 1_000L, overlappingCached.endMs)
                    .coerceAtMost(totalDurationMs)
                recordCoveredRange(cursor, advanceTo)
                cursor = advanceTo
                continue
            }

            val isFirstImmediateWindow = coveredRanges.isEmpty() && cursor <= currentPosMs + 1000L
            val targetSliceMs = if (isFirstImmediateWindow) 12_000L else steadyChunkMs
            val nextBoundary = coveredRanges
                .filter { it.first > cursor }
                .minOfOrNull { it.first }
                ?: totalDurationMs

            val windowEnd = minOf(cursor + targetSliceMs, nextBoundary, totalDurationMs)
            if (windowEnd - cursor < 500L) {
                cursor = windowEnd
                continue
            }
            return cursor to windowEnd
        }
        return null
    }

    fun stopDubbing() {
        pipelineJob?.cancel()
        syncJob?.cancel()
        stopDubbedPlayback()
        _state.value = _state.value.copy(
            isDubbingActive = false,
            isRetrying = false,
            retryAttempt = 0,
            isSpeakingNow = false,
            isBufferingDub = false,
            errorMessage = null,
            errorInfo = null,
            activeTrackMode = AudioTrackMode.ORIGINAL,
            stage = DubbingPipelineStage.IDLE,
            statusDetail = "AI Dubbing stopped"
        )
        applyMixState(
            mode = AudioTrackMode.ORIGINAL,
            isDubSpeaking = false,
            preserveBackgroundMusic = currentConfig.preserveBackgroundMusic,
            reduceOriginalDialogue = currentConfig.reduceOriginalDialogue,
            duckLevel = currentConfig.originalDialogueDuckLevel
        )
    }

    override fun findActiveSegment(segments: List<DubSegment>, positionMs: Long): DubSegment? {
        if (segments.isEmpty()) return null
        // Binary search startMs for O(log n) lookup even on 3-hour movies with 2,000+ segments
        var low = 0
        var high = segments.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (segments[mid].startMs <= positionMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate >= 0) {
            val seg = segments[candidate]
            if (positionMs <= seg.endMs) return seg
        }
        // Fallback scan near candidate in case of slightly overlapping segments
        val startIdx = (candidate - 2).coerceAtLeast(0)
        val endIdx = (candidate + 2).coerceAtMost(segments.lastIndex)
        for (i in startIdx..endIdx) {
            val s = segments[i]
            if (positionMs in s.startMs..s.endMs) return s
        }
        return null
    }

    private fun findNextUnplayedCue(
        segments: List<DubSegment>,
        positionMs: Long,
        playedIds: Set<String>
    ): DubSegment? {
        if (segments.isEmpty()) return null
        val exact = findActiveSegment(segments, positionMs)
        if (exact != null && exact.id !in playedIds) {
            return exact
        }
        // Catch up cleanly to a cue that started just behind playhead (within 1.5s) while previous cue finished
        return segments.firstOrNull { seg ->
            seg.id !in playedIds &&
                seg.startMs <= positionMs + 150L &&
                seg.endMs >= positionMs - 1_500L
        }
    }

    private fun sanitizeAndDeoverlapSegments(raw: List<DubSegment>): List<DubSegment> =
        sanitizeSegments(raw)

    override fun isBufferReadyAt(
        segments: List<DubSegment>,
        processedUntilMs: Long,
        positionMs: Long
    ): Boolean {
        return isPositionCovered(positionMs, segments) || processedUntilMs >= positionMs + 1500L
    }

    override fun applyMixState(
        mode: AudioTrackMode,
        isDubSpeaking: Boolean,
        preserveBackgroundMusic: Boolean,
        reduceOriginalDialogue: Boolean,
        duckLevel: Float
    ) {
        val player = exoPlayer ?: return
        val targetVolume = when {
            mode == AudioTrackMode.ORIGINAL -> originalMasterVolume
            !preserveBackgroundMusic -> 0.04f
            isDubSpeaking && reduceOriginalDialogue -> (originalMasterVolume * duckLevel).coerceIn(0.05f, 0.55f)
            isDubSpeaking -> (originalMasterVolume * 0.45f).coerceIn(0.1f, 0.7f)
            else -> originalMasterVolume * 0.88f
        }
        player.volume = targetVolume
    }

    override fun playDubbedFile(
        filePath: String,
        volume: Float,
        speed: Float,
        onComplete: () -> Unit
    ) {
        stopDubbedPlayback()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePath)
                prepare()
                setVolume(volume, volume)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    runCatching {
                        playbackParams = playbackParams.setSpeed(speed.coerceIn(0.75f, 1.5f))
                    }
                }
                setOnCompletionListener {
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    onComplete()
                    true
                }
                start()
            }
            dubMediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play dubbed WAV $filePath", e)
            onComplete()
        }
    }

    override fun stopDubbedPlayback() {
        runCatching {
            dubMediaPlayer?.stop()
        }
        runCatching {
            dubMediaPlayer?.release()
        }
        dubMediaPlayer = null
    }

    override fun pauseDubbedPlayback() {
        runCatching {
            if (dubMediaPlayer?.isPlaying == true) {
                dubMediaPlayer?.pause()
            }
        }
    }

    override fun resumeDubbedPlayback() {
        runCatching {
            dubMediaPlayer?.start()
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, mins, secs)
        else "%02d:%02d".format(minOrZero(mins), minOrZero(secs))
    }

    private fun minOrZero(v: Long): Long = v.coerceAtLeast(0L)

    fun release() {
        pipelineJob?.cancel()
        syncJob?.cancel()
        stopDubbedPlayback()
        voiceEngine.shutdown()
    }

    internal companion object {
        const val TAG = "DubbingCoordinator"

        internal fun sanitizeSegments(raw: List<DubSegment>): List<DubSegment> {
            if (raw.isEmpty()) return emptyList()
            val sorted = raw
                .distinctBy { it.id }
                .sortedWith(compareBy<DubSegment> { it.startMs }.thenBy { it.endMs })
            val out = mutableListOf<DubSegment>()
            for (seg in sorted) {
                val prev = out.lastOrNull()
                if (prev == null) {
                    out.add(seg)
                    continue
                }
                if (seg.startMs < prev.endMs) {
                    val overlapMs = minOf(prev.endMs, seg.endMs) - maxOf(prev.startMs, seg.startMs)
                    val segLen = (seg.endMs - seg.startMs).coerceAtLeast(1L)
                    val prevLen = (prev.endMs - prev.startMs).coerceAtLeast(1L)
                    val isDuplicateTurn = kotlin.math.abs(seg.startMs - prev.startMs) <= 850L ||
                        overlapMs > (minOf(segLen, prevLen) * 0.45f)
                    if (isDuplicateTurn) {
                        val prevHasAudio = !prev.synthesizedAudioPath.isNullOrBlank()
                        val segHasAudio = !seg.synthesizedAudioPath.isNullOrBlank()
                        if (!prevHasAudio && segHasAudio) {
                            out[out.lastIndex] = seg
                        }
                    } else {
                        val trimmedStart = prev.endMs + 40L
                        if (seg.endMs > trimmedStart + 350L) {
                            out.add(seg.copy(startMs = trimmedStart))
                        }
                    }
                } else {
                    out.add(seg)
                }
            }
            return out
        }
    }
}
