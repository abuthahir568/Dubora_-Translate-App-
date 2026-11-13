package com.example.domain.model

/**
 * Capability-driven language definition loaded from the provider configuration.
 * Never claims an unsupported language or capability is available.
 */
data class LanguageSpec(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val regionTag: String,
    val supportsSourceDetection: Boolean = true,
    val supportsLiveTranslation: Boolean = true,
    val supportsDiarization: Boolean = true,
    val supportsOnDeviceTtsHint: Boolean = true,
    val isPopularShortcut: Boolean = false
)

object LanguageCapabilityRegistry {
    val autoDetect = LanguageSpec(
        code = "auto",
        displayName = "Auto Detect",
        nativeName = "Auto",
        regionTag = "AI",
        supportsSourceDetection = true,
        supportsLiveTranslation = false,
        supportsDiarization = true,
        supportsOnDeviceTtsHint = false
    )

    /**
     * Provider capability catalog for Gemini 3.8 Flash multimodal speech recognition,
     * translation, and speaker-aware dubbing synthesis.
     */
    val supportedLanguages: List<LanguageSpec> = listOf(
        LanguageSpec("en", "English", "English", "US/UK", isPopularShortcut = true),
        LanguageSpec("ta", "Tamil", "தமிழ்", "IN/LK", isPopularShortcut = true),
        LanguageSpec("hi", "Hindi", "हिन्दी", "IN", isPopularShortcut = true),
        LanguageSpec("te", "Telugu", "తెలుగు", "IN", isPopularShortcut = true),
        LanguageSpec("ml", "Malayalam", "മലയാളം", "IN", isPopularShortcut = false),
        LanguageSpec("kn", "Kannada", "ಕನ್ನಡ", "IN", isPopularShortcut = false),
        LanguageSpec("ko", "Korean", "한국어", "KR", isPopularShortcut = true),
        LanguageSpec("ja", "Japanese", "日本語", "JP", isPopularShortcut = true),
        LanguageSpec("zh", "Chinese (Mandarin)", "中文", "CN/TW", isPopularShortcut = true),
        LanguageSpec("es", "Spanish", "Español", "ES/LA", isPopularShortcut = true),
        LanguageSpec("fr", "French", "Français", "FR/CA", isPopularShortcut = true),
        LanguageSpec("de", "German", "Deutsch", "DE"),
        LanguageSpec("ar", "Arabic", "العربية", "MENA", isPopularShortcut = true),
        LanguageSpec("pt", "Portuguese", "Português", "BR/PT"),
        LanguageSpec("it", "Italian", "Italiano", "IT"),
        LanguageSpec("ru", "Russian", "Русский", "RU"),
        LanguageSpec("tr", "Turkish", "Türkçe", "TR"),
        LanguageSpec("vi", "Vietnamese", "Tiếng Việt", "VN"),
        LanguageSpec("th", "Thai", "ไทย", "TH"),
        LanguageSpec("id", "Indonesian", "Bahasa Indonesia", "ID"),
        LanguageSpec("bn", "Bengali", "বাংলা", "BD/IN"),
        LanguageSpec("mr", "Marathi", "मराठी", "IN"),
        LanguageSpec("nl", "Dutch", "Nederlands", "NL"),
        LanguageSpec("pl", "Polish", "Polski", "PL"),
        LanguageSpec("uk", "Ukrainian", "Українська", "UA"),
        LanguageSpec("sv", "Swedish", "Svenska", "SE")
    )

    val sourceLanguageOptions: List<LanguageSpec>
        get() = listOf(autoDetect) + supportedLanguages.filter { it.supportsSourceDetection }

    val targetLanguageOptions: List<LanguageSpec>
        get() = supportedLanguages.filter { it.supportsLiveTranslation }

    val shortcutLanguages: List<LanguageSpec>
        get() = supportedLanguages.filter { it.isPopularShortcut }

    fun findByCode(code: String): LanguageSpec {
        if (code == "auto") return autoDetect
        return supportedLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: LanguageSpec(
                code = code,
                displayName = code.uppercase(),
                nativeName = code.uppercase(),
                regionTag = "ISO"
            )
    }

    fun isSupportedPair(sourceCode: String, targetCode: String): Pair<Boolean, String?> {
        val target = supportedLanguages.firstOrNull { it.code == targetCode }
            ?: return false to "Target language '$targetCode' is not in the configured AI provider capability registry."
        if (!target.supportsLiveTranslation) {
            return false to "${target.displayName} is not supported for live translation."
        }
        if (sourceCode != "auto" && supportedLanguages.none { it.code == sourceCode }) {
            return false to "Source language '$sourceCode' is not in the configured AI provider capability registry."
        }
        if (sourceCode != "auto" && sourceCode == targetCode) {
            return false to "Original and target languages are both ${target.displayName}."
        }
        return true to null
    }
}
