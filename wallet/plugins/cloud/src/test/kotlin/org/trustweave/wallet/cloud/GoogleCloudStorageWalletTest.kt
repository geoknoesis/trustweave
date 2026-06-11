package org.trustweave.wallet.cloud

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GoogleCloudStorageWallet.deleteFromStorage] error mapping
 * (P1 follow-up: ANY failure — including auth errors — was swallowed to `false`).
 *
 * The GCS [Storage] interface is far too wide to hand-roll a full fake, so a
 * [java.lang.reflect.Proxy] intercepts only the `delete` calls the wallet makes;
 * any other invocation fails the test.
 */
class GoogleCloudStorageWalletTest {

    private fun wallet(storage: Storage): GoogleCloudStorageWallet =
        GoogleCloudStorageWallet(
            walletId = "wallet-test",
            walletDid = "did:key:z6MkWallet",
            holderDid = "did:key:z6MkHolder",
            bucketName = "test-bucket",
            basePath = "wallets/wallet-test",
            storage = storage
        )

    /** [Storage] proxy that delegates every `delete` call to [onDelete]. */
    private fun storageWithDelete(onDelete: () -> Boolean): Storage =
        Proxy.newProxyInstance(
            Storage::class.java.classLoader,
            arrayOf(Storage::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "delete" -> onDelete()
                else -> throw UnsupportedOperationException("Unexpected Storage call: ${method.name}")
            }
        } as Storage

    @Test
    fun `delete returns true and cleans up sidecar objects when the blob exists`() {
        runBlocking {
            var deleteCalls = 0
            val wallet = wallet(
                storageWithDelete {
                    deleteCalls++
                    true
                }
            )

            assertTrue(wallet.delete("some-credential"))
            // credential + metadata + tags objects
            assertEquals(3, deleteCalls)
        }
    }

    @Test
    fun `delete maps a missing blob to false`() {
        runBlocking {
            var deleteCalls = 0
            val wallet = wallet(
                storageWithDelete {
                    deleteCalls++
                    false // Storage.delete returns false when the blob does not exist
                }
            )

            assertFalse(wallet.delete("missing-credential"))
            // No cleanup attempted when the credential object was not deleted
            assertEquals(1, deleteCalls)
        }
    }

    @Test
    fun `delete maps a 404 StorageException to false`() {
        runBlocking {
            val wallet = wallet(storageWithDelete { throw StorageException(404, "Not Found") })

            assertFalse(wallet.delete("missing-credential"))
        }
    }

    @Test
    fun `delete propagates auth failures instead of returning false`() {
        runBlocking {
            val wallet = wallet(storageWithDelete { throw StorageException(403, "Forbidden") })

            val exception = assertFailsWith<RuntimeException> {
                wallet.delete("some-credential")
            }
            assertTrue(exception.message!!.contains("Failed to delete from Google Cloud Storage"))
        }
    }

    @Test
    fun `delete propagates unexpected failures instead of returning false`() {
        runBlocking {
            val wallet = wallet(storageWithDelete { throw IllegalStateException("connection reset") })

            val exception = assertFailsWith<RuntimeException> {
                wallet.delete("some-credential")
            }
            assertTrue(exception.message!!.contains("Failed to delete from Google Cloud Storage"))
        }
    }
}
