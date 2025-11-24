# Bug Report Summary: Kotlin Gradle Plugin 2.2.21

## Quick Reference

**Issue**: `kotlin.build.archivesTaskOutputAsFriendModule=false` property not working  
**Kotlin Version**: 2.2.21  
**Gradle Version**: 9.2.0  
**Documentation**: https://kotlinlang.org/docs/gradle-configure-project.html#other-details

## Files Created

1. **BUG_REPRODUCER.md** - Complete bug report with reproduction steps
2. **minimal-reproducer/** - Standalone test project (may need dependencies configured)

## How to Report

1. Go to: https://youtrack.jetbrains.com/issues/KT
2. Create new issue with:
   - Title: "archivesTaskOutputAsFriendModule property not working in KGP 2.2.21"
   - Attach: `BUG_REPRODUCER.md` content
   - Link to: https://kotlinlang.org/docs/gradle-configure-project.html#other-details
   - Tag: `Kotlin`, `Gradle`, `KGP`

## Current Workaround

Until this is fixed, use the workaround in `credentials/core/build.gradle.kts`:

```kotlin
afterEvaluate {
    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
        val jarTask = tasks.findByName("jar")
        if (jarTask != null) {
            val currentDeps = dependsOn.toList().filter { it != jarTask }.toSet()
            setDependsOn(currentDeps)
        }
    }
}
```

