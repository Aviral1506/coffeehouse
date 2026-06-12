// app/src/main/java/com/coffeehouse/model/Preset.kt
package com.coffeehouse.model

import android.media.audiofx.PresetReverb

/**
 * The four user-selectable presets (spec §5.2).
 * Each preset knows how to materialise itself into a [CafeSettings]
 * given the device-reported number of EQ bands.
 */
enum class Preset {
    CAFE,
    LIVING_ROOM,
    MY_ROOM,
    OFF;

    fun toSettings(numBands: Int): CafeSettings = when (this) {
        CAFE -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_LARGEROOM.toShort(),
            reverbEnabled       = true,
            virtualizerStrength = 820.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 260.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildCafeEqBands(
                numBands = numBands,
                airCut = -950,
                bodyBoost = 180,
                lowCut = -180,
            ),
            eqEnabled           = true,
        )
        LIVING_ROOM -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_MEDIUMROOM.toShort(),
            reverbEnabled       = true,
            virtualizerStrength = 450.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 100.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildCafeEqBands(
                numBands = numBands,
                airCut = -450,
                bodyBoost = 90,
                lowCut = -80,
            ),
            eqEnabled           = true,
        )
        MY_ROOM -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_SMALLROOM.toShort(),
            reverbEnabled       = true,
            virtualizerStrength = 250.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 50.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildCafeEqBands(
                numBands = numBands,
                airCut = -200,
                bodyBoost = 40,
                lowCut = 0,
            ),
            eqEnabled           = true,
        )
        OFF -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_NONE.toShort(),
            reverbEnabled       = false,
            virtualizerStrength = 0.toShort(),
            virtualizerEnabled  = false,
            bassBoostStrength   = 0.toShort(),
            bassBoostEnabled    = false,
            eqBands             = List(numBands) { 0 },
            eqEnabled           = false,
        )
    }
}

/**
 * Build a cafe/small-speaker voicing curve.
 *
 * Android EQ bands are ordered low to high, but the exact centre frequencies
 * vary by device. This normalized shape keeps the intent stable:
 * contain sub-bass, warm the low mids, soften upper mids, and roll off air.
 */
fun buildCafeEqBands(
    numBands: Int,
    airCut: Int,
    bodyBoost: Int,
    lowCut: Int,
): List<Int> {
    if (numBands <= 0) return emptyList()
    if (numBands == 1) return listOf(airCut)

    return List(numBands) { index ->
        val position = index.toFloat() / (numBands - 1).toFloat()
        when {
            position < 0.18f -> lowCut
            position < 0.45f -> bodyBoost
            position < 0.65f -> bodyBoost / 2
            position < 0.82f -> (airCut * 0.65f).toInt()
            else             -> airCut
        }
    }
}
