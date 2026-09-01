package com.example.admutetv

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple on-device activity log for debugging ad detection.
 * Stores recent scan results and mute/restore actions.
 */
class DiagnosticsLog(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("diagnostics_log", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val MAX_ENTRIES = 50
        private const val KEY_LOG = "log_entries"
    }
    
    data class LogEntry(
        val timestamp: String,
        val type: String, // "scan", "mute", "restore", "service"
        val packageName: String?,
        val details: String
    )
    
    private val entries = mutableListOf<LogEntry>()
    
    init {
        loadEntries()
    }
    
    fun logScan(packageName: String?, nodeCount: Int, isPlaying: Boolean, sampleText: String) {
        addEntry(LogEntry(
            timestamp = getCurrentTime(),
            type = "scan",
            packageName = packageName,
            details = "nodes=$nodeCount, playing=$isPlaying, text=[$sampleText]"
        ))
    }
    
    fun logMute(packageName: String?, label: String) {
        addEntry(LogEntry(
            timestamp = getCurrentTime(),
            type = "mute",
            packageName = packageName,
            details = "label=$label"
        ))
    }
    
    fun logRestore(packageName: String?, reason: String) {
        addEntry(LogEntry(
            timestamp = getCurrentTime(),
            type = "restore",
            packageName = packageName,
            details = reason
        ))
    }
    
    fun logService(status: String) {
        addEntry(LogEntry(
            timestamp = getCurrentTime(),
            type = "service",
            packageName = null,
            details = status
        ))
    }
    
    fun getEntries(): List<LogEntry> = entries.toList()
    
    fun clear() {
        entries.clear()
        saveEntries()
    }
    
    private fun addEntry(entry: LogEntry) {
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        saveEntries()
    }
    
    private fun saveEntries() {
        val serialized = entries.joinToString(";") { entry ->
            "${entry.timestamp}|${entry.type}|${entry.packageName ?: ""}|${entry.details}"
        }
        prefs.edit().putString(KEY_LOG, serialized).apply()
    }
    
    private fun loadEntries() {
        val serialized = prefs.getString(KEY_LOG, "") ?: ""
        if (serialized.isEmpty()) return
        
        entries.clear()
        serialized.split(";").forEach { part ->
            if (part.isEmpty()) return@forEach
            val components = part.split("|", limit = 4)
            if (components.size == 4) {
                entries.add(LogEntry(
                    timestamp = components[0],
                    type = components[1],
                    packageName = components[2].ifEmpty { null },
                    details = components[3]
                ))
            }
        }
    }
    
    private fun getCurrentTime(): String = dateFormat.format(Date())
    
    fun formatForDisplay(): String {
        if (entries.isEmpty()) return "No activity recorded yet..."
        
        return entries.joinToString("\n") { entry ->
            val pkg = entry.packageName?.let { " [$it]" } ?: ""
            "${entry.timestamp} ${entry.type.uppercase()}$pkg: ${entry.details}"
        }
    }
}