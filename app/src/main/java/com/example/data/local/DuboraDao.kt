package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DuboraDao {
    @Query("SELECT * FROM local_movies ORDER BY lastPlayedAt DESC, addedAt DESC")
    fun observeMovies(): Flow<List<LocalMovieEntity>>

    @Query("SELECT * FROM local_movies WHERE isDownloadedDub = 1 OR hasCachedDub = 1 ORDER BY lastPlayedAt DESC")
    fun observeDownloadedDubs(): Flow<List<LocalMovieEntity>>

    @Query("SELECT * FROM local_movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieById(movieId: String): LocalMovieEntity?

    @Query("SELECT * FROM local_movies WHERE uriString = :uriString LIMIT 1")
    suspend fun getMovieByUri(uriString: String): LocalMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovie(movie: LocalMovieEntity)

    @Query("UPDATE local_movies SET lastPositionMs = :positionMs, lastPlayedAt = :playedAt WHERE id = :movieId")
    suspend fun updatePlaybackPosition(movieId: String, positionMs: Long, playedAt: Long)

    @Query("UPDATE local_movies SET hasCachedDub = :hasDub, cachedDubLang = :langCode, isDownloadedDub = :isDownloaded WHERE id = :movieId")
    suspend fun updateDubCacheStatus(movieId: String, hasDub: Boolean, langCode: String?, isDownloaded: Boolean)

    @Query("DELETE FROM local_movies WHERE id = :movieId")
    suspend fun deleteMovie(movieId: String)

    @Query("SELECT * FROM dub_segments WHERE movieId = :movieId AND targetLangCode = :targetLangCode ORDER BY startMs ASC")
    fun observeSegments(movieId: String, targetLangCode: String): Flow<List<DubSegmentEntity>>

    @Query("SELECT * FROM dub_segments WHERE movieId = :movieId AND targetLangCode = :targetLangCode ORDER BY startMs ASC")
    suspend fun getSegments(movieId: String, targetLangCode: String): List<DubSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(segments: List<DubSegmentEntity>)

    @Query("DELETE FROM dub_segments WHERE movieId = :movieId AND targetLangCode = :targetLangCode")
    suspend fun deleteSegmentsForMovieAndLang(movieId: String, targetLangCode: String)

    @Query("DELETE FROM dub_segments WHERE movieId = :movieId")
    suspend fun deleteSegmentsForMovie(movieId: String)

    @Query("DELETE FROM dub_segments")
    suspend fun clearAllSegments()
}
