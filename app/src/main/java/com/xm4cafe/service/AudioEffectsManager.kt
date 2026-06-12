// app/src/main/java/com/xm4cafe/service/AudioEffectsManager.kt
package com.xm4cafe.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.xm4cafe.model.CafeSettings

/**
 * Owns the four AudioEffect objects on session 0 (global mix).
 * All public methods are safe to call from any thread; native calls
 * are wrapped in try/catch so a single failed effect never tears the
 * whole chain down.
 *
 * See spec §6 for chain order and rationale.
 */
class AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var presetReverb: PresetReverb? = null
    private var virtualizer: Virtualizer? = null

    /**
     * Construct all four effects against session 0, in the order required
     * by the spec (§6.3): Equalizer → BassBoost → PresetReverb → Virtualizer.
     * Any constructor that throws is logged and leaves its field null;
     * applySettings() tolerates null fields gracefully.
     */
    fun initEffects(settings: CafeSettings) {
        try {
            equalizer = Equalizer(0, 0).also {
                Log.d("XM4Cafe", "Equalizer init OK, bands=${it.numberOfBands}")
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "Equalizer init failed: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, 0).also {
                Log.d("XM4Cafe", "BassBoost init OK")
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "BassBoost init failed: ${e.message}")
        }

        try {
            presetReverb = PresetReverb(0, 0).also {
                Log.d("XM4Cafe", "PresetReverb init OK")
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "PresetReverb init failed: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, 0).also {
                Log.d("XM4Cafe", "Virtualizer init OK")
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "Virtualizer init failed: ${e.message}")
        }

        applySettings(settings)
    }

    /**
     * Apply DSP values from [settings] to the live effect chain.
     * Order: EQ → BassBoost → Reverb → Virtualizer.
     * Each native call is independently guarded so one bad call does not
     * abort the rest of the chain.
     */
    fun applySettings(settings: CafeSettings) {
        // ---- Equalizer ----
        try {
            equalizer?.let { eq ->
                eq.enabled = settings.eqEnabled
                if (settings.eqEnabled) {
                    val bands = eq.numberOfBands.toInt()
                    val safeBands = minOf(bands, settings.eqBands.size)
                    for (i in 0 until safeBands) {
                        eq.setBandLevel(i.toShort(), settings.eqBands[i].toShort())
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "Equalizer apply failed: ${e.message}")
        }

        // ---- BassBoost ----
        try {
            bassBoost?.let { bb ->
                bb.enabled = settings.bassBoostEnabled
                if (settings.bassBoostEnabled) {
                    bb.setStrength(settings.bassBoostStrength)
                }
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "BassBoost apply failed: ${e.message}")
        }

        // ---- PresetReverb ----
        try {
            presetReverb?.let { pr ->
                pr.enabled = settings.reverbEnabled
                if (settings.reverbEnabled) {
                    pr.preset = settings.reverbPreset
                }
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "PresetReverb apply failed: ${e.message}")
        }

        // ---- Virtualizer ----
        try {
            virtualizer?.let { v ->
                v.enabled = settings.virtualizerEnabled
                if (settings.virtualizerEnabled) {
                    v.setStrength(settings.virtualizerStrength)
                }
            }
        } catch (e: RuntimeException) {
            Log.e("XM4Cafe", "Virtualizer apply failed: ${e.message}")
        }
    }

    /**
     * Release every effect individually. Each release is independently
     * guarded; the field is nulled unconditionally afterwards so a partial
     * release still leaves the manager in a clean re-initialisable state.
     */
    fun releaseAll() {
        try { equalizer?.release() } catch (e: Exception) {
            Log.e("XM4Cafe", "Equalizer release error: ${e.message}")
        }
        equalizer = null

        try { bassBoost?.release() } catch (e: Exception) {
            Log.e("XM4Cafe", "BassBoost release error: ${e.message}")
        }
        bassBoost = null

        try { presetReverb?.release() } catch (e: Exception) {
            Log.e("XM4Cafe", "PresetReverb release error: ${e.message}")
        }
        presetReverb = null

        try { virtualizer?.release() } catch (e: Exception) {
            Log.e("XM4Cafe", "Virtualizer release error: ${e.message}")
        }
        virtualizer = null
    }
}
