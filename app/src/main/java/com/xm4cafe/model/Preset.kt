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
            virtualizerStrength = 700.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 150.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildEqBands(numBands, topCut = -250),
            eqEnabled           = true,
        )
        LIVING_ROOM -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_MEDIUMROOM.toShort(),
            reverbEnabled       = true,
            virtualizerStrength = 450.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 100.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildEqBands(numBands, topCut = -150),
            eqEnabled           = true,
        )
        MY_ROOM -> CafeSettings(
            reverbPreset        = PresetReverb.PRESET_SMALLROOM.toShort(),
            reverbEnabled       = true,
            virtualizerStrength = 250.toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = 50.toShort(),
            bassBoostEnabled    = true,
            eqBands             = buildEqBands(numBands, topCut = -75),
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
 * Build an EQ band list of length [numBands] where all values are 0 except
 * the top two bands (highest frequencies), which are cut to [topCut] millibels.
 *
 *  - numBands == 0  -> emptyList()
 *  - numBands == 1  -> [topCut]
 *  - numBands >= 2  -> [0, 0, ..., topCut, topCut]
 */
private fun buildEqBands(numBands: Int, topCut: Int): List<Int> {
    if (numBands <= 0) return emptyList()
    if (numBands == 1) return listOf(topCut)
    return List(numBands) { index ->
        if (index >= numBands - 2) topCut else 0
    }
}
