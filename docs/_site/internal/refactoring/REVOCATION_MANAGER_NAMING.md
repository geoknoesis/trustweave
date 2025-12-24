# Revocation Manager Naming Analysis

## Current Name
`CredentialStatusListManager`

## Issues
- "Status List" is a W3C implementation detail, not the primary purpose
- The name emphasizes the mechanism (status list) rather than the role (revocation management)
- "Status" is generic and doesn't convey revocation/suspension clearly

## Primary Responsibilities
1. **Revoke credentials** (primary use case)
2. **Suspend credentials** (secondary use case)
3. **Check revocation/suspension status**
4. **Manage status lists** (implementation detail)

## Recommended Names

### Option 1: `CredentialRevocationManager` ⭐ (Recommended)
**Pros:**
- ✅ Clear and intuitive - immediately conveys purpose
- ✅ "Revocation" is the primary use case
- ✅ "Manager" indicates active management
- ✅ Follows naming pattern of other managers

**Cons:**
- ⚠️ Doesn't explicitly mention "suspension" (but suspension is less common)

### Option 2: `RevocationService`
**Pros:**
- ✅ Follows Service pattern used elsewhere (`CredentialService`, `TemplateService`)
- ✅ Shorter
- ✅ Clear purpose

**Cons:**
- ⚠️ Loses "Credential" prefix (but context from package is clear)
- ⚠️ Doesn't mention suspension

### Option 3: `CredentialRevocationService`
**Pros:**
- ✅ Follows Service pattern
- ✅ Explicit about credentials
- ✅ Clear purpose

**Cons:**
- ⚠️ Doesn't mention suspension
- ⚠️ Longer name

### Option 4: `RevocationManager`
**Pros:**
- ✅ Short and clear
- ✅ Context from package is sufficient

**Cons:**
- ⚠️ Loses "Credential" prefix

## Recommendation

**`CredentialRevocationManager`** is the best choice because:
1. It clearly communicates the primary purpose (revocation)
2. It's explicit about the domain (credentials)
3. "Manager" accurately describes its role (actively managing revocation)
4. Suspension is a secondary use case and is still supported
5. The package name `org.trustweave.credential.revocation` provides additional context

## Migration
- Rename `CredentialStatusListManager` → `CredentialRevocationManager`
- Update factory: `RevocationManagers` → `RevocationManagers` (keep as-is, or consider `RevocationManagers`)
- Update all references

