package io.github.yungamp.shortvideoblocker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live state of the accessibility service, observed by the UI (same process). */
object ServiceState {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _foregroundPackage.value = null
    }

    internal fun setForeground(pkg: String?) {
        _foregroundPackage.value = pkg
    }

    internal fun setMessage(msg: String?) {
        _lastMessage.value = msg
    }
}
