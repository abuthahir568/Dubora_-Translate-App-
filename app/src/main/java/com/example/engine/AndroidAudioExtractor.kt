package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class NoAudioTrackException(message: String) : IllegalStateException(message)

class AndroidAudioExtractor(private val context: Context) : MediaBridge {

    override val capabilities = PlatformBridgeStatus(
        platform = "Android (Native Media3 / MediaCodec)",
        playerEngine = "Jetpack Media3 ExoPlayer 1.11.1",
        audioExtractor = "MediaExtractor + MediaCodec PCM16 -> 16kHz Mono WAV (with silence skip)",
        stemSeparationStatus = "Original-audio ducking active (Demucs stem separation requires external server/native NDK model)",
        backgroundAudioReady = true,
        pipSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    )

    override suspend fun extractAudioChunk(
        uri: Uri,
        movieId: String,
        windowStartMs: Long,
        windowEndMs: Long
    ): Result<AudioChunk> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext Result.failure(
                    NoAudioTrackException("This video file has no audio track to dub or translate.")
                )
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            var sourceSampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            var sourceChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            extractor.selectTrack(audioTrackIndex)
            val safeStartMs = windowStartMs.coerceAtLeast(0L)
            val safeEndMs = windowEndMs.coerceAtLeast(safeStartMs + 1000L)
            val startUs = safeStartMs * 1000L
            val endUs = safeEndMs * 1000L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val pcmOut = ByteArrayOutputStream()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L
            val maxBytesLimit = 12 * 1024 * 1024
            val decodeDeadlineMs = System.currentTimeMillis() + 12_000L

            while (!outputDone && pcmOut.size() < maxBytesLimit) {
                if (System.currentTimeMillis() > decodeDeadlineMs) {
                    Log.w(TAG, "Decode deadline reached for window [$safeStartMs..$safeEndMs]; using decoded audio so far")
                    break
                }

                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuffer = codec.getInputBuffer(inIdx)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (sampleTimeUs >= 0L && sampleTimeUs > endUs)) {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    sampleSize,
                                    sampleTimeUs.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sourceSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            sourceChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }

                    outIdx >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIdx)
                        if (outBuffer != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs - 200_000L) {
                            val chunkBytes = ByteArray(bufferInfo.size)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outBuffer.get(chunkBytes)
                            pcmOut.write(chunkBytes)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val rawPcm = pcmOut.toByteArray()
            if (rawPcm.isEmpty()) {
                return@withContext Result.success(
                    AudioChunk(
                        movieId = movieId,
                        windowStartMs = safeStartMs,
                        windowEndMs = safeEndMs,
                        sampleRateHz = 16000,
                        channelCount = 1,
                        wavBytes = ByteArray(0),
                        rmsEnergy = 0f,
                        hasAudioSignal = false
                    )
                )
            }

            val targetSampleRate = 16000
            val (mono16kPcm, rmsEnergy) = downsampleToMono16k(
                pcm16 = rawPcm,
                sourceRate = sourceSampleRate,
                channels = sourceChannels,
                targetRate = targetSampleRate
            )
            val hasSpeechSignal = rmsEnergy >= SILENCE_RMS_THRESHOLD && mono16kPcm.isNotEmpty()
            val wavBytes = if (hasSpeechSignal) {
                wrapPcmAsWav(mono16kPcm, targetSampleRate, 1)
            } else {
                ByteArray(0)
            }

            Result.success(
                AudioChunk(
                    movieId = movieId,
                    windowStartMs = safeStartMs,
                    windowEndMs = safeEndMs,
                    sampleRateHz = targetSampleRate,
                    channelCount = 1,
                    wavBytes = wavBytes,
                    rmsEnergy = rmsEnergy,
                    hasAudioSignal = hasSpeechSignal
                )
            )
        } catch (e: MediaCodec.CodecException) {
            Log.e(TAG, "Unsupported audio codec for chunk extraction", e)
            Result.failure(IllegalStateException("Unsupported audio codec: ${e.diagnosticInfo}", e))
        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction failed", e)
            Result.failure(e)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun downsampleToMono16k(
        pcm16: ByteArray,
        sourceRate: Int,
        channels: Int,
        targetRate: Int
    ): Pair<ByteArray, Float> {
        val safeChannels = channels.coerceAtLeast(1)
        val totalFrames = pcm16.size / (2 * safeChannels)
        if (totalFrames <= 0) return ByteArray(0) to 0f

        val shortBuf = ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val monoSource = ShortArray(totalFrames)
        var sumSquares = 0.0
        for (i in 0 until totalFrames) {
            var sum = 0
            for (c in 0 until safeChannels) {
                sum += shortBuf.get(i * safeChannels + c).toInt()
            }
            val monoSample = (sum / safeChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            monoSource[i] = monoSample.toShort()
            val norm = monoSample / 32768.0
            sumSquares += norm * norm
        }
        val rms = sqrt(sumSquares / totalFrames.toDouble()).toFloat()

        val ratio = sourceRate.coerceAtLeast(8000).toDouble() / targetRate.toDouble()
        val targetFrames = (totalFrames / ratio).toInt().coerceAtLeast(1)
        val outBytes = ByteArray(targetFrames * 2)
        val outShort = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until targetFrames) {
            val srcIdx = (i * ratio).toInt().coerceIn(0, totalFrames - 1)
            outShort.put(i, monoSource[srcIdx])
        }
        return outBytes to rms
    }

    private fun wrapPcmAsWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        return header + pcmData
    }

    private companion object {
        const val TAG = "AndroidAudioExtractor"
        const val SILENCE_RMS_THRESHOLD = 0.002f
    }
}
