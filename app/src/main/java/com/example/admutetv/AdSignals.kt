package com.example.admutetv

import android.os.SystemClock

/**
 * Process-wide rendezvous point between detection sources. The accessibility
 * service polls it, and the optional DNS detector publishes into it.
 */
internal object AdSignals {
    @Volatile
    private var lastNetworkHitElapsedMs = 0L

    @Volatile
    var lastNetworkHost: String? = null
        private set

    @Volatile
    var networkDetectorRunning = false

    fun reportNetworkAdHost(host: String) {
        lastNetworkHitElapsedMs = SystemClock.elapsedRealtime()
        lastNetworkHost = host
    }

    fun networkAdActive(holdMs: Long): Boolean {
        val last = lastNetworkHitElapsedMs
        return last != 0L && SystemClock.elapsedRealtime() - last <= holdMs
    }

    fun clearNetworkSignal() {
        lastNetworkHitElapsedMs = 0L
        lastNetworkHost = null
    }
}
