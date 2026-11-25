package io.github.drnerdlove.earacle.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import io.github.drnerdlove.earacle.recognition.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Thrown when the microphone cannot be opened or read. */
class MicrophoneException(message: String) : Exception(message)

/** Records a fixed window from the microphone (16 kHz, mono, 16-bit PCM). */
object MicrophoneRecorder {
    const val SAMPLE_RATE_HZ = 16000

    /** Shazam returns no match for windows of 15 s or more; SongRec sends 12 s. */
    const val RECORD_SECONDS = 12
    private const val TOTAL_SAMPLES = SAMPLE_RATE_HZ * RECORD_SECONDS

    /**
     * Suspends and can be cancelled; [onProgress] gets the progress (0.0 to 1.0) and the wave height (see
     * [waveAmplitude]) of the chunk just read.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(onProgress: (progress: Float, level: Float) -> Unit): Recording = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw MicrophoneException("Microphone settings are not supported")
        val bufferBytes = maxOf(minBuffer, SAMPLE_RATE_HZ) * 2 // about one second of audio
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw MicrophoneException("Microphone could not be initialized")
            }
            recorder.startRecording()
            // Another app may hold the microphone; then reads return 0 forever instead of failing.
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneException("Microphone is busy")
            }
            val startedAt = SystemClock.elapsedRealtime()
            val samples = ShortArray(TOTAL_SAMPLES)
            var filled = 0
            while (filled < TOTAL_SAMPLES) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(samples, filled, minOf(4096, TOTAL_SAMPLES - filled))
                if (read < 0) throw MicrophoneException("Microphone read failed ($read)")
                val level = waveAmplitude(chunkRms(samples, filled, read))
                filled += read
                onProgress(filled.toFloat() / TOTAL_SAMPLES, level)
            }
            Recording(samples, SAMPLE_RATE_HZ, startedAt)
        } finally {
            recorder.release()
        }
    }
}
