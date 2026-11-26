package io.github.yungamp.shortvideoblocker.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Inspector settings, kept in memory as StateFlows so the service never touches disk on the
 * event path. Phase 0 uses SharedPreferences; the real settings move to DataStore in phase 3.
 */
class InspectorSettings(context: Context) {
    private val prefs = context.getSharedPreferences("inspector", Context.MODE_PRIVATE)

    private val _overlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY, true))
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _redactText = MutableStateFlow(prefs.getBoolean(KEY_REDACT, true))
    val redactText: StateFlow<Boolean> = _redactText.asStateFlow()

    fun setOverlayEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_OVERLAY, value) }
        _overlayEnabled.value = value
    }

    fun setRedactText(value: Boolean) {
        prefs.edit { putBoolean(KEY_REDACT, value) }
        _redactText.value = value
    }

    private companion object {
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_REDACT = "redact_text"
    }
}
