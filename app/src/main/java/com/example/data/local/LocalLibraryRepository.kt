package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.domain.model.DubSegment
import com.example.domain.model.LocalMovie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class LocalLibraryRepository(
    private val dao: DuboraDao,
    private val importer: LocalMediaImporter,
    private val appContext: Context
) {
    val movies: Flow<List<LocalMovie>> = dao.observeMovies().map { list ->
        list.map { it.toDomain() }
    }

    val downloadedDubs: Flow<List<LocalMovie>> = dao.observeDownloadedDubs().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun importMovie(uri: Uri): Result<LocalMovie> {
        val existing = dao.getMovieByUri(uri.toString())
        if (existing != null) {
            val refreshed = existing.copy(lastPlayedAt = System.currentTimeMillis())
            dao.upsertMovie(refreshed)
            return Result.success(refreshed.toDomain())
        }
        val imported = importer.importVideoUri(uri)
        imported.onSuccess { movie ->
            dao.upsertMovie(LocalMovieEntity.fromDomain(movie))
        }
        return imported
    }

    suspend fun getMovieById(movieId: String): LocalMovie? {
        return dao.getMovieById(movieId)?.toDomain()
    }

    suspend fun savePlaybackPosition(movieId: String, positionMs: Long) {
        dao.updatePlaybackPosition(
            movieId = movieId,
            positionMs = positionMs.coerceAtLeast(0L),
            playedAt = System.currentTimeMillis()
        )
    }

    suspend fun markDubCached(movieId: String, langCode: String, isDownloaded: Boolean) {
        dao.updateDubCacheStatus(
            movieId = movieId,
            hasDub = true,
            langCode = langCode,
            isDownloaded = isDownloaded
        )
    }

    suspend fun saveSegments(segments: List<DubSegment>) {
        if (segments.isEmpty()) return
        dao.upsertSegments(segments.map { DubSegmentEntity.fromDomain(it) })
    }

    fun observeSegments(movieId: String, targetLangCode: String): Flow<List<DubSegment>> {
        return dao.observeSegments(movieId, targetLangCode).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getCachedSegments(movieId: String, targetLangCode: String): List<DubSegment> {
        return dao.getSegments(movieId, targetLangCode).map { it.toDomain() }
    }

    suspend fun removeMovie(movie: LocalMovie) {
        dao.deleteSegmentsForMovie(movie.id)
        dao.deleteMovie(movie.id)
        movie.thumbnailPath?.let { path ->
            runCatching { File(path).delete() }
        }
        val dubDir = File(appContext.filesDir, "dub_audio/${movie.id}")
        if (dubDir.exists()) {
            dubDir.deleteRecursively()
        }
    }

    suspend fun clearDownloadedDub(movie: LocalMovie) {
        dao.deleteSegmentsForMovie(movie.id)
        dao.updateDubCacheStatus(
            movieId = movie.id,
            hasDub = false,
            langCode = null,
            isDownloaded = false
        )
        val dubDir = File(appContext.filesDir, "dub_audio/${movie.id}")
        if (dubDir.exists()) {
            dubDir.deleteRecursively()
        }
    }

    suspend fun clearAllLocalDubCache() {
        dao.clearAllSegments()
        val dubRoot = File(appContext.filesDir, "dub_audio")
        if (dubRoot.exists()) {
            dubRoot.deleteRecursively()
        }
    }
}
