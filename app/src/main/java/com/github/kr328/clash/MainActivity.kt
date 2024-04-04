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
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity<MainDesign>() {
    override suspend fun main() {
        val design = MainDesign(this)
        setContentDesign(design)
        design.fetch()

        coroutineScope {
            val tickerChannel = ticker(TimeUnit.SECONDS.toMillis(1), isActive and clashRunning)
            
            launch {
                events.consumeEach { event ->
                    when (event) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded,
                        Event.ProfileChanged -> design.fetch()
                    }
                }
            }

            launch {
                design.requests.consumeEach { request ->
                    when (request) {
                        MainDesign.Request.ToggleStatus -> toggleClash(design)
                        MainDesign.Request.OpenProxy -> openActivity(ProxyActivity::class)
                        MainDesign.Request.OpenProfiles -> openActivity(ProfilesActivity::class)
                        MainDesign.Request.OpenProviders -> openActivity(ProvidersActivity::class)
                        MainDesign.Request.OpenLogs -> openActivity(LogsActivity::class)
                        MainDesign.Request.OpenSettings -> openActivity(SettingsActivity::class)
                        MainDesign.Request.OpenHelp -> openActivity(HelpActivity::class)
                        MainDesign.Request.OpenAbout -> showAbout(design)
                    }
                }
            }

            launch {
                for (tick in tickerChannel) {
                    design.fetchTraffic()
                }
            }
        }
    }

    private fun openActivity(activityClass: KClass<out Activity>) {
        startActivity(activityClass.intent)
    }

    private suspend fun showAbout(design: MainDesign) {
        design.showAbout(queryAppVersionName())
    }

    private suspend fun toggleClash(design: MainDesign) {
        withClash {
            if (clashRunning) {
                stopClashService()
                design.setClashRunning(false)
            } else {
                design.startClash()
            }
        }
    }

    private suspend fun MainDesign.startClash() {
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
            val result = startForResult(vpnRequest)
            if (result.resultCode == RESULT_OK) {
                startClashService()
                setClashRunning(true)
            }
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(withClash { queryTunnelState().running })
        setMode(withClash { queryTunnelState().mode })
        setHasProviders(withClash { queryProviders().isNotEmpty() })
        withProfile { setProfileName(queryActive()?.name) }
    }

    private suspend fun startForResult(vpnRequest: Intent): ActivityResult {
        return withContext(Dispatchers.Main) {
            startActivityForResult(ActivityResultContracts.StartActivityForResult(), vpnRequest)
        }
    }

    private suspend fun queryAppVersionName(): String = withContext(Dispatchers.IO) {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val coreVersion = Bridge.nativeCoreVersion().replace("_", "-")
        "$versionName\n$coreVersion"
    }
}
