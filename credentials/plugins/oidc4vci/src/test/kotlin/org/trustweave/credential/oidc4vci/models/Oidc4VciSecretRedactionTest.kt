package org.trustweave.credential.oidc4vci.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Oidc4VciSecretRedactionTest {
    @Test
    fun `credential and deferred and batch requests redact access tokens and txCodeValue`() {
        val credReq =
            Oidc4VciCredentialRequest(
                requestId = "req-1",
                holderDid = "did:key:holder",
                offerId = "offer-1",
                credentialIssuer = "https://issuer.example",
                credentialTypes = listOf("VerifiableCredential"),
                accessToken = "tok-secret-123",
                txCodeValue = "987654",
            )
        assertFalse(credReq.toString().contains("tok-secret-123"))
        assertFalse(credReq.toString().contains("987654"))
        assertTrue(credReq.toString().contains("[REDACTED]"))

        val deferredReq = DeferredCredentialRequest(transactionId = "tx-1", accessToken = "tok-deferred-456")
        assertFalse(deferredReq.toString().contains("tok-deferred-456"))
        assertTrue(deferredReq.toString().contains("[REDACTED]"))

        val batchReq = BatchCredentialRequest(credentialRequests = listOf(credReq), accessToken = "tok-batch-789")
        assertFalse(batchReq.toString().contains("tok-batch-789"))
        assertTrue(batchReq.toString().contains("[REDACTED]"))
    }
}
