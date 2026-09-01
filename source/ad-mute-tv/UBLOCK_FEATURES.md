# uBlock-Origin Features for ADmute TV

This document outlines feasible uBlock-Origin features that can be incorporated into ADmute TV, considering Android accessibility service constraints.

## Feasible Features

### 1. Enhanced Pattern Matching

**Current Implementation:**
- Simple text matching: "ad", "advertisement", "skip ad"
- Basic word boundary detection
- Case-insensitive matching

**uBlock-Style Enhancements:**

#### Regex Pattern Support
```kotlin
// Current: "ad 1 of" 
// Enhanced: "/ad.*[0-9]+.*of/" matches "ad 1 of 2", "advertisement 5 of 5"
// Enhanced: "/skip.*[0-9]+s/" matches "skip 5s", "skip ad in 30 seconds"
```

#### Wildcard Patterns
```kotlin
// Current: "advertisement"
// Enhanced: "*ad*" matches "advert", "advertisement", "sponsored ad"
// Enhanced: "*skip*" matches "skip", "skip ad", "skip intro"
```

#### Domain/Package-Specific Patterns
```kotlin
// Format: package_name##pattern
com.youtube.android##ad 1 of
com.netflix##skip intro
com.hulu.plus##advertisement
```

### 2. Filter List Management

#### Import/Export Format
```
# ADmute TV Filter List (EasyList-compatible format)
! Title: Streaming Services Ads
! Description: Ad patterns for popular streaming apps
! Version: 1.0
! Homepage: https://github.com/yourrepo/admute-filters

# YouTube
com.youtube.android##ad 1 of
com.youtube.android##skip ad
com.youtube.android##/advertisement.*[0-9]+s/

# Netflix
com.netflix##skip intro
com.netflix##/promo.*[0-9]+s/

# Hulu
com.hulu.plus##advertisement
com.hulu.plus##sponsored content
```

#### Filter Categories
```
# Ads (high confidence)
advertisement
ad 1 of
/sponsored.*content/

# Annoyances (lower confidence)
skip intro
coming up next
suggested for you

# Privacy
/tracking/
/analytics/
```

### 3. Advanced Matching Logic

#### Confidence Scoring
```kotlin
data class FilterRule(
    val pattern: String,
    val confidence: Float, // 0.0-1.0
    val category: FilterCategory,
    val packageName: String? = null
)

enum class FilterCategory {
    ADS,           // High confidence, always mute
    ANNOYANCES,    // Medium confidence, optional mute
    PRIVACY,       // Privacy-related patterns
    CUSTOM         // User-defined patterns
}
```

#### Context-Aware Matching
```kotlin
// Match only when combined with other indicators
"skip" + "seconds" + numeric → High confidence
"ad" alone → Medium confidence  
"advertisement" → High confidence
```

### 4. Filter List Synchronization

#### Online Filter Lists
```kotlin
// Fetch from URL
val filterListUrl = "https://raw.githubusercontent.com/yourrepo/admute-filters/main/streaming-ads.txt"
// Parse and integrate with existing rules
```

#### Community Filter Lists
- Pre-packaged lists for popular streaming services
- User-submitted patterns
- Automatic updates (with user consent)

### 5. Performance Optimizations

#### Filter Prioritization
```kotlin
// High-confidence patterns checked first
val highPriorityFilters = listOf("advertisement", "ad 1 of", "skip ad")
val mediumPriorityFilters = listOf("sponsored", "promo")
val lowPriorityFilters = listOf("suggested", "recommended")
```

#### Adaptive Scanning
```kotlin
// Increase scan frequency when ads detected
// Decrease frequency during normal content
val adaptiveInterval = when (lastAdDetected) {
    true -> 1.0.seconds  // Scan every second during ads
    false -> 3.0.seconds // Normal scanning
}
```

## Implementation Priority

### Phase 1: Pattern Enhancement
1. Add regex pattern support to `AdDetector`
2. Implement wildcard matching
3. Add package-specific pattern syntax

### Phase 2: Filter Management
1. Add filter list import/export
2. Implement filter categories
3. Add confidence scoring

### Phase 3: Advanced Features
1. Online filter list synchronization
2. Community filter list integration
3. Adaptive scanning based on detection patterns

## Technical Constraints

### What Won't Work (uBlock features that require browser APIs)
- **Network request filtering**: Android accessibility services can't intercept network traffic
- **Script injection**: Can't modify app behavior
- **CSS hiding**: Can't modify app UI
- **Response header modification**: No network-level access
- **Cookie filtering**: No browser cookie access

### What Will Work
- **Text pattern matching**: Enhanced accessibility text inspection
- **Domain/package rules**: Package-specific pattern application
- **Filter list management**: Import/export of pattern lists
- **Confidence scoring**: Smart pattern matching with probabilities
- **Community lists**: Shared pattern repositories

## Example Enhanced Filter Syntax

```
# Basic patterns (current)
advertisement
ad 1 of
skip ad

# Regex patterns (enhanced)
/advertisement.*[0-9]+s/
/skip.*[0-9]+s/

# Package-specific (enhanced)
com.youtube.android##ad 1 of
com.netflix##skip intro

# Wildcard patterns (enhanced)
*advertisement*
*skip*ad*

# Confidence scoring (enhanced)
advertisement::1.0        # Always mute
sponsored::0.8           # High confidence
suggested::0.5           # Medium confidence
```

## Benefits

1. **Better Detection**: More sophisticated pattern matching catches more ads
2. **Fewer False Positives**: Confidence scoring reduces accidental mutes
3. **Community Contribution**: Shared filter lists benefit all users
4. **Flexibility**: Users can customize patterns for their specific apps
5. **Maintainability**: Filter lists easier to update than app code

## Migration Path

1. Maintain backward compatibility with current simple patterns
2. Add enhanced pattern support as optional features
3. Provide UI for switching between simple/advanced modes
4. Include migration tools to convert existing patterns to enhanced format