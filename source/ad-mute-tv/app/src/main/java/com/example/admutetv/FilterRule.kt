package com.example.admutetv

/**
 * Enhanced filter rule supporting uBlock-style pattern syntax.
 * Supports simple text, regex, wildcards, and package-specific rules.
 */
data class FilterRule(
    val originalPattern: String,
    val patternType: PatternType,
    val pattern: String,
    val packageName: String? = null,
    val confidence: Float = 1.0f
) {
    enum class PatternType {
        SIMPLE,      // Exact or substring match: "advertisement"
        REGEX,       // Regex pattern: "/ad.*[0-9]+s/"
        WILDCARD,    // Wildcard pattern: "*skip*"
        PACKAGE_SPECIFIC // Package-specific: "com.youtube.android##ad 1 of"
    }
    
    companion object {
        /**
         * Parse a filter rule string into a FilterRule object.
         * Supports:
         * - Simple: "advertisement"
         * - Regex: "/ad.*[0-9]+s/"
         * - Wildcard: "*skip*"
         * - Package-specific: "com.youtube.android##ad 1 of"
         */
        fun parse(ruleString: String): FilterRule? {
            val trimmed = ruleString.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            
            // Check for package-specific rule
            if (trimmed.contains("##")) {
                val parts = trimmed.split("##", limit = 2)
                if (parts.size == 2) {
                    val pkg = parts[0].trim()
                    val pattern = parts[1].trim()
                    if (pkg.isNotEmpty() && pattern.isNotEmpty()) {
                        val innerType = detectPatternType(pattern)
                        return FilterRule(
                            originalPattern = trimmed,
                            patternType = PatternType.PACKAGE_SPECIFIC,
                            pattern = pattern,
                            packageName = pkg,
                            confidence = 1.0f
                        )
                    }
                }
            }
            
            // Check for regex pattern
            if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
                val regexPattern = trimmed.substring(1, trimmed.length - 1)
                return try {
                    // Validate regex
                    Regex(regexPattern)
                    FilterRule(
                        originalPattern = trimmed,
                        patternType = PatternType.REGEX,
                        pattern = regexPattern,
                        confidence = 1.0f
                    )
                } catch (e: Exception) {
                    // Invalid regex, treat as simple pattern
                    null
                }
            }
            
            // Check for wildcard pattern
            if (trimmed.contains("*")) {
                return FilterRule(
                    originalPattern = trimmed,
                    patternType = PatternType.WILDCARD,
                    pattern = trimmed,
                    confidence = 0.8f // Lower confidence for wildcards
                )
            }
            
            // Default to simple pattern
            return FilterRule(
                originalPattern = trimmed,
                patternType = PatternType.SIMPLE,
                pattern = trimmed,
                confidence = 1.0f
            )
        }
        
        private fun detectPatternType(pattern: String): PatternType {
            return when {
                pattern.startsWith("/") && pattern.endsWith("/") -> PatternType.REGEX
                pattern.contains("*") -> PatternType.WILDCARD
                else -> PatternType.SIMPLE
            }
        }
        
        /**
         * Parse multiple rule strings into a list of FilterRule objects.
         */
        fun parseAll(ruleStrings: List<String>): List<FilterRule> {
            return ruleStrings.mapNotNull { parse(it) }
        }
    }
    
    /**
     * Check if this rule applies to the given package name.
     */
    fun appliesToPackage(targetPackage: String?): Boolean {
        return packageName == null || packageName == targetPackage
    }
    
    /**
     * Check if this rule matches the given text.
     */
    fun matches(text: String): Boolean {
        return when (patternType) {
            PatternType.SIMPLE -> {
                // Exact or substring match
                if (pattern.contains(" ")) {
                    text.contains(pattern, ignoreCase = true)
                } else {
                    // Word boundary matching
                    text.split(Regex("[^\\p{L}\\p{N}]+"))
                        .any { it.equals(pattern, ignoreCase = true) }
                }
            }
            PatternType.REGEX -> {
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
                } catch (e: Exception) {
                    false
                }
            }
            PatternType.WILDCARD -> {
                // Convert wildcard to regex
                val regexPattern = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".")
                try {
                    Regex(regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
                } catch (e: Exception) {
                    false
                }
            }
            PatternType.PACKAGE_SPECIFIC -> {
                // Use the inner pattern logic
                val innerRule = FilterRule.parse(pattern) ?: return false
                innerRule.matches(text)
            }
        }
    }
}