---
title: Credential Exchange Protocols - Glossary
---

# Credential Exchange Protocols - Glossary

Complete glossary of terms and concepts used in credential exchange protocols.

## A

### Attribute Restriction
A constraint on credential attributes in proof requests. Specifies which issuer, schema, or credential definition is acceptable.

**Example:**
```kotlin
AttributeRestriction(
    issuerDid = "did:key:issuer",
    schemaId = "https://schema.org/Person"
)
```

---

### Authentication
The process of verifying the identity of a party in a credential exchange. Can be done through cryptographic proofs, signatures, or other mechanisms.

---

## C

### CHAPI (Credential Handler API)
A browser-based API that enables credential wallet interactions through the browser's credential management system. Provides a standardized way for web applications to interact with credential wallets.

**See also:** [CHAPI Protocol](./chapi.md)

---

### Credential Attribute
A single piece of information in a credential preview or credential subject. Consists of a name, value, and optional MIME type.

**Example:**
```kotlin
CredentialAttribute(
    name = "name",
    value = "Alice",
    mimeType = "text/plain"
)
```

---

### Credential Exchange Protocol
An interface that defines how credentials are exchanged between parties. All protocols (DIDComm, OIDC4VCI, CHAPI) implement this interface, allowing them to be used interchangeably.

**See also:** [Protocol Abstraction](../../core-concepts/credential-exchange-protocols.md)

---

### Credential Issue
The act of issuing a verifiable credential to a holder. Occurs after the holder requests a credential that was offered.

