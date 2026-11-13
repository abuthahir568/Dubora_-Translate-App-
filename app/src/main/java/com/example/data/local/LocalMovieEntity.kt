package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.LocalMovie

@Entity(tableName = "local_movies")
data class LocalMovieEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val lastPositionMs: Long,
    val lastPlayedAt: Long,
    val addedAt: Long,
    val thumbnailPath: String?,
    val hasCachedDub: Boolean,
    val cachedDubLang: String?,
    val isDownloadedDub: Boolean
) {
    fun toDomain(): LocalMovie = LocalMovie(
        id = id,
        uriString = uriString,
        title = title,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        lastPositionMs = lastPositionMs,
        lastPlayedAt = lastPlayedAt,
        addedAt = addedAt,
        thumbnailPath = thumbnailPath,
        hasCachedDub = hasCachedDub,
        cachedDubLang = cachedDubLang,
        isDownloadedDub = isDownloadedDub
    )

    companion object {
        fun fromDomain(movie: LocalMovie): LocalMovieEntity = LocalMovieEntity(
            id = movie.id,
            uriString = movie.uriString,
            title = movie.title,
            fileName = movie.fileName,
            mimeType = movie.mimeType,
            sizeBytes = movie.sizeBytes,
            durationMs = movie.durationMs,
            lastPositionMs = movie.lastPositionMs,
            lastPlayedAt = movie.lastPlayedAt,
            addedAt = movie.addedAt,
            thumbnailPath = movie.thumbnailPath,
            hasCachedDub = movie.hasCachedDub,
            cachedDubLang = movie.cachedDubLang,
            isDownloadedDub = movie.isDownloadedDub
        )
    }
}
