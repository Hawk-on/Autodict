package com.autodict.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioResamplerTest {

    private fun sine(freqHz: Double, sampleRate: Int, seconds: Double = 1.0): FloatArray {
        val n = (sampleRate * seconds).toInt()
        return FloatArray(n) { i -> sin(2.0 * PI * freqHz * i / sampleRate).toFloat() }
    }

    /** RMS over midtpartiet, så filterets kant-effektar ikkje forstyrrar målinga. */
    private fun middleRms(samples: FloatArray): Double {
        val from = samples.size / 10
        val to = samples.size - samples.size / 10
        if (to <= from) return 0.0
        var sum = 0.0
        for (i in from until to) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun returnsInputWhenRateAlreadyMatches() {
        val input = sine(440.0, 16_000, seconds = 0.1)
        assertTrue(AudioResampler.toWhisperRate(input, 16_000) === input)
    }

    @Test
    fun decimationGivesExpectedLength() {
        val input = sine(440.0, 48_000)
        val output = AudioResampler.toWhisperRate(input, 48_000)
        assertEquals(16_000, output.size)
    }

    @Test
    fun speechRangeTonePassesThrough() {
        // 400 Hz ligg midt i talebandet og skal overleve nedsamplinga tilnærma uendra.
        val input = sine(400.0, 48_000)
        val output = AudioResampler.toWhisperRate(input, 48_000)

        val ratio = middleRms(output) / middleRms(input)
        assertTrue("Talebandet vart dempa for mykje: $ratio", ratio > 0.9)
        assertTrue("Uventa forsterking: $ratio", ratio < 1.1)
    }

    @Test
    fun toneAboveTargetNyquistIsFilteredOut() {
        // 12 kHz er over 8 kHz (Nyquist for 16 kHz). Utan lågpassfilter ville denne blitt
        // spegla ned til 4 kHz – midt i talebandet – som falsk støy.
        val input = sine(12_000.0, 48_000)
        val output = AudioResampler.toWhisperRate(input, 48_000)

        val ratio = middleRms(output) / middleRms(input)
        assertTrue("Aliasing: tonen over Nyquist vart ikkje filtrert bort ($ratio)", ratio < 0.05)
    }

    @Test
    fun handlesNonIntegerRatio() {
        val input = sine(400.0, 44_100)
        val output = AudioResampler.toWhisperRate(input, 44_100)

        // 44,1 kHz -> 16 kHz er ikkje eit heiltalsforhold; lengda skal likevel stemme.
        assertEquals(16_000, output.size)
        assertTrue(middleRms(output) / middleRms(input) > 0.9)
    }

    @Test
    fun handlesEmptyAndInvalidInput() {
        assertEquals(0, AudioResampler.toWhisperRate(FloatArray(0), 48_000).size)
        val input = sine(400.0, 48_000, seconds = 0.01)
        assertTrue(AudioResampler.resample(input, 0, 16_000) === input)
    }

    @Test
    fun upsamplesWhenDeviceRecordsBelowTargetRate() {
        val input = sine(400.0, 8_000)
        val output = AudioResampler.toWhisperRate(input, 8_000)
        assertEquals(16_000, output.size)
    }
}
