// app/src/main/java/com/xm4cafe/model/CafeSettings.kt
package com.xm4cafe.model

/**
 * All DSP parameters in one serialisable data class (spec §5.1).
 * Written to DataStore; passed between ViewModel and AudioEffectService.
 */
data class CafeSettings(
    val reverbPreset: Short,           // PresetReverb.PRESET_* constant
    val reverbEnabled: Boolean,        // allows disabling reverb independently
    val virtualizerStrength: Short,    // 0–1000
    val virtualizerEnabled: Boolean,
    val bassBoostStrength: Short,      // 0–1000
    val bassBoostEnabled: Boolean,
    val eqBands: List<Int>,            // millibels per band, length = numBands
    val eqEnabled: Boolean,
)
