# Deep Analysis: What's Specific About `:did:core`?

## Investigation Results

### 1. Direct Dependencies
- `:did:core` depends on: `:common` only
- `:did:core` does NOT depend on: `:credentials:core` (confirmed)
- `:did:core` does NOT depend on: any `did:plugins/*` modules

### 2. Transitive Dependencies
- `:did:core` → `:common` (direct)
- No transitive project dependencies from `:did:core`

### 3. Reverse Dependencies (What Depends on `:did:core`)
- `:credentials:core` depends on `:did:core` ✅
- Many `did:plugins/*` modules depend on `:did:core` ✅
- Many `did:plugins/*` modules ALSO depend on `:credentials:core` ⚠️

### 4. ⚠️ CRITICAL DISCOVERY: Transitive Dependency Cycle!

**Dependency output shows:**
```
\--- project :did:core -> project :credentials:core (*)
```

This indicates a **transitive dependency cycle**:
- `credentials:core` → `:did:core` (direct dependency)
- `:did:core` → `:credentials:core` (transitive dependency - HOW?)

**Investigation:**
- `:did:core` does NOT directly depend on `:credentials:core` (confirmed)
- BUT: All `did:plugins/*` modules depend on BOTH `:did:core` AND `:credentials:core`
- If `:did:core` transitively depends on its plugins (through SPI or runtime), this creates a cycle!

**Potential Cycle:**
```
credentials:core → :did:core → [transitive through plugins?] → :credentials:core
```

However, `:did:core` doesn't list `did:plugins/*` as dependencies, so this shouldn't be a compile-time cycle. But the Kotlin plugin might be resolving this during compilation, triggering the friend module behavior.

## Key Differences Between `:did:core` and `:common`

### Structure
- `:common` - Simple, flat structure
- `:did:core` - Complex structure with multiple subdirectories (registrar, resolver, registration, etc.)

### Dependencies
- `:common` - No project dependencies
- `:did:core` - Has project dependency on `:common` (creates transitive chain)

### Resources
- `:common` - No resources
- `:did:core` - Has 7 resource files (JSON configuration files)

### Project Path
- `:common` - Root level (`include("common")`)
- `:did:core` - Nested (`include("did:core")`)

### Code References
- `:did:core` has comments mentioning `credentials:core` but no actual code dependencies
- `:did:core` has code that mentions "credentials" in comments (delegation credentials, etc.) but doesn't import or use `:credentials:core`

## Most Likely Root Cause

**Transitive Dependency Chain + Complex Module Structure**

1. `credentials:core` depends on `:did:core`
2. `:did:core` depends on `:common` (transitive chain: `credentials:core` → `:did:core` → `:common`)
3. `:did:core` has a more complex structure (registrar, resolver, registration subdirectories)
4. `:did:core` has resources (JSON files) that might need to be processed
5. When the Kotlin plugin compiles `credentials:core`:
   - It needs to resolve `:did:core`'s artifact
   - The transitive dependency on `:common` adds complexity
   - The complex structure and resources might trigger additional processing
   - This triggers the `archivesTaskOutputAsFriendModule` behavior
   - Creates `compileKotlin → jar` dependency
   - Circular dependency occurs

## Why `:common` Doesn't Trigger It

- No transitive project dependencies (simpler resolution)
- Simple structure (no complex subdirectories)
- No resources (less processing needed)
- Root-level project (simpler path resolution)

## Why `:did:core` Triggers It

- Has transitive dependency on `:common` (more complex resolution)
- Complex structure (multiple subdirectories)
- Has resources (additional processing)
- Nested project path (might affect resolution)

## Critical Discovery: Dependency Graph Annotation

**Dependency output shows:**
```
\--- project :did:core -> project :credentials:core (*)
```

The `(*)` annotation means "repeated occurrences of a transitive dependency subtree". This indicates:
- Gradle detects that `:credentials:core` appears in `:did:core`'s dependency tree
- This could be through transitive resolution, even if not a direct dependency
- The Kotlin plugin might be resolving this during compilation
- This triggers the friend module behavior

**However**: `:did:core` does NOT directly depend on `:credentials:core`. The `(*)` might indicate:
1. Gradle's dependency resolution is detecting a potential cycle
2. The Kotlin plugin is resolving dependencies in a way that creates this annotation
3. This triggers the `archivesTaskOutputAsFriendModule` behavior

## Conclusion

The combination of:
1. **Transitive dependency chain** (`:did:core` → `:common`)
2. **Complex module structure** (multiple subdirectories: registrar, resolver, registration, etc.)
3. **Presence of resources** (7 JSON configuration files)
4. **Nested project path** (`:did:core` vs `:common`)
5. **Dependency graph complexity** (Gradle detecting `:credentials:core` in `:did:core`'s tree)

...creates a scenario where the Kotlin plugin's dependency resolution triggers the `archivesTaskOutputAsFriendModule` behavior, which the documented fix (`kotlin.build.archivesTaskOutputAsFriendModule=false`) doesn't prevent.

## Why This Specific Combination Triggers It

When `credentials:core` depends on `:did:core`:
1. Kotlin plugin needs to resolve `:did:core`'s dependencies
2. `:did:core` has transitive dependency on `:common`
3. The complex structure and resources require more processing
4. Gradle's dependency resolution detects `:credentials:core` in the tree (via `(*)` annotation)
5. This complexity triggers the friend module behavior
6. Plugin creates `compileKotlin → jar` dependency to access current project's artifact
7. Circular dependency occurs

The `:common` dependency alone doesn't have this complexity, so it doesn't trigger the issue.

