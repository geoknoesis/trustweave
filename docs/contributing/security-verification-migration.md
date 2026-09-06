# Verification security migration

The production-readiness remediation changes acceptance behavior and public signatures.
Recompile consumers and review the ABI diff before release.

## OpenID Federation

Configure `TrustChainResolver(trustedAnchorKeys = mapOf(anchorId to anchorPublicJwkSet))`.
Provision those public keys through an independently trusted channel, never from the incoming
chain. With no configured keys, verification fails closed. The exchange provider accepts the
same map in its `trustedAnchorKeys` option, alongside `trustedAnchorIds`.

This implementation requires a complete chain ending in the anchor entity configuration.
It checks the leaf self-signature, superior endorsements, issuer/subject links, validity times
and the pinned anchor signature. Resolution returns success only after verification.
The default HTTP client applies the federation egress guard; injecting a client transfers
responsibility for egress controls to the integrator.

## Verifiable Intent

Provide verifier-controlled expected audience and nonce for L2 and each presented L3 token:
`expectedL3PaymentAud`, `expectedL3PaymentNonce`, `expectedL3CheckoutAud`, and
`expectedL3CheckoutNonce`. Missing expectations fail by default. Nonce comparison is not a
replay database: the application must issue fresh challenges and atomically consume them.
The explicit offline-audit opt-out is unsuitable for authorizing payments.

Amounts must be non-negative JSON integers and constraints must name their currency.
Budget and recurrence constraints require external state that this verifier cannot establish;
open mandates containing them fail closed. Autonomous checkout remains unsupported because
line-item matching is unimplemented. These restrictions are not claims of complete VI support.
