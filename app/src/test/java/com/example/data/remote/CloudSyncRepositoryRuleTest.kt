package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_andObserveProfile_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile(
                displayName = "Alice Cinema",
                email = ALICE_EMAIL
            )
        }
        assertTrue(result.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals(uid, profile?.userId)
        assertEquals("es", profile?.defaultTargetLang)
    }

    @Test
    fun upsertDubbingSession_andObserveHistory_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val sessionId = "sess_${UUID.randomUUID()}"

        val writeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertDubbingSession(
                sessionId = sessionId,
                movieTitle = "Neon Horizon",
                fileName = "neon_horizon.mp4",
                fileSizeBytes = 250_000_000L,
                durationMs = 5_400_000L,
                lastPositionMs = 42_000L,
                sourceLang = "en",
                targetLang = "ja",
                voiceProfile = "speaker_matched",
                segmentsProcessed = 12L,
                status = "active"
            )
        }
        assertTrue(writeResult.isSuccess)

        val history = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDubbingHistory(uid).first { list -> list.any { it.id == sessionId } }
        }
        assertTrue(history.any { it.id == sessionId && it.movieTitle == "Neon Horizon" })
    }

    @Test
    fun observeHistory_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val sessionId = "sess_${UUID.randomUUID()}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertDubbingSession(
                sessionId = sessionId,
                movieTitle = "Private Film",
                fileName = "private_film.mp4",
                fileSizeBytes = 10_000L,
                durationMs = 60_000L,
                lastPositionMs = 5_000L,
                sourceLang = "en",
                targetLang = "fr",
                voiceProfile = "natural_female",
                segmentsProcessed = 3L,
                status = "active"
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeDubbingHistory(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user access")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException?.code)
        }
    }

    @Test
    fun observeHistory_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudSyncRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDubbingHistory("some_user_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_dubora@test.com"
        const val BOB_EMAIL = "bob_dubora@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
