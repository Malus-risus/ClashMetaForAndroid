package com.github.kr328.clash.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.GlobalScope
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

class ProfileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> rescheduleUpdate(context)
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> requestProfileUpdate(context, intent)
        }
    }

    companion object {
        private val lock = Mutex()
        private var initialized = false

        fun rescheduleUpdate(context: Context) = CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                if (!initialized) {
                    initialized = true
                    ImportedDao().queryAllProfiles().forEach {
                        scheduleNext(context, it)
                    }
                }
            }
        }

        fun requestProfileUpdate(context: Context, intent: Intent) {
            val redirect = Intent(Intents.ACTION_PROFILE_SCHEDULE_UPDATES)
            context.startService(redirect)
        }

        fun cancelNext(context: Context, imported: Imported) {
            context.alarmManager?.cancel(pendingIntentOf(context, imported))
        }

        fun scheduleNext(context: Context, imported: Imported) {
            lock.withLock {
                val interval = imported.interval
                if (interval < TimeUnit.MINUTES.toMillis(15)) return

                with(context.importedDir.resolve(imported.uuid.toString()).resolve("config.yaml")) {
                    if (!this.exists()) return
                    val nextUpdateTime = lastModified() + interval
                    val delay = (nextUpdateTime - System.currentTimeMillis()).coerceAtLeast(0)

                    context.alarmManager?.set(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + delay,
                        pendingIntentOf(context, imported)
                    )
                }
            }
        }
        
        private val Context.alarmManager get() = getSystemService<AlarmManager>()

        private fun pendingIntentOf(context: Context, imported: Imported): PendingIntent {
            val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }
    }
}
