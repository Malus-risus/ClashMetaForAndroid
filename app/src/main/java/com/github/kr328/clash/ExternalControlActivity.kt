package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.action?.let { action ->
            when (action) {
                Intent.ACTION_VIEW -> handleActionView()
                Intents.ACTION_TOGGLE_CLASH -> toggleClash()
                Intents.ACTION_START_CLASH -> startOrShowRunning()
                Intents.ACTION_STOP_CLASH -> stopOrShowStopped()
            }
        }
        finish()
    }

    private fun handleActionView() {
        intent.data?.let { uri ->
            val url = uri.getQueryParameter("url") ?: return
            launch {
                withProfile {
                    val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                        "url" -> Profile.Type.Url
                        "file" -> Profile.Type.File
                        else -> Profile.Type.Url
                    }
                    val name = uri.getQueryParameter("name") ?: getString(R.string.new_profile)
                    create(type, name).apply {
                        patch(this, name, url, 0)
                        startActivity(PropertiesActivity::class.intent.setUUID(this))
                    }
                }
            }
        }
    }

    private fun toggleClash() {
        if(Remote.broadcasts.clashRunning) stopClash() else startClash()
    }

    private fun startOrShowRunning() {
        if(!Remote.broadcasts.clashRunning) startClash() else showToast(R.string.external_control_started)
    }

    private fun stopOrShowStopped() {
        if(Remote.broadcasts.clashRunning) stopClash() else showToast(R.string.external_control_stopped)
    }

    private fun startClash() {
        startClashService()?.let {
            showToast(R.string.unable_to_start_vpn)
        } ?: showToast(R.string.external_control_started)
    }

    private fun stopClash() {
        stopClashService()
        showToast(R.string.external_control_stopped)
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }
}
