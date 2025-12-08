package com.trustweave.testkit

import com.trustweave.did.model.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Base class for all plugin unit tests.
 *
 * Provides common setup, test data factories, and cleanup utilities.
 *
 * **Example Usage**:
 * ```kotlin
 * class MyPluginTest : BasePluginTest() {
 *     @Test
 *     fun testSomething() = runBlocking {
 *         val fixture = createFixture()
 *         // Test code using fixture
 *     }
 * }
 * ```
 */
abstract class BasePluginTest {

    /**
     * The test fixture instance. Created fresh for each test.
     */
    protected lateinit var fixture: TrustWeaveTestFixture

    /**
     * Creates a minimal test fixture with defaults.
     * Override this method to customize fixture setup.
     */
    protected open fun createFixture(): TrustWeaveTestFixture {
        return TrustWeaveTestFixture.minimal()
    }

    /**
     * Creates a test fixture with custom configuration.
     */
    protected fun createFixture(block: TrustWeaveTestFixture.Builder.() -> Unit): TrustWeaveTestFixture {
        return TrustWeaveTestFixture.builder().apply(block).build()
    }

    /**
     * Sets up test environment before each test.
     * Creates a fresh fixture instance.
     */
    @BeforeEach
    fun setUp() {
        fixture = createFixture()
    }

    /**
     * Cleans up test environment after each test.
     * Closes the fixture to clean up registries.
     */
    @AfterEach
    fun tearDown() {
        if (::fixture.isInitialized) {
            fixture.close()
        }
    }

    /**
     * Gets the KMS from the fixture.
     */
    protected open fun getKms(): KeyManagementService = fixture.getKms()

    /**
     * Gets the DID method from the fixture.
     */
    protected open fun getDidMethod(): DidMethod = fixture.getDidMethod()

    /**
     * Creates a test DID document.
     */
    protected suspend fun createTestDid(algorithm: String = "Ed25519"): DidDocument {
        return fixture.createIssuerDid(algorithm)
    }

    /**
     * Creates a test KMS instance.
     */
    protected fun createTestKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }

    /**
     * Helper to run a test with a custom fixture that's automatically cleaned up.
     */
    protected inline fun <T> withFixture(
        noinline block: TrustWeaveTestFixture.Builder.() -> Unit,
        crossinline test: suspend (TrustWeaveTestFixture) -> T
    ): T = runBlocking {
        createFixture(block).use { customFixture ->
            test(customFixture)
        }
    }
}

