# DIDComm Location Analysis

## Current Location
`credentials/plugins/didcomm`

## Question
Should DIDComm be under `credentials/plugins/` or `did/plugins/`?

## Analysis

### Arguments for `did/plugins/didcomm` (DID folder)

✅ **DIDComm is fundamentally about DID communication**
- It's a messaging protocol for DID-to-DID communication
- Not credential-specific - used for many protocols:
  - Basic messages
  - Proof presentations  
  - Trust pings
  - Out-of-band invitations
  - Feature discovery
  - Credential exchange (just one use case)

✅ **Fits with DID infrastructure**
- `did/` contains DID-related functionality
- DIDComm is part of the DID ecosystem
- Similar to how DID methods are in `did/plugins/`

✅ **More general-purpose**
- Can be used independently of credentials
- Other systems might use DIDComm without credentials

### Arguments for `credentials/plugins/didcomm` (Current location)

✅ **Primary use case is credential exchange**
- Most common use: Issue Credential protocol
- Present Proof protocol (also credential-related)
- Documentation emphasizes credential exchange

✅ **Consistency with other credential protocols**
- `credentials/plugins/oidc4vci` - credential issuance protocol
- `credentials/plugins/chapi` - credential handler API
- DIDComm fits this pattern

✅ **Already documented and integrated**
- Listed in docs as credential exchange protocol
- Already integrated into credential workflows
- Moving would require updates to docs and references

## Recommendation

**Keep it in `credentials/plugins/didcomm`** for these reasons:

1. **Primary Use Case**: While DIDComm is general-purpose, in TrustWeave's context, the primary and most important use case is credential exchange.

2. **User Mental Model**: Users looking for credential exchange protocols will find it alongside OIDC4VCI and CHAPI.

3. **Minimal Disruption**: It's already implemented, documented, and integrated. Moving it would require:
   - Updating all documentation
   - Updating package names
   - Updating imports across the codebase
   - Updating build files
   - Risk of breaking existing integrations

4. **Practical Organization**: The `credentials/plugins/` folder contains protocols and integrations related to credential workflows, which is where DIDComm fits in practice.

## Alternative: Hybrid Approach

If you want to emphasize DIDComm's general-purpose nature:

1. **Keep implementation in `credentials/plugins/didcomm`** (for credential exchange use cases)
2. **Add a reference/shim in `did/plugins/didcomm`** that re-exports or delegates to the credentials version
3. **Document both locations** explaining when to use which

This would allow:
- Credential-focused users to find it in `credentials/`
- DID-focused users to find it in `did/`
- Single implementation to maintain

## Conclusion

**Recommendation: Keep in `credentials/plugins/didcomm`**

The benefits of moving don't outweigh the costs, and the current location makes sense given TrustWeave's primary use case for DIDComm (credential exchange).

