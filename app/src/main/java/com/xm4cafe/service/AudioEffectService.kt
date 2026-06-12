// app/src/main/java/com/coffeehouse/service/AudioEffectService.kt
package com.coffeehouse.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.coffeehouse.data.SettingsRepositoryImpl
import com.coffeehouse.model.CafeSettings
import com.coffeehouse.model.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns the global-mix AudioEffect chain (spec §6).
 *
 * Extends [LifecycleService] (not raw Service) so we get a real
 * [lifecycleScope] — required because AudioEffect constructors block for
 * 10–50ms each, and Android 16's foreground-promotion deadline forces us
 * to call [ServiceCompat.startForeground] before any of that work runs.
 */
class AudioEffectService : LifecycleService() {

    private val effectsManager = AudioEffectsManager()
    private var currentPreset = Preset.CAFE

    // Phase 3: DataStore-backed settings persistence.
    private lateinit var repository: SettingsRepositoryImpl

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "coffeehouse_channel"
    private val ACTION_TURN_OFF = "com.coffeehouse.action.TURN_OFF"

    // ---- Binder for the Phase 4 ViewModel ----
    inner class LocalBinder : Binder() {
        fun getService(): AudioEffectService = this@AudioEffectService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    // ---- Public API consumed by MainViewModel in Phase 4 ----

    fun applyPreset(preset: Preset) {
        currentPreset = preset
        lifecycleScope.launch {
            val numBands = getNumBands()
            val settings = preset.toSettings(numBands)
            effectsManager.applySettings(settings)
            // Phase 3: persist both the full DSP state and the active-preset name.
            repository.saveSettings(settings)
            repository.savePreset(preset)
            updateNotification(preset)
        }
    }

    fun getCurrentPreset(): Preset = currentPreset

    fun isEffectActive(): Boolean = (currentPreset != Preset.OFF)

    /**
     * Phase 4 — push a manually-tweaked CafeSettings straight at the effect
     * chain (sliders bypass the preset table). Persisted alongside so it
     * survives reboot via the standard load path in onStartCommand.
     */
    fun applyManualSettings(settings: CafeSettings) {
        lifecycleScope.launch {
            effectsManager.applySettings(settings)
            repository.saveSettings(settings)
        }
    }

    // ---- Lifecycle ----

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Phase 3: instantiate repository before any registration so onStartCommand
        // can safely call into it on any subsequent invocation.
        repository = SettingsRepositoryImpl(applicationContext)

        // Constraint C: dynamic registration — implicit broadcasts from the
        // manifest are blocked on Android 8+ and silently dropped on 16.
        ContextCompat.registerReceiver(
            this,
            sessionReceiver,
            IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Phase 5 Note A — Bluetooth ACL connect/disconnect. These broadcasts
        // are explicit on API 36 and would fire for the manifest receiver too,
        // but registering here keeps the service self-contained and avoids
        // any ambiguity about lifecycle.
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_TURN_OFF) {
            applyPreset(Preset.OFF)
            return START_STICKY
        }

        // Step 1 — promote to foreground IMMEDIATELY (Constraints A + B).
        // Must come before any AudioEffect work; ServiceCompat handles the
        // API-level branching internally, so no SDK_INT guard here.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        // Step 2 — load persisted settings, then initialise the effect chain
        // asynchronously so the ~200ms of native constructor work never
        // blocks the main thread. Constraint B: all DataStore work via a
        // coroutine, never runBlocking on the main thread.
        lifecycleScope.launch {
            val numBands = getNumBands()
            val savedSettings = try {
                repository.loadSettings()
            } catch (e: Exception) {
                Log.w("Coffeehouse", "loadSettings failed, using default: ${e.message}")
                currentPreset.toSettings(numBands)
            }
            val savedPresetName = repository.loadActivePresetName()
            currentPreset = try {
                Preset.valueOf(savedPresetName)
            } catch (e: IllegalArgumentException) {
                Preset.CAFE
            }
            effectsManager.initEffects(savedSettings)
            updateNotification(currentPreset)
            Log.d("Coffeehouse", "Effects initialised with saved preset: $currentPreset")
        }

        // Step 3 — sticky so the system tries to revive us if killed.
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Was never registered (onCreate failed) or already torn down.
        }
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: IllegalArgumentException) {
            // onCreate may have crashed before registration; safe to ignore.
        }
        effectsManager.releaseAll()
        super.onDestroy()
    }

    // ---- Notification plumbing ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Coffeehouse",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(preset: Preset = currentPreset): Notification {
        val pretty = preset.name
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        val isActive = preset != Preset.OFF

        val turnOffIntent = Intent(this, AudioEffectService::class.java).apply {
            action = ACTION_TURN_OFF
        }
        val turnOffPendingIntent = PendingIntent.getService(
            this,
            0,
            turnOffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Coffeehouse")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(isActive)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .also { builder ->
                if (isActive) {
                    builder
                        .setContentText("$pretty - Active")
                        .addAction(
                            android.R.drawable.ic_media_pause,
                            "Turn off",
                            turnOffPendingIntent
                        )
                } else {
                    builder.setContentText("Off")
                }
            }
            .build()
    }

    private fun updateNotification(preset: Preset) {
        if (preset == Preset.OFF) {
            removeForegroundNotification()
            return
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(preset),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun removeForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    // ---- Helpers ----

    /**
     * Query the device-reported number of EQ bands by briefly constructing
     * a throwaway Equalizer on session 0. Done off the main thread because
     * the constructor blocks. Falls back to a safe default of 5 if the
     * native layer refuses (very rare on stock Pixel).
     */
    private suspend fun getNumBands(): Int = withContext(Dispatchers.IO) {
        try {
            Equalizer(0, 0).let { tmp ->
                val n = tmp.numberOfBands.toInt()
                tmp.release()
                n
            }
        } catch (e: RuntimeException) {
            Log.w("Coffeehouse", "getNumBands fallback to 5: ${e.message}")
            5
        }
    }

    // ---- Audio effect control session broadcasts ----
    // Phase 2: log only. Session 0 already catches YouTube Music on Pixel 6;
    // per-session attachment is a Phase 5 enhancement if ever needed.
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                    Log.d("Coffeehouse", "Audio session opened: $sessionId")
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                    Log.d("Coffeehouse", "Audio session closed: $sessionId")
                }
            }
        }
    }

    // ---- Phase 5: XM4 reconnect handling ----
    // When the XM4 disconnects and reconnects, the AudioEffect instances on
    // session 0 are still alive but their attachment to the active output
    // becomes stale. Re-applying the current preset's settings after a short
    // delay (waiting for A2DP to re-route) restores the cafe sound with no
    // user action. We do NOT release effects on disconnect — they re-attach
    // automatically when the next output appears, and rebuilding them on
    // every disconnect/reconnect cycle would just add latency.
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d("Coffeehouse", "Bluetooth connected — re-applying effects in 1500ms")
                    lifecycleScope.launch {
                        delay(1500)          // Wait for A2DP route to activate
                        val numBands = getNumBands()
                        val settings = currentPreset.toSettings(numBands)
                        effectsManager.applySettings(settings)
                        Log.d("Coffeehouse", "Effects re-applied after reconnect")
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d("Coffeehouse", "Bluetooth disconnected")
                    // Do not release effects — they re-attach on reconnect.
                }
            }
        }
    }
}
