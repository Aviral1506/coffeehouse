// app/src/main/java/com/coffeehouse/data/SettingsRepositoryImpl.kt
package com.coffeehouse.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.coffeehouse.model.CafeSettings
import com.coffeehouse.model.CustomPreset
import com.coffeehouse.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Top-level Context extension property — Constraint C.
 * The preferencesDataStore delegate registers an instance per Context at file
 * scope; declaring it inside the class body would create a new instance per
 * SettingsRepositoryImpl, violating the single-DataStore-per-file rule.
 */
private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "xm4_settings")

/**
 * DataStore-backed implementation of [SettingsRepository] (spec §9, Phase 3).
 *
 * All reads/writes happen on coroutines (Constraint B). The DataStore library
 * itself dispatches to Dispatchers.IO internally; callers only need to be in
 * a coroutine context (lifecycleScope.launch { ... } in the service).
 */
class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    // ---- Preference keys (per spec §9 key reference) ----
    private val KEY_ACTIVE_PRESET   = stringPreferencesKey("active_preset")
    private val KEY_REVERB_PRESET   = intPreferencesKey("reverb_preset")
    private val KEY_VIRTUALIZER_STR = intPreferencesKey("virtualizer_strength")
    private val KEY_BASS_BOOST_STR  = intPreferencesKey("bass_boost_strength")
    private val KEY_REVERB_ENABLED  = booleanPreferencesKey("reverb_enabled")
    private val KEY_VIRTUALIZER_EN  = booleanPreferencesKey("virtualizer_enabled")
    private val KEY_BASS_BOOST_EN   = booleanPreferencesKey("bass_boost_enabled")
    private val KEY_EQ_ENABLED      = booleanPreferencesKey("eq_enabled")
    private val KEY_EFFECTS_ENABLED = booleanPreferencesKey("effects_enabled")
    private val KEY_EQ_BAND_PREFIX  = "eq_band_"

    companion object {
        /** Spec §9: always read this many EQ bands from DataStore. */
        const val DEFAULT_NUM_BANDS = 5
    }

    // ---- Reactive stream ----

    override val settingsFlow: Flow<CafeSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs -> prefsToSettings(prefs) }

    // ---- One-shot reads ----

    override suspend fun loadSettings(): CafeSettings = try {
        context.dataStore.data.first().let { prefs -> prefsToSettings(prefs) }
    } catch (e: Exception) {
        // On any failure (IO, corruption, migration) fall back to CAFE preset.
        Preset.CAFE.toSettings(DEFAULT_NUM_BANDS)
    }

    /**
     * Read just the active-preset name. Used by AudioEffectService.onStartCommand
     * to restore the notification label without re-deriving it from raw DSP values.
     */
    suspend fun loadActivePresetName(): String = try {
        context.dataStore.data.first()[KEY_ACTIVE_PRESET] ?: Preset.CAFE.name
    } catch (e: Exception) {
        Preset.CAFE.name
    }

    // ---- Writes ----

    override suspend fun saveSettings(settings: CafeSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REVERB_PRESET]   = settings.reverbPreset.toInt()
            prefs[KEY_REVERB_ENABLED]  = settings.reverbEnabled
            prefs[KEY_VIRTUALIZER_STR] = settings.virtualizerStrength.toInt()
            prefs[KEY_VIRTUALIZER_EN]  = settings.virtualizerEnabled
            prefs[KEY_BASS_BOOST_STR]  = settings.bassBoostStrength.toInt()
            prefs[KEY_BASS_BOOST_EN]   = settings.bassBoostEnabled
            prefs[KEY_EQ_ENABLED]      = settings.eqEnabled
            prefs[KEY_EFFECTS_ENABLED] = true
            // EQ bands: write one int per band under "eq_band_$i".
            settings.eqBands.forEachIndexed { i, value ->
                prefs[intPreferencesKey("$KEY_EQ_BAND_PREFIX$i")] = value
            }
        }
    }

    override suspend fun savePreset(preset: Preset) {
        // Only the active-preset name — DSP values are saved separately via
        // saveSettings(). Keeps single-responsibility per the spec.
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PRESET] = preset.name
        }
    }

    // ---- Mapping helper ----

    private fun prefsToSettings(prefs: Preferences): CafeSettings {
        // CAFE defaults are the fallback when no value has ever been written.
        val defaults = Preset.CAFE.toSettings(DEFAULT_NUM_BANDS)

        val eqBands: List<Int> = List(DEFAULT_NUM_BANDS) { i ->
            prefs[intPreferencesKey("$KEY_EQ_BAND_PREFIX$i")]
                ?: defaults.eqBands.getOrElse(i) { 0 }
        }

        return CafeSettings(
            reverbPreset        = (prefs[KEY_REVERB_PRESET]   ?: defaults.reverbPreset.toInt()).toShort(),
            reverbEnabled       =  prefs[KEY_REVERB_ENABLED]  ?: defaults.reverbEnabled,
            virtualizerStrength = (prefs[KEY_VIRTUALIZER_STR] ?: defaults.virtualizerStrength.toInt()).toShort(),
            virtualizerEnabled  =  prefs[KEY_VIRTUALIZER_EN]  ?: defaults.virtualizerEnabled,
            bassBoostStrength   = (prefs[KEY_BASS_BOOST_STR]  ?: defaults.bassBoostStrength.toInt()).toShort(),
            bassBoostEnabled    =  prefs[KEY_BASS_BOOST_EN]   ?: defaults.bassBoostEnabled,
            eqBands             = eqBands,
            eqEnabled           =  prefs[KEY_EQ_ENABLED]      ?: defaults.eqEnabled,
        )
    }

    // ---- Custom presets (Phase 5) ----
    // Not part of the SettingsRepository interface — see the Phase 5 spec
    // "do not add custom preset methods to the interface" directive.
    // Stored under "custom_preset_$safeKey" where safeKey sanitises out any
    // character that DataStore preference keys reject.

    suspend fun saveCustomPreset(name: String, settings: CafeSettings) {
        val safeKey = name.replace(Regex("[^A-Za-z0-9_]"), "_")
        val preset  = CustomPreset(name, settings)
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("custom_preset_$safeKey")] =
                CustomPreset.toJson(preset)
        }
    }

    suspend fun loadCustomPresets(): Map<String, CafeSettings> {
        return try {
            val prefs = context.dataStore.data.first()
            prefs.asMap()
                .entries
                .filter { it.key.name.startsWith("custom_preset_") }
                .mapNotNull { entry ->
                    val json = entry.value as? String ?: return@mapNotNull null
                    CustomPreset.fromJson(json)
                }
                .associate { it.name to it.settings }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun deleteCustomPreset(name: String) {
        val safeKey = name.replace(Regex("[^A-Za-z0-9_]"), "_")
        context.dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("custom_preset_$safeKey"))
        }
    }
}
