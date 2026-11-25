package io.github.drnerdlove.earacle.audio

import io.github.drnerdlove.earacle.recognition.SongTracking
import kotlin.math.log10
import kotlin.math.sqrt

/** Wave height shown for silence: almost flat, but not zero, so the ring keeps moving. */
const val MIN_WAVE = 0.05f

/** RMS at (and above) which the waves are at full height. */
const val LOUD_RMS = 4000.0

/** How far the shown level moves towards a louder target per chunk (fast attack). */
const val WAVE_RISE = 0.6f

/** How far the shown level moves towards a quieter target per chunk (slow release). */
const val WAVE_FALL = 0.25f

/** Root mean square of `samples[from until from + count]`; 0.0 for an empty range. */
fun chunkRms(samples: ShortArray, from: Int, count: Int): Double {
    if (count <= 0) return 0.0
    var sum = 0.0
    for (i in from until from + count) {
        val s = samples[i].toDouble()
        sum += s * s
    }
    return sqrt(sum / count)
}

/**
 * Maps an RMS to a wave height from [MIN_WAVE] to 1 on a log scale: at or below the silence limit of listening
 * along it is [MIN_WAVE], at or above [LOUD_RMS] it is 1. NaN and negative values count as silence.
 */
fun waveAmplitude(rms: Double): Float {
    if (rms.isNaN() || rms <= SongTracking.SILENCE_RMS) return MIN_WAVE
    if (rms >= LOUD_RMS) return 1f
    val low = log10(SongTracking.SILENCE_RMS)
    val t = (log10(rms) - low) / (log10(LOUD_RMS) - low)
    return (MIN_WAVE + (1f - MIN_WAVE) * t).toFloat()
}

/** Moves [previous] towards [target]: 60 % of the way up, 25 % of the way down. */
fun smoothAmplitude(previous: Float, target: Float): Float {
    val factor = if (target > previous) WAVE_RISE else WAVE_FALL
    return previous + (target - previous) * factor
}
