package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                Global.launch {
                    Companion.reset()
                    Companion.rescheduleAll(context)
                    val serviceIntent = Intent(Intents.ACTION_PROFILE_SCHEDULE_UPDATES)
                        .setComponent(profileWorkerComponentName)
                    context.startForegroundServiceCompat(serviceIntent)
                }
            }
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                val redirectIntent = intent.setComponent(profileWorkerComponentName)
                context.startForegroundServiceCompat(redirectIntent)
            }
        }
    }

    companion object {
        private val lock = Mutex()
        private var initialized: Boolean by lazy { false }
        private val profileReceiverComponentName = ProfileReceiver::class.componentName
        private val profileWorkerComponentName = ProfileWorker::class.componentName
        private val alarmManager by lazy { Global.application.getSystemService<AlarmManager>() }

        suspend fun rescheduleAll(context: Context) = lock.withLock {
            if (initialized) return
            initialized = true

            Log.i("Reschedule all profiles update")

            val profiles = ImportedDao().queryAllUUIDs()
                .mapNotNull { ImportedDao().queryByUUID(it) }
                .filter { it.type != Profile.Type.File }

            profiles.forEach { scheduleNext(context, it) }
        }

        fun cancelNext(context: Context, imported: Imported) {
            val intent = pendingIntentOf(context, imported)
            alarmManager?.cancel(intent)
        }

        fun scheduleNext(context: Context, imported: Imported) {
            val intent = pendingIntentOf(context, imported)

            if (imported.interval < TimeUnit.MINUTES.toMillis(15))
                return

            val current = System.currentTimeMillis()
            val last = context.importedDir
                .resolve(imported.uuid.toString())
                .resolve("config.yaml")
                .lastModified()

            // file not existed
            if (last < 0)
                return

            val interval = (imported.interval - (current - last)).coerceAtLeast(0)
            alarmManager?.set(AlarmManager.RTC, current + interval, intent)
        }

        private suspend fun reset() {
            initialized = false
        }

        private fun pendingIntentOf(context: Context, imported: Imported): PendingIntent {
            val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
                .setComponent(profileReceiverComponentName)
                .setUUID(imported.uuid)
            return PendingIntent.getBroadcast(
                context,
                imported.uuid.hashCode(),
                intent,
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
    }
}
