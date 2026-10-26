package com.troanhmolo.eartrainer.audio

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

/** Decodes compressed recordings from assets into PCM with the platform's MediaCodec decoders. */
class AssetSampleLoader(private val assets: AssetManager) {

    /**
     * Decodes the recordings of [instrument]. Only the first [maxSeconds] of each recording are kept:
     * prompts never hold a note that long, and it keeps the decoded bank to a few megabytes.
     */
    suspend fun load(instrument: Instrument, maxSeconds: Double = 3.0): SampleBank = coroutineScope {
        // Most of each decode is codec start-up rather than CPU work, so a few in parallel cut the
        // total load time several-fold.
        val decoding = Dispatchers.Default.limitedParallelism(PARALLEL_DECODES)
        val set = instrument.samples
        val samples = set.recordings.map { (name, root) ->
            async(decoding) { decode("${set.assetDir}/$name.flac", root, maxSeconds) }
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
