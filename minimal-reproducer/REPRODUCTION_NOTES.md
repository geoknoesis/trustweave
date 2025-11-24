# Reproduction Notes

## Current Status

The minimal reproducer currently **does NOT reproduce the bug** - the build succeeds. This indicates that the bug requires more specific conditions than initially replicated.

## What We Know Triggers the Bug

Based on the actual project analysis:

1. **Transitive dependency chain**: `credentials:core` → `:did:core` → `:common`
2. **Complex module structure**: `:did:core` has multiple subdirectories
3. **Resources**: `:did:core` has resource files (JSON configs)
4. **Nested project path**: `:did:core` vs `:common`
5. **Dependency graph complexity**: Gradle detects `:credentials:core` in `:did:core`'s tree

## Why the Minimal Reproducer Doesn't Work

The minimal reproducer has:
- ✅ Transitive dependency chain (module-a → module-b → module-c)
- ✅ Resources (module-b has config.json)
- ✅ Nested project paths
- ❌ **Missing**: The specific complexity that triggers the friend module behavior

## Possible Missing Elements

1. **Internal declarations**: The friend module feature might be triggered by `internal` visibility modifiers
2. **Specific Kotlin compiler options**: There might be compiler options that trigger it
3. **Project dependency resolution complexity**: The actual project might have more complex dependency resolution
4. **Artifact naming**: The interaction with artifact naming might be required

## Next Steps

1. Try adding `internal` declarations to trigger friend module behavior
2. Check if specific Kotlin compiler options are needed
3. Verify if the property is actually being read (add debug output)
4. Test with the exact same Kotlin/Gradle versions

## Alternative Approach

Since the minimal reproducer doesn't reproduce the bug, we can:
1. Document the actual project as the reproducer
2. Provide clear steps to reproduce in the actual project
3. Include all the analysis and findings
4. Reference KT-69330 and explain why the fix doesn't work

