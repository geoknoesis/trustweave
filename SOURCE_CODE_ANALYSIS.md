# Source Code Analysis: Finding the Bug in Kotlin Gradle Plugin

## Repository Information

The Kotlin Gradle Plugin source code is available at:
- **Repository**: https://github.com/JetBrains/kotlin
- **Plugin Location**: `libraries/tools/gradle-plugin/`
- **Version**: 2.2.21

## Where to Look

Based on the property name `kotlin.build.archivesTaskOutputAsFriendModule`, we need to find:

1. **Property Reading Code**: Where the plugin reads this property from `gradle.properties`
2. **Task Dependency Creation**: Where `compileKotlin` task dependencies are configured
3. **Friend Module Logic**: Where the friend module feature is implemented

## Expected Code Locations

### 1. Property Reading
Look for code that reads Gradle properties, likely in:
- `KotlinJvmProjectExtension.kt` or similar extension classes
- Property reading utilities
- Build configuration classes

Search for:
```kotlin
project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")
// or
providers.gradleProperty("kotlin.build.archivesTaskOutputAsFriendModule")
```

### 2. Task Dependency Configuration
Look for code that creates task dependencies, likely in:
- `KotlinJvmPlugin.kt` - Main plugin class
- `KotlinCompile.kt` - Task configuration
- Task dependency setup code

Search for:
```kotlin
compileKotlinTask.dependsOn(jarTask)
// or
task.dependsOn(archivesTask)
```

### 3. Friend Module Implementation
Look for friend module related code:
- Classes with "Friend" in the name
- Code that uses archive outputs as friend modules
- Compilation classpath configuration

## Analysis Steps

### Step 1: Clone the Repository
```bash
git clone https://github.com/JetBrains/kotlin.git
cd kotlin
git checkout v2.2.21  # or the specific commit for 2.2.21
```

### Step 2: Search for Property Usage
```bash
# Search for the property name
grep -r "archivesTaskOutputAsFriendModule" libraries/tools/gradle-plugin/

# Search for property reading patterns
grep -r "findProperty.*archivesTaskOutputAsFriendModule" libraries/tools/gradle-plugin/
grep -r "gradleProperty.*archivesTaskOutputAsFriendModule" libraries/tools/gradle-plugin/
```

### Step 3: Find Task Dependency Creation
```bash
# Search for where compileKotlin depends on jar
grep -r "dependsOn.*jar" libraries/tools/gradle-plugin/
grep -r "compileKotlin.*dependsOn" libraries/tools/gradle-plugin/

# Search for friend module dependency creation
grep -r "friend.*module" libraries/tools/gradle-plugin/ -i
```

### Step 4: Compare with Working Version
If available, compare with Kotlin 2.2.20 to see what changed:
```bash
git diff v2.2.20..v2.2.21 -- libraries/tools/gradle-plugin/
```

## Potential Bug Locations

Based on the symptoms (property is read but ignored), the bug is likely in:

1. **Property Reading vs Usage Mismatch**
   - Property is read correctly
   - But the value is not passed to the code that creates dependencies
   - Or the check happens after dependencies are already created

2. **Timing Issue**
   - Dependencies are created before the property is read
   - Or the property check happens in the wrong phase of the build lifecycle

3. **Conditional Logic Bug**
   - The property check has incorrect logic (e.g., checking `== true` instead of `!= false`)
   - Or the property value is being overridden somewhere

4. **Scope Issue**
   - Property is read at the wrong scope (project vs subproject)
   - Or the property is read but not propagated to where it's needed

## Expected Fix Location

The fix should be in code that:
1. Reads the property (likely in a plugin extension or configuration class)
2. Uses the property to conditionally create task dependencies
3. Should look something like:

```kotlin
val useArchivesAsFriendModule = project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")
    ?.toString()?.toBoolean() ?: true  // Default to true

if (useArchivesAsFriendModule) {
    compileKotlinTask.dependsOn(jarTask)
    // ... friend module setup
}
```

## How to Verify the Bug

1. **Add Debug Logging**: Add println statements in the Kotlin plugin source to see:
   - When the property is read
   - What value it has
   - Whether the conditional check is executed
   - When task dependencies are created

2. **Set Breakpoints**: In an IDE, set breakpoints at:
   - Property reading location
   - Task dependency creation location
   - Conditional check location

3. **Compare Behavior**: Compare behavior between:
   - Property set to `true` (should create dependency)
   - Property set to `false` (should NOT create dependency)
   - Property not set (should default to `true` and create dependency)

## Next Steps

1. Clone the Kotlin repository
2. Checkout version 2.2.21
3. Search for `archivesTaskOutputAsFriendModule` in the gradle-plugin directory
4. Analyze the code flow from property reading to dependency creation
5. Identify where the property value is being ignored
6. Create a patch or report the exact location to JetBrains

## Useful Commands

```bash
# Find all references to the property
cd kotlin
git grep -n "archivesTaskOutputAsFriendModule" libraries/tools/gradle-plugin/

# Find where task dependencies are created
git grep -n "dependsOn.*jar\|jar.*dependsOn" libraries/tools/gradle-plugin/

# Find friend module related code
git grep -n "friend.*module\|FriendModule" libraries/tools/gradle-plugin/ -i

# View changes between versions
git log --oneline --grep="archivesTaskOutputAsFriendModule" libraries/tools/gradle-plugin/
git log --oneline --grep="friend.*module" libraries/tools/gradle-plugin/ -i
```

