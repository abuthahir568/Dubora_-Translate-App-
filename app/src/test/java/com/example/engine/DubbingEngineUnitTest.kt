package com.example.engine

import com.example.domain.model.DubSegment
import com.example.domain.model.DubbingConfig
import com.example.domain.model.TranslationQuality
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DubbingEngineUnitTest {

    @Test
    fun assignConsistentSpeakers_mapsRepeatedSpeakersToSameId() {
        val engine = GeminiDubbingEngine()
        val knownSpeakers = mutableMapOf<String, String>()

        val firstBatch = engine.assignConsistentSpeakers(
            utterances = listOf(
                RecognizedUtterance(0L, 1200L, "Hello there.", "en", "Speaker_1", "male"),
                RecognizedUtterance(1500L, 2600L, "Who is it?", "en", "Speaker_2", "female")
            ),
            knownSpeakers = knownSpeakers
        )

        val secondBatch = engine.assignConsistentSpeakers(
            utterances = listOf(
                RecognizedUtterance(3000L, 4200L, "It's me.", "en", "Speaker_1", "male")
            ),
            knownSpeakers = knownSpeakers
        )

        assertEquals("SPK_1", firstBatch[0].speakerId)
        assertEquals("SPK_2", firstBatch[1].speakerId)
        assertEquals("SPK_1", secondBatch[0].speakerId)
    }

    @Test
    fun retryPolicy_503Overload_retriesWithExponentialBackoffAndSucceedsOnThirdAttempt() = runTest {
        var calls = 0
        val recordedDelays = mutableListOf<Long>()
        val retryUpdates = mutableListOf<RetryProgress>()

        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String {
                calls++
                if (calls <= 2) {
                    throw RuntimeException(
                        "503 UNAVAILABLE: This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later."
                    )
                }
                return """
                    {
                      "detectedSourceLanguage": "en",
                      "segments": [
                        {
                          "startOffsetMs": 100,
                          "endOffsetMs": 1400,
                          "speakerHint": "Speaker_1",
                          "speakerGender": "male",
                          "originalText": "We need to move now.",
                          "translatedText": "Tenemos que movernos ahora."
                        }
                      ]
                    }
                """.trimIndent()
            }

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String = ""
        }

        val engine = GeminiDubbingEngine(
            modelInvoker = fakeInvoker,
            delayStrategy = RetryDelayStrategy { ms -> recordedDelays.add(ms) },
            retryPolicy = GeminiRetryPolicy(maxRetries = 3, initialDelayMs = 2000L, backoffMultiplier = 2.0)
        )

        val result = engine.recognizeDiarizeAndTranslateChunk(
            chunk = AudioChunk("mov1", 0L, 6000L, 16000, 1, ByteArray(16)),
            config = DubbingConfig(sourceLangCode = "en", targetLangCode = "es"),
            knownSpeakers = mutableMapOf(),
            onRetry = { retryUpdates.add(it) }
        )

        assertTrue(result.isSuccess)
        assertEquals(3, calls)
        assertEquals(listOf(2000L, 4000L), recordedDelays)
        assertEquals(2, retryUpdates.size)
        assertEquals(DubbingErrorCategory.OVERLOADED_503, retryUpdates[0].category)
        assertEquals("Tenemos que movernos ahora.", result.getOrThrow().first().translatedText)
    }

    @Test
    fun retryPolicy_429RateLimit_stopsAfterMax3RetriesAndNeverFakesResult() = runTest {
        var calls = 0
        val recordedDelays = mutableListOf<Long>()

        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String = ""

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String {
                calls++
                throw RuntimeException("HTTP 429 RESOURCE_EXHAUSTED: Rate limit exceeded for requests per minute.")
            }
        }

        val engine = GeminiDubbingEngine(
            modelInvoker = fakeInvoker,
            delayStrategy = RetryDelayStrategy { ms -> recordedDelays.add(ms) },
            retryPolicy = GeminiRetryPolicy(maxRetries = 3, initialDelayMs = 2000L, backoffMultiplier = 2.0)
        )

        val result = engine.translateCueList(
            cues = listOf(RecognizedUtterance(0L, 1000L, "Hello", "en", "Speaker_1", "male")),
            sourceLangCode = "en",
            targetLangCode = "es",
            modelId = GeminiDubbingEngine.FAST_MODEL_ID
        )

        assertTrue(result.isFailure)
        assertEquals(4, calls) // 1 initial attempt + max 3 retry attempts
        assertEquals(listOf(2000L, 4000L, 8000L), recordedDelays)
        val err = result.exceptionOrNull() as DubbingApiException
        assertEquals(DubbingErrorCategory.RATE_LIMITED_429, err.errorInfo.category)
        assertTrue(err.errorInfo.retriesExhausted)
        assertEquals(GeminiDubbingEngine.FAST_MODEL_ID, err.errorInfo.modelId)
    }

    @Test
    fun errorClassifier_configurationAndQuotaErrorsFailFastWithoutRetries() = runTest {
        var calls = 0
        val recordedDelays = mutableListOf<Long>()

        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String = ""

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String {
                calls++
                throw RuntimeException("HTTP 403 PERMISSION_DENIED: App Check token invalid.")
            }
        }

        val engine = GeminiDubbingEngine(
            modelInvoker = fakeInvoker,
            delayStrategy = RetryDelayStrategy { ms -> recordedDelays.add(ms) }
        )

        val configResult = engine.translateCueList(
            cues = listOf(RecognizedUtterance(0L, 1000L, "Hello", "en", "Speaker_1", "male")),
            sourceLangCode = "en",
            targetLangCode = "es",
            modelId = "gemini-3.8-flash"
        )

        assertTrue(configResult.isFailure)
        assertEquals(1, calls)
        assertTrue(recordedDelays.isEmpty())
        val configErr = (configResult.exceptionOrNull() as DubbingApiException).errorInfo
        assertEquals(DubbingErrorCategory.CONFIGURATION_ERROR, configErr.category)
        assertFalse(configErr.isRetryable)

        val quotaErr = GeminiErrorClassifier.classify(
            RuntimeException("You have exceeded your current quota, please check your plan and billing details."),
            modelId = "gemini-3.8-flash"
        )
        assertEquals(DubbingErrorCategory.QUOTA_EXCEEDED, quotaErr.category)
        assertFalse(quotaErr.isRetryable)
    }

    @Test
    fun errorClassifier_perMinute429WithBillingBoilerplate_isRetryableAndHonorsRetryInDelay() = runTest {
        var calls = 0
        val recordedDelays = mutableListOf<Long>()

        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String {
                calls++
                if (calls == 1) {
                    throw RuntimeException(
                        "You exceeded your current quota, please check your plan and billing details. " +
                            "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests per minute. Please retry in 3.2s."
                    )
                }
                return """
                    {
                      "detectedSourceLanguage": "en",
                      "segments": [
                        {
                          "startOffsetMs": 0,
                          "endOffsetMs": 1500,
                          "speakerHint": "Speaker_1",
                          "speakerGender": "female",
                          "originalText": "Good morning!",
                          "translatedText": "¡Buenos días!"
                        }
                      ]
                    }
                """.trimIndent()
            }

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String = ""
        }

        val engine = GeminiDubbingEngine(
            modelInvoker = fakeInvoker,
            delayStrategy = RetryDelayStrategy { ms -> recordedDelays.add(ms) },
            retryPolicy = GeminiRetryPolicy(maxRetries = 3, initialDelayMs = 2000L, backoffMultiplier = 2.0)
        )

        val result = engine.recognizeDiarizeAndTranslateChunk(
            chunk = AudioChunk("mov_10s", 0L, 10_000L, 16000, 1, ByteArray(32), rmsEnergy = 0.08f, hasAudioSignal = true),
            config = DubbingConfig(
                sourceLangCode = "en",
                targetLangCode = "es",
                translationQuality = TranslationQuality.LOW_LATENCY
            ),
            knownSpeakers = mutableMapOf()
        )

        assertTrue(result.isSuccess)
        assertEquals(2, calls)
        assertEquals(1, recordedDelays.size)
        assertEquals(3200L, recordedDelays.first())
        assertEquals("¡Buenos días!", result.getOrThrow().first().translatedText)
    }

    @Test
    fun fallback_qualityModelLimitZeroQuota_automaticallySwitchesToFastStreamModel() = runTest {
        val invokedModels = mutableListOf<String>()
        val progressEvents = mutableListOf<RetryProgress>()

        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String {
                invokedModels.add(modelId)
                if (modelId == GeminiDubbingEngine.QUALITY_MODEL_ID) {
                    throw RuntimeException("429 Quota exceeded for requests_per_day, limit: 0 on model gemini-3.8-flash")
                }
                return """
                    {
                      "detectedSourceLanguage": "en",
                      "segments": [
                        {
                          "startOffsetMs": 200,
                          "endOffsetMs": 1800,
                          "speakerHint": "Speaker_1",
                          "speakerGender": "male",
                          "originalText": "Run faster!",
                          "translatedText": "¡Corre más rápido!"
                        }
                      ]
                    }
                """.trimIndent()
            }

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String = ""
        }

        val engine = GeminiDubbingEngine(
            modelInvoker = fakeInvoker,
            delayStrategy = RetryDelayStrategy { }
        )

        val result = engine.recognizeDiarizeAndTranslateChunk(
            chunk = AudioChunk("mov_3hr", 0L, 24_000L, 16000, 1, ByteArray(32), rmsEnergy = 0.12f, hasAudioSignal = true),
            config = DubbingConfig(
                sourceLangCode = "en",
                targetLangCode = "es",
                translationQuality = TranslationQuality.BALANCED
            ),
            knownSpeakers = mutableMapOf(),
            onRetry = { progressEvents.add(it) }
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf(GeminiDubbingEngine.QUALITY_MODEL_ID, GeminiDubbingEngine.FAST_MODEL_ID), invokedModels)
        assertTrue(progressEvents.any { it.isModelFallback && it.modelId == GeminiDubbingEngine.FAST_MODEL_ID })
        assertEquals("¡Corre más rápido!", result.getOrThrow().first().translatedText)
    }

    @Test
    fun silentChunk_skipsGeminiApiCallCompletely() = runTest {
        var calls = 0
        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String {
                calls++
                return "{}"
            }

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String = ""
        }

        val engine = GeminiDubbingEngine(modelInvoker = fakeInvoker)
        val result = engine.recognizeDiarizeAndTranslateChunk(
            chunk = AudioChunk(
                movieId = "mov_3hr",
                windowStartMs = 600_000L,
                windowEndMs = 624_000L,
                sampleRateHz = 16000,
                channelCount = 1,
                wavBytes = ByteArray(0),
                rmsEnergy = 0.0f,
                hasAudioSignal = false
            ),
            config = DubbingConfig(),
            knownSpeakers = mutableMapOf()
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun parseAudioDubSegments_normalizesOverlappingTurnBoundariesAndDeterministicIds() = runTest {
        val fakeInvoker = object : GeminiModelInvoker {
            override suspend fun generateAudioDubJson(
                modelId: String,
                wavBytes: ByteArray,
                prompt: String,
                responseSchema: Schema
            ): String = """
                {
                  "detectedSourceLanguage": "en",
                  "segments": [
                    {
                      "startOffsetMs": 0,
                      "endOffsetMs": 2500,
                      "speakerHint": "Speaker_1",
                      "speakerGender": "male",
                      "originalText": "Do you have any experience?",
                      "translatedText": "உங்களுக்கு ஏதாவது அனுபவம் இருக்கா?"
                    },
                    {
                      "startOffsetMs": 1800,
                      "endOffsetMs": 6000,
                      "speakerHint": "Speaker_2",
                      "speakerGender": "male",
                      "originalText": "No sir, I am a big fan of money.",
                      "translatedText": "இல்லை ஐயா, எனக்கு பணத்தை பிடிக்கும்."
                    }
                  ]
                }
            """.trimIndent()

            override suspend fun generateTextDubJson(
                modelId: String,
                prompt: String,
                responseSchema: Schema
            ): String = ""
        }

        val engine = GeminiDubbingEngine(modelInvoker = fakeInvoker)
        val result = engine.recognizeDiarizeAndTranslateChunk(
            chunk = AudioChunk("mov1", 0L, 14_000L, 16000, 1, ByteArray(32)),
            config = DubbingConfig(sourceLangCode = "en", targetLangCode = "ta"),
            knownSpeakers = mutableMapOf()
        )

        assertTrue(result.isSuccess)
        val segs = result.getOrThrow()
        assertEquals(2, segs.size)
        assertEquals(0L, segs[0].startMs)
        assertEquals(2500L, segs[0].endMs)
        assertTrue("Second turn must not overlap first", segs[1].startMs >= segs[0].endMs)
        assertEquals("mov1_ta_0_2500", segs[0].id)
    }

    @Test
    fun sanitizeSegments_removesOverlappingOldCacheRunsWhilePreservingShortSequentialTurns() {
        val mixed = listOf(
            DubSegment("old_1", "m", "ta", 0L, 700L, "SPK_1", "Speaker 1", "male", "Hey", "ஏய்", "/a.wav"),
            DubSegment("short_2", "m", "ta", 750L, 2000L, "SPK_2", "Speaker 2", "male", "What?", "என்ன?", "/b.wav"),
            DubSegment("dup_2", "m", "ta", 900L, 2100L, "SPK_2", "Speaker 2", "male", "What?", "என்ன ஐயா?", null)
        )

        val cleaned = DubbingPlaybackCoordinator.sanitizeSegments(mixed)
        assertEquals(2, cleaned.size)
        assertEquals("old_1", cleaned[0].id)
        assertEquals("short_2", cleaned[1].id)
    }
}
