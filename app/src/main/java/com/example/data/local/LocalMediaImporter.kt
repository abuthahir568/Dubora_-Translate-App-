package com.example.data.local

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.domain.model.LocalMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LocalMediaImporter(private val context: Context) {

    suspend fun importVideoUri(uri: Uri): Result<LocalMovie> = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (sec: SecurityException) {
                Log.w(TAG, "Persistable permission unavailable for $uri, using transient grant", sec)
            }

            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "video/mp4"
            var fileName = "movie_${System.currentTimeMillis()}.mp4"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            val supportedExtensions = listOf(".mp4", ".mov", ".mkv", ".m4v", ".webm")
            val lowerName = fileName.lowercase()
            val isVideoMime = mimeType.startsWith("video/")
            val hasSupportedExt = supportedExtensions.any { lowerName.endsWith(it) }
            if (!isVideoMime && !hasSupportedExt) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Unsupported video format ($mimeType). Please select an MP4, MOV, or MKV movie file."
                    )
                )
            }

            var durationMs = 0L
            var embeddedTitle: String? = null
            val movieId = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
            var thumbnailPath: String? = null

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)

                val frameTimeUs = if (durationMs > 4000L) (durationMs * 1000L) / 5L else 1_000_000L
                val frame = retriever.getFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                    val thumbFile = File(thumbDir, "$movieId.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    frame.recycle()
                    thumbnailPath = thumbFile.absolutePath
                }
            } catch (e: Exception) {
                Log.w(TAG, "Metadata/thumbnail extraction partial for $uri", e)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            val cleanTitle = embeddedTitle?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
                    .trim().ifBlank { "Imported Movie" }

            val now = System.currentTimeMillis()
            Result.success(
                LocalMovie(
                    id = movieId,
                    uriString = uri.toString(),
                    title = cleanTitle,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    durationMs = durationMs,
                    lastPositionMs = 0L,
                    lastPlayedAt = now,
                    addedAt = now,
                    thumbnailPath = thumbnailPath
                )
            )
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException("Permission denied reading movie file. Please re-select the video.", e)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "LocalMediaImporter"
    }
}
