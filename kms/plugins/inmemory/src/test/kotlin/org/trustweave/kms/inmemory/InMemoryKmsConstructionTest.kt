package org.trustweave.kms.inmemory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import java.security.KeyPair
import java.security.Security
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryKmsConstructionTest {
    @Test
    fun `loading and using the service does not register BouncyCastle globally`() =
        runBlocking<Unit> {
            val before = Security.getProvider("BC")
            val kms = InMemoryKeyManagementService()
            assertTrue(kms.generateKey(Algorithm.Secp256k1) is GenerateKeyResult.Success)
            if (before == null) assertNull(Security.getProvider("BC"))
        }

    @Test
    fun `a ConcurrentHashMap argument is shared with the caller`() =
        runBlocking<Unit> {
            val store = ConcurrentHashMap<KeyId, KeyPair>()
            val meta = ConcurrentHashMap<KeyId, Algorithm>()
            val kms = InMemoryKeyManagementService(store, meta)
            val result = kms.generateKey(Algorithm.P256) as GenerateKeyResult.Success
            assertEquals(1, store.size)
            assertEquals(Algorithm.P256, meta[result.keyHandle.id])
        }

    @Test
    fun `a non-concurrent map argument is copied and later changes are not seen by the caller`() =
        runBlocking<Unit> {
            val store = HashMap<KeyId, KeyPair>()
            val meta = HashMap<KeyId, Algorithm>()
            val kms = InMemoryKeyManagementService(store, meta)
            assertTrue(kms.generateKey(Algorithm.P256) is GenerateKeyResult.Success)
            assertTrue(store.isEmpty(), "documented: the caller's map is a snapshot, not a live view")
            assertTrue(meta.isEmpty())
        }
}

/** Registers BouncyCastle for tests that verify with it by name (the service itself no longer does). */
internal fun bcRegistered(): String {
    val name =
        org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME
    if (Security.getProvider(name) == null) {
        Security.addProvider(
            org.bouncycastle.jce.provider
                .BouncyCastleProvider(),
        )
    }
    return name
}
