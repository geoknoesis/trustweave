# Assessed module capabilities

Generated from `common/src/main/resources/trustweave-capabilities.json`. Unlisted modules are unassessed, not implicitly supported. Use `ModuleCapabilities.requireOperations` during startup.

| Module | Maturity | Operations | Formats |
|---|---|---|---|
| `anchors:plugins:starknet` | stub | None | None |
| `credentials:plugins:oidc4vci` | experimental | receive, deferred-receive | ldp_vc |
| `did:plugins:btcr` | stub | None | None |
| `did:plugins:tezos` | stub | None | None |
| `did:plugins:threebox` | stub | None | None |
| `wallet:plugins:cloud` | experimental | store, get, list, list-records, recover-records, delete, query | json-vc |
| `wallet:plugins:database` | experimental | store, get, list, list-records, delete, query, tags, collections, page-records | json-vc |
| `wallet:plugins:file` | experimental | store, get, list, list-records, delete, query, recover-records | json-vc |

## Unassessed modules

No capability guarantee is made for these modules. Application requirements fail closed until an assessment is added.

- `anchors:anchor-core`
- `anchors:plugins:algorand`
- `anchors:plugins:arbitrum`
- `anchors:plugins:base`
- `anchors:plugins:bitcoin`
- `anchors:plugins:cardano`
- `anchors:plugins:ethereum`
- `anchors:plugins:evm-base`
- `anchors:plugins:ganache`
- `anchors:plugins:indy`
- `anchors:plugins:optimism`
- `anchors:plugins:polygon`
- `anchors:plugins:zksync`
- `common`
- `common-mp`
- `contract`
- `credentials:avp-authorization-server`
- `credentials:credential-api`
- `credentials:credential-models-mp`
- `credentials:oidc4vci-server`
- `credentials:plugins:anchor`
- `credentials:plugins:avp-micro`
- `credentials:plugins:chapi`
- `credentials:plugins:didcomm`
- `credentials:plugins:eudiw`
- `credentials:plugins:jades`
- `credentials:plugins:mdl`
- `credentials:plugins:oidc4vp`
- `credentials:plugins:openid-federation`
- `credentials:plugins:platforms:entra`
- `credentials:plugins:platforms:salesforce`
- `credentials:plugins:platforms:servicenow`
- `credentials:plugins:presentation-exchange`
- `credentials:plugins:siop`
- `credentials:plugins:status-list:bitstring`
- `credentials:plugins:status-list:database`
- `credentials:plugins:status-list:publishing`
- `credentials:plugins:status-list:server`
- `credentials:plugins:status-list:token`
- `credentials:plugins:verifiable-intent`
- `credentials:vc-api-server`
- `did:did-core`
- `did:did-identifiers-mp`
- `did:plugins:base`
- `did:plugins:cheqd`
- `did:plugins:ebsi`
- `did:plugins:ens`
- `did:plugins:ethr`
- `did:plugins:godiddy`
- `did:plugins:ion`
- `did:plugins:jwk`
- `did:plugins:key`
- `did:plugins:orb`
- `did:plugins:peer`
- `did:plugins:plc`
- `did:plugins:polygon`
- `did:plugins:sidetree-core`
- `did:plugins:sol`
- `did:plugins:web`
- `did:registrar`
- `did:registrar-server-ktor`
- `did:registrar-server-spring`
- `distribution:all`
- `distribution:bom`
- `distribution:conformance`
- `distribution:examples`
- `kms:kms-core`
- `kms:plugins:aws`
- `kms:plugins:azure`
- `kms:plugins:cloudhsm`
- `kms:plugins:cyberark`
- `kms:plugins:entrust`
- `kms:plugins:fortanix`
- `kms:plugins:google`
- `kms:plugins:hashicorp`
- `kms:plugins:ibm`
- `kms:plugins:inmemory`
- `kms:plugins:pkcs11`
- `kms:plugins:thales`
- `kms:plugins:thales-luna`
- `kms:plugins:utimaco`
- `kms:plugins:venafi`
- `kms:plugins:waltid`
- `reference-wallet:android`
- `reference-wallet:android:app`
- `reference-wallet:android:shared`
- `signatures:cades`
- `signatures:etsi-validation`
- `signatures:jades`
- `signatures:pades`
- `signatures:trust-lists`
- `signatures:tsa-core`
- `signatures:xades`
- `testkit`
- `trust`
- `trust-registry:plugins:database`
- `trust-registry:trust-registry-core`
- `trust-registry:trust-registry-server`
- `wallet:wallet-core`
- `wallet:wallet-core-mp`
- `wallet:wallet-services`
