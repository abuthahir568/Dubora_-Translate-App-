package com.example.engine

import android.net.Uri
import com.example.domain.model.AudioTrackMode
import com.example.domain.model.DubSegment
import com.example.domain.model.DubbingConfig
import com.example.domain.model.VoiceGenderMode
import com.example.domain.model.VoiceStyle

data class AudioChunk(
    val movieId: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val sampleRateHz: Int,
    val channelCount: Int,
    val wavBytes: ByteArray,
    val rmsEnergy: Float = 1f,
    val hasAudioSignal: Boolean = true
)

data class RecognizedUtterance(
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val originalText: String,
    val detectedLanguageCode: String,
    val speakerHint: String,
    val speakerGenderHint: String
)

data class DiarizedUtterance(
    val utterance: RecognizedUtterance,
    val speakerId: String,
    val speakerLabel: String,
    val speakerGender: String
)

data class TranslatedUtterance(
    val diarized: DiarizedUtterance,
    val translatedText: String,
    val targetLangCode: String
)

data class SynthesizedSpeech(
    val filePath: String,
    val durationMs: Long
)

data class PlatformBridgeStatus(
    val platform: String,
    val playerEngine: String,
    val audioExtractor: String,
    val stemSeparationStatus: String,
    val backgroundAudioReady: Boolean,
    val pipSupported: Boolean
)

/**
 * Separate interface for Speech Recognition (ASR + timestamping).
 */
interface SpeechRecognitionEngine {
    suspend fun transcribeAudioChunk(
        chunk: AudioChunk,
        sourceLangHint: String
    ): Result<List<RecognizedUtterance>>
}

/**
 * Separate interface for Speaker Diarization (consistent speaker IDs across chunks).
 */
interface SpeakerDiarizationEngine {
    fun assignConsistentSpeakers(
        utterances: List<RecognizedUtterance>,
        knownSpeakers: MutableMap<String, String>
    ): List<DiarizedUtterance>
}

/**
 * Separate interface for Translation preserving dialogue timing & cinematic tone.
 */
interface TranslationEngine {
    suspend fun recognizeDiarizeAndTranslateChunk(
        chunk: AudioChunk,
        config: DubbingConfig,
        knownSpeakers: MutableMap<String, String>,
        onRetry: (suspend (RetryProgress) -> Unit)? = null
    ): Result<List<DubSegment>>

    suspend fun translateCueList(
        cues: List<RecognizedUtterance>,
        sourceLangCode: String,
        targetLangCode: String,
        modelId: String,
        onRetry: (suspend (RetryProgress) -> Unit)? = null
    ): Result<List<TranslatedUtterance>>
}

/**
 * Separate interface for Voice Generation (TTS / speaker-aware voice synthesis).
 */
interface VoiceGenerationEngine {
    suspend fun synthesizeSegment(
        movieId: String,
        segmentId: String,
        text: String,
        targetLangCode: String,
        speakerId: String,
        speakerGender: String,
        voiceGenderMode: VoiceGenderMode,
        voiceSpeed: Float,
        voicePitch: Float,
        voiceStyle: VoiceStyle,
        targetMaxDurationMs: Long = 0L
    ): Result<SynthesizedSpeech>

    fun shutdown()
}

/**
 * Separate interface for Audio Synchronization (timestamps, lookahead buffer, drift guard).
 */
interface AudioSynchronizationEngine {
    fun findActiveSegment(segments: List<DubSegment>, positionMs: Long): DubSegment?
    fun isBufferReadyAt(segments: List<DubSegment>, processedUntilMs: Long, positionMs: Long): Boolean
}

/**
 * Separate interface for Audio Mixing (original dialogue ducking, background preservation, dub playback).
 */
interface AudioMixingEngine {
    fun applyMixState(
        mode: AudioTrackMode,
        isDubSpeaking: Boolean,
        preserveBackgroundMusic: Boolean,
        reduceOriginalDialogue: Boolean,
        duckLevel: Float
    )

    fun playDubbedFile(filePath: String, volume: Float, speed: Float, onComplete: () -> Unit)
    fun stopDubbedPlayback()
    fun pauseDubbedPlayback()
    fun resumeDubbedPlayback()
}

/**
 * Cross-platform MediaBridge abstraction (Android Media3 implemented here;
 * iOS AVPlayer / Capacitor plugin spec documented for native packaging).
 */
interface MediaBridge {
    val capabilities: PlatformBridgeStatus
    suspend fun extractAudioChunk(
        uri: Uri,
        movieId: String,
        windowStartMs: Long,
        windowEndMs: Long
    ): Result<AudioChunk>
}
