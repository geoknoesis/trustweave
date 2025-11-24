# Kotlin Version Test Results

## Test Objective
Test if `kotlin.build.archivesTaskOutputAsFriendModule=false` property works in different Kotlin versions to determine if this is a regression or a long-standing bug.

## Test Results

### Kotlin 2.2.21 (Original)
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No

### Kotlin 2.2.20
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No
- **Conclusion**: Bug exists in 2.2.20 as well - not a 2.2.21 regression

### Kotlin 2.2.0
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No
- **Conclusion**: Bug exists in 2.2.0 as well

### Kotlin 2.1.0
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No
- **Conclusion**: Bug exists in 2.1.0 as well

### Kotlin 2.0.0
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No
- **Conclusion**: Bug exists in 2.0.0 as well

### Kotlin 1.9.24
- **Status**: ❌ Circular dependency persists
- **Property Read**: ✅ Yes (verified via debug output)
- **Property Respected**: ❌ No
- **Conclusion**: Bug exists in 1.9.24 as well - property exists but doesn't work

### Kotlin 1.9.0 and 1.8.22
- **Status**: ⚠️ Build configuration incompatibility (deprecated buildDir API)
- **Note**: Cannot test due to project structure incompatibility with older Kotlin versions
- **Conclusion**: Testing stopped at 1.9.24 due to compatibility issues

## Findings

1. **The bug exists in all tested versions** - 1.9.24, 2.0.0, 2.1.0, 2.2.0, 2.2.20, and 2.2.21
2. **The property is consistently read** but never respected across all tested versions
3. **This is a long-standing bug** - the property exists in Kotlin 1.9.24+ but doesn't work
4. **The property was introduced to address circular dependencies**, but it doesn't actually prevent them
5. **Testing stopped at 1.9.24** - older versions (1.9.0, 1.8.22) have build configuration incompatibilities

## Next Steps

1. Test with Kotlin 2.1.x or 2.0.x to see when the bug was introduced
2. Check Kotlin issue tracker for known issues
3. File bug report with evidence that it affects multiple versions
4. Consider if the property was ever functional or if documentation is incorrect

## Recommendation

Since the property doesn't work in multiple versions, this suggests:
- Either the property was never fully implemented
- Or there's a fundamental issue with how the property is checked
- Or the property only works in specific configurations that this project doesn't match

The bug report should note that this affects multiple Kotlin versions, not just 2.2.21.

