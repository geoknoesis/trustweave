# Wallet configuration rejection

This complete test checks deployment maturity, unsafe paths and invalid/conflicting encryption keys before accepting configuration. It demonstrates rejected configurations, not a production endorsement of the file wallet. Run `:wallet:plugins:file:test --tests "*FileWalletFactoryTest"`.

The source block is an exact copy of a compiled SDK file. Documentation CI checks synchronization, and required test gates check execution.

<!-- example-source: wallet/plugins/file/src/test/kotlin/org/trustweave/wallet/file/FileWalletFactoryTest.kt -->
```kotlin
package org.trustweave.wallet.file

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import org.trustweave.wallet.exception.WalletException
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletDeploymentPolicy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileWalletFactoryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `production policy rejects before storage creation`() =
        runBlocking {
            val path = directory.resolve("not-created")
            val error =
                assertFailsWith<IllegalArgumentException> {
                    FileWalletFactory().create(
                        "file",
                        holderDid = "did:key:holder",
                        options =
                            WalletCreationOptions(
                                storagePath = path.toString(),
                                deploymentPolicy = WalletDeploymentPolicy.SUPPORTED_ONLY,
                            ),
                    )
                }
            assertTrue(error.message!!.contains("deployment maturity policy"))
            assertFalse(Files.exists(path))
        }

    @Test
    fun `unsafe wallet ids cannot escape or reuse the storage root`() =
        runBlocking {
            for (id in listOf("..", ".", "../outside", "a/b", "a\\b", "C:\\outside", "")) {
                assertFailsWith<IllegalArgumentException> {
                    FileWalletFactory().create(
                        "file",
                        walletId = id,
                        holderDid = "did:key:holder",
                        options = WalletCreationOptions(storagePath = directory.resolve("root").toString()),
                    )
                }
            }
            assertFalse(Files.exists(directory.resolve("root")))
        }

    @Test
    fun `typed encryption key is validated rather than silently ignored`(): Unit =
        runBlocking {
            assertFailsWith<WalletException.WalletCreationFailed> {
                FileWalletFactory().create(
                    "file",
                    holderDid = "did:key:holder",
                    options =
                        WalletCreationOptions(
                            storagePath = directory.toString(),
                            encryptionKey = "invalid-key",
                        ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                FileWalletFactory().create(
                    "file",
                    holderDid = "did:key:holder",
                    options =
                        WalletCreationOptions(
                            storagePath = directory.toString(),
                            encryptionKey = "one",
                            additionalProperties = mapOf("encryptionKey" to "two"),
                        ),
                )
            }
        }
}
```

[All verified examples](README.md) ? [Testing acceptance](../../contributing/testing/acceptance.md)
