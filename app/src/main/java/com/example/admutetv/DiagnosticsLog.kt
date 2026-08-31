package com.example.admutetv

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer shared by the accessibility service, the network
 * detector, and the settings screen. It exists so a person can see which text
 * and which domains the utility actually observed on their own television
 * instead of guessing why a mute did or did not happen.
 */
internal object DiagnosticsLog {
    private const val CAPACITY = 200
    private val entries = ArrayDeque<String>(CAPACITY)
    private val timestampFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(line: String) {
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast("${timestampFormat.format(Date())}  $line")
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList().asReversed()

    @Synchronized
    fun clear() = entries.clear()
}
