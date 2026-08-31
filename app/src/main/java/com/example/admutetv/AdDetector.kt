package com.example.admutetv

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * Text-only detector for content that foreground applications expose to
 * Android accessibility services. It deliberately does not inspect pixels,
 * capture audio, or drive another application's UI.
 */
internal class AdDetector {

    data class Match(
        val isAdLikely: Boolean,
        val matchedLabels: List<String>,
        val packageName: String?,
        val inspectedNodeCount: Int,
        val sampledTexts: List<String>
    )

    /**
     * Inspects every supplied window root instead of only the focused window.
     * On television devices the focused window is frequently a system or
     * launcher window while playback continues in another application window.
     */
    fun inspect(
        roots: List<AccessibilityNodeInfo>,
        eventTexts: List<CharSequence?>,
        markers: Set<String>,
        idMarkers: Set<String>,
        collectSamples: Boolean
    ): Match {
        val normalizedMarkers = markers.map(::normalize).filter { it.isNotBlank() }.toSet()
        val normalizedIdMarkers = idMarkers.map(::normalize).filter { it.isNotBlank() }.toSet()

        val found = linkedSetOf<String>()
        val samples = linkedSetOf<String>()
        var matchedPackage: String? = null

        eventTexts.forEach { text -> matchText(text, normalizedMarkers, found) }

        var inspected = 0
        for (root in roots) {
            if (inspected >= MAX_NODES_TO_INSPECT) break
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty() && inspected < MAX_NODES_TO_INSPECT) {
                val node = queue.removeFirst()
                inspected += 1

                val before = found.size
                matchText(node.text, normalizedMarkers, found)
                matchText(node.contentDescription, normalizedMarkers, found)
                matchText(node.hintText, normalizedMarkers, found)
                matchViewId(node.viewIdResourceName, normalizedIdMarkers, found)
                if (found.size > before && matchedPackage == null) {
                    matchedPackage = node.packageName?.toString() ?: root.packageName?.toString()
                }

                if (collectSamples) {
                    collectSample(node.text, samples)
                    collectSample(node.contentDescription, samples)
                }

                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }

        return Match(
            isAdLikely = found.isNotEmpty(),
            matchedLabels = found.toList(),
            packageName = matchedPackage,
            inspectedNodeCount = inspected,
            sampledTexts = samples.toList()
        )
    }

    private fun collectSample(value: CharSequence?, samples: MutableSet<String>) {
        if (samples.size >= MAX_SAMPLES) return
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && text.length <= MAX_SAMPLE_LENGTH) samples.add(text)
    }

    private fun matchText(
        value: CharSequence?,
        markers: Set<String>,
        found: MutableSet<String>
    ): Boolean {
        val candidate = normalize(value?.toString().orEmpty())
        if (candidate.isBlank()) return false

        var matched = false
        markers.forEach { marker ->
            if (matchesMarker(candidate, marker)) {
                found.add(marker)
                matched = true
            }
        }
        return matched
    }

    private fun matchViewId(
        viewId: String?,
        idMarkers: Set<String>,
        found: MutableSet<String>
    ) {
        if (viewId.isNullOrBlank() || idMarkers.isEmpty()) return
        val candidate = normalize(viewId)
        idMarkers.forEach { marker ->
            if (candidate.contains(marker)) found.add("id:$marker")
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
        private const val MAX_NODES_TO_INSPECT = 1_200
        private const val MAX_SAMPLES = 40
        private const val MAX_SAMPLE_LENGTH = 80
        private val WHITESPACE = Regex("\\s+")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    }
}
