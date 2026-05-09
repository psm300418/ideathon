package com.ideathon.kondaeshield.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ideathon.kondaeshield.recording.RecordingService

class VolumeKeyAccessibilityService : AccessibilityService() {
    private var lastPressAt = 0L
    private var pressCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        pressCount = if (now - lastPressAt <= DOUBLE_PRESS_WINDOW_MS) {
            pressCount + 1
        } else {
            1
        }
        lastPressAt = now

        if (pressCount >= 2) {
            pressCount = 0
            RecordingService.toggle(this)
        }

        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 650L
    }
}
