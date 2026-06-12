// app/src/main/java/com/coffeehouse/data/BootStartWorker.kt
package com.coffeehouse.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.coffeehouse.service.AudioEffectService

/**
 * WorkManager Worker that promotes a post-boot context into a valid
 * foreground-service launcher (Constraint A).
 *
 * BOOT_COMPLETED receivers on Android 15+ cannot directly call
 * startForegroundService() for a mediaPlayback FGS — it throws
 * ForegroundServiceStartNotAllowedException. WorkManager's execution
 * context is NOT a broadcast receiver context, so the call is allowed
 * here. The Worker just kicks off the service and returns immediately;
 * the service itself reads its own settings from DataStore in onCreate().
 */
class BootStartWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, AudioEffectService::class.java)
            )
            Log.d("Coffeehouse", "BootStartWorker: startForegroundService dispatched")
            Result.success()
        } catch (e: Exception) {
            Log.e("Coffeehouse", "BootStartWorker: failed to start service: ${e.message}")
            Result.failure()
        }
    }
}
