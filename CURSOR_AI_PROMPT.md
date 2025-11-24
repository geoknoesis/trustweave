# Cursor.ai Prompt: Find Bug in Kotlin Gradle Plugin 2.2.21

## Task
Investigate and locate the bug in Kotlin Gradle Plugin 2.2.21 where the `kotlin.build.archivesTaskOutputAsFriendModule=false` property is being read correctly by Gradle but ignored by the plugin, causing circular dependency errors.

## Context

### The Bug
- **Property**: `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties`
- **Expected Behavior**: When set to `false`, the `compileKotlin` task should NOT depend on the `jar` task
- **Actual Behavior**: The property is read correctly (verified via debug output), but the plugin still creates the `compileKotlin → jar` dependency, causing a circular dependency:
  ```
  compileKotlin → jar → classes → compileJava → compileKotlin
  ```

### Evidence
- Property is correctly set in `gradle.properties`: `kotlin.build.archivesTaskOutputAsFriendModule=false`
- Gradle reads the property: `project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")` returns `false`
- Plugin ignores the property: Circular dependency persists
- Official documentation: https://kotlinlang.org/docs/gradle-configure-project.html#other-details

## Repository Setup

1. Clone the Kotlin repository:
   ```bash
   git clone https://github.com/JetBrains/kotlin.git
   cd kotlin
   git checkout v2.2.21
   ```

2. Navigate to the Gradle plugin directory:
   ```bash
   cd libraries/tools/gradle-plugin
   ```

## Investigation Steps

### Step 1: Find Property Reading Code
Search for where the plugin reads the `archivesTaskOutputAsFriendModule` property:

```bash
# Search for the property name
grep -r "archivesTaskOutputAsFriendModule" .

# Search for property reading patterns
grep -r "findProperty.*archivesTaskOutputAsFriendModule" .
grep -r "gradleProperty.*archivesTaskOutputAsFriendModule" .
grep -r "getProperty.*archivesTaskOutputAsFriendModule" .
```

**Look for:**
- Files that read this property
- Where the property value is stored
- How the property value is passed to other parts of the code

### Step 2: Find Task Dependency Creation
Search for where the `compileKotlin` task dependency on `jar` is created:

```bash
# Search for compileKotlin task configuration
grep -r "compileKotlin" . | grep -i "dependsOn\|dependency"

# Search for jar task dependencies
grep -r "dependsOn.*jar\|jar.*dependsOn" .

# Search for friend module related code
grep -r "friend.*module\|FriendModule" . -i

# Search for archive task dependencies
grep -r "archiveTask\|archivesTask" . -i
```

**Look for:**
- Where `compileKotlinTask.dependsOn(jarTask)` or similar is called
- Conditional logic that should check the property before creating dependencies
- Friend module setup code

### Step 3: Trace the Code Flow
For each file found in Steps 1 and 2:

1. **Read the file** and understand:
   - When the property is read (build lifecycle phase)
   - When task dependencies are created (build lifecycle phase)
   - Whether there's a conditional check using the property value

2. **Identify the bug** by checking:
   - Is the property value passed to the code that creates dependencies?
   - Is there a conditional check that uses the property?
   - Is the conditional logic correct (e.g., checking `== true` vs `!= false`)?
   - Are dependencies created before the property is read?

### Step 4: Compare with Previous Version
Compare with Kotlin 2.2.20 to see what changed:

```bash
git diff v2.2.20..v2.2.21 -- libraries/tools/gradle-plugin/
```

**Look for:**
- Changes to property reading code
- Changes to task dependency creation
- Changes to friend module implementation
- Any new code that might have introduced the bug

### Step 5: Identify the Root Cause
Based on the symptoms, the bug is likely one of:

1. **Property Reading vs Usage Mismatch**
   - Property is read but value is not passed to dependency creation code
   - Property is read in wrong scope (project vs subproject)

2. **Timing Issue**
   - Dependencies are created before property is read
   - Property check happens in wrong build lifecycle phase

3. **Conditional Logic Bug**
   - Property check has incorrect logic
   - Property value is being overridden somewhere
   - Default value logic is wrong

4. **Missing Conditional Check**
   - Code creates dependencies without checking the property at all

## Expected Code Pattern

The correct implementation should look something like:

```kotlin
// Read property
val useArchivesAsFriendModule = project.findProperty("kotlin.build.archivesTaskOutputAsFriendModule")
    ?.toString()?.toBoolean() ?: true  // Default to true

// Use property to conditionally create dependency
if (useArchivesAsFriendModule) {
    compileKotlinTask.dependsOn(jarTask)
    // ... friend module setup
} else {
    // Do NOT create the dependency
}
```

## Key Files to Examine

Based on typical Gradle plugin structure, check these files:

1. **Plugin Main Class**: `KotlinJvmPlugin.kt` or similar
2. **Extension Classes**: `KotlinJvmProjectExtension.kt` or similar
3. **Task Configuration**: `KotlinCompile.kt` or task configuration classes
4. **Friend Module Code**: Any files with "Friend" in the name
5. **Build Configuration**: Classes that set up task dependencies

## Deliverables

1. **Identify the exact file and line number** where the bug is
2. **Explain what's wrong** with the code
3. **Show what the code should be** (the fix)
4. **Create a minimal patch** that fixes the issue

## Verification

After identifying the bug, verify by:

1. Adding debug logging to see:
   - When property is read
   - What value it has
   - Whether conditional check is executed
   - When dependencies are created

2. Testing the fix:
   - With property set to `true` (should create dependency)
   - With property set to `false` (should NOT create dependency)
   - With property not set (should default to `true` and create dependency)

## Additional Context

- **Kotlin Version**: 2.2.21
- **Gradle Version**: 9.2.0
- **Issue**: Property is read but ignored
- **Impact**: Blocks builds with circular dependency errors
- **Workarounds**: None found - all attempted solutions failed

## Success Criteria

The investigation is successful when you can:
1. Point to the exact code location causing the bug
2. Explain why the property is being ignored
3. Provide a fix that makes the property work as documented
4. Show that the fix resolves the circular dependency issue

---

**Start by cloning the repository and following Step 1. Work through each step systematically, documenting findings as you go.**

