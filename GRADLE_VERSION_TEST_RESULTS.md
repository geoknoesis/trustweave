# Gradle Version Test Results

## Test Objective
Test if downgrading Gradle version affects the circular dependency issue, to determine if it's a Gradle version compatibility problem or purely a Kotlin plugin issue.

## Test Results

### Gradle 9.2.0 (Original)
- **Kotlin Version**: 2.2.21
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes
- **Property Respected**: ❌ No

### Gradle 8.10
- **Kotlin Version**: 2.2.21
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes
- **Property Respected**: ❌ No
- **Conclusion**: Bug persists with Gradle 8.10

### Gradle 8.5
- **Kotlin Version**: 2.2.21
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes
- **Property Respected**: ❌ No
- **Conclusion**: Bug persists with Gradle 8.5

### Gradle 8.0
- **Kotlin Version**: 2.2.21
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes
- **Property Respected**: ❌ No
- **Conclusion**: Bug persists with Gradle 8.0

## Findings

1. **The bug is not Gradle version-specific** - it persists across Gradle 8.0, 8.5, 8.10, and 9.2.0
2. **The issue is purely a Kotlin plugin problem** - not related to Gradle version compatibility
3. **Downgrading Gradle does not help** - the property is still read but not respected
4. **This confirms the bug is in the Kotlin Gradle Plugin**, not in Gradle itself

## Conclusion

The `kotlin.build.archivesTaskOutputAsFriendModule=false` property bug is **not related to Gradle version**. It's a bug in the Kotlin Gradle Plugin that affects all tested Gradle versions (8.0, 8.5, 8.10, 9.2.0) and all tested Kotlin versions (1.9.24, 2.0.0, 2.1.0, 2.2.0, 2.2.20, 2.2.21).

## Recommendation

Since downgrading Gradle doesn't help, the focus should be on:
1. Filing a bug report with JetBrains (Kotlin plugin team)
2. Waiting for a fix in the Kotlin plugin
3. Using workarounds (if any are found) or restructuring the project

