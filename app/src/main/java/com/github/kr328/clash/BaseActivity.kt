package com.github.kr328.clash

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.compat.*
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.DayNight
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.ActivityResultLifecycle
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class BaseActivity<D : Design<*>> : AppCompatActivity(), CoroutineScope by MainScope(), Broadcasts.Observer {
    private val uiStore by lazy { UiStore(this) }
    private val events = Channel<Event>(Channel.CONFLATED)
    private var activityStarted: Boolean = false
    private val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning
    private var design: D? = null
        set(value) {
            field = value
            setContentView(value?.root ?: View(this))
        }
    
    private var defer: suspend () -> Unit = {}
    private var deferRunning = false
    private val nextRequestKey = AtomicInteger(0)
    private var dayNight: DayNight = DayNight.Day
    
    protected abstract suspend fun main()
    
    enum class Event {
        ServiceRecreated,
        ActivityStart,
        ActivityStop,
        ClashStop,
        ClashStart,
        ProfileLoaded,
        ProfileChanged,
        ProfileUpdateCompleted,
        ProfileUpdateFailed
    }
    
    suspend fun <I, O> startActivityForResult(
        contracts: ActivityResultContract<I, O>,
        input: I
    ): O = withContext(Dispatchers.Main) {
        val requestKey = nextRequestKey.getAndIncrement().toString()
        
        suspendCoroutine { continuation ->
            ActivityResultLifecycle().use { lifecycle, start ->
                activityResultRegistry.register(requestKey, lifecycle, contracts) { output ->
                    continuation.resume(output)
                }.also {
                    start()
                }.launch(input)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDayNight()
        
        launch {
            main()
            finish()
        }
    }
    
    override fun onStart() {
        super.onStart()
        activityStarted = true
        Remote.broadcasts.addObserver(this)
        events.trySend(Event.ActivityStart)
    }
    
    override fun onStop() {
        super.onStop()
        activityStarted = false
        Remote.broadcasts.removeObserver(this)
        events.trySend(Event.ActivityStop)
    }
    
    override fun onDestroy() {
        design?.cancel()
        cancel()
        super.onDestroy()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK != resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            ApplicationObserver.createdActivities.forEach {
                it.recreate()
            }
        }
    }
    
    private fun applyDayNight(config: Configuration = resources.configuration) {
        dayNight = when (uiStore.darkMode) {
            DarkMode.Auto -> {
                if ((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                    DayNight.Night
                else
                    DayNight.Day
            }
            DarkMode.ForceLight -> DayNight.Day
            DarkMode.ForceDark -> DayNight.Night
        }
        
        theme.applyStyle(when (dayNight) {
            DayNight.Night -> R.style.AppThemeDark
            DayNight.Day -> R.style.AppThemeLight
        }, true)
        
        window.apply {
            isAllowForceDarkCompat = false
            isSystemBarsTranslucentCompat = true
            statusBarColor = resolveThemedColor(android.R.attr.statusBarColor)
            navigationBarColor = resolveThemedColor(android.R.attr.navigationBarColor)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = if (resolveThemedBoolean(android.R.attr.windowLightStatusBar))
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = if (resolveThemedBoolean(android.R.attr.windowLightNavigationBar))
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }
}
