package com.example.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCapabilityRegistryTest {

    @Test
    fun isSupportedPair_validPairs_returnSupported() {
        val (ok1, err1) = LanguageCapabilityRegistry.isSupportedPair("auto", "ja")
        assertTrue(err1 ?: "", ok1)

        val (ok2, err2) = LanguageCapabilityRegistry.isSupportedPair("ta", "en")
        assertTrue(err2 ?: "", ok2)
    }

    @Test
    fun isSupportedPair_sameSourceAndTarget_returnsUnsupported() {
        val (ok, err) = LanguageCapabilityRegistry.isSupportedPair("es", "es")
        assertFalse(ok)
        assertTrue(err?.contains("both") == true)
    }

    @Test
    fun isSupportedPair_unknownTarget_returnsUnsupported() {
        val (ok, err) = LanguageCapabilityRegistry.isSupportedPair("en", "xx_unknown")
        assertFalse(ok)
        assertTrue(err?.contains("not in the configured AI provider") == true)
    }
}
