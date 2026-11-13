package com.example.engine

import android.util.Log
import com.example.domain.model.DubSegment
import com.example.domain.model.DubbingConfig
import com.example.domain.model.LanguageCapabilityRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

interface GeminiModelInvoker {
    suspend fun generateAudioDubJson(
        modelId: String,
        wavBytes: ByteArray,
        prompt: String,
        responseSchema: Schema
    ): String

    suspend fun generateTextDubJson(
        modelId: String,
        prompt: String,
        responseSchema: Schema
    ): String
}

class FirebaseAiModelInvoker : GeminiModelInvoker {
    private val modelCache = mutableMapOf<String, com.google.firebase.ai.GenerativeModel>()

    private fun getOrCreateModel(
        modelId: String,
        schema: Schema
    ): com.google.firebase.ai.GenerativeModel {
        return modelCache.getOrPut(modelId) {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                    temperature = 0.2f
                }
            )
        }
    }

    override suspend fun generateAudioDubJson(
        modelId: String,
        wavBytes: ByteArray,
        prompt: String,
        responseSchema: Schema
    ): String {
        val model = getOrCreateModel(modelId, responseSchema)
        val response = withTimeout(45_000L) {
            model.generateContent(
                content {
                    inlineData(wavBytes, "audio/wav")
                    text(prompt)
                }
            )
        }
        return response.text ?: throw IllegalStateException("Gemini ($modelId) returned an empty response.")
    }

    override suspend fun generateTextDubJson(
        modelId: String,
        prompt: String,
        responseSchema: Schema
    ): String {
        val model = getOrCreateModel(modelId, responseSchema)
        val response = withTimeout(45_000L) {
            model.generateContent(prompt)
        }
        return response.text ?: throw IllegalStateException("Gemini ($modelId) returned an empty response.")
    }
}

