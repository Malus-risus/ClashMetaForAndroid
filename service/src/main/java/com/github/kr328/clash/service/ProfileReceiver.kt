package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> rescheduleAll(context)
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> redirectUpdate(context)
        }
    }

    companion object {
        private val lock = Mutex()
        private var initialized = false

        private fun rescheduleAll(context: Context) = CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                if (!initialized) {
                    initialized = true
                    val profiles = ImportedDao().queryAllProfiles() // Assuming this method exists and returns a list of profiles
                    profiles.forEach { profile ->
                        scheduleNext(context, profile)
                    }
                }
            }
        }

        private fun redirectUpdate(context: Context) {
            val intent = Intent(Intents.ACTION_PROFILE_SCHEDULE_UPDATES)
            context.startForegroundService(intent)
        }

        private fun scheduleNext(context: Context, imported: Imported) = CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                if (imported.interval < TimeUnit.MINUTES.toMillis(15)) return@withLock

                val configFile = context.importedDir.resolve(imported.uuid.toString()).resolve("config.yaml")
                if (!configFile.exists()) return@withLock

                val lastModified = configFile.lastModified()
                val currentTime = System.currentTimeMillis()
                val interval = imported.interval
                val scheduleTime = currentTime + (interval - (currentTime - lastModified)).coerceAtLeast(0)

                val intent = pendingIntentOf(context, imported)
                context.getSystemService<AlarmManager>()?.set(AlarmManager.RTC_WAKEUP, scheduleTime, intent)
            }
        }

        private fun pendingIntentOf(context: Context, imported: Imported): PendingIntent {
            val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE).apply {
                putExtra("uuid", imported.uuid.toString())
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }
    }
}
