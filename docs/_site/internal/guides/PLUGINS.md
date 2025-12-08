# Available Plugins

TrustWeave ships with comprehensive plugin support for DID methods, blockchain anchors, and key management services.

## DID Method Plugins

- **`com.trustweave.did:key`** - Native did:key implementation (most widely-used DID method). See [Key DID Integration Guide](docs/integrations/key-did.md).
- **`com.trustweave.did:web`** - Web DID method for HTTP/HTTPS-based resolution. See [Web DID Integration Guide](docs/integrations/web-did.md).
- **`com.trustweave.did:ethr`** - Ethereum DID method with blockchain anchoring. See [Ethereum DID Integration Guide](docs/integrations/ethr-did.md).
- **`com.trustweave.did:ion`** - Microsoft ION DID method using Sidetree protocol. See [ION DID Integration Guide](docs/integrations/ion-did.md).
- **`com.trustweave.did:polygon`** - Polygon DID method (lower fees than Ethereum). See [Polygon DID Integration Guide](docs/integrations/polygon-did.md).
- **`com.trustweave.did:sol`** - Solana DID method with program integration. See [Solana DID Integration Guide](docs/integrations/sol-did.md).
- **`com.trustweave.did:peer`** - Peer-to-peer DID method (no external registry). See [Peer DID Integration Guide](docs/integrations/peer-did.md).
- **`com.trustweave.did:jwk`** - W3C-standard did:jwk using JSON Web Keys directly. See [JWK DID Integration Guide](docs/integrations/jwk-did.md).
- **`com.trustweave.did:ens`** - Ethereum Name Service (ENS) resolver integration. See [ENS DID Integration Guide](docs/integrations/ens-did.md).
- **`com.trustweave.did:plc`** - Personal Linked Container (PLC) for AT Protocol. See [PLC DID Integration Guide](docs/integrations/plc-did.md).
- **`com.trustweave.did:cheqd`** - Cheqd network DID method with payment features. See [Cheqd DID Integration Guide](docs/integrations/cheqd-did.md).

## Blockchain Anchor Plugins

- **`com.trustweave.chains:ethereum`** - Ethereum mainnet anchoring with Sepolia testnet support. See [Ethereum Anchor Integration Guide](docs/integrations/ethereum-anchor.md).
- **`com.trustweave.chains:base`** - Base (Coinbase L2) anchoring with fast confirmations and lower fees. See [Base Anchor Integration Guide](docs/integrations/base-anchor.md).
- **`com.trustweave.chains:arbitrum`** - Arbitrum One (largest L2 by TVL) anchoring. See [Arbitrum Anchor Integration Guide](docs/integrations/arbitrum-anchor.md).
- **`com.trustweave.chains:algorand`** - Algorand blockchain anchoring. See [Algorand Integration Guide](docs/integrations/algorand.md).
- **`com.trustweave.chains:polygon`** - Polygon blockchain anchoring. See [Integration Modules](docs/integrations/README.md#blockchain-anchor-integrations).
- **`com.trustweave.chains:ganache`** - Local developer anchoring using Ganache. See [Integration Modules](docs/integrations/README.md#blockchain-anchor-integrations).

## Key Management Service Plugins

- **`com.trustweave.kms:aws`** - AWS Key Management Service integration. See [AWS KMS Integration Guide](docs/integrations/aws-kms.md).
- **`com.trustweave.kms:azure`** - Azure Key Vault integration. See [Azure KMS Integration Guide](docs/integrations/azure-kms.md).
- **`com.trustweave.kms:google`** - Google Cloud KMS integration. See [Google KMS Integration Guide](docs/integrations/google-kms.md).
- **`com.trustweave.kms:hashicorp`** - HashiCorp Vault Transit engine integration. See [HashiCorp Vault KMS Integration Guide](docs/integrations/hashicorp-vault-kms.md).

## Comprehensive Plugin Documentation

See [Supported Plugins](docs/plugins.md) for a comprehensive table view of all plugins, or [Integration Modules](docs/integrations/README.md) for detailed integration guides.

## Third-Party Integrations

- **[walt.id Integration](INTEGRATIONS.md#waltid-integration)** - walt.id-based implementations
- **[godiddy Integration](INTEGRATIONS.md#godiddy-integration)** - HTTP-based integration with godiddy services




