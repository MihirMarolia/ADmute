# Enhanced uBlock-Style Features Implementation

This document describes the uBlock-Origin inspired features that have been successfully implemented in ADmute TV.

## Implemented Features

### 1. **Advanced Pattern Matching System**

#### FilterRule.kt - New File
Created a comprehensive pattern matching system that supports multiple pattern types:

- **Simple Patterns**: `"advertisement"` - exact or substring match
- **Regex Patterns**: `/ad.*[0-9]+s/` - regex pattern matching (e.g., matches "ad 5s", "advertisement 30s")
- **Wildcard Patterns**: `*skip*` - wildcard matching (e.g., matches "skip", "skip ad", "skip intro")
- **Package-Specific Rules**: `com.youtube.android##ad 1 of` - app-specific patterns

#### Pattern Type Detection
```kotlin
enum class PatternType {
    SIMPLE,           // "advertisement"
    REGEX,            // "/ad.*[0-9]+s/"
    WILDCARD,         // "*skip*"
    PACKAGE_SPECIFIC  // "com.youtube.android##ad 1 of"
}
```

### 2. **Enhanced AdDetector**

#### Updated Detection Logic
- Now accepts package name parameter for package-specific filtering
- Uses FilterRule parsing instead of simple string matching
- Returns confidence scores for each match
- Processes patterns in order of specificity

#### Confidence Scoring
- Simple patterns: 1.0 confidence (always match)
- Regex patterns: 1.0 confidence (always match)
- Wildcard patterns: 0.8 confidence (slightly lower confidence)
- Package-specific patterns: Uses inner pattern confidence

### 3. **Settings Repository Updates**

#### New Settings
- `confidenceThreshold`: Minimum confidence (0.0-1.0) required to mute
- Default threshold: 0.5 (requires medium-high confidence)
- Prevents false positives from low-confidence matches

### 4. **Enhanced User Interface**

#### Pattern Input Improvements
- Updated hint text to explain advanced pattern syntax
- Added example patterns: `/ad.*[0-9]+s/`, `*skip*`, `com.youtube.android##ad 1 of`
- Enhanced marker input with modern default patterns

#### Confidence Threshold Control
- Added input field for confidence threshold (0.0-1.0)
- Helps users balance between sensitivity and false positives
- Explains that higher values reduce false positives

#### Pattern Testing Tool
- Added "Pattern test" section with real-time pattern testing
- Users can enter sample text and test which patterns match
- Shows matched patterns with confidence percentages
- Color-coded results (green for matches, red for no matches)

#### Filter List Management
- **Export Filter List**: Copies current patterns to clipboard in EasyList-compatible format
- **Import Filter List**: Imports patterns from clipboard, filtering out comments
- Supports community filter list sharing
- Format includes metadata header with export timestamp

### 5. **Accessibility Service Integration**

#### Package-Aware Detection
- Updated both event-based and polling detection to use package names
- Package-specific rules only apply to their target apps
- Confidence threshold filtering before muting

## Pattern Syntax Examples

### Simple Patterns
```
advertisement
ad 1 of
skip ad
sponsored
```

### Regex Patterns
```
/ad.*[0-9]+s/          # Matches "ad 5s", "advertisement 30 seconds"
/skip.*[0-9]+s/        # Matches "skip 5s", "skip ad in 30 seconds"
/promo.*[0-9]+s/       # Matches "promo 10s", "promotion in 5 seconds"
```

### Wildcard Patterns
```
*skip*                 # Matches "skip", "skip ad", "skip intro"
*advertisement*       # Matches "advertisement", "skip advertisement"
*ad*[0-9]*             # Matches "ad 1", "advertisement 5"
```

### Package-Specific Patterns
```
com.youtube.android##ad 1 of
com.netflix##skip intro
com.hulu.plus##advertisement
com.amazon.video##sponsored content
```

## Usage Examples

### YouTube-Specific Rules
```
com.youtube.android##ad 1 of
com.youtube.android##skip ad
com.youtube.android##/advertisement.*[0-9]+s/
```

### Netflix-Specific Rules
```
com.netflix##skip intro
com.netflix##/promo.*[0-9]+s/
com.netflix##suggested for you
```

