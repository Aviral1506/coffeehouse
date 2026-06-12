// app/src/main/java/com/coffeehouse/viewmodel/MainViewModel.kt
package com.coffeehouse.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.audiofx.PresetReverb
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coffeehouse.data.SettingsRepositoryImpl
import com.coffeehouse.model.CafeSettings
import com.coffeehouse.model.Preset
import com.coffeehouse.service.AudioEffectService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phase 4 — single screen UI state holder and service-binding owner.
 *
 * Lifecycle:
 *   - init binds to AudioEffectService (BIND_AUTO_CREATE) and also calls
 *     startForegroundService() so the service outlives the ViewModel.
 *   - A coroutine combines the four slider StateFlows, debounces by 80ms
 *     (Constraint E), and pushes the merged CafeSettings to the service.
 *   - Another coroutine polls BluetoothManager every 5s for XM4 presence.
 *   - onCleared() unbinds — the service keeps running.
 *
 * Note on Constraint E: the prompt explicitly asks for snapshotFlow + debounce
 * in spirit, but slider state lives in MutableStateFlow already, so combine()
 * gives the same stream shape without a snapshotFlow indirection.
 */
data class UiState(
    val activePreset: Preset = Preset.CAFE,
    val settings: CafeSettings = Preset.CAFE.toSettings(5),
    val effectsEnabled: Boolean = true,
    val xm4Connected: Boolean = false,
    val xm4DeviceName: String = "",
    val serviceConnected: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ---- Service binding ----
    private var audioService: AudioEffectService? = null
    private var serviceBound = false

    // ---- Aggregate UI state ----
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ---- Per-slider state (debounced as a group before hitting the service) ----
    private val _roomSize = MutableStateFlow(67f)
    private val _width    = MutableStateFlow(700f)
    private val _air      = MutableStateFlow(-250f)
    private val _warmth   = MutableStateFlow(150f)
    val roomSize: StateFlow<Float> = _roomSize.asStateFlow()
    val width:    StateFlow<Float> = _width.asStateFlow()
    val air:      StateFlow<Float> = _air.asStateFlow()
    val warmth:   StateFlow<Float> = _warmth.asStateFlow()

    // ---- Phase 5: custom presets ----
    private val _customPresets = MutableStateFlow<Map<String, CafeSettings>>(emptyMap())
    val customPresets: StateFlow<Map<String, CafeSettings>> = _customPresets.asStateFlow()

    // ---- Phase 5: save-preset dialog visibility ----
    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            audioService = (binder as AudioEffectService.LocalBinder).getService()
            serviceBound = true
            val preset = audioService!!.getCurrentPreset()
            _uiState.update {
                it.copy(
                    serviceConnected = true,
                    activePreset = preset,
                )
            }
            syncSlidersFromPreset(preset)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            serviceBound = false
            _uiState.update { it.copy(serviceConnected = false) }
        }
    }

    init {
        val app = getApplication<Application>()

        // 1. Bind + start the service so it outlives this ViewModel.
        val intent = Intent(app, AudioEffectService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        ContextCompat.startForegroundService(app, intent)

        // 2. Poll Bluetooth every 5s for XM4 presence.
        viewModelScope.launch {
            while (true) {
                checkBluetoothConnection()
                delay(5000)
            }
        }

        // 3. Debounce-collect slider changes and push to the service.
        viewModelScope.launch {
            sliderSettingsFlow().collect { settings ->
                val state = _uiState.value
                // Suppress programmatic slider syncs that fire when OFF is
                // selected (they would otherwise re-enable the reverb), and
                // suppress the startup emission before the service has synced
                // its real preset.
                if (state.serviceConnected && state.activePreset != Preset.OFF) {
                    audioService?.applyManualSettings(settings)
                    _uiState.update { it.copy(settings = settings) }
                }
            }
        }

        // 4. Phase 5: hydrate custom presets from DataStore at startup so
        //    the chip row reflects persisted state without a UI gesture.
        viewModelScope.launch {
            val repo = SettingsRepositoryImpl(getApplication())
            _customPresets.value = repo.loadCustomPresets()
        }
    }

    @OptIn(FlowPreview::class)
    private fun sliderSettingsFlow() =
        combine(_roomSize, _width, _air, _warmth) { r, w, a, wm ->
            buildSettingsFromSliders(r, w, a, wm)
        }.debounce(80)

    // Tracks the most recent non-OFF preset so toggling effects off then on
    // resumes where the user left off rather than ping-ponging OFF→OFF.
    private var lastNonOffPreset: Preset = Preset.CAFE

    // ---- Public API for MainScreen ----

    fun selectPreset(preset: Preset) {
        if (preset != Preset.OFF) lastNonOffPreset = preset
        audioService?.applyPreset(preset)
        _uiState.update { it.copy(activePreset = preset) }
        syncSlidersFromPreset(preset)
    }

    fun toggleEffects(enabled: Boolean) {
        if (enabled) {
            // Resume the last non-OFF preset (deviates from a literal reading
            // of the spec to avoid the "toggle on re-applies OFF" loop).
            selectPreset(lastNonOffPreset)
        } else {
            audioService?.applyPreset(Preset.OFF)
            _uiState.update {
                it.copy(activePreset = Preset.OFF, effectsEnabled = false)
            }
        }
        _uiState.update { it.copy(effectsEnabled = enabled) }
    }

    // ---- Phase 5: custom preset API ----

    fun requestSaveCustomPreset() { _showSaveDialog.value = true }
    fun dismissSaveDialog()       { _showSaveDialog.value = false }

    fun saveCustomPreset(name: String) {
        if (name.isBlank()) return
        _showSaveDialog.value = false
        // Snapshot the live slider values into a CafeSettings — same path the
        // debounced collector uses, so the stored preset is identical to what
        // the user is currently hearing.
        val currentSettings = buildSettingsFromSliders(
            _roomSize.value, _width.value, _air.value, _warmth.value
        )
        viewModelScope.launch {
            val repo = SettingsRepositoryImpl(getApplication())
            repo.saveCustomPreset(name, currentSettings)
            _customPresets.value = repo.loadCustomPresets()
        }
    }

    fun deleteCustomPreset(name: String) {
        viewModelScope.launch {
            val repo = SettingsRepositoryImpl(getApplication())
            repo.deleteCustomPreset(name)
            _customPresets.value = repo.loadCustomPresets()
        }
    }

    fun selectCustomPreset(name: String) {
        val settings = _customPresets.value[name] ?: return
        audioService?.applyManualSettings(settings)
        // Sync sliders to custom preset values so the panel reflects the
        // actual DSP state. Reverb is reverse-engineered from the preset
        // constant; the other three are direct mappings.
        _roomSize.value = when (settings.reverbPreset) {
            PresetReverb.PRESET_SMALLROOM.toShort()  -> 16.5f
            PresetReverb.PRESET_MEDIUMROOM.toShort() -> 50f
            else                                     -> 83.5f
        }
        _width.value  = settings.virtualizerStrength.toFloat()
        _air.value    = settings.eqBands.lastOrNull()?.toFloat() ?: -250f
        _warmth.value = settings.bassBoostStrength.toFloat()
        // Keep activePreset showing the last built-in preset (not OFF) so the
        // SliderPanel stays visible — selecting a custom preset is a tweak,
        // not a master-effects-off action.
        _uiState.update {
            it.copy(
                activePreset = if (it.activePreset == Preset.OFF) lastNonOffPreset else it.activePreset
            )
        }
    }

    fun onRoomSizeChange(value: Float) { _roomSize.value = value }
    fun onWidthChange(value: Float)    { _width.value    = value }
    fun onAirChange(value: Float)      { _air.value      = value }
    fun onWarmthChange(value: Float)   { _warmth.value   = value }

    // ---- Helpers ----

    private fun syncSlidersFromPreset(preset: Preset) {
        val s = preset.toSettings(SettingsRepositoryImpl.DEFAULT_NUM_BANDS)
        _roomSize.value = when (s.reverbPreset) {
            PresetReverb.PRESET_SMALLROOM.toShort()  -> 16.5f
            PresetReverb.PRESET_MEDIUMROOM.toShort() -> 50f
            else                                     -> 83.5f
        }
        _width.value  = s.virtualizerStrength.toFloat()
        _air.value    = s.eqBands.lastOrNull()?.toFloat() ?: -250f
        _warmth.value = s.bassBoostStrength.toFloat()
    }

    internal fun buildSettingsFromSliders(
        roomSize: Float,
        width: Float,
        air: Float,
        warmth: Float,
    ): CafeSettings {
        val reverbPreset = when {
            roomSize <= 33f -> PresetReverb.PRESET_SMALLROOM.toShort()
            roomSize <= 66f -> PresetReverb.PRESET_MEDIUMROOM.toShort()
            else            -> PresetReverb.PRESET_LARGEROOM.toShort()
        }
        val numBands = SettingsRepositoryImpl.DEFAULT_NUM_BANDS
        val eqBands  = List(numBands) { i ->
            if (i >= numBands - 2) air.toInt() else 0
        }
        return CafeSettings(
            reverbPreset        = reverbPreset,
            reverbEnabled       = true,
            virtualizerStrength = width.toInt().toShort(),
            virtualizerEnabled  = true,
            bassBoostStrength   = warmth.toInt().toShort(),
            bassBoostEnabled    = true,
            eqBands             = eqBands,
            eqEnabled           = true,
        )
    }

    /**
     * Pragmatic XM4 detection: list bonded BT devices and report whether any
     * match the WH-1000XM4 name pattern. bondedDevices does NOT distinguish
     * "currently connected" from "paired but offline" — Phase 5 will refine
     * this with a Bluetooth connection-state broadcast receiver. For now,
     * paired-with-XM4 is treated as connected.
     *
     * All calls are guarded against SecurityException because the user may
     * have denied BLUETOOTH_CONNECT (Constraint D).
     */
    private fun checkBluetoothConnection() {
        val btManager = getApplication<Application>()
            .getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _uiState.update { it.copy(xm4Connected = false, xm4DeviceName = "") }
            return
        }
        try {
            // Spec note: getConnectedDevices(BluetoothProfile.HEADSET/A2DP)
            // requires a BluetoothProfile.ServiceListener round-trip — too
            // heavy for a 5s poll. bondedDevices is the cheap approximation
            // documented in the prompt; Phase 5 swaps this for a connection
            // broadcast receiver.
            val xm4 = adapter.bondedDevices
                ?.firstOrNull { device ->
                    device.name?.contains("WH-1000XM4", ignoreCase = true) == true ||
                    device.name?.contains("WH1000XM4",  ignoreCase = true) == true
                }
            _uiState.update {
                it.copy(
                    xm4Connected  = xm4 != null,
                    xm4DeviceName = xm4?.name ?: "",
                )
            }
        } catch (e: SecurityException) {
            Log.w("Coffeehouse", "Bluetooth permission denied: ${e.message}")
            _uiState.update { it.copy(xm4Connected = false, xm4DeviceName = "") }
        }
    }

    override fun onCleared() {
        if (serviceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Already unbound (e.g. process death recovery); safe to swallow.
            }
            serviceBound = false
        }
        super.onCleared()
    }
}
