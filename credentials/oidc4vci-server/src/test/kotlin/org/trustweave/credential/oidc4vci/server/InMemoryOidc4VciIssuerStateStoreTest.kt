package org.trustweave.credential.oidc4vci.server

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class InMemoryOidc4VciIssuerStateStoreTest : Oidc4VciIssuerStateStoreContract() {
    override fun newStore(): Oidc4VciIssuerStateStore = InMemoryOidc4VciIssuerStateStore()

    @Test
    fun `the service uses the in-memory store by default and any supplied store otherwise`() {
        val default = Oidc4VciIssuerService("https://issuer.example", "did:example:issuer")
        assertEquals(InMemoryOidc4VciIssuerStateStore::class, default.stateStore::class)

        val store = InMemoryOidc4VciIssuerStateStore()
        val service = Oidc4VciIssuerService("https://issuer.example", "did:example:issuer", TEST_CONFIGURATIONS, stateStore = store)
        assertSame(store, service.stateStore)
        service.createOffer(listOf("A"))
        assertEquals(1, store.offerCount())
    }
}
