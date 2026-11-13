package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DubbingConfig
import com.example.domain.model.SubtitleMode
import com.example.domain.model.SubtitlePosition
import com.example.domain.model.TranslationQuality
import com.example.domain.model.VoiceGenderMode
import com.example.domain.model.VoiceStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.duboraPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dubora_preferences"
)

class UserPreferencesDataStore(context: Context) {
    private val dataStore = context.applicationContext.duboraPrefsDataStore

    private object Keys {
        val SOURCE_LANG = stringPreferencesKey("source_lang")
        val TARGET_LANG = stringPreferencesKey("target_lang")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_STYLE = stringPreferencesKey("voice_style")
        val PRESERVE_BG_MUSIC = booleanPreferencesKey("preserve_bg_music")
        val REDUCE_ORIG_DIALOGUE = booleanPreferencesKey("reduce_orig_dialogue")
        val DUCK_LEVEL = floatPreferencesKey("duck_level")
        val SUBTITLE_MODE = stringPreferencesKey("subtitle_mode")
        val SUBTITLE_FONT_SP = floatPreferencesKey("subtitle_font_sp")
        val SUBTITLE_BG_OPACITY = floatPreferencesKey("subtitle_bg_opacity")
        val SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
        val TRANSLATION_QUALITY = stringPreferencesKey("translation_quality")
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
        val AUTO_DUBBING = booleanPreferencesKey("auto_dubbing")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    val configFlow: Flow<DubbingConfig> = dataStore.data.map { prefs ->
        DubbingConfig(
            sourceLangCode = prefs[Keys.SOURCE_LANG] ?: "auto",
            targetLangCode = prefs[Keys.TARGET_LANG] ?: "es",
            voiceGenderMode = VoiceGenderMode.fromId(prefs[Keys.VOICE_GENDER] ?: "speaker_matched"),
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.0f,
            voiceStyle = VoiceStyle.fromId(prefs[Keys.VOICE_STYLE] ?: "cinematic"),
            preserveBackgroundMusic = prefs[Keys.PRESERVE_BG_MUSIC] ?: true,
            reduceOriginalDialogue = prefs[Keys.REDUCE_ORIG_DIALOGUE] ?: true,
            originalDialogueDuckLevel = prefs[Keys.DUCK_LEVEL] ?: 0.16f,
            subtitleMode = SubtitleMode.fromId(prefs[Keys.SUBTITLE_MODE] ?: "dual"),
            subtitleFontSizeSp = prefs[Keys.SUBTITLE_FONT_SP] ?: 16f,
            subtitleBgOpacity = prefs[Keys.SUBTITLE_BG_OPACITY] ?: 0.72f,
            subtitlePosition = SubtitlePosition.fromId(prefs[Keys.SUBTITLE_POSITION] ?: "bottom"),
            translationQuality = TranslationQuality.fromId(prefs[Keys.TRANSLATION_QUALITY] ?: "low_latency"),
            lookaheadBufferSeconds = prefs[Keys.BUFFER_SECONDS] ?: 24,
            autoDubbing = prefs[Keys.AUTO_DUBBING] ?: false,
            wifiOnlyProcessing = prefs[Keys.WIFI_ONLY] ?: false
        )
    }

    suspend fun updateConfig(transform: (DubbingConfig) -> DubbingConfig) {
        dataStore.edit { prefs ->
            val current = DubbingConfig(
                sourceLangCode = prefs[Keys.SOURCE_LANG] ?: "auto",
                targetLangCode = prefs[Keys.TARGET_LANG] ?: "es",
                voiceGenderMode = VoiceGenderMode.fromId(prefs[Keys.VOICE_GENDER] ?: "speaker_matched"),
                voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
                voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.0f,
                voiceStyle = VoiceStyle.fromId(prefs[Keys.VOICE_STYLE] ?: "cinematic"),
                preserveBackgroundMusic = prefs[Keys.PRESERVE_BG_MUSIC] ?: true,
                reduceOriginalDialogue = prefs[Keys.REDUCE_ORIG_DIALOGUE] ?: true,
                originalDialogueDuckLevel = prefs[Keys.DUCK_LEVEL] ?: 0.16f,
                subtitleMode = SubtitleMode.fromId(prefs[Keys.SUBTITLE_MODE] ?: "dual"),
                subtitleFontSizeSp = prefs[Keys.SUBTITLE_FONT_SP] ?: 16f,
                subtitleBgOpacity = prefs[Keys.SUBTITLE_BG_OPACITY] ?: 0.72f,
                subtitlePosition = SubtitlePosition.fromId(prefs[Keys.SUBTITLE_POSITION] ?: "bottom"),
                translationQuality = TranslationQuality.fromId(prefs[Keys.TRANSLATION_QUALITY] ?: "low_latency"),
                lookaheadBufferSeconds = prefs[Keys.BUFFER_SECONDS] ?: 24,
                autoDubbing = prefs[Keys.AUTO_DUBBING] ?: false,
                wifiOnlyProcessing = prefs[Keys.WIFI_ONLY] ?: false
            )
            val next = transform(current)
            prefs[Keys.SOURCE_LANG] = next.sourceLangCode
            prefs[Keys.TARGET_LANG] = next.targetLangCode
            prefs[Keys.VOICE_GENDER] = next.voiceGenderMode.id
            prefs[Keys.VOICE_SPEED] = next.voiceSpeed
            prefs[Keys.VOICE_PITCH] = next.voicePitch
            prefs[Keys.VOICE_STYLE] = next.voiceStyle.id
            prefs[Keys.PRESERVE_BG_MUSIC] = next.preserveBackgroundMusic
            prefs[Keys.REDUCE_ORIG_DIALOGUE] = next.reduceOriginalDialogue
            prefs[Keys.DUCK_LEVEL] = next.originalDialogueDuckLevel
            prefs[Keys.SUBTITLE_MODE] = next.subtitleMode.id
            prefs[Keys.SUBTITLE_FONT_SP] = next.subtitleFontSizeSp
            prefs[Keys.SUBTITLE_BG_OPACITY] = next.subtitleBgOpacity
            prefs[Keys.SUBTITLE_POSITION] = next.subtitlePosition.id
            prefs[Keys.TRANSLATION_QUALITY] = next.translationQuality.id
            prefs[Keys.BUFFER_SECONDS] = next.lookaheadBufferSeconds
            prefs[Keys.AUTO_DUBBING] = next.autoDubbing
            prefs[Keys.WIFI_ONLY] = next.wifiOnlyProcessing
        }
    }
}
