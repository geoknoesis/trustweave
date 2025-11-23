# Use Cases

TrustWeave is designed to be domain-agnostic, making it suitable for a wide range of applications. Here are some common use cases:

## Earth Observation (EO) Catalogues

**Problem**: EO data needs verifiable provenance and integrity guarantees.

**Solution**: Use TrustWeave to:
- Create DIDs for data providers
- Compute digests for datasets and metadata
- Anchor digests to blockchains for tamper-proof records
- Verify data integrity through the integrity chain

**Example Flow**:
1. Create DID for data provider
2. Generate metadata, provenance, and quality reports
3. Create Linkset connecting artifacts
4. Issue Verifiable Credential with Linkset digest
5. Anchor VC digest to blockchain
6. Verify integrity chain: Blockchain → VC → Linkset → Artifacts

## Spatial Web Nodes

**Problem**: Decentralized spatial data requires identity and trust mechanisms.

**Solution**: Use TrustWeave to:
- Establish identity for spatial data nodes
- Anchor spatial data references to blockchains
- Verify data authenticity and provenance
- Enable trust between distributed nodes

## Agentic / LLM-based Platforms

**Problem**: AI agents need verifiable identity and trust relationships.

**Solution**: Use TrustWeave to:
- Create DIDs for AI agents
- Issue credentials for agent capabilities
- Verify agent credentials before interaction
- Anchor agent actions and decisions

## Supply Chain Management

**Problem**: Track products through supply chain with verifiable records.

**Solution**: Use TrustWeave to:
- Create DIDs for supply chain participants
- Issue credentials for product attributes
- Anchor product events to blockchain
- Verify product history and authenticity

## Academic Credentials

**Problem**: Verify academic credentials without centralized authorities.

**Solution**: Use TrustWeave to:
- Create DIDs for educational institutions
- Issue Verifiable Credentials for degrees/certificates
- Anchor credential digests to blockchain
- Enable verifiable credential verification

## Digital Identity Wallets

**Problem**: Users need self-sovereign identity management.

**Solution**: Use TrustWeave to:
- Create user DIDs
- Manage keys through KMS
- Issue and store Verifiable Credentials
- Present credentials when needed

## IoT Device Identity

**Problem**: IoT devices need secure identity and attestation.

**Solution**: Use TrustWeave to:
- Create DIDs for IoT devices
- Issue credentials for device capabilities
- Anchor device events to blockchain
- Verify device identity and status

## Content Integrity

**Problem**: Ensure content hasn't been tampered with.

**Solution**: Use TrustWeave to:
- Compute content digests
- Anchor digests to blockchain
- Verify content integrity by comparing digests
- Track content versions

## Cross-Chain Applications

**Problem**: Applications need to work across multiple blockchains.

**Solution**: Use TrustWeave's chain-agnostic interface to:
- Anchor data to different chains
- Read data from any supported chain
- Switch chains without code changes
- Support multi-chain workflows

## Next Steps

- Understand the [Architecture Overview](architecture-overview.md)
- Get started with [Installation](../getting-started/installation.md)