### Generic High-Confidence Rules
```
/advertisement/
/sponsored content/
/ad 1 of [0-9]*/
```

## Testing the Implementation

### Using the Pattern Test Tool
1. Enter sample text like "Skip ad in 5 seconds"
2. Click "Test patterns"
3. See which patterns match with confidence scores
4. Adjust patterns based on results

### Using Filter List Import/Export
1. Click "Export filter list" to copy current patterns
2. Share with community or backup
3. Paste community filter lists
4. Click "Import filter list" to load
5. Review and save configuration

## Benefits

### 1. **More Accurate Detection**
- Regex patterns catch variations in ad text
- Package-specific rules reduce false positives
- Confidence scoring prevents accidental mutes

### 2. **Community Contributions**
- EasyList-compatible format for sharing
- Import/export functionality
- Standardized pattern syntax

### 3. **User Control**
- Confidence threshold adjustment
- Pattern testing before deployment
- Granular package-specific control

### 4. **Backward Compatibility**
- Simple patterns still work as before
- Enhanced features are optional
- Gradual migration path

## Technical Implementation Details

### FilterRule Parsing
```kotlin
// Automatic pattern type detection
FilterRule.parse("advertisement")              // SIMPLE
FilterRule.parse("/ad.*[0-9]+s/")              // REGEX  
FilterRule.parse("*skip*")                     // WILDCARD
FilterRule.parse("com.youtube.android##ad")    // PACKAGE_SPECIFIC
```

### Confidence-Based Muting
```kotlin
// Only mute if confidence meets threshold
if (evidence.isAdLikely && evidence.confidence >= settings.confidenceThreshold) {
    // Proceed with muting
}
```

### Package-Specific Filtering
```kotlin
// Rules only apply to target packages
if (rule.appliesToPackage(currentPackageName)) {
    // Check if pattern matches
}
```

## Future Enhancements

### Planned Features
1. **Online Filter List Synchronization**
   - Fetch filter lists from URLs
   - Automatic updates with user consent
   - Community-maintained TV-specific lists

2. **Filter Categories**
   - Separate ad/annoyance/privacy filters
   - Category-specific confidence thresholds
   - Enable/disable by category

3. **Adaptive Scanning**
   - Increase scan frequency during ads
   - Decrease during normal content
   - Machine learning for pattern optimization

## Migration Guide

### From Simple Patterns to Enhanced
1. Existing simple patterns work unchanged
2. Gradually add regex patterns for complex cases
3. Use package-specific rules for problematic apps
4. Adjust confidence threshold based on experience

### Recommended Starting Configuration
```
# High-confidence simple patterns
advertisement
ad 1 of
skip ad

# Regex patterns for variations
/ad.*[0-9]+s/
/skip.*[0-9]+s/

# Package-specific rules
com.youtube.android##ad 1 of
com.netflix##skip intro

# Confidence threshold: 0.5
```

## Performance Considerations

### Pattern Matching Efficiency
- Regex patterns validated during parsing
- Invalid regex patterns fall back to simple matching
- Wildcard patterns converted to regex internally
- Early termination on first match per text

### Memory Usage
- FilterRule objects created once per settings load
- Pattern compilation cached
- Minimal overhead during scanning

## Troubleshooting

### Pattern Not Matching
1. Use pattern test tool to verify syntax
2. Check confidence threshold isn't too high
3. Verify package name for package-specific rules
4. Enable verbose diagnostics to see actual text

### Too Many False Positives
1. Increase confidence threshold
2. Make patterns more specific
3. Use package-specific rules
4. Review diagnostic logs

### Patterns Too Specific
1. Add wildcard variants
2. Use regex for pattern families
3. Lower confidence threshold
4. Add more pattern variations

## Conclusion

The enhanced uBlock-style features significantly improve ADmute TV's detection capabilities while maintaining backward compatibility. Users can now:

- Use sophisticated pattern matching
- Share filter lists with the community
- Fine-tune detection with confidence thresholds
- Test patterns before deployment
- Create app-specific rules

This implementation provides a solid foundation for future enhancements while giving users powerful tools to customize their ad detection experience.