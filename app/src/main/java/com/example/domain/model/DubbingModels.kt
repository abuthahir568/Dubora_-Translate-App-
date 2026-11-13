package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VoiceGenderMode(val id: String, val label: String, val subtitle: String) {
    SPEAKER_MATCHED(
        "speaker_matched",
        "Speaker-Matched AI",
        "Tracks speaker IDs and adapts voice pitch & timbre per character"
    ),
    NATURAL_MALE("natural_male", "Natural Male", "Warm cinematic baritone voice"),
    NATURAL_FEMALE("natural_female", "Natural Female", "Expressive natural soprano/alto voice");

    companion object {
        fun fromId(id: String): VoiceGenderMode =
            entries.firstOrNull { it.id == id } ?: SPEAKER_MATCHED
    }
}

enum class VoiceStyle(val id: String, val label: String) {
    CINEMATIC("cinematic", "Cinematic"),
    CONVERSATIONAL("conversational", "Conversational"),
    DRAMATIC("dramatic", "Dramatic"),
    DOCUMENTARY("documentary", "Documentary");

    companion object {
        fun fromId(id: String): VoiceStyle =
            entries.firstOrNull { it.id == id } ?: CINEMATIC
    }
}

enum class TranslationQuality(val id: String, val label: String, val modelId: String, val description: String) {
    LOW_LATENCY(
        "low_latency",
        "Fast Stream",
        "gemini-3.5-flash-lite",
        "Default ultra-fast model for instant dubbing & 3-hour movies"
    ),
    BALANCED(
        "balanced",
        "Cinema Balanced",
        "gemini-3.8-flash",
        "Rich Gemini 3.8 Flash dialogue nuance (auto-falls back to Fast Stream on quota)"
    ),
    CINEMA_HIGH(
        "cinema_high",
        "Studio Nuance",
        "gemini-3.8-flash",
        "Maximum idiomatic cinema translation"
    );

    companion object {
        fun fromId(id: String): TranslationQuality =
            entries.firstOrNull { it.id == id } ?: LOW_LATENCY
    }
}

enum class SubtitleMode(val id: String, val label: String) {
    OFF("off", "Off"),
    ORIGINAL("original", "Original"),
    AI_TRANSLATED("ai_translated", "AI Subtitles"),
    DUAL("dual", "Dual Subtitles");

    companion object {
        fun fromId(id: String): SubtitleMode =
            entries.firstOrNull { it.id == id } ?: DUAL
    }
}

enum class SubtitlePosition(val id: String, val label: String) {
    BOTTOM("bottom", "Bottom"),
    TOP("top", "Top"),
    CINEMA_BAR("cinema_bar", "Lower Safe Bar");

    companion object {
        fun fromId(id: String): SubtitlePosition =
            entries.firstOrNull { it.id == id } ?: BOTTOM
    }
}

enum class AudioTrackMode {
    ORIGINAL,
    AI_DUBBED
}

enum class DubbingPipelineStage(val label: String, val isBusy: Boolean) {
    IDLE("Ready to dub", false),
    LISTENING("Listening to movie audio", true),
    DETECTING_DIALOGUE("Detecting dialogue & speakers", true),
    TRANSLATING("Translating with Gemini AI", true),
    RETRYING("High demand — retrying AI", true),
    GENERATING_VOICE("Generating AI voice", true),
    READY_TO_PLAY("Ready to play", false),
    BUFFERING("Buffering synchronized dub", true),
    ERROR("Pipeline error", false)
}

data class DubbingConfig(
    val sourceLangCode: String = "auto",
    val targetLangCode: String = "es",
    val voiceGenderMode: VoiceGenderMode = VoiceGenderMode.SPEAKER_MATCHED,
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val voiceStyle: VoiceStyle = VoiceStyle.CINEMATIC,
    val preserveBackgroundMusic: Boolean = true,
    val reduceOriginalDialogue: Boolean = true,
    val originalDialogueDuckLevel: Float = 0.16f,
    val subtitleMode: SubtitleMode = SubtitleMode.DUAL,
    val subtitleFontSizeSp: Float = 16f,
    val subtitleBgOpacity: Float = 0.72f,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val translationQuality: TranslationQuality = TranslationQuality.LOW_LATENCY,
    val lookaheadBufferSeconds: Int = 24,
    val autoDubbing: Boolean = false,
    val wifiOnlyProcessing: Boolean = false
)

data class DubSegment(
    val id: String,
    val movieId: String,
    val targetLangCode: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String,
    val speakerLabel: String,
    val speakerGender: String,
    val originalText: String,
    val translatedText: String,
    val synthesizedAudioPath: String? = null,
    val synthesizedDurationMs: Long = 0L
)

data class LocalMovie(
    val id: String,
    val uriString: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val lastPositionMs: Long,
    val lastPlayedAt: Long,
    val addedAt: Long,
    val thumbnailPath: String?,
    val hasCachedDub: Boolean = false,
    val cachedDubLang: String? = null,
    val isDownloadedDub: Boolean = false
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val defaultSourceLang: String = "auto",
    val defaultTargetLang: String = "es",
    val defaultVoiceGender: String = "speaker_matched",
    val autoDubbing: Boolean = false,
    val wifiOnlyProcessing: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DubbingSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val movieTitle: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val sourceLang: String = "auto",
    val targetLang: String = "es",
    val voiceProfile: String = "speaker_matched",
    val segmentsProcessed: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
