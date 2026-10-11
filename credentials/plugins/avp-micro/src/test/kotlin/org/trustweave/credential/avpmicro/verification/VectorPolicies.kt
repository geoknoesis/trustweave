package org.trustweave.credential.avpmicro.verification

/** The issuer of the shipped test vectors, which the vector-based tests explicitly trust. */
const val VECTOR_ISSUER = "did:key:zDnaekFd1ybjRXtmEJKwuDV4GQhHgQovoGbdVVDSwUwDuy8tv"

val vectorTrust = IssuerTrustPolicy { did, _ -> did == VECTOR_ISSUER }
val noRevocation = StatusResolver { false }
