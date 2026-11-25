package io.github.drnerdlove.earacle.lyrics

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Limits for [fillColour]. */
object FillColourLimits {
    /** The fill must differ from the muted text by at least this CIE76 colour difference (chosen by the owner from a preview, 2026-11-25). */
    const val MIN_FILL_DIFFERENCE = 65.0

    /** The fill must stay readable on the background (WCAG AA for text). */
    const val MIN_CONTRAST = 4.5
}

/**
 * The colour for the sung part of a lyrics line (and the countdown dots), as opaque ARGB.
 *
 * [primary] is kept when it already differs from [muted] by [FillColourLimits.MIN_FILL_DIFFERENCE]. Otherwise
 * the nearest colour of the same hue is used that does (more saturated and/or lighter or darker), as long as it keeps [FillColourLimits.MIN_CONTRAST] on [background]. If none reaches
 * the difference, the one that comes closest wins; [primary] stays when nothing is better. A grey [primary]
 * has no hue to strengthen, so only its lightness changes. [muted] is never changed.
 */
fun fillColour(primary: Int, muted: Int, background: Int): Int {
    val opaquePrimary = primary or OPAQUE
    val mutedLab = lab(muted)
    val primaryLab = lab(opaquePrimary)
    if (deltaE(primaryLab, mutedLab) >= FillColourLimits.MIN_FILL_DIFFERENCE) return opaquePrimary

    val chroma = hypot(primaryLab[1], primaryLab[2])
    val hue = atan2(primaryLab[2], primaryLab[1])
    val hasHue = chroma >= MIN_HUE_CHROMA

    var bestColour = opaquePrimary
    var bestDifference = deltaE(primaryLab, mutedLab)
    var bestReached = false
    var bestDistance = Double.MAX_VALUE
    // Every candidate keeps the hue; chroma only grows. Lightness may move either way, because a stronger
    // chroma often needs a little less lightness to stay inside the screen's colours (sRGB).
    for (step in -100..100) {
        val l = primaryLab[0] + step
        if (l < 0 || l > 100) continue
        val chromaSteps = if (hasHue) (0..MAX_EXTRA_CHROMA) else (0..0)
        for (extra in chromaSteps) {
            val c = if (hasHue) chroma + extra else 0.0
            val argb = fromLab(l, c * cos(hue), c * sin(hue)) ?: continue
            if (contrast(argb, background) < FillColourLimits.MIN_CONTRAST) continue
            val candidate = lab(argb)
            val difference = deltaE(candidate, mutedLab)
            val reached = difference >= FillColourLimits.MIN_FILL_DIFFERENCE
            val distance = deltaE(candidate, primaryLab)
            val better = when {
                reached && !bestReached -> true
                reached && bestReached -> distance < bestDistance
                !reached && !bestReached -> difference > bestDifference
                else -> false
            }
            if (better) {
                bestColour = argb
                bestDifference = difference
                bestDistance = distance
                bestReached = reached
            }
        }
    }
    return bestColour
}

/** CIE76 colour difference of two ARGB colours (alpha ignored). */
fun colourDifference(a: Int, b: Int): Double = deltaE(lab(a), lab(b))

/** WCAG contrast ratio of two ARGB colours (alpha ignored), 1.0 to 21.0. */
fun contrast(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** CIELAB hue angle in degrees (0-360) of an ARGB colour; meaningless for greys. */
fun labHue(argb: Int): Double {
    val l = lab(argb)
    val degrees = Math.toDegrees(atan2(l[2], l[1]))
    return if (degrees < 0) degrees + 360 else degrees
}

private const val OPAQUE = 0xFF shl 24
private const val MIN_HUE_CHROMA = 2.0
private const val MAX_EXTRA_CHROMA = 60

private fun channel(argb: Int, shift: Int): Double = ((argb shr shift) and 0xFF) / 255.0

private fun toLinear(v: Double): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

private fun fromLinear(v: Double): Double = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055

private fun luminance(argb: Int): Double =
    0.2126 * toLinear(channel(argb, 16)) + 0.7152 * toLinear(channel(argb, 8)) + 0.0722 * toLinear(channel(argb, 0))

private const val XN = 0.95047
private const val YN = 1.0
private const val ZN = 1.08883

private fun labF(t: Double): Double = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116

private fun labFInverse(t: Double): Double = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) / (24389.0 / 27)

/** [L, a, b] of an ARGB colour (sRGB, D65). */
private fun lab(argb: Int): DoubleArray {
    val r = toLinear(channel(argb, 16))
    val g = toLinear(channel(argb, 8))
    val b = toLinear(channel(argb, 0))
    val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
    val fx = labF(x / XN)
    val fy = labF(y / YN)
    val fz = labF(z / ZN)
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

/** The opaque ARGB colour for CIELAB values, rounded to 8 bits per channel, or null when outside sRGB. */
private fun fromLab(l: Double, a: Double, b: Double): Int? {
    val fy = (l + 16) / 116
    val x = XN * labFInverse(fy + a / 500)
    val z = ZN * labFInverse(fy - b / 200)
    val y = YN * labFInverse(fy)
    val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    val tolerance = 0.002
    if (listOf(r, g, bl).any { it < -tolerance || it > 1 + tolerance }) return null
    fun byte(v: Double): Int = (fromLinear(v.coerceIn(0.0, 1.0)) * 255).roundToInt()
    return OPAQUE or (byte(r) shl 16) or (byte(g) shl 8) or byte(bl)
}

private fun deltaE(x: DoubleArray, y: DoubleArray): Double =
    sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
