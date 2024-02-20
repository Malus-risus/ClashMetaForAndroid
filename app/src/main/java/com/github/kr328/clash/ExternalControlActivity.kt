package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            Intent.ACTION_VIEW -> handleActionView()
            Intents.ACTION_TOGGLE_CLASH -> handleToggleClash()
            Intents.ACTION_START_CLASH -> handleStartClash()
            Intents.ACTION_STOP_CLASH -> handleStopClash()
        }
    }

    private fun handleActionView() {
        val uri = intent.data ?: return finish()
        val url = uri.getQueryParameter("url") ?: return finish()

        launch {
            val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                "url" -> Profile.Type.Url
                "file" -> Profile.Type.File
                else -> Profile.Type.Url
            }
            val name = uri.getQueryParameter("name") ?: getString(R.string.new_profile)

            withProfile {
                create(type, name).also {
                    it.patch(name, url, 0)
                    startActivity(PropertiesActivity::class.intent.setUUID(it.uuid))
                }
            }
        }
        finish()
    }

    private fun handleToggleClash() {
        if (Remote.broadcasts.clashRunning) {
            handleStopClash()
        } else {
            handleStartClash()
        }
    }

    private fun handleStartClash() {
        if (!Remote.broadcasts.clashRunning) {
            val vpnRequest = startClashService()
            if (vpnRequest != null) {
                showToast(R.string.unable_to_start_vpn)
                return
            }
        }
        showToast(R.string.external_control_started)
        finish()
    }

    private fun handleStopClash() {
        if (Remote.broadcasts.clashRunning) {
            stopClashService()
        }
        showToast(R.string.external_control_stopped)
        finish()
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }
}
