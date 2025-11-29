package com.trustweave.testkit.annotations

import com.trustweave.testkit.extensions.PluginCredentialExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Annotation to mark tests that require specific plugins with their credentials.
 *
 * The test framework will automatically discover the plugin and check if its
 * required environment variables are available. If not, the test will be skipped.
 *
 * **Example:**
 * ```kotlin
 * @RequiresPlugin("aws")
 * @Test
 * fun `test AWS KMS`() = runBlocking {
 *     // Test will be skipped if AWS_REGION is not set
 * }
 *
 * @RequiresPlugin("google-cloud-kms", "ethereum")
 * @Test
 * fun `test with multiple plugins`() = runBlocking {
 *     // Test will be skipped if required env vars are not set
 * }
 * ```
 *
 * Plugin names should match the provider name:
 * - KMS providers: "aws", "azure", "google-cloud-kms", "hashicorp", etc.
 * - DID method providers: "ethr", "ion", "polygon", "key", "web", etc.
 * - Blockchain anchor providers: "ethereum", "algorand", "polygon", etc.
 *
 * The extension will automatically discover providers via ServiceLoader and check
 * their advertised required environment variables.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(PluginCredentialExtension::class)
annotation class RequiresPlugin(
    /**
     * List of plugin names that must have their required environment variables available.
     */
    vararg val plugins: String
)

