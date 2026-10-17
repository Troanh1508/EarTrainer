package com.example.eartrainer.audio

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FreePats "Upright Piano KW" (small), CC0 public domain; see the README and licence next to
 * the recordings in assets. Recorded roughly every minor third; the root pitches come from the
 * sound bank's SFZ map.
 */
internal object UprightPianoKw {
    const val ASSET_DIR = "instruments/upright-piano-kw"

    val recordings: Map<String, Int> = mapOf(
        "C1" to 24, "Fs1" to 30, "B1" to 35, "Ds2" to 39, "Fs2" to 42, "B2" to 47,
        "Ds3" to 51, "Fs3" to 54, "A3" to 57, "C4" to 60, "Ds4" to 63, "Fs4" to 66,
        "A4" to 69, "C5" to 72, "Ds5" to 75, "Fs5" to 78, "A5" to 81, "C6" to 84,
        "Ds6" to 87, "Fs6" to 90, "A6" to 93, "C7" to 96, "Ds7" to 99, "Fs7" to 102,
        "A7" to 105, "B7" to 107,
    )
}

/** Decodes compressed recordings from assets into PCM with the platform's MediaCodec decoders. */
class AssetSampleLoader(private val assets: AssetManager) {

    /**
     * Decodes the bundled upright piano. Only the first [maxSeconds] of each recording are kept:
     * prompts never hold a note that long, and it keeps the decoded bank to a few megabytes.
     */
    suspend fun loadUprightPiano(maxSeconds: Double = 3.0): SampleBank = coroutineScope {
        // Most of each decode is codec start-up rather than CPU work, so a few in parallel cut the
        // total load time several-fold.
        val decoding = Dispatchers.Default.limitedParallelism(PARALLEL_DECODES)
        val samples = UprightPianoKw.recordings.map { (name, root) ->
            async(decoding) { decode("${UprightPianoKw.ASSET_DIR}/$name.flac", root, maxSeconds) }
        }
        SampleBank(samples.awaitAll())
    }

    internal fun decode(path: String, rootMidi: Int, maxSeconds: Double): InstrumentSample {
        val extractor = MediaExtractor()
        try {
            // Requires the asset to be stored uncompressed (see noCompress in build.gradle.kts).
            assets.openFd(path).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val output = ShortArray((maxSeconds * sampleRate).toInt())
            val written = decodeInto(extractor, format, output)
            return InstrumentSample(rootMidi, sampleRate, output.copyOf(written))
        } finally {
            extractor.release()
        }
    }

    /** Runs the decoder until the stream ends or [output] is full; returns the frames written. */
    private fun decodeInto(extractor: MediaExtractor, format: MediaFormat, output: ShortArray): Int {
        val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var written = 0

            while (written < output.size) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val size = extractor.readSampleData(checkNotNull(codec.getInputBuffer(inputIndex)), 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outputFormat = codec.outputFormat
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outputIndex >= 0) {
                    val buffer = checkNotNull(codec.getOutputBuffer(outputIndex))
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    written = appendFrames(buffer.order(ByteOrder.nativeOrder()), encoding, channels, output, written)
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return written
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    /** Appends decoded frames as mono 16-bit, averaging channels if the recording is not mono. */
    private fun appendFrames(buffer: ByteBuffer, encoding: Int, channels: Int, output: ShortArray, start: Int): Int {
        var written = start
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                while (floats.remaining() >= channels && written < output.size) {
                    var sum = 0f
                    repeat(channels) { sum += floats.get() }
                    output[written++] = (sum / channels * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = buffer.asShortBuffer()
                while (shorts.remaining() >= channels && written < output.size) {
                    var sum = 0
                    repeat(channels) { sum += shorts.get() }
                    output[written++] = (sum / channels).toShort()
                }
            }
            else -> throw AudioPlaybackException("Unsupported decoded PCM encoding $encoding")
        }
        return written
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val PARALLEL_DECODES = 4
    }
}
