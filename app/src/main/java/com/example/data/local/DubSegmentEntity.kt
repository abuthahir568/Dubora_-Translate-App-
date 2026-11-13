package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DubSegment

@Entity(tableName = "dub_segments")
data class DubSegmentEntity(
    @PrimaryKey val id: String,
    val movieId: String,
    val targetLangCode: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String,
    val speakerLabel: String,
    val speakerGender: String,
    val originalText: String,
    val translatedText: String,
    val synthesizedAudioPath: String?,
    val synthesizedDurationMs: Long
) {
    fun toDomain(): DubSegment = DubSegment(
        id = id,
        movieId = movieId,
        targetLangCode = targetLangCode,
        startMs = startMs,
        endMs = endMs,
        speakerId = speakerId,
        speakerLabel = speakerLabel,
        speakerGender = speakerGender,
        originalText = originalText,
        translatedText = translatedText,
        synthesizedAudioPath = synthesizedAudioPath,
        synthesizedDurationMs = synthesizedDurationMs
    )

    companion object {
        fun fromDomain(segment: DubSegment): DubSegmentEntity = DubSegmentEntity(
            id = segment.id,
            movieId = segment.movieId,
            targetLangCode = segment.targetLangCode,
            startMs = segment.startMs,
            endMs = segment.endMs,
            speakerId = segment.speakerId,
            speakerLabel = segment.speakerLabel,
            speakerGender = segment.speakerGender,
            originalText = segment.originalText,
            translatedText = segment.translatedText,
            synthesizedAudioPath = segment.synthesizedAudioPath,
            synthesizedDurationMs = segment.synthesizedDurationMs
        )
    }
}
