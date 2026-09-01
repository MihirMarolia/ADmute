package com.example.admutetv

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * Text-only detector for content that the foreground application exposes to
 * Android accessibility services. It deliberately does not inspect pixels,
 * capture audio, intercept network traffic, or drive another application's UI.
 */
internal class AdDetector {

    data class Match(
        val isAdLikely: Boolean,
        val matchedLabels: List<String>,
        val inspectedNodeCount: Int,
        val sampleText: String = ""
    )

    fun inspect(
        root: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence?>,
        markers: Set<String>
    ): Match {
        val normalizedMarkers = markers
            .map(::normalize)
            .filter { it.isNotBlank() }
            .toSet()

        if (normalizedMarkers.isEmpty()) {
            return Match(false, emptyList(), 0)
        }

        val found = linkedSetOf<String>()
        val textSamples = mutableListOf<String>()
        eventTexts.forEach { text -> 
            matchText(text, normalizedMarkers, found)
            if (!text.isNullOrEmpty()) textSamples.add(text.toString())
        }

        if (root == null) {
            return Match(found.isNotEmpty(), found.toList(), 0, textSamples.take(3).joinToString(" | "))
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0

        // A cap prevents a large accessibility tree from causing service lag.
        while (queue.isNotEmpty() && inspected < MAX_NODES_TO_INSPECT) {
            val node = queue.removeFirst()
            inspected += 1

            matchText(node.text, normalizedMarkers, found)
            matchText(node.contentDescription, normalizedMarkers, found)
            matchText(node.hintText, normalizedMarkers, found)
            
            // Collect text samples for debugging
            if (textSamples.size < 5) {
                node.text?.let { if (it.isNotBlank()) textSamples.add(it.toString()) }
                node.contentDescription?.let { if (it.isNotBlank()) textSamples.add(it.toString()) }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        return Match(found.isNotEmpty(), found.toList(), inspected, textSamples.take(3).joinToString(" | "))
    }

    private fun matchText(
        value: CharSequence?,
        markers: Set<String>,
        found: MutableSet<String>
    ) {
        val candidate = normalize(value?.toString().orEmpty())
        if (candidate.isBlank()) return

        markers.forEach { marker ->
            if (matchesMarker(candidate, marker)) found.add(marker)
        }
    }

    private fun matchesMarker(candidate: String, marker: String): Boolean {
        if (marker.contains(' ')) return candidate.contains(marker)

        // Single-word matching avoids treating words such as "additional" as "ad".
        return candidate.split(NON_ALPHANUMERIC).any { token -> token == marker }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")

    private companion object {
        private const val MAX_NODES_TO_INSPECT = 240
        private val WHITESPACE = Regex("\\s+")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    }
}
