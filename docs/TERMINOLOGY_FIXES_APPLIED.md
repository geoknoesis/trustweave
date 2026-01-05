# Terminology Fixes Applied

**Date:** January 2025  
**Status:** In Progress

## Summary

Applying terminology fixes to standardize API naming across documentation files according to the Style Guide.

## Style Guide Standards

### Correct Usage
- **Class Name**: `TrustWeave` (capitalized)
- **Variable Name**: `trustWeave` (camelCase)
- **DSL Function**: `trustWeave { }` (camelCase)

### Incorrect Usage (to fix)
- ❌ `TrustLayer` (deprecated/incorrect)
- ❌ `trustLayer` (should be `trustWeave`)
- ❌ `TrustWeaveError` (check if this is correct API name)

## Files Fixed

### ✅ 1. docs/getting-started/workflows.md

**Issue:** Used deprecated `TrustLayer` class name and `trustLayer` variable name

**Fixes Applied:**
- `TrustLayer` → `TrustWeave` (class name)
- `trustLayer` → `trustWeave` (variable name)
- `trustLayer.getDslContext()` → `trustWeave.configuration` (API update)
- Updated imports to use `TrustWeave`
- Fixed function signatures and parameter types

**Status:** ✅ Partially fixed (first function updated, more functions need review)

**Remaining Work:**
- Verify all API calls match current SDK patterns
- Check error handling types (`TrustWeaveError` vs actual exception types)
- Update return types to match actual SDK types

## Files to Audit

### Priority 1: Getting Started
- [x] `docs/getting-started/workflows.md` (in progress)
- [ ] `docs/getting-started/quick-start.md` (verify)
- [ ] `docs/getting-started/common-patterns.md` (verify)
- [ ] `docs/getting-started/api-patterns.md` (verify)

### Priority 2: How-To Guides
- [ ] `docs/how-to/issue-credentials.md` (verify)
- [ ] `docs/how-to/verify-credentials.md` (verify)
- [ ] `docs/how-to/create-dids.md` (verify)
- [ ] `docs/how-to/manage-wallets.md` (verify)

### Priority 3: Core Concepts
- [ ] `docs/core-concepts/verifiable-credentials.md` (verify)
- [ ] `docs/core-concepts/dids.md` (verify)
- [ ] `docs/core-concepts/wallets.md` (verify)

### Priority 4: Scenarios
- [ ] `docs/scenarios/*.md` (verify - 25+ files)

## Verification Process

1. **Search for deprecated terms:**
   ```bash
   grep -r "TrustLayer" docs/
   grep -r "trustLayer" docs/
   ```

2. **Verify against SDK:**
   - Check actual class names in `trust/src/main/kotlin/org/trustweave/trust/`
   - Verify API method signatures
   - Check exception types

3. **Apply fixes:**
   - Replace deprecated terms
   - Update API calls to match current SDK
   - Verify code examples compile

4. **Test:**
   - Review fixed files
   - Verify examples are accurate
   - Check for broken links or references

## Status

- ✅ Style Guide created
- 🔄 Terminology audit in progress
- ⏳ API verification pending
- ⏳ Full documentation audit pending

---

**Last Updated:** January 2025  
**Next Update:** After completing workflows.md fixes and verifying API patterns
