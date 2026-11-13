package com.example.engine

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class DubbingErrorCategory(
    val title: String,
    val isRetryable: Boolean
) {
    OVERLOADED_503("AI Server High Demand (503)", true),
    RATE_LIMITED_429("Rate Limit Reached (429)", true),
    TIMEOUT_OR_NETWORK("Network or Timeout Issue", true),
    QUOTA_EXCEEDED("API Quota Exhausted", false),
    CONFIGURATION_ERROR("API / App Check Configuration Error", false),
    UNSUPPORTED_OR_SAFETY("Unsupported Input or Safety Filter", false),
    UNKNOWN("Unexpected AI Error", false)
}

data class DubbingErrorInfo(
    val category: DubbingErrorCategory,
    val apiName: String,
    val modelId: String,
    val userMessage: String,
    val actionableHint: String,
    val technicalDetail: String,
    val attemptsMade: Int = 1,
    val retriesExhausted: Boolean = false,
    val suggestedDelayMs: Long? = null
) {
    val isRetryable: Boolean get() = category.isRetryable
}

data class RetryProgress(
    val attempt: Int,
    val maxRetries: Int,
    val delayMs: Long,
    val category: DubbingErrorCategory,
    val modelId: String,
    val isModelFallback: Boolean = false
) {
    val userStatusMessage: String
        get() = when {
            isModelFallback ->
                "Switching to Fast Stream ($modelId) for faster dubbing..."
            category == DubbingErrorCategory.OVERLOADED_503 ->
                "Model experiencing high demand — retrying ($attempt/$maxRetries)..."
            category == DubbingErrorCategory.RATE_LIMITED_429 ->
                "Rate limit reached — backing off & retrying ($attempt/$maxRetries)..."
            else ->
                "Connection interrupted — retrying ($attempt/$maxRetries)..."
        }
}

data class GeminiRetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 2_000L,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 16_000L
) {
    fun delayForRetryAttempt(retryNumber: Int): Long {
        if (retryNumber <= 0) return initialDelayMs
        var delay = initialDelayMs.toDouble()
        repeat(retryNumber - 1) {
            delay *= backoffMultiplier
        }
        return delay.toLong().coerceIn(initialDelayMs, maxDelayMs)
    }
}

data class ApiHealthCheckResult(
    val apiProvider: String,
    val activeModelId: String,
    val fallbackModelId: String,
    val firebaseInitialized: Boolean,
    val appCheckConfigured: Boolean,
    val appCheckMode: String,
    val configurationIssue: String? = null
) {
    val isConfigHealthy: Boolean
        get() = firebaseInitialized && appCheckConfigured && configurationIssue == null
}

class DubbingApiException(
    val errorInfo: DubbingErrorInfo,
    cause: Throwable? = null
) : Exception(errorInfo.userMessage, cause)

object GeminiErrorClassifier {

