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
        val sampleText: String = "",
        val confidence: Float = 1.0f
    )
    
    data class MatchResult(
        val matches: Boolean,
        val matchedPattern: String,
        val confidence: Float
    )

    fun inspect(
        root: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence?>,
        markers: Set<String>,
        packageName: String? = null
    ): Match {
        // Parse markers into FilterRule objects
        val filterRules = FilterRule.parseAll(markers.toList())
        
        if (filterRules.isEmpty()) {
            return Match(false, emptyList(), 0, 0.0f)
        }

        val found = linkedSetOf<String>()
        val textSamples = mutableListOf<String>()
        var totalConfidence = 0.0f
        var matchCount = 0
        
        eventTexts.forEach { text -> 
            val matchResult = matchTextWithRules(text, filterRules, packageName)
            if (matchResult.matches) {
                found.add(matchResult.matchedPattern)
                totalConfidence += matchResult.confidence
                matchCount++
            }
            if (!text.isNullOrEmpty()) textSamples.add(text.toString())
        }

        if (root == null) {
            val avgConfidence = if (matchCount > 0) totalConfidence / matchCount else 0.0f
            return Match(found.isNotEmpty(), found.toList(), 0, textSamples.take(3).joinToString(" | "), avgConfidence)
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0

        // A cap prevents a large accessibility tree from causing service lag.
        while (queue.isNotEmpty() && inspected < MAX_NODES_TO_INSPECT) {
            val node = queue.removeFirst()
            inspected += 1

            val textResult = matchTextWithRules(node.text, filterRules, packageName)
            if (textResult.matches) {
                found.add(textResult.matchedPattern)
                totalConfidence += textResult.confidence
                matchCount++
            }
            
            val descResult = matchTextWithRules(node.contentDescription, filterRules, packageName)
            if (descResult.matches) {
                found.add(descResult.matchedPattern)
                totalConfidence += descResult.confidence
                matchCount++
            }
            
            val hintResult = matchTextWithRules(node.hintText, filterRules, packageName)
            if (hintResult.matches) {
                found.add(hintResult.matchedPattern)
                totalConfidence += hintResult.confidence
                matchCount++
            }
            
            // Collect text samples for debugging
            if (textSamples.size < 5) {
                node.text?.let { if (it.isNotBlank()) textSamples.add(it.toString()) }
                node.contentDescription?.let { if (it.isNotBlank()) textSamples.add(it.toString()) }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        val avgConfidence = if (matchCount > 0) totalConfidence / matchCount else 0.0f
        return Match(found.isNotEmpty(), found.toList(), inspected, textSamples.take(3).joinToString(" | "), avgConfidence)
    }

    private fun matchTextWithRules(
        value: CharSequence?,
        rules: List<FilterRule>,
        packageName: String?
    ): MatchResult {
        val text = value?.toString() ?: return MatchResult(false, "", 0.0f)
        if (text.isBlank()) return MatchResult(false, "", 0.0f)
        
        // Try each rule, return first match
        for (rule in rules) {
            if (!rule.appliesToPackage(packageName)) continue
            
            if (rule.matches(text)) {
                return MatchResult(true, rule.originalPattern, rule.confidence)
            }
        }
        
        return MatchResult(false, "", 0.0f)
    }

    private companion object {
        private const val MAX_NODES_TO_INSPECT = 240
    }
}
