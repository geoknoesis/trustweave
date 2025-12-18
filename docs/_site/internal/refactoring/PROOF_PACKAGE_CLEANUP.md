# Proof Package Cleanup Plan

## Current Issues

1. **Duplicate Files**:
   - `ProofAdapters.kt` and `ProofEngines.kt` - identical content
   - `ProofAdapterDiscovery.kt` and `ProofEngineDiscovery.kt` - identical content  
   - `DefaultProofAdapterRegistry.kt` and `DefaultProofEngineRegistry.kt` - old vs new

2. **Unnecessary Complexity**:
   - `ProofEngineRegistryFactory.kt` - just a simple function, could be merged
   - `registry/` subpackage adds extra layer without clear benefit
   - Too many files for internal-only functionality

3. **Structure Issues**:
   - Internal APIs mixed with public APIs
   - Unclear separation of concerns
   - Package doesn't clearly communicate what's public vs internal

## Proposed Clean Structure

```
proof/
├── ProofOptions.kt          # Public API - proof generation options
└── internal/                 # All internal implementation
    ├── ProofEngineRegistry.kt      # Registry interface (internal)
    ├── DefaultProofEngineRegistry.kt # Registry implementation
    └── ProofEngineDiscovery.kt     # SPI discovery utilities
```

## Changes

1. **Remove duplicates**:
   - Delete `ProofAdapters.kt` (keep `ProofEngines.kt`)
   - Delete `ProofAdapterDiscovery.kt` (keep `ProofEngineDiscovery.kt`)
   - Delete `DefaultProofAdapterRegistry.kt` (keep `DefaultProofEngineRegistry.kt`)

2. **Move to internal**:
   - Move `ProofEngineRegistry.kt` → `proof/internal/`
   - Move `DefaultProofEngineRegistry.kt` → `proof/internal/`
   - Move `ProofEngines.kt` → `proof/internal/ProofEngineDiscovery.kt` (consolidate)
   - Move `ProofEngineRegistryFactory.kt` → merge into `ProofEngineDiscovery.kt`

3. **Simplify**:
   - Consolidate registry creation into discovery utilities
   - Make it clear these are internal-only APIs
   - Keep only `ProofOptions.kt` as public API in `proof/` package

## Result

**Public API** (what developers use):
- `ProofOptions` - for configuring proof generation

**Internal API** (implementation details):
- All registry and discovery code in `proof/internal/`
- Clear separation of concerns
- Easier to understand and maintain

