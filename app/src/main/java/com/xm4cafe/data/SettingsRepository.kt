// app/src/main/java/com/coffeehouse/data/SettingsRepository.kt
package com.coffeehouse.data

import com.coffeehouse.model.CafeSettings
import com.coffeehouse.model.Preset
import kotlinx.coroutines.flow.Flow

/**
 * DataStore (Preferences) wrapper. Spec §9.
 *
 * Phase 1: interface declared, no implementation.
 * Phase 2: add a class backed by Context.dataStore (preferencesDataStore delegate)
 *          and emit CafeSettings on every key change.
 */
interface SettingsRepository {
    val settingsFlow: Flow<CafeSettings>
    suspend fun saveSettings(settings: CafeSettings)
    suspend fun savePreset(preset: Preset)
    suspend fun loadSettings(): CafeSettings
}
