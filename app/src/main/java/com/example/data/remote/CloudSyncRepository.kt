package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.DubbingSessionRecord
import com.example.domain.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        defaultSourceLang: String = "auto",
        defaultTargetLang: String = "es",
        defaultVoiceGender: String = "speaker_matched",
        autoDubbing: Boolean = false,
        wifiOnlyProcessing: Boolean = false
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(80),
                    "email" to email.take(160),
                    "defaultSourceLang" to defaultSourceLang.take(20),
                    "defaultTargetLang" to defaultTargetLang.take(20),
                    "defaultVoiceGender" to defaultVoiceGender,
                    "autoDubbing" to autoDubbing,
                    "wifiOnlyProcessing" to wifiOnlyProcessing,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        defaultSourceLang: String,
        defaultTargetLang: String,
        defaultVoiceGender: String,
        autoDubbing: Boolean,
        wifiOnlyProcessing: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val updates = mapOf(
                "defaultSourceLang" to defaultSourceLang.take(20),
                "defaultTargetLang" to defaultTargetLang.take(20),
                "defaultVoiceGender" to defaultVoiceGender,
                "autoDubbing" to autoDubbing,
                "wifiOnlyProcessing" to wifiOnlyProcessing,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDubbingHistory(userId: String): Flow<List<DubbingSessionRecord>> = flow {
        val colRef = db.collection("users").document(userId).collection("dubbing_sessions")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DubbingSessionRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertDubbingSession(
        sessionId: String,
        movieTitle: String,
        fileName: String,
        fileSizeBytes: Long,
        durationMs: Long,
        lastPositionMs: Long,
        sourceLang: String,
        targetLang: String,
        voiceProfile: String,
        segmentsProcessed: Long,
        status: String
    ): Result<String> {
        val uid = requireUserId()
        val sanitizedId = sessionId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank {
            "session_${System.currentTimeMillis()}"
        }
        val docRef = db.collection("users").document(uid)
            .collection("dubbing_sessions").document(sanitizedId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "movieTitle" to movieTitle.take(180).ifBlank { "Untitled Movie" },
                    "fileName" to fileName.take(220).ifBlank { "movie.mp4" },
                    "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                    "durationMs" to durationMs.coerceAtLeast(0L),
                    "lastPositionMs" to lastPositionMs.coerceAtLeast(0L),
                    "sourceLang" to sourceLang.take(20),
                    "targetLang" to targetLang.take(20),
                    "voiceProfile" to voiceProfile.take(40),
                    "segmentsProcessed" to segmentsProcessed.coerceAtLeast(0L),
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "movieTitle" to movieTitle.take(180).ifBlank { "Untitled Movie" },
                    "lastPositionMs" to lastPositionMs.coerceAtLeast(0L),
                    "targetLang" to targetLang.take(20),
                    "voiceProfile" to voiceProfile.take(40),
                    "segmentsProcessed" to segmentsProcessed.coerceAtLeast(0L),
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(sanitizedId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDubbingSession(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("dubbing_sessions").document(sessionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearAllHistory(): Result<Unit> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("dubbing_sessions")
        return try {
            val docs = colRef.get().await()
            for (doc in docs.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            Result.failure(e)
        }
    }
}
