package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.DuboraDatabase
import com.example.data.local.LocalLibraryRepository
import com.example.data.local.LocalMediaImporter
import com.example.data.local.UserPreferencesDataStore
import com.example.data.remote.CloudSyncRepository
import com.example.domain.model.AudioTrackMode
import com.example.domain.model.DubbingConfig
import com.example.domain.model.DubbingSessionRecord
import com.example.domain.model.LocalMovie
import com.example.domain.model.TranslationQuality
import com.example.domain.model.UserProfile
import com.example.engine.AndroidAudioExtractor
import com.example.engine.AndroidTtsVoiceEngine
import com.example.engine.ApiHealthCheckResult
import com.example.engine.DubbingApiException
import com.example.engine.DubbingErrorCategory
import com.example.engine.DubbingErrorInfo
import com.example.engine.DubbingPlaybackCoordinator
import com.example.engine.DubbingRuntimeState
import com.example.engine.GeminiConfigValidator
import com.example.engine.GeminiDubbingEngine
import com.example.engine.GeminiErrorClassifier
import com.example.engine.PlatformBridgeStatus
import com.example.engine.RecognizedUtterance
import com.example.engine.TranslatedUtterance
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

sealed interface StudioProbeState {
    data object Idle : StudioProbeState
    data class Loading(
        val statusMessage: String = "Translating & synthesizing with Gemini...",
        val retryAttempt: Int = 0,
        val maxRetries: Int = 3,
        val isRetrying: Boolean = false
    ) : StudioProbeState
    data class Success(val result: TranslatedUtterance, val audioPath: String?) : StudioProbeState
    data class Error(
        val message: String,
        val errorInfo: DubbingErrorInfo,
        val lastDialogueInput: String
    ) : StudioProbeState
}

class DuboraViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val localRepo: LocalLibraryRepository,
    private val prefsStore: UserPreferencesDataStore,
    private val cloudRepo: CloudSyncRepository,
    private val geminiEngine: GeminiDubbingEngine,
    private val ttsEngine: AndroidTtsVoiceEngine,
    private val coordinator: DubbingPlaybackCoordinator,
    val bridgeCapabilities: PlatformBridgeStatus
) : ViewModel() {

    val movies: StateFlow<List<LocalMovie>> = localRepo.movies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val downloadedDubs: StateFlow<List<LocalMovie>> = localRepo.downloadedDubs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val config: StateFlow<DubbingConfig> = prefsStore.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DubbingConfig())

    val dubbingRuntime: StateFlow<DubbingRuntimeState> = coordinator.state

    val userProfileState: StateFlow<CloudUiState<UserProfile?>> = cloudRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(CloudUiState.Error(root.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val cloudHistoryState: StateFlow<CloudUiState<List<DubbingSessionRecord>>> =
        cloudRepo.observeDubbingHistory(currentUserId)
            .map<List<DubbingSessionRecord>, CloudUiState<List<DubbingSessionRecord>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing cloud dubbing history", root)
                emit(CloudUiState.Error(root.localizedMessage ?: "Failed to load history"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    private val _activeMovie = MutableStateFlow<LocalMovie?>(null)
    val activeMovie: StateFlow<LocalMovie?> = _activeMovie.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _studioProbe = MutableStateFlow<StudioProbeState>(StudioProbeState.Idle)
    val studioProbe: StateFlow<StudioProbeState> = _studioProbe.asStateFlow()

    private val _apiHealth = MutableStateFlow(
        GeminiConfigValidator.inspect(appContext, GeminiDubbingEngine.DEFAULT_MODEL_ID)
    )
    val apiHealth: StateFlow<ApiHealthCheckResult> = _apiHealth.asStateFlow()

    private var lastProbeInput: String = ""

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            cloudRepo.ensureUserProfile(
                displayName = user?.displayName ?: "Dubora Listener",
                email = user?.email ?: ""
            )
        }
        viewModelScope.launch {
            config.collect { cfg ->
                _apiHealth.value = GeminiConfigValidator.inspect(appContext, cfg.translationQuality.modelId)
            }
        }
    }

    fun refreshApiHealthCheck() {
        _apiHealth.value = GeminiConfigValidator.inspect(appContext, config.value.translationQuality.modelId)
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun importMovieUri(uri: Uri, openImmediately: Boolean = true) {
        viewModelScope.launch {
            val res = localRepo.importMovie(uri)
            res.onSuccess { movie ->
                if (openImmediately) {
                    openMovie(movie)
                } else {
                    _bannerMessage.value = "Imported \"${movie.title}\" (${movie.fileName})"
                }
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Unable to import selected movie file."
            }
        }
    }

    fun openMovie(movie: LocalMovie) {
        viewModelScope.launch {
            val fresh = localRepo.getMovieById(movie.id) ?: movie
            _activeMovie.value = fresh
            coordinator.loadCachedSegmentsForMovie(fresh, config.value.targetLangCode)
            if (config.value.autoDubbing) {
                coordinator.startLiveDubbing(fresh, config.value)
            }
        }
    }

    fun closePlayer(lastPositionMs: Long) {
        val movie = _activeMovie.value
        val rt = coordinator.state.value
        val cfg = config.value
        coordinator.stopDubbing()
        _activeMovie.value = null
        if (movie != null) {
            viewModelScope.launch {
                localRepo.savePlaybackPosition(movie.id, lastPositionMs)
                cloudRepo.upsertDubbingSession(
                    sessionId = "${movie.id}_${cfg.targetLangCode}",
                    movieTitle = movie.title,
                    fileName = movie.fileName,
                    fileSizeBytes = movie.sizeBytes,
                    durationMs = movie.durationMs,
                    lastPositionMs = lastPositionMs,
                    sourceLang = cfg.sourceLangCode,
                    targetLang = cfg.targetLangCode,
                    voiceProfile = cfg.voiceGenderMode.id,
                    segmentsProcessed = rt.segments.size.toLong(),
                    status = if (movie.isDownloadedDub) "downloaded" else "paused"
                )
            }
        }
    }

    fun savePositionOnly(movieId: String, positionMs: Long) {
        viewModelScope.launch {
            localRepo.savePlaybackPosition(movieId, positionMs)
        }
    }

    fun removeMovie(movie: LocalMovie) {
        viewModelScope.launch {
            localRepo.removeMovie(movie)
            _bannerMessage.value = "Removed \"${movie.title}\" from local library."
        }
    }

    fun clearDownloadedDub(movie: LocalMovie) {
        viewModelScope.launch {
            localRepo.clearDownloadedDub(movie)
            _bannerMessage.value = "Cleared offline AI dub cache for \"${movie.title}\"."
        }
    }

    fun updateConfig(transform: (DubbingConfig) -> DubbingConfig) {
        viewModelScope.launch {
            prefsStore.updateConfig(transform)
            val next = config.value.let(transform)
            coordinator.updateConfig(next)
            cloudRepo.updateUserPreferences(
                defaultSourceLang = next.sourceLangCode,
                defaultTargetLang = next.targetLangCode,
                defaultVoiceGender = next.voiceGenderMode.id,
                autoDubbing = next.autoDubbing,
                wifiOnlyProcessing = next.wifiOnlyProcessing
            )
        }
    }

    fun attachPlayer(player: ExoPlayer) {
        coordinator.attachPlayer(player)
    }

    fun startLiveDubbingForActiveMovie(
        forceRestart: Boolean = true,
        clearPreviousCache: Boolean = false
    ) {
        val movie = _activeMovie.value ?: return
        val cfg = config.value
        coordinator.startLiveDubbing(
            movie = movie,
            config = cfg,
            forceRestart = forceRestart,
            clearPreviousCache = clearPreviousCache
        )
        viewModelScope.launch {
            cloudRepo.upsertDubbingSession(
                sessionId = "${movie.id}_${cfg.targetLangCode}",
                movieTitle = movie.title,
                fileName = movie.fileName,
                fileSizeBytes = movie.sizeBytes,
                durationMs = movie.durationMs,
                lastPositionMs = movie.lastPositionMs,
                sourceLang = cfg.sourceLangCode,
                targetLang = cfg.targetLangCode,
                voiceProfile = cfg.voiceGenderMode.id,
                segmentsProcessed = coordinator.state.value.segments.size.toLong(),
                status = "active"
            )
        }
    }

    fun retryLiveDubbing() {
        coordinator.retryLiveDubbing()
    }

    fun switchToFastStreamAndRetry() {
        val movie = _activeMovie.value ?: return
        viewModelScope.launch {
            prefsStore.updateConfig {
                it.copy(
                    translationQuality = TranslationQuality.LOW_LATENCY,
                    lookaheadBufferSeconds = it.lookaheadBufferSeconds.coerceIn(12, 30)
                )
            }
            val fastConfig = config.value.copy(
                translationQuality = TranslationQuality.LOW_LATENCY,
                lookaheadBufferSeconds = config.value.lookaheadBufferSeconds.coerceIn(12, 30)
            )
            coordinator.updateConfig(fastConfig)
            coordinator.startLiveDubbing(movie, fastConfig, forceRestart = true)
        }
    }

    fun stopLiveDubbing() {
        coordinator.stopDubbing()
    }

    fun switchAudioTrack(mode: AudioTrackMode) {
        if (mode == AudioTrackMode.AI_DUBBED && !coordinator.state.value.isDubbingActive) {
            startLiveDubbingForActiveMovie(forceRestart = false)
        } else {
            coordinator.switchTrackMode(mode)
        }
    }

    fun setPlayerMasterVolume(volume: Float) {
        coordinator.setMasterVolume(volume)
    }

    fun testGeminiDialogueCue(sampleDialogue: String) {
        if (_studioProbe.value is StudioProbeState.Loading) {
            // Single-flight guard: do not send duplicate requests while waiting or retrying
            return
        }
        val trimmed = sampleDialogue.trim()
        val cfg = config.value
        val modelId = cfg.translationQuality.modelId
        if (trimmed.isBlank()) {
            val emptyErr = DubbingErrorInfo(
                category = DubbingErrorCategory.UNSUPPORTED_OR_SAFETY,
                apiName = GeminiErrorClassifier.API_NAME,
                modelId = modelId,
                userMessage = "Enter a line of movie dialogue to test.",
                actionableHint = "Type a sample line above and tap Translate & Speak Cue.",
                technicalDetail = "Blank input"
            )
            _studioProbe.value = StudioProbeState.Error(emptyErr.userMessage, emptyErr, trimmed)
            return
        }

        lastProbeInput = trimmed

        // Check API configuration & App Check separately before invoking network
        val health = GeminiConfigValidator.inspect(appContext, modelId)
        _apiHealth.value = health
        if (!health.isConfigHealthy) {
            val configErr = DubbingErrorInfo(
                category = DubbingErrorCategory.CONFIGURATION_ERROR,
                apiName = GeminiErrorClassifier.API_NAME,
                modelId = modelId,
                userMessage = health.configurationIssue ?: "Firebase AI Logic configuration check failed.",
                actionableHint = "Verify FirebaseApp initialization and App Check setup, then tap Retry.",
                technicalDetail = "${health.appCheckMode}: ${health.configurationIssue}"
            )
            _studioProbe.value = StudioProbeState.Error(configErr.userMessage, configErr, trimmed)
            return
        }

        viewModelScope.launch {
            _studioProbe.value = StudioProbeState.Loading(
                statusMessage = "Translating with $modelId...",
                retryAttempt = 0,
                maxRetries = 3,
                isRetrying = false
            )
            val cue = RecognizedUtterance(
                startOffsetMs = 0L,
                endOffsetMs = 3500L,
                originalText = trimmed,
                detectedLanguageCode = cfg.sourceLangCode,
                speakerHint = "Speaker_1",
                speakerGenderHint = "male"
            )
            val result = geminiEngine.translateCueList(
                cues = listOf(cue),
                sourceLangCode = cfg.sourceLangCode,
                targetLangCode = cfg.targetLangCode,
                modelId = modelId,
                onRetry = { progress ->
                    _studioProbe.value = StudioProbeState.Loading(
                        statusMessage = progress.userStatusMessage,
                        retryAttempt = progress.attempt,
                        maxRetries = progress.maxRetries,
                        isRetrying = true
                    )
                }
            )
            result.onSuccess { translated ->
                val item = translated.firstOrNull()
                if (item == null) {
                    val emptyErr = DubbingErrorInfo(
                        category = DubbingErrorCategory.UNKNOWN,
                        apiName = GeminiErrorClassifier.API_NAME,
                        modelId = modelId,
                        userMessage = "Gemini returned no translated cue.",
                        actionableHint = "Tap Retry to try translating again.",
                        technicalDetail = "Empty segment list"
                    )
                    _studioProbe.value = StudioProbeState.Error(emptyErr.userMessage, emptyErr, trimmed)
                } else {
                    val synth = ttsEngine.synthesizeSegment(
                        movieId = "studio_probe",
                        segmentId = "probe_${System.currentTimeMillis()}",
                        text = item.translatedText,
                        targetLangCode = cfg.targetLangCode,
                        speakerId = item.diarized.speakerId,
                        speakerGender = item.diarized.speakerGender,
                        voiceGenderMode = cfg.voiceGenderMode,
                        voiceSpeed = cfg.voiceSpeed,
                        voicePitch = cfg.voicePitch,
                        voiceStyle = cfg.voiceStyle
                    ).getOrNull()
                    _studioProbe.value = StudioProbeState.Success(item, synth?.filePath)
                }
            }.onFailure { err ->
                val errInfo = (err as? DubbingApiException)?.errorInfo
                    ?: GeminiErrorClassifier.classify(err, modelId)
                _studioProbe.value = StudioProbeState.Error(
                    message = errInfo.userMessage,
                    errorInfo = errInfo,
                    lastDialogueInput = trimmed
                )
            }
        }
    }

    fun retryLastStudioProbe() {
        val current = _studioProbe.value
        if (current is StudioProbeState.Loading) return
        val input = when (current) {
            is StudioProbeState.Error -> current.lastDialogueInput
            else -> lastProbeInput
        }
        if (input.isNotBlank()) {
            testGeminiDialogueCue(input)
        }
    }

    fun playStudioProbeAudio(path: String) {
        coordinator.playDubbedFile(path, volume = 1.0f, speed = config.value.voiceSpeed) {}
    }

    fun clearAllHistoryAndCache() {
        viewModelScope.launch {
            localRepo.clearAllLocalDubCache()
            cloudRepo.clearAllHistory()
            _bannerMessage.value = "Cleared local dubbing cache and synced cloud history."
        }
    }

    override fun onCleared() {
        super.onCleared()
        coordinator.release()
    }

    class Factory(
        private val context: Context,
        private val databaseId: String,
        private val currentUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = DuboraDatabase.getInstance(appContext)
            val importer = LocalMediaImporter(appContext)
            val localRepo = LocalLibraryRepository(db.duboraDao(), importer, appContext)
            val prefsStore = UserPreferencesDataStore(appContext)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            val cloudRepo = CloudSyncRepository(firestore)
            val mediaBridge = AndroidAudioExtractor(appContext)
            val geminiEngine = GeminiDubbingEngine()
            val ttsEngine = AndroidTtsVoiceEngine(appContext)
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
            )
            val coordinator = DubbingPlaybackCoordinator(
                context = appContext,
                mediaBridge = mediaBridge,
                translationEngine = geminiEngine,
                voiceEngine = ttsEngine,
                localRepo = localRepo,
                scope = scope
            )
            return DuboraViewModel(
                appContext = appContext,
                currentUserId = currentUserId,
                localRepo = localRepo,
                prefsStore = prefsStore,
                cloudRepo = cloudRepo,
                geminiEngine = geminiEngine,
                ttsEngine = ttsEngine,
                coordinator = coordinator,
                bridgeCapabilities = mediaBridge.capabilities
            ) as T
        }
    }

    private companion object {
        const val TAG = "DuboraVM"
    }
}
