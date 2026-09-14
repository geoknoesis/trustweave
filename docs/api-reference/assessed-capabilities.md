# Assessed module capabilities

Generated from `common/src/main/resources/trustweave-capabilities.json`. Unlisted modules are unassessed, not implicitly supported. Use `ModuleCapabilities.requireDeployment` before constructing production clients. It checks maturity, operations and formats; `requireOperations` alone checks functionality only. No catalog entry currently meets the supported-only production policy. See [deployment profiles](provider-deployment-profiles.md).

| Module | Maturity | Operations | Formats |
|---|---|---|---|
| `anchors:anchor-core` | experimental | None | None |
| `anchors:plugins:algorand` | experimental | None | None |
| `anchors:plugins:arbitrum` | experimental | None | None |
| `anchors:plugins:base` | experimental | None | None |
| `anchors:plugins:bitcoin` | experimental | None | None |
| `anchors:plugins:cardano` | experimental | None | None |
| `anchors:plugins:ethereum` | experimental | None | None |
| `anchors:plugins:evm-base` | experimental | None | None |
| `anchors:plugins:ganache` | experimental | None | None |
| `anchors:plugins:indy` | experimental | None | None |
| `anchors:plugins:optimism` | experimental | None | None |
| `anchors:plugins:polygon` | experimental | None | None |
| `anchors:plugins:starknet` | stub | None | None |
| `anchors:plugins:zksync` | experimental | None | None |
| `common` | experimental | None | None |
| `common-mp` | experimental | None | None |
| `contract` | experimental | None | None |
| `credentials:avp-authorization-server` | experimental | None | None |
| `credentials:credential-api` | experimental | None | None |
| `credentials:credential-models-mp` | experimental | None | None |
| `credentials:oidc4vci-server` | experimental | None | None |
| `credentials:plugins:anchor` | experimental | None | None |
| `credentials:plugins:avp-micro` | experimental | None | None |
| `credentials:plugins:chapi` | experimental | None | None |
| `credentials:plugins:didcomm` | experimental | None | None |
| `credentials:plugins:eudiw` | experimental | None | None |
| `credentials:plugins:jades` | experimental | None | None |
| `credentials:plugins:mdl` | experimental | None | None |
| `credentials:plugins:oidc4vci` | experimental | receive, deferred-receive | ldp_vc |
| `credentials:plugins:oidc4vp` | experimental | None | None |
| `credentials:plugins:openid-federation` | experimental | None | None |
| `credentials:plugins:platforms:entra` | experimental | None | None |
| `credentials:plugins:platforms:salesforce` | stub | None | None |
| `credentials:plugins:platforms:servicenow` | stub | None | None |
| `credentials:plugins:presentation-exchange` | experimental | None | None |
| `credentials:plugins:siop` | experimental | None | None |
| `credentials:plugins:status-list:bitstring` | experimental | None | None |
| `credentials:plugins:status-list:database` | experimental | None | None |
| `credentials:plugins:status-list:publishing` | experimental | None | None |
| `credentials:plugins:status-list:server` | experimental | None | None |
| `credentials:plugins:status-list:token` | experimental | None | None |
| `credentials:plugins:verifiable-intent` | experimental | None | None |
| `credentials:vc-api-server` | experimental | None | None |
| `did:did-core` | experimental | None | None |
| `did:did-identifiers-mp` | experimental | None | None |
| `did:plugins:base` | experimental | None | None |
| `did:plugins:btcr` | stub | None | None |
| `did:plugins:cheqd` | experimental | None | None |
| `did:plugins:ebsi` | experimental | None | None |
| `did:plugins:ens` | experimental | None | None |
| `did:plugins:ethr` | experimental | None | None |
| `did:plugins:godiddy` | experimental | None | None |
| `did:plugins:ion` | experimental | None | None |
| `did:plugins:jwk` | experimental | None | None |
| `did:plugins:key` | experimental | None | None |
| `did:plugins:orb` | experimental | None | None |
| `did:plugins:peer` | experimental | None | None |
| `did:plugins:plc` | experimental | None | None |
| `did:plugins:polygon` | experimental | None | None |
| `did:plugins:sidetree-core` | experimental | None | None |
| `did:plugins:sol` | experimental | None | None |
| `did:plugins:tezos` | stub | None | None |
| `did:plugins:threebox` | stub | None | None |
| `did:plugins:web` | experimental | None | None |
| `did:registrar` | experimental | None | None |
| `did:registrar-server-ktor` | experimental | None | None |
| `did:registrar-server-spring` | experimental | None | None |
| `distribution:all` | stub | None | None |
| `distribution:bom` | stub | None | None |
| `distribution:conformance` | experimental | None | None |
| `distribution:examples` | experimental | None | None |
| `kms:kms-core` | experimental | None | None |
| `kms:plugins:aws` | experimental | None | None |
| `kms:plugins:azure` | experimental | None | None |
| `kms:plugins:cloudhsm` | experimental | None | None |
| `kms:plugins:cyberark` | experimental | None | None |
| `kms:plugins:entrust` | experimental | None | None |
| `kms:plugins:fortanix` | experimental | None | None |
| `kms:plugins:google` | experimental | None | None |
| `kms:plugins:hashicorp` | experimental | None | None |
| `kms:plugins:ibm` | experimental | None | None |
| `kms:plugins:inmemory` | experimental | None | None |
| `kms:plugins:pkcs11` | experimental | None | None |
| `kms:plugins:thales` | experimental | None | None |
| `kms:plugins:thales-luna` | experimental | None | None |
| `kms:plugins:utimaco` | experimental | None | None |
| `kms:plugins:venafi` | experimental | None | None |
| `kms:plugins:waltid` | experimental | None | None |
| `observability` | experimental | None | None |
| `reference-wallet:android` | stub | None | None |
| `reference-wallet:android:app` | stub | None | None |
| `reference-wallet:android:shared` | stub | None | None |
| `signatures:cades` | experimental | None | None |
| `signatures:etsi-validation` | experimental | None | None |
| `signatures:jades` | experimental | None | None |
| `signatures:pades` | experimental | None | None |
| `signatures:trust-lists` | experimental | None | None |
| `signatures:tsa-core` | experimental | None | None |
| `signatures:xades` | experimental | None | None |
| `testkit` | experimental | None | None |
| `trust` | experimental | None | None |
| `trust-registry:plugins:database` | experimental | None | None |
| `trust-registry:trust-registry-core` | experimental | None | None |
| `trust-registry:trust-registry-server` | experimental | None | None |
| `wallet:plugins:cloud` | experimental | store, get, list, list-records, recover-records, delete, query | json-vc |
| `wallet:plugins:database` | experimental | store, get, list, list-records, delete, query, tags, collections, page-records | json-vc |
| `wallet:plugins:file` | experimental | store, get, list, list-records, delete, query, recover-records | json-vc |
| `wallet:wallet-core` | experimental | None | None |
| `wallet:wallet-core-mp` | experimental | None | None |
| `wallet:wallet-services` | experimental | None | None |

## Unassessed modules

No capability guarantee is made for these modules. Application requirements fail closed until an assessment is added.