**See also:** [Issue Credential](./API_REFERENCE.md#issuecredential)

---

### Credential Offer
A message from an issuer to a holder proposing a credential. Contains a preview of the credential attributes and allows the holder to decide whether to request the credential.

**See also:** [Offer Credential](./API_REFERENCE.md#offercredential)

---

### Credential Preview
A preview of credential attributes shown to the holder before they request the credential. Contains the attributes that will be in the final credential.

**Example:**
```kotlin
CredentialPreview(
    attributes = listOf(
        CredentialAttribute("name", "Alice"),
        CredentialAttribute("email", "alice@example.com")
    )
)
```

---

### Credential Request
A message from a holder to an issuer requesting a credential that was offered. References the original offer and indicates the holder wants to receive the credential.

**See also:** [Request Credential](./API_REFERENCE.md#requestcredential)

---

## D

### DID (Decentralized Identifier)
A W3C standard identifier that enables verifiable, decentralized digital identity. DIDs are used to identify issuers, holders, verifiers, and provers in credential exchanges.

**Format:** `did:<method>:<identifier>`

**Example:** `did:key:z6Mk...`

**See also:** [DIDs](../../core-concepts/dids.md)

---

### DIDComm
A secure, private, and decentralized communication protocol for exchanging messages between parties using DIDs. Supports end-to-end encryption, message threading, and JWS signing.

**See also:** [DIDComm Protocol](./didcomm.md)

---

### DID Document
A JSON document that describes a DID, including verification methods, service endpoints, and other metadata. Required for resolving DIDs and finding cryptographic keys.

**See also:** [DIDs](../../core-concepts/dids.md)

---

### DID Resolver
A function or service that resolves a DID to its DID document. Required for credential exchange to find cryptographic keys and verify identities.

**Example:**
```kotlin
val resolveDid: suspend (String) -> DidDocument? = { did ->
    yourDidResolver.resolve(did)
}
```

---

## E

### Encryption
The process of encoding messages so only authorized parties can read them. DIDComm uses ECDH-1PU for authenticated encryption.

**See also:** [DIDComm Protocol](./didcomm.md)

---

### Exchange Operation
A type of operation in credential exchange. Includes:
- `OFFER_CREDENTIAL`: Issuer offers a credential
- `REQUEST_CREDENTIAL`: Holder requests a credential
- `ISSUE_CREDENTIAL`: Issuer issues a credential
- `REQUEST_PROOF`: Verifier requests a proof
- `PRESENT_PROOF`: Prover presents a proof

**See also:** [ExchangeOperation](./API_REFERENCE.md#exchangeoperation)

---

## H

### Holder
The party that receives and stores credentials. In credential exchange, the holder requests credentials from issuers and presents proofs to verifiers.

**See also:** [Wallets](../../core-concepts/wallets.md)

---

## I

### Issuer
The party that creates and issues verifiable credentials. In credential exchange, the issuer offers credentials to holders and issues them upon request.

---

### Issue ID
A unique identifier for a credential issue operation. Returned when a credential is issued and can be used for tracking and reference.

---

## K

### Key ID
An identifier for a cryptographic key, typically in the format `did:key:...#key-1`. Used in protocol options to specify which key to use for encryption, signing, or decryption.

**Example:** `did:key:issuer#key-1`

---

### Key Management Service (KMS)
A service that manages cryptographic keys. Required for credential exchange to sign credentials, encrypt messages, and perform other cryptographic operations.

**See also:** [Key Management](../../core-concepts/key-management.md)

---

## O

### OIDC4VCI (OpenID Connect for Verifiable Credential Issuance)
A protocol for issuing verifiable credentials using OpenID Connect and OAuth 2.0. Designed for web-based credential issuance with OAuth integration.

**See also:** [OIDC4VCI Protocol](./oidc4vci.md)

---

### Offer ID
A unique identifier for a credential offer. Returned when an offer is created and used to reference the offer in subsequent requests.

---

## P

### Presentation
A verifiable presentation containing one or more verifiable credentials. Used to present credentials to verifiers in proof requests.

**See also:** [Present Proof](./API_REFERENCE.md#presentproof)

---

### Presentation ID
A unique identifier for a proof presentation. Returned when a proof is presented and can be used for tracking and reference.

---

### Prover
The party that presents proofs to verifiers. In credential exchange, the prover creates verifiable presentations and presents them in response to proof requests.

---

### Proof
A cryptographic proof that demonstrates the authenticity and integrity of a credential or presentation. Can be a digital signature, zero-knowledge proof, or other cryptographic mechanism.

**See also:** [Verifiable Credentials](../../core-concepts/verifiable-credentials.md)

---

### Proof Presentation
The act of presenting a verifiable presentation to a verifier. Occurs after the verifier requests a proof.

**See also:** [Present Proof](./API_REFERENCE.md#presentproof)

---

### Proof Request
A message from a verifier to a prover requesting a proof. Specifies which attributes or predicates are required and any restrictions on acceptable credentials.

**See also:** [Request Proof](./API_REFERENCE.md#requestproof)

---

### Proof Request ID
A unique identifier for a proof request. Returned when a proof request is created and used to reference the request in subsequent presentations.

---

### Protocol
An implementation of the `CredentialExchangeProtocol` interface. Examples include DIDComm, OIDC4VCI, and CHAPI.

**See also:** [Credential Exchange Protocol](#credential-exchange-protocol)

---

### Protocol Abstraction Layer
A unified interface for credential exchange operations across different protocols. Allows applications to use any protocol interchangeably without being tightly coupled to a specific implementation.

**See also:** [Protocol Abstraction](../../core-concepts/credential-exchange-protocols.md)

---

### Protocol Name
A string identifier for a protocol (e.g., "didcomm", "oidc4vci", "chapi"). Used to specify which protocol to use in registry operations.

---

### Protocol Registry
A registry that manages multiple credential exchange protocols. Allows registration, discovery, and unified access to different protocols.

**See also:** [CredentialExchangeProtocolRegistry](./API_REFERENCE.md#credentialexchangeprotocolregistry)

---

## R

### Request ID
A unique identifier for a credential request. Returned when a request is created and used to reference the request in subsequent issue operations.

---

### Requested Attribute
An attribute that a verifier requires in a proof request. Can include restrictions on which issuer, schema, or credential definition is acceptable.

**Example:**
```kotlin
RequestedAttribute(
    name = "name",
    restrictions = listOf(
        AttributeRestriction(issuerDid = "did:key:issuer")
    )
)
```

---

### Requested Predicate
A predicate (comparison) that a verifier requires in a proof request. Specifies an attribute name, comparison type (>=, <=, ==), and value.

**Example:**
```kotlin
RequestedPredicate(
    name = "age",
    pType = ">=",
    pValue = 18
)
```

---

## S

### Secret Resolver
A component that resolves cryptographic secrets (private keys) for use with external libraries like `didcomm-java`. Bridges KMS with protocol libraries that need direct key access.

**See also:** [Storage & Secret Resolver](./STORAGE_AND_SECRET_RESOLVER.md)

---

### Supported Operations
The set of operations that a protocol supports. Not all protocols support all operations. For example, OIDC4VCI primarily supports issuance operations, while DIDComm supports all operations.

**See also:** [Exchange Operation](#exchange-operation)

---

## T

### Thread ID
An identifier for grouping related messages in a conversation. Used in DIDComm for message threading, allowing multiple messages to be linked together.

**Example:**
```kotlin
options = mapOf(
    "thid" to "thread-123"
)
```

---

## V

### Verifiable Credential
A W3C standard credential that is cryptographically verifiable. Contains claims about a subject, issued by an issuer, and includes a cryptographic proof.

**See also:** [Verifiable Credentials](../../core-concepts/verifiable-credentials.md)

---

### Verifiable Presentation
A W3C standard presentation containing one or more verifiable credentials. Used to present credentials to verifiers while maintaining privacy and control.

**See also:** [Verifiable Credentials](../../core-concepts/verifiable-credentials.md)

---

### Verifier
The party that requests and verifies proofs. In credential exchange, the verifier requests proofs from provers and verifies the presented credentials.

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly
- **[API Reference](./API_REFERENCE.md)** - Complete API documentation
- **[Core Concepts](../../core-concepts/credential-exchange-protocols.md)** - Deep dive into concepts
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows

