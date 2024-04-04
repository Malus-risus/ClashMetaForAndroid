package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.tryReceive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity<MainDesign>() {
    override suspend fun main() {
        val design = MainDesign(this)
        setContentDesign(design)
        design.fetch()

        while (isActive) {
            val nextEvent = events.tryReceive().getOrNull()
            val nextRequest = if (clashRunning) design.requests.tryReceive().getOrNull() else null

            nextEvent?.let { handleEvents(it, design) }
            nextRequest?.let { handleRequests(it, design) }

            if (clashRunning) {
                design.fetchTraffic()
            }

            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }

    private suspend fun handleEvents(event: Event, design: MainDesign) {
        when (event) {
            Event.ActivityStart, Event.ServiceRecreated, Event.ClashStop, Event.ClashStart,
            Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
            else -> Unit
        }
    }

    private suspend fun handleRequests(request: MainDesign.Request, design: MainDesign) {
        when (request) {
            MainDesign.Request.ToggleStatus -> toggleClashStatus()
            MainDesign.Request.OpenProxy -> startActivity(ProxyActivity::class.intent)
            MainDesign.Request.OpenProfiles -> startActivity(ProfilesActivity::class.intent)
            MainDesign.Request.OpenProviders -> startActivity(ProvidersActivity::class.intent)
            MainDesign.Request.OpenLogs -> startActivity(LogsActivity::class.intent)
            MainDesign.Request.OpenSettings -> startActivity(SettingsActivity::class.intent)
            MainDesign.Request.OpenHelp -> startActivity(HelpActivity::class.intent)
            MainDesign.Request.OpenAbout -> design.showAbout(queryAppVersionName())
        }
    }

    private fun toggleClashStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                design?.startClash()
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        withContext(Dispatchers.IO) {
            val state = withClash { queryTunnelState() }
            val providers = withClash { queryProviders() }
            val activeProfile = withProfile { queryActive() }

            withContext(Dispatchers.Main) {
                setMode(state?.mode ?: "direct")
                setHasProviders(providers.isNotEmpty())
                setProfileName(activeProfile?.name ?: "None")
            }
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withContext(Dispatchers.IO) {
            val traffic = withClash { queryTrafficTotal() }
            withContext(Dispatchers.Main) {
                setForwarded(traffic)
            }
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" +
            Bridge.nativeCoreVersion().replace("_", "-")
        }
    }
}
