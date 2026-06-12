// app/src/main/java/com/xm4cafe/BootReceiver.kt
package com.xm4cafe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xm4cafe.data.BootStartWorker
import java.util.concurrent.TimeUnit

/**
 * BOOT_COMPLETED receiver — Phase 3 implementation.
 *
 * Cannot call startForegroundService() directly (Android 15+ blocks
 * mediaPlayback FGS launches from BOOT_COMPLETED). Instead, enqueues a
 * one-time WorkRequest with a 3-second initial delay: the delay gives the
 * audio server time to settle so attaching effects to session 0 doesn't
 * race the framework's own startup, and the Worker's execution context
 * is permitted to start the FGS.
 *
 * Uniqueness key "boot_start" + REPLACE policy ensures a redundant
 * BOOT_COMPLETED (rare but possible on multi-user devices) won't fan out.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("XM4Cafe", "Boot received — scheduling service start via WorkManager")
        val request = OneTimeWorkRequestBuilder<BootStartWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "boot_start",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
