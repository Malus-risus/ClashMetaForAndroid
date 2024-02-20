package com.github.kr328.clash.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.coroutineScope

class ActivityResultLifecycle : LifecycleOwner {
    private val lifecycle = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.INITIALIZED
    }

    override fun getLifecycle(): Lifecycle = lifecycle

    suspend fun <T> use(block: suspend (lifecycle: ActivityResultLifecycle, start: () -> Unit) -> T): T {
        markState(Lifecycle.State.CREATED)
        try {
            return block(this, this::markResumed)
        } finally {
            markState(Lifecycle.State.DESTROYED)
        }
    }

    private fun markResumed() {
        markState(Lifecycle.State.STARTED)
        markState(Lifecycle.State.RESUMED)
    }

    private fun markState(state: Lifecycle.State) {
        coroutineScope {
            lifecycle.currentState = state
        }
    }
}
