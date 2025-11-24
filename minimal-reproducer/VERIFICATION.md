# Verification Steps

## Verify the Bug

1. **Check property is set:**
   ```bash
   cat gradle.properties | grep archivesTaskOutputAsFriendModule
   ```
   Should show: `kotlin.build.archivesTaskOutputAsFriendModule=false`

2. **Run the build:**
   ```bash
   ./gradlew :module-a:build
   ```

3. **Expected result:** Build should succeed (property should prevent circular dependency)

4. **Actual result:** Build fails with circular dependency error

## Verify Property is Read

Add this to `module-a/build.gradle.kts`:
```kotlin
println("kotlin.build.archivesTaskOutputAsFriendModule = ${project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")}")
```

Run:
```bash
./gradlew :module-a:build
```

Output should show: `kotlin.build.archivesTaskOutputAsFriendModule = false`

This confirms the property is read but not respected.

## Test Without Module B Dependency

Temporarily comment out the dependency in `module-a/build.gradle.kts`:
```kotlin
dependencies {
    // implementation(project(":module-b"))  // Commented out
}
```

Run:
```bash
./gradlew :module-a:build
```

Result: Build succeeds (no circular dependency)

This confirms that the dependency on Module B triggers the issue.

