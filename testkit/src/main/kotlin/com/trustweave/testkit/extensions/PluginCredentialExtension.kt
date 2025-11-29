package com.trustweave.testkit.extensions

import com.trustweave.testkit.annotations.RequiresPlugin
import com.trustweave.testkit.config.TestConfig
import com.trustweave.kms.spi.KeyManagementServiceProvider
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader as JavaServiceLoader

/**
 * JUnit 5 extension that automatically checks if required plugins have their
 * environment variables available. If not, tests are skipped.
 *
 * This extension discovers providers via ServiceLoader and checks their
 * advertised required environment variables. Each plugin is self-describing
 * and declares what environment variables it needs.
 *
 * **How it works:**
 * 1. Test is annotated with `@RequiresPlugin("plugin-name")`
 * 2. Extension discovers all providers via ServiceLoader
 * 3. Finds the provider matching the plugin name
 * 4. Checks if provider's `hasRequiredEnvironmentVariables()` returns true
 * 5. If not, test is skipped with a descriptive message
 *
 * **Example:**
 * ```kotlin
 * @RequiresPlugin("aws")
 * @Test
 * fun `test AWS KMS`() {
 *     // Only runs if AWS_REGION is set
 * }
 * ```
 */
class PluginCredentialExtension : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val method = context.element.orElse(null) ?: return ConditionEvaluationResult.enabled("No method")

        val requiresPlugin = method.getAnnotation(RequiresPlugin::class.java)
            ?: context.testClass.map { it.getAnnotation(RequiresPlugin::class.java) }.orElse(null)

        if (requiresPlugin == null || requiresPlugin.plugins.isEmpty()) {
            return ConditionEvaluationResult.enabled("No @RequiresPlugin annotation")
        }

        // Discover all available providers (lazy loading)
        val kmsProviders = try {
            JavaServiceLoader.load(KeyManagementServiceProvider::class.java).associateBy { it.name }
        } catch (e: Exception) {
            emptyMap<String, KeyManagementServiceProvider>()
        }

        val didProviders = try {
            JavaServiceLoader.load(DidMethodProvider::class.java).associateBy { it.name }
        } catch (e: Exception) {
            emptyMap<String, DidMethodProvider>()
        }

        val chainProviders = try {
            JavaServiceLoader.load(BlockchainAnchorClientProvider::class.java).associateBy { it.name }
        } catch (e: Exception) {
            emptyMap<String, BlockchainAnchorClientProvider>()
        }

        val missingPlugins = mutableListOf<String>()

        requiresPlugin.plugins.forEach { pluginName ->
            // Try to find the plugin in any provider type
            val provider: Any? = kmsProviders[pluginName]
                ?: didProviders[pluginName]
                ?: chainProviders[pluginName]

            if (provider == null) {
                // Plugin not found - might not be on classpath
                if (TestConfig.skipIfNoCredentials()) {
                    missingPlugins.add("$pluginName (plugin not found on classpath)")
                } else {
                    return ConditionEvaluationResult.disabled(
                        "Plugin '$pluginName' not found on classpath. " +
                        "Add the plugin dependency or remove the @RequiresPlugin annotation."
                    )
                }
            } else {
                // Check if provider has required env vars
                val hasRequiredEnvVars = when (provider) {
                    is KeyManagementServiceProvider -> provider.hasRequiredEnvironmentVariables()
                    is DidMethodProvider -> provider.hasRequiredEnvironmentVariables()
                    is BlockchainAnchorClientProvider -> provider.hasRequiredEnvironmentVariables()
                    else -> false
                }

                if (!hasRequiredEnvVars) {
                    val requiredVars = when (provider) {
                        is KeyManagementServiceProvider -> {
                            val vars = provider.requiredEnvironmentVariables
                            if (vars.isEmpty()) "no env vars declared" else vars.joinToString(", ")
                        }
                        is DidMethodProvider -> {
                            val vars = provider.requiredEnvironmentVariables
                            if (vars.isEmpty()) "no env vars declared" else vars.joinToString(", ")
                        }
                        is BlockchainAnchorClientProvider -> {
                            val vars = provider.requiredEnvironmentVariables
                            if (vars.isEmpty()) "no env vars declared" else vars.joinToString(", ")
                        }
                        else -> "unknown"
                    }
                    missingPlugins.add("$pluginName (missing env vars: $requiredVars)")
                }
            }
        }

        return if (missingPlugins.isEmpty()) {
            ConditionEvaluationResult.enabled("All required plugins have their environment variables available")
        } else {
            val skipReason = if (TestConfig.skipIfNoCredentials()) {
                "SKIPPED: ${missingPlugins.joinToString("; ")}. " +
                "Set required environment variables or remove @RequiresPlugin annotation."
            } else {
                "DISABLED: ${missingPlugins.joinToString("; ")}. " +
                "Set VERICORE_TEST_SKIP_IF_NO_CREDENTIALS=false to fail instead of skip."
            }
            ConditionEvaluationResult.disabled(skipReason)
        }
    }
}