    const val API_NAME = "Firebase AI Logic (Gemini API)"
    private val RETRY_IN_REGEX = Regex("""retry\s+in\s+(\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE)

    fun classify(
        throwable: Throwable,
        modelId: String,
        attemptsMade: Int = 1,
        retriesExhausted: Boolean = false
    ): DubbingErrorInfo {
        if (throwable is DubbingApiException) {
            return throwable.errorInfo.copy(
                attemptsMade = attemptsMade.coerceAtLeast(throwable.errorInfo.attemptsMade),
                retriesExhausted = retriesExhausted || throwable.errorInfo.retriesExhausted
            )
        }

        val chain = generateSequence(throwable) { it.cause }.toList()
        val combinedMessages = chain.mapNotNull { t ->
            val cls = t::class.java.simpleName
            val msg = t.message.orEmpty()
            "$cls: $msg"
        }.joinToString(" | ")
        val lower = combinedMessages.lowercase()
        val suggestedDelayMs = extractSuggestedRetryDelayMs(combinedMessages)

        // 1. Inspect API configuration / App Check / model access errors separately first (never retry)
        if (isConfigurationFailure(chain, lower)) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: throwable::class.java.simpleName
            val hint = when {
                lower.contains("app check") || lower.contains("appcheck") || lower.contains("attestation") ->
                    "App Check attestation failed. Launch the app from AI Studio or verify your debug/reCAPTCHA App Check setup."
                lower.contains("not found") || lower.contains("404") || lower.contains("unsupported model") ->
                    "Selected model ($modelId) is unavailable or disabled on this Firebase project."
                else ->
                    "Verify Firebase AI Logic is enabled for this project and App Check permissions are valid."
            }
            return DubbingErrorInfo(
                category = DubbingErrorCategory.CONFIGURATION_ERROR,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = "AI Dubbing configuration issue detected for $modelId.",
                actionableHint = hint,
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = false
            )
        }

        // 2. Temporary 503 Overloaded / High Demand (retryable)
        if (isOverloaded503(chain, lower)) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: "HTTP 503 Service Unavailable"
            val userMsg = if (retriesExhausted) {
                "Gemini AI ($modelId) is still experiencing high demand after ${attemptsMade - 1} automatic retries. Please tap Retry in a moment."
            } else {
                "Gemini AI ($modelId) is currently experiencing high demand."
            }
            return DubbingErrorInfo(
                category = DubbingErrorCategory.OVERLOADED_503,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = userMsg,
                actionableHint = "Traffic spikes on $modelId are usually temporary. Tap Retry to send a fresh request.",
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = retriesExhausted,
                suggestedDelayMs = suggestedDelayMs
            )
        }

        // 3. Distinguish true hard/zero quota exhaustion vs. temporary per-minute 429 rate-limit bursts
        if (isHardQuotaExhausted(lower)) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: "Quota exceeded"
            return DubbingErrorInfo(
                category = DubbingErrorCategory.QUOTA_EXCEEDED,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = "AI Dubbing project quota has been reached for $modelId.",
                actionableHint = "Switch to Fast Stream (gemini-3.5-flash-lite) or check your Firebase AI Logic quota in Firebase Console.",
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = false
            )
        }

        // 4. Temporary 429 Rate Limit (retryable)
        if (isRateLimit429(chain, lower)) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: "HTTP 429 Too Many Requests"
            val userMsg = if (retriesExhausted) {
                "AI request rate limit (429) persisted after ${attemptsMade - 1} automatic retries. Wait a few seconds and tap Retry."
            } else {
                "AI request rate limit reached (429)."
            }
            return DubbingErrorInfo(
                category = DubbingErrorCategory.RATE_LIMITED_429,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = userMsg,
                actionableHint = "Short cooldown needed between movie chunks. Tap Retry in a few seconds.",
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = retriesExhausted,
                suggestedDelayMs = suggestedDelayMs
            )
        }

        // 5. Network / Timeout errors (retryable)
        if (isTimeoutOrNetwork(chain, lower)) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: throwable::class.java.simpleName
            val userMsg = if (retriesExhausted) {
                "Timed out or lost connection while contacting Gemini AI ($modelId). Check your internet connection and tap Retry."
            } else {
                "Network connection or AI request timed out."
            }
            return DubbingErrorInfo(
                category = DubbingErrorCategory.TIMEOUT_OR_NETWORK,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = userMsg,
                actionableHint = "Ensure stable internet connectivity and tap Retry.",
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = retriesExhausted
            )
        }

        // 6. Content safety / unsupported argument / missing audio track
        if (throwable is NoAudioTrackException ||
            lower.contains("no audio track") ||
            lower.contains("contentblocked") ||
            lower.contains("safety") ||
            lower.contains("invalid_argument")
        ) {
            val detail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?: throwable::class.java.simpleName
            val msg = if (throwable is NoAudioTrackException || lower.contains("no audio track")) {
                "This video file has no audio track to transcribe or dub."
            } else {
                "This dialogue segment could not be processed by the AI model."
            }
            return DubbingErrorInfo(
                category = DubbingErrorCategory.UNSUPPORTED_OR_SAFETY,
                apiName = API_NAME,
                modelId = modelId,
                userMessage = msg,
                actionableHint = "Choose a video with spoken dialogue or adjust source/target languages.",
                technicalDetail = detail,
                attemptsMade = attemptsMade,
                retriesExhausted = false
            )
        }

        val fallbackDetail = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
            ?: throwable::class.java.simpleName
        return DubbingErrorInfo(
            category = DubbingErrorCategory.UNKNOWN,
            apiName = API_NAME,
            modelId = modelId,
            userMessage = "AI Dubbing could not complete the request. Please try again.",
            actionableHint = "Tap Retry to try processing this segment again.",
            technicalDetail = fallbackDetail,
            attemptsMade = attemptsMade,
            retriesExhausted = retriesExhausted
        )
    }

    private fun extractSuggestedRetryDelayMs(raw: String): Long? {
        val match = RETRY_IN_REGEX.find(raw) ?: return null
        val seconds = match.groupValues[1].toDoubleOrNull() ?: return null
        return (seconds * 1000.0).toLong().coerceIn(2_000L, 18_000L)
    }

    private fun isConfigurationFailure(chain: List<Throwable>, lower: String): Boolean {
        val classNames = chain.map { it::class.java.simpleName.lowercase() }
        if (classNames.any {
                it.contains("servicedisabled") ||
                    it.contains("invalidapikey") ||
                    it.contains("invalidstate")
            }
        ) {
            return true
        }
        return lower.contains("api key") ||
            lower.contains("app check") ||
            lower.contains("appcheck") ||
            lower.contains("attestation") ||
            lower.contains("service disabled") ||
            lower.contains("api has not been used") ||
            lower.contains("permission_denied") ||
            lower.contains("unauthenticated") ||
            lower.contains("401") ||
            lower.contains("403") ||
            (lower.contains("404") && lower.contains("model")) ||
            lower.contains("firebaseapp is not initialized")
    }

    private fun isHardQuotaExhausted(lower: String): Boolean {
        // Explicit zero quota or daily/monthly exhaustion is hard quota.
        if (lower.contains("limit: 0") || lower.contains("limit=0") ||
            lower.contains("daily limit") || lower.contains("monthly limit") ||
            lower.contains("requests_per_day") || lower.contains("per_day")
        ) {
            return true
        }

        // Standard Google 429 RPM/TPM rate-limit errors include boilerplate mentioning "plan and billing"
        // and "exceeded your current quota", while also stating "per minute", "rpm", "tpm", or "retry in Xs".
        // Treat those as retryable 429 rate limits rather than permanent quota exhaustion.
        val isPerMinuteOrRetryableBurst = lower.contains("retry in") ||
            lower.contains("per minute") ||
            lower.contains("per_minute") ||
            lower.contains("rpm") ||
            lower.contains("tpm") ||
            lower.contains("rate limit") ||
            lower.contains("rate-limit") ||
            lower.contains("too many requests")

        if (isPerMinuteOrRetryableBurst) {
            return false
        }

        return lower.contains("billing") ||
            lower.contains("free tier") ||
            lower.contains("insufficient quota") ||
            lower.contains("exceeded your current quota")
    }

    private fun isOverloaded503(chain: List<Throwable>, lower: String): Boolean {
        val classNames = chain.map { it::class.java.simpleName.lowercase() }
        if (classNames.any { it.contains("serverexception") } &&
            (lower.contains("503") || lower.contains("demand") || lower.contains("unavailable") || lower.contains("overload"))
        ) {
            return true
        }
        return lower.contains("503") ||
            lower.contains("high demand") ||
            lower.contains("spikes in demand") ||
            lower.contains("overloaded") ||
            lower.contains("model is currently overloaded") ||
            lower.contains("service unavailable") ||
            lower.contains("status: unavailable")
    }

    private fun isRateLimit429(chain: List<Throwable>, lower: String): Boolean {
        val classNames = chain.map { it::class.java.simpleName.lowercase() }
        if (classNames.any { it.contains("quotaexceeded") }) {
            return true
        }
        return lower.contains("429") ||
            lower.contains("rate limit") ||
            lower.contains("rate-limit") ||
            lower.contains("too many requests") ||
            lower.contains("resource_exhausted") ||
            lower.contains("requests per minute") ||
            lower.contains("per_minute") ||
            lower.contains("retry in") ||
            lower.contains("quota")
    }

    private fun isTimeoutOrNetwork(chain: List<Throwable>, lower: String): Boolean {
        if (chain.any {
                it is TimeoutCancellationException ||
                    it is SocketTimeoutException ||
                    it is UnknownHostException ||
                    it is IOException
            }
        ) {
            return true
        }
        val classNames = chain.map { it::class.java.simpleName.lowercase() }
        if (classNames.any { it.contains("requesttimeout") }) return true
        return lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("unable to resolve host") ||
            lower.contains("network error") ||
            lower.contains("connection reset")
    }
}

object GeminiConfigValidator {
    fun inspect(context: Context, selectedModelId: String): ApiHealthCheckResult {
        val firebaseInitialized = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
        val appCheckMode = if (BuildConfig.DEBUG) "Debug App Check Provider" else "reCAPTCHA Enterprise"

        var appCheckConfigured = false
        var configIssue: String? = null

        if (!firebaseInitialized) {
            configIssue = "FirebaseApp is not initialized."
        } else if (BuildConfig.DEBUG) {
            val persistenceKey = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrNull()
            if (persistenceKey != null) {
                appCheckConfigured = true
            } else {
                configIssue = "Unable to resolve Firebase persistence key for App Check."
            }
        } else {
            val resId = context.resources.getIdentifier("recaptcha_site_key", "string", context.packageName)
            val siteKey = if (resId != 0) context.getString(resId) else ""
            appCheckConfigured = siteKey.isNotBlank()
            if (!appCheckConfigured) {
                configIssue = "Missing recaptcha_site_key for release App Check."
            }
        }

        return ApiHealthCheckResult(
            apiProvider = GeminiErrorClassifier.API_NAME,
            activeModelId = selectedModelId,
            fallbackModelId = GeminiDubbingEngine.FAST_MODEL_ID,
            firebaseInitialized = firebaseInitialized,
            appCheckConfigured = appCheckConfigured,
            appCheckMode = appCheckMode,
            configurationIssue = configIssue
        )
    }
}

fun interface RetryDelayStrategy {
    suspend fun delayMs(timeMillis: Long)
}

object CoroutineRetryDelayStrategy : RetryDelayStrategy {
    override suspend fun delayMs(timeMillis: Long) {
        kotlinx.coroutines.delay(timeMillis)
    }
}

suspend fun <T> executeWithGeminiRetry(
    modelId: String,
    policy: GeminiRetryPolicy = GeminiRetryPolicy(),
    delayStrategy: RetryDelayStrategy = CoroutineRetryDelayStrategy,
    onRetry: (suspend (RetryProgress) -> Unit)? = null,
    block: suspend () -> T
): Result<T> {
    var lastError: DubbingErrorInfo? = null
    val maxAttempts = policy.maxRetries + 1

    for (attempt in 1..maxAttempts) {
        try {
            val value = block()
            return Result.success(value)
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val classified = GeminiErrorClassifier.classify(
                    throwable = e,
                    modelId = modelId,
                    attemptsMade = attempt,
                    retriesExhausted = attempt >= maxAttempts
                )
                lastError = classified
                if (attempt >= maxAttempts) {
                    return Result.failure(DubbingApiException(classified, e))
                }
                val waitMs = policy.delayForRetryAttempt(attempt)
                onRetry?.invoke(RetryProgress(attempt, policy.maxRetries, waitMs, classified.category, modelId))
                delayStrategy.delayMs(waitMs)
            } else {
                throw e
            }
        } catch (e: Throwable) {
            val isLastAttempt = attempt >= maxAttempts
            val initialClassification = GeminiErrorClassifier.classify(
                throwable = e,
                modelId = modelId,
                attemptsMade = attempt,
                retriesExhausted = false
            )
            val classified = if (initialClassification.isRetryable && isLastAttempt) {
                initialClassification.copy(retriesExhausted = true)
            } else {
                initialClassification
            }
            lastError = classified

            if (!classified.isRetryable || isLastAttempt) {
                return Result.failure(DubbingApiException(classified, e))
            }

            val waitMs = maxOf(
                policy.delayForRetryAttempt(attempt),
                classified.suggestedDelayMs ?: 0L
            )
            onRetry?.invoke(RetryProgress(attempt, policy.maxRetries, waitMs, classified.category, modelId))
            delayStrategy.delayMs(waitMs)
        }
    }

    val finalErr = lastError ?: DubbingErrorInfo(
        category = DubbingErrorCategory.UNKNOWN,
        apiName = GeminiErrorClassifier.API_NAME,
        modelId = modelId,
        userMessage = "AI Dubbing failed after ${policy.maxRetries} retries.",
        actionableHint = "Tap Retry to try again.",
        technicalDetail = "Exhausted attempts",
        attemptsMade = maxAttempts,
        retriesExhausted = true
    )
    return Result.failure(DubbingApiException(finalErr))
}
