package com.autodict.data.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Endrar samplingsrate på mono float-lyd.
 *
 * Vi tek opp i telefonen si native rate (48 kHz) for arkivet sin del, men whisper.cpp vil
 * ha 16 kHz. Å berre plukke kvart n-te sample gir **aliasing**: alt over 8 kHz blir spegla
 * ned i talebandet som falsk støy, og det gjer transkripsjonen dårlegare. Difor
 * lågpassfiltrerer vi først med eit vindauga-sinc-filter (Hamming), og filteret blir rekna
 * ut berre for dei sample vi faktisk beheld.
 *
 * Rein Kotlin utan Android-API → unit-testbar.
 */
object AudioResampler {

    /** Samplingsraten whisper.cpp krev. */
    const val WHISPER_SAMPLE_RATE = 16_000

    /** Talet på filterkoeffisientar. Odde tal gir lineær fase (symmetrisk filter). */
    private const val TAPS = 63

    /** Resamplar til [WHISPER_SAMPLE_RATE] for transkripsjon. */
    fun toWhisperRate(samples: FloatArray, fromRate: Int): FloatArray =
        resample(samples, fromRate, WHISPER_SAMPLE_RATE)

    fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate <= 0 || toRate <= 0 || samples.isEmpty()) return samples
        if (fromRate == toRate) return samples
        if (fromRate < toRate) return interpolate(samples, fromRate, toRate)

        // Nedsampling: lågpass ved den nye Nyquist-frekvensen, så desimering.
        val cutoff = (toRate / 2.0) / fromRate // normalisert (syklar per sample)
        val kernel = lowPassKernel(cutoff)
        val ratio = fromRate.toDouble() / toRate
        val outLength = (samples.size / ratio).toInt()
        if (outLength <= 0) return FloatArray(0)

        val half = TAPS / 2
        val out = FloatArray(outLength)
        for (i in 0 until outLength) {
            val center = (i * ratio).roundToInt()
            var sum = 0.0
            for (k in 0 until TAPS) {
                val idx = center + k - half
                if (idx in samples.indices) sum += samples[idx] * kernel[k]
            }
            out[i] = sum.toFloat()
        }
        return out
    }

    /** Vindauga sinc-lågpass, normalisert til 1,0 i forsterking ved DC. */
    private fun lowPassKernel(cutoff: Double): DoubleArray {
        val half = TAPS / 2
        val kernel = DoubleArray(TAPS)
        var sum = 0.0
        for (k in 0 until TAPS) {
            val n = k - half
            val sinc = if (n == 0) 2.0 * cutoff else sin(2.0 * PI * cutoff * n) / (PI * n)
            // Hamming-vindauge dempar sidelobane.
            val window = 0.54 - 0.46 * cos(2.0 * PI * k / (TAPS - 1))
            kernel[k] = sinc * window
            sum += kernel[k]
        }
        if (abs(sum) > 1e-12) {
            for (k in 0 until TAPS) kernel[k] /= sum
        }
        return kernel
    }

    /** Enkel lineær interpolasjon for oppsampling (sjeldan – berre om eininga tek opp lågt). */
    private fun interpolate(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        val ratio = fromRate.toDouble() / toRate
        val outLength = (samples.size / ratio).toInt()
        val out = FloatArray(outLength)
        for (i in 0 until outLength) {
            val pos = i * ratio
            val left = pos.toInt()
            val right = (left + 1).coerceAtMost(samples.size - 1)
            val frac = (pos - left).toFloat()
            out[i] = samples[left] * (1f - frac) + samples[right] * frac
        }
        return out
    }
}
