package com.example.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.domain.model.VoiceGenderMode
import com.example.domain.model.VoiceStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

class AndroidTtsVoiceEngine(private val context: Context) : VoiceGenerationEngine {

    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var readyDeferred: CompletableDeferred<Boolean>? = null

    private suspend fun ensureInitialized(): TextToSpeech {
        val existing = tts
        val deferred = readyDeferred
        if (existing != null && deferred != null && deferred.isCompleted) {
            if (deferred.await()) return existing
        }
        val newDeferred = CompletableDeferred<Boolean>()
        readyDeferred = newDeferred
        return withContext(Dispatchers.Main) {
            val engine = TextToSpeech(context.applicationContext) { status ->
                newDeferred.complete(status == TextToSpeech.SUCCESS)
            }
            tts = engine
            val ok = withTimeout(10_000L) { newDeferred.await() }
            if (!ok) {
                throw IllegalStateException("Android TextToSpeech engine failed to initialize on this device.")
            }
            engine
        }
    }

    override suspend fun synthesizeSegment(
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
        targetMaxDurationMs: Long
    ): Result<SynthesizedSpeech> = mutex.withLock {
        try {
            val engine = ensureInitialized()
            val locale = Locale.forLanguageTag(targetLangCode)
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "On-device TTS data missing for $targetLangCode; using default voice fallback")
            }

            // Pick a matching voice when available on device
            val voices = runCatching { engine.voices }.getOrNull().orEmpty()
                .filter { it.locale.language.equals(locale.language, ignoreCase = true) }
            val desiredMale = when (voiceGenderMode) {
                VoiceGenderMode.NATURAL_MALE -> true
                VoiceGenderMode.NATURAL_FEMALE -> false
                VoiceGenderMode.SPEAKER_MATCHED -> speakerGender.equals("male", ignoreCase = true)
            }
            val matchedVoice = voices.firstOrNull { v ->
                val n = v.name.lowercase()
                if (desiredMale) n.contains("male") || n.contains("#m") || n.endsWith("m")
                else n.contains("female") || n.contains("#f") || n.endsWith("f")
            } ?: voices.firstOrNull()
            if (matchedVoice != null) {
                engine.voice = matchedVoice
            }

            // Compute expressive pitch & rate per speaker ID + voice style
            val speakerHash = (speakerId.hashCode() and 0x7FFFFFFF) % 5
            val speakerPitchOffset = when {
                voiceGenderMode == VoiceGenderMode.NATURAL_MALE -> -0.10f
                voiceGenderMode == VoiceGenderMode.NATURAL_FEMALE -> 0.10f
                speakerGender.equals("male", ignoreCase = true) -> -0.11f + (speakerHash * 0.02f)
                speakerGender.equals("female", ignoreCase = true) -> 0.11f - (speakerHash * 0.02f)
                else -> (speakerHash - 2) * 0.04f
            }
            val styleSpeedMultiplier = when (voiceStyle) {
                VoiceStyle.CINEMATIC -> 0.98f
                VoiceStyle.CONVERSATIONAL -> 1.04f
                VoiceStyle.DRAMATIC -> 0.94f
                VoiceStyle.DOCUMENTARY -> 0.96f
            }
            val estimatedBaseMs = (text.length.coerceAtLeast(1) * 68L)
            val durationFitBoost = if (targetMaxDurationMs in 700L..15_000L && estimatedBaseMs > targetMaxDurationMs) {
                (estimatedBaseMs.toFloat() / targetMaxDurationMs.toFloat()).coerceIn(1.0f, 1.32f)
            } else {
                1.0f
            }
            val finalPitch = (voicePitch + speakerPitchOffset).coerceIn(0.65f, 1.45f)
            val finalRate = (voiceSpeed * styleSpeedMultiplier * durationFitBoost).coerceIn(0.65f, 1.65f)

            engine.setPitch(finalPitch)
            engine.setSpeechRate(finalRate)

            val outDir = File(context.filesDir, "dub_audio/$movieId/$targetLangCode").apply {
                mkdirs()
            }
            val outFile = File(outDir, "$segmentId.wav")
            val synthDone = CompletableDeferred<Boolean>()

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == segmentId) {
                        synthDone.complete(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == segmentId) {
                        synthDone.complete(false)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == segmentId) {
                        synthDone.complete(false)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, segmentId)
            }
            val enqueueStatus = engine.synthesizeToFile(text, params, outFile, segmentId)
            if (enqueueStatus != TextToSpeech.SUCCESS) {
                return@withLock Result.failure(
                    IllegalStateException("TTS failed to enqueue synthesis for $segmentId")
                )
            }

            val completed = withTimeout(15_000L) { synthDone.await() }
            if (!completed || !outFile.exists() || outFile.length() == 0L) {
                return@withLock Result.failure(
                    IllegalStateException("Synthesized WAV output was empty for segment $segmentId")
                )
            }

            val durationMs = withContext(Dispatchers.IO) {
                readWavDurationMs(outFile)
            }

            Result.success(SynthesizedSpeech(filePath = outFile.absolutePath, durationMs = durationMs))
        } catch (e: Exception) {
            Log.e(TAG, "Voice synthesis failed for $segmentId", e)
            Result.failure(e)
        }
    }

    private fun readWavDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 2000L
        } catch (_: Exception) {
            2000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private companion object {
        const val TAG = "AndroidTtsVoiceEngine"
    }
}
