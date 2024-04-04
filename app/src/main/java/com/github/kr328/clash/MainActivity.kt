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

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1), isActive)
        val tickerJob = launch {
            for (event in ticker) {
                design.fetchTraffic()
            }
        }

        launch {
            for (event in events) {
                when (event) {
                    Event.ActivityStart,
                    Event.ServiceRecreated,
                    Event.ClashStop, Event.ClashStart,
                    Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                }
            }
        }

        launch {
            for (request in design.requests) {
                when (request) {
                    MainDesign.Request.ToggleStatus -> {
                        if (clashRunning) {
                            stopClashService()
                            design.setClashRunning(false)
                        } else {
                            design.startClash()
                        }
                    }
                    MainDesign.Request.OpenProxy -> startActivity(ProxyActivity::class.intent)
                    MainDesign.Request.OpenProfiles -> startActivity(ProfilesActivity::class.intent)
                    MainDesign.Request.OpenProviders -> startActivity(ProvidersActivity::class.intent)
                    MainDesign.Request.OpenLogs -> startActivity(LogsActivity::class.intent)
                    MainDesign.Request.OpenSettings -> startActivity(SettingsActivity::class.intent)
                    MainDesign.Request.OpenHelp -> startActivity(HelpActivity::class.intent)
                    MainDesign.Request.OpenAbout -> design.showAbout(queryAppVersionName())
                }
            }
        }

        // Handle the service lifecycle
        if (!clashRunning) {
            tickerJob.cancel() // Stop the ticker if the service is not running
        }
    }

    private suspend fun MainDesign.fetch() {
        withContext(Dispatchers.Main) {
            setClashRunning(clashRunning)
            val state = withClash { queryTunnelState() }
            val providers = withClash { queryProviders() }
            setMode(state.mode)
            setHasProviders(providers.isNotEmpty())
            withProfile { setProfileName(queryActive()?.name) }
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withContext(Dispatchers.Main) {
            setForwarded(withClash { queryTrafficTotal() })
        }
    }

    private suspend fun MainDesign.startClash() {
        withContext(Dispatchers.Main) {
            val active = withProfile { queryActive() }
            if (active == null || !active.imported) {
                showToast(R.string.no_profile_selected, ToastDuration.Long) {
                    setAction(R.string.profiles) {
                        startActivity(ProfilesActivity::class.intent)
                    }
                }
                return@withContext
            }

            val vpnRequest = startClashService()

            vpnRequest?.let {
                try {
                    val result = startActivityForResult(
                        ActivityResultContracts.StartActivityForResult(),
                        it
                    )

                    if (result.resultCode == RESULT_OK) {
                        startClashService()
                    }
                } catch (e: Exception) {
                    showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
                }
            }
        }
    }

    private suspend fun queryAppVersionName(): String = withContext(Dispatchers.IO) {
        packageManager.getPackageInfo(packageName, 0).versionName + "\n" +
        Bridge.nativeCoreVersion().replace("_", "-")
    }
}