class GeminiDubbingEngine(
    private val modelInvoker: GeminiModelInvoker = FirebaseAiModelInvoker(),
    private val delayStrategy: RetryDelayStrategy = CoroutineRetryDelayStrategy,
    private val retryPolicy: GeminiRetryPolicy = GeminiRetryPolicy()
) : SpeechRecognitionEngine,
    SpeakerDiarizationEngine,
    TranslationEngine {

    private val dubbingSegmentSchema = Schema.obj(
        properties = mapOf(
            "detectedSourceLanguage" to Schema.string(
                description = "ISO-639-1 language code detected in the audio"
            ),
            "segments" to Schema.array(
                description = "Timestamped dialogue segments inside this audio chunk",
                items = Schema.obj(
                    properties = mapOf(
                        "startOffsetMs" to Schema.integer(
                            description = "Start offset in milliseconds relative to chunk start"
                        ),
                        "endOffsetMs" to Schema.integer(
                            description = "End offset in milliseconds relative to chunk start"
                        ),
                        "speakerHint" to Schema.string(
                            description = "Consistent speaker key such as Speaker_1 or Speaker_2"
                        ),
                        "speakerGender" to Schema.enumeration(
                            values = listOf("male", "female", "neutral"),
                            description = "Perceived vocal register of the speaker"
                        ),
                        "originalText" to Schema.string(
                            description = "Verbatim transcribed dialogue in the original language"
                        ),
                        "translatedText" to Schema.string(
                            description = "Natural, timing-matched dubbing translation in the target language"
                        )
                    )
                )
            )
        )
    )

    override suspend fun transcribeAudioChunk(
        chunk: AudioChunk,
        sourceLangHint: String
    ): Result<List<RecognizedUtterance>> {
        if (!chunk.hasAudioSignal || chunk.wavBytes.isEmpty()) {
            return Result.success(emptyList())
        }
        val modelId = FAST_MODEL_ID
        val sourceSpec = LanguageCapabilityRegistry.findByCode(sourceLangHint)
        val prompt = buildString {
            append("Transcribe spoken dialogue in this movie clip. ")
            if (sourceLangHint != "auto") {
                append("Language: ${sourceSpec.displayName} (${sourceSpec.code}). ")
            } else {
                append("Auto-detect language. ")
            }
            append("Offsets 0..${chunk.windowEndMs - chunk.windowStartMs}ms, speakerHint (Speaker_1..), speakerGender. ")
            append("Set translatedText=originalText. Return empty segments if music/silence only.")
        }

        return executeWithGeminiRetry(
            modelId = modelId,
            policy = retryPolicy,
            delayStrategy = delayStrategy,
            onRetry = { progress ->
                Log.w(TAG, "Retrying ASR ($modelId) attempt ${progress.attempt}/${progress.maxRetries} in ${progress.delayMs}ms (${progress.category})")
            }
        ) {
            val raw = modelInvoker.generateAudioDubJson(
                modelId = modelId,
                wavBytes = chunk.wavBytes,
                prompt = prompt,
                responseSchema = dubbingSegmentSchema
            )
            val root = JSONObject(raw)
            val detectedLang = root.optString("detectedSourceLanguage", sourceLangHint)
            val arr = root.optJSONArray("segments")
            val out = mutableListOf<RecognizedUtterance>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val orig = obj.optString("originalText").trim()
                    if (orig.isNotBlank()) {
                        out.add(
                            RecognizedUtterance(
                                startOffsetMs = obj.optLong("startOffsetMs", 0L).coerceAtLeast(0L),
                                endOffsetMs = obj.optLong("endOffsetMs", 2500L).coerceAtLeast(500L),
                                originalText = orig,
                                detectedLanguageCode = detectedLang,
                                speakerHint = obj.optString("speakerHint", "Speaker_1"),
                                speakerGenderHint = obj.optString("speakerGender", "neutral")
                            )
                        )
                    }
                }
            }
            out
        }.onFailure { err ->
            if (err !is CancellationException) {
                Log.e(TAG, "Speech recognition failed on $modelId", err)
            }
        }
    }

    override fun assignConsistentSpeakers(
        utterances: List<RecognizedUtterance>,
        knownSpeakers: MutableMap<String, String>
    ): List<DiarizedUtterance> {
        return utterances.map { utt ->
            val key = "${utt.speakerHint}_${utt.speakerGenderHint}".lowercase()
            val speakerId = knownSpeakers.getOrPut(key) {
                "SPK_${knownSpeakers.size + 1}"
            }
            val label = "Speaker ${speakerId.removePrefix("SPK_")}"
            DiarizedUtterance(
                utterance = utt,
                speakerId = speakerId,
                speakerLabel = label,
                speakerGender = utt.speakerGenderHint
            )
        }
    }

    override suspend fun recognizeDiarizeAndTranslateChunk(
        chunk: AudioChunk,
        config: DubbingConfig,
        knownSpeakers: MutableMap<String, String>,
        onRetry: (suspend (RetryProgress) -> Unit)?
    ): Result<List<DubSegment>> {
        if (!chunk.hasAudioSignal || chunk.wavBytes.isEmpty()) {
            return Result.success(emptyList())
        }

        val (supported, reason) = LanguageCapabilityRegistry.isSupportedPair(
            config.sourceLangCode,
            config.targetLangCode
        )
        val primaryModelId = config.translationQuality.modelId
        if (!supported) {
            return Result.failure(
                DubbingApiException(
                    DubbingErrorInfo(
                        category = DubbingErrorCategory.UNSUPPORTED_OR_SAFETY,
                        apiName = GeminiErrorClassifier.API_NAME,
                        modelId = primaryModelId,
                        userMessage = reason ?: "Unsupported language pair.",
                        actionableHint = "Select a different source or target language in AI Dub settings.",
                        technicalDetail = reason ?: "Unsupported language pair"
                    )
                )
            )
        }

        val sourceSpec = LanguageCapabilityRegistry.findByCode(config.sourceLangCode)
        val targetSpec = LanguageCapabilityRegistry.findByCode(config.targetLangCode)
        val clipDurationMs = (chunk.windowEndMs - chunk.windowStartMs).coerceAtLeast(1000L)

        val prompt = buildString {
            append("Cinema dubbing ASR+translation. Clip: ${clipDurationMs}ms. ")
            if (config.sourceLangCode == "auto") {
                append("Source: auto-detect. ")
            } else {
                append("Source: ${sourceSpec.displayName} (${sourceSpec.code}). ")
            }
            append("Target: ${targetSpec.displayName} (${targetSpec.code}), style: ${config.voiceStyle.label}. ")
            append("Return JSON segments with startOffsetMs/endOffsetMs (0..$clipDurationMs), consistent speakerHint (Speaker_1..), ")
            append("speakerGender, verbatim originalText, and concise timing-matched translatedText in ${targetSpec.displayName}. ")
            append("Return empty segments if music/SFX/silence only.")
        }

        suspend fun runForModel(modelId: String): Result<List<DubSegment>> {
            return executeWithGeminiRetry(
                modelId = modelId,
                policy = retryPolicy,
                delayStrategy = delayStrategy,
                onRetry = { progress ->
                    Log.w(TAG, "Retrying dubbing chunk ($modelId) attempt ${progress.attempt}/${progress.maxRetries} in ${progress.delayMs}ms (${progress.category})")
                    onRetry?.invoke(progress)
                }
            ) {
                val raw = modelInvoker.generateAudioDubJson(
                    modelId = modelId,
                    wavBytes = chunk.wavBytes,
                    prompt = prompt,
                    responseSchema = dubbingSegmentSchema
                )
                parseAudioDubSegments(
                    raw = raw,
                    chunk = chunk,
                    config = config,
                    clipDurationMs = clipDurationMs,
                    knownSpeakers = knownSpeakers
                )
            }
        }

        val primaryResult = runForModel(primaryModelId)
        if (primaryResult.isSuccess || primaryModelId == FAST_MODEL_ID) {
            return primaryResult.onFailure { err ->
                if (err !is CancellationException) {
                    Log.e(TAG, "Gemini audio dubbing failed on $primaryModelId", err)
                }
            }
        }

        // Automatic fallback to gemini-3.5-flash-lite if gemini-3.8-flash hit quota/rate/overload
        val primaryErr = (primaryResult.exceptionOrNull() as? DubbingApiException)?.errorInfo
        val shouldFallbackToFast = primaryErr != null && primaryErr.category in setOf(
            DubbingErrorCategory.QUOTA_EXCEEDED,
            DubbingErrorCategory.RATE_LIMITED_429,
            DubbingErrorCategory.OVERLOADED_503
        )
        if (!shouldFallbackToFast) {
            return primaryResult
        }

        Log.w(TAG, "Primary model $primaryModelId failed (${primaryErr?.category}); falling back to $FAST_MODEL_ID")
        onRetry?.invoke(
            RetryProgress(
                attempt = 1,
                maxRetries = retryPolicy.maxRetries,
                delayMs = 0L,
                category = primaryErr!!.category,
                modelId = FAST_MODEL_ID,
                isModelFallback = true
            )
        )
        return runForModel(FAST_MODEL_ID).onFailure { err ->
            if (err !is CancellationException) {
                Log.e(TAG, "Fallback model $FAST_MODEL_ID also failed", err)
            }
        }
    }

    private fun parseAudioDubSegments(
        raw: String,
        chunk: AudioChunk,
        config: DubbingConfig,
        clipDurationMs: Long,
        knownSpeakers: MutableMap<String, String>
    ): List<DubSegment> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("segments")
        val utterances = mutableListOf<Pair<RecognizedUtterance, String>>()
        val detectedLang = root.optString("detectedSourceLanguage", config.sourceLangCode)

        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val orig = obj.optString("originalText").trim()
                val trans = obj.optString("translatedText").trim()
                if (orig.isNotBlank() && trans.isNotBlank()) {
                    val startOff = obj.optLong("startOffsetMs", 0L).coerceIn(0L, clipDurationMs)
                    val endOff = obj.optLong("endOffsetMs", startOff + 2200L)
                        .coerceIn(startOff + 400L, clipDurationMs + 1500L)
                    utterances.add(
                        RecognizedUtterance(
                            startOffsetMs = startOff,
                            endOffsetMs = endOff,
                            originalText = orig,
                            detectedLanguageCode = detectedLang,
                            speakerHint = obj.optString("speakerHint", "Speaker_1"),
                            speakerGenderHint = obj.optString("speakerGender", "neutral")
                        ) to trans
                    )
                }
            }
        }

        val diarized = assignConsistentSpeakers(utterances.map { it.first }, knownSpeakers)
        return diarized.mapIndexed { idx, d ->
            val trans = utterances[idx].second
            val absStart = chunk.windowStartMs + d.utterance.startOffsetMs
            val absEnd = chunk.windowStartMs + d.utterance.endOffsetMs
            DubSegment(
                id = "${chunk.movieId}_${config.targetLangCode}_${absStart}_${UUID.randomUUID().toString().take(6)}",
                movieId = chunk.movieId,
                targetLangCode = config.targetLangCode,
                startMs = absStart,
                endMs = absEnd,
                speakerId = d.speakerId,
                speakerLabel = d.speakerLabel,
                speakerGender = d.speakerGender,
                originalText = d.utterance.originalText,
                translatedText = trans
            )
        }
    }

    override suspend fun translateCueList(
        cues: List<RecognizedUtterance>,
        sourceLangCode: String,
        targetLangCode: String,
        modelId: String,
        onRetry: (suspend (RetryProgress) -> Unit)?
    ): Result<List<TranslatedUtterance>> {
        if (cues.isEmpty()) return Result.success(emptyList())

        val (supported, reason) = LanguageCapabilityRegistry.isSupportedPair(sourceLangCode, targetLangCode)
        if (!supported) {
            return Result.failure(
                DubbingApiException(
                    DubbingErrorInfo(
                        category = DubbingErrorCategory.UNSUPPORTED_OR_SAFETY,
                        apiName = GeminiErrorClassifier.API_NAME,
                        modelId = modelId,
                        userMessage = reason ?: "Unsupported language pair.",
                        actionableHint = "Select a different source or target language.",
                        technicalDetail = reason ?: "Unsupported language pair"
                    )
                )
            )
        }

        val targetSpec = LanguageCapabilityRegistry.findByCode(targetLangCode)
        val prompt = buildString {
            append("Translate the following timestamped movie dialogue lines into ${targetSpec.displayName} (${targetSpec.code}), preserving speaking cadence and meaning:\n")
            cues.forEachIndexed { idx, cue ->
                append("[$idx] (${cue.startOffsetMs}ms-${cue.endOffsetMs}ms, ${cue.speakerHint}, ${cue.speakerGenderHint}): ${cue.originalText}\n")
            }
        }

        suspend fun runTextModel(targetModel: String): Result<List<TranslatedUtterance>> {
            return executeWithGeminiRetry(
                modelId = targetModel,
                policy = retryPolicy,
                delayStrategy = delayStrategy,
                onRetry = { progress ->
                    Log.w(TAG, "Retrying cue translation ($targetModel) attempt ${progress.attempt}/${progress.maxRetries} in ${progress.delayMs}ms (${progress.category})")
                    onRetry?.invoke(progress)
                }
            ) {
                val raw = modelInvoker.generateTextDubJson(
                    modelId = targetModel,
                    prompt = prompt,
                    responseSchema = dubbingSegmentSchema
                )
                val root = JSONObject(raw)
                val arr = root.optJSONArray("segments")
                val knownSpeakers = mutableMapOf<String, String>()
                val diarized = assignConsistentSpeakers(cues, knownSpeakers)
                diarized.mapIndexed { idx, d ->
                    val obj = if (arr != null && idx < arr.length()) arr.getJSONObject(idx) else null
                    val translated = obj?.optString("translatedText")?.trim()
                    if (translated.isNullOrBlank()) {
                        throw IllegalStateException("Gemini ($targetModel) returned an incomplete translation cue.")
                    }
                    TranslatedUtterance(
                        diarized = d,
                        translatedText = translated,
                        targetLangCode = targetLangCode
                    )
                }
            }
        }

        val primaryResult = runTextModel(modelId)
        if (primaryResult.isSuccess || modelId == FAST_MODEL_ID) {
            return primaryResult.onFailure { err ->
                if (err !is CancellationException) {
                    Log.e(TAG, "translateCueList failed on $modelId", err)
                }
            }
        }

        val primaryErr = (primaryResult.exceptionOrNull() as? DubbingApiException)?.errorInfo
        val shouldFallbackToFast = primaryErr != null && primaryErr.category in setOf(
            DubbingErrorCategory.QUOTA_EXCEEDED,
            DubbingErrorCategory.RATE_LIMITED_429,
            DubbingErrorCategory.OVERLOADED_503
        )
        if (!shouldFallbackToFast) {
            return primaryResult
        }

        onRetry?.invoke(
            RetryProgress(
                attempt = 1,
                maxRetries = retryPolicy.maxRetries,
                delayMs = 0L,
                category = primaryErr!!.category,
                modelId = FAST_MODEL_ID,
                isModelFallback = true
            )
        )
        return runTextModel(FAST_MODEL_ID)
    }

    companion object {
        const val FAST_MODEL_ID = "gemini-3.5-flash-lite"
        const val QUALITY_MODEL_ID = "gemini-3.8-flash"
        const val DEFAULT_MODEL_ID = FAST_MODEL_ID
        private const val TAG = "GeminiDubbingEngine"
    }
}
