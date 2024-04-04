package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity<MainDesign>() {
    private val design by lazy { MainDesign(this) }

    override suspend fun main() {
        setContentDesign(design)
        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive { event ->
                    when (event) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive { request ->
                    handleDesignRequest(request)
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun handleDesignRequest(request: MainDesign.Request) {
        when (request) {
            MainDesign.Request.ToggleStatus -> {
                if (clashRunning) stopClashService() else startClashService()
            }
            MainDesign.Request.OpenProxy -> {
                startActivity(ProxyActivity::class.intent)
            }
            MainDesign.Request.OpenProfiles -> {
                startActivity(ProfilesActivity::class.intent)
            }
            MainDesign.Request.OpenProviders -> {
                startActivity(ProvidersActivity::class.intent)
            }
            MainDesign.Request.OpenLogs -> {
                startActivity(LogsActivity::class.intent)
            }
            MainDesign.Request.OpenSettings -> {
                startActivity(SettingsActivity::class.intent)
            }
            MainDesign.Request.OpenHelp -> {
                startActivity(HelpActivity::class.intent)
            }
            MainDesign.Request.OpenAbout -> {
                design.showAbout(queryAppVersionName())
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }
        setClashRunning(clashRunning)
        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())
        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun MainDesign.startClash(){
        val active = withProfile { queryActive() }
        
        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        if (vpnRequest != null) {
            val result = startActivityForResult(ActivityResultContracts.StartActivityForResult(), vpnRequest)

            if (result.resultCode == RESULT_OK)
                startClashService()
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }
}
