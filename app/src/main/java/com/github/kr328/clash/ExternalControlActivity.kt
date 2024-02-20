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
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.remote.StatusClient
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
    handleIntent()
  }

  private fun handleIntent() {
    when (intent.action) {
      Intent.ACTION_VIEW -> handleActionView()
      Intents.ACTION_TOGGLE_CLASH -> toggleClash()
      Intents.ACTION_START_CLASH -> startClash()
      Intents.ACTION_STOP_CLASH -> stopClash()
    }
  }

  private fun handleActionView() {
    val uri = intent.data ?: return finish()
    val url = uri.getQueryParameter("url") ?: return finish()
    launch {
      withProfile {
        val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
          "url" -> Profile.Type.Url
          "file" -> Profile.Type.File
          else -> Profile.Type.Url
        }
        val name = uri.getQueryParameter("name") ?: getString(R.string.new_profile)

        create(type, name).also {
          it.patch(name, url, 0)
          startActivity(PropertiesActivity::class.intent.setUUID(it.uuid))
        }
      }
    }
  }

  private fun toggleClash() = if (Remote.broadcasts.clashRunning) stopClash() else startClash()

  private fun startClash() {
    if (!Remote.broadcasts.clashRunning) {
      startClashService()?.let {
        showToast(R.string.unable_to_start_vpn)
      } ?: showToast(R.string.external_control_started)
    } else {
      showToast(R.string.external_control_started)
    }
  }

  private fun stopClash() {
    if (Remote.broadcasts.clashRunning) {
      stopClashService()
      showToast(R.string.external_control_stopped)
    } else {
      showToast(R.string.external_control_stopped)
    }
  }

  private fun showToast(messageResId: Int) {
    Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
  }
}
