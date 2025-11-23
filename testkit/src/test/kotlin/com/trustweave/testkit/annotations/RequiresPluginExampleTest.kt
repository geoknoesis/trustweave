package com.trustweave.testkit.annotations

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * Example test demonstrating the @RequiresPlugin annotation.
 * 
 * These tests will be automatically skipped if the required plugins
 * don't have their environment variables set.
 */
class RequiresPluginExampleTest {
    
    /**
     * Example: Test that requires AWS KMS plugin.
     * This test will be skipped if AWS_REGION is not set.
     */
    @Test
    @RequiresPlugin("aws")
    fun `example test requiring AWS KMS`() = runBlocking {
        // This test will only run if AWS_REGION is set
        // The test framework automatically checks AWS provider's requiredEnvironmentVariables
        assertNotNull(System.getenv("AWS_REGION") ?: System.getenv("AWS_DEFAULT_REGION"))
    }
    
    /**
     * Example: Test that requires Google Cloud KMS plugin.
     * This test will be skipped if GOOGLE_CLOUD_PROJECT is not set.
     */
    @Test
    @RequiresPlugin("google-cloud-kms")
    fun `example test requiring Google Cloud KMS`() = runBlocking {
        // This test will only run if GOOGLE_CLOUD_PROJECT or GCLOUD_PROJECT is set
        assertNotNull(
            System.getenv("GOOGLE_CLOUD_PROJECT") ?: 
            System.getenv("GCLOUD_PROJECT") ?:
            System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        )
    }
    
    /**
     * Example: Test that requires multiple plugins.
     * This test will be skipped if any of the required plugins don't have their env vars.
     */
    @Test
    @RequiresPlugin("aws", "azure")
    fun `example test requiring multiple plugins`() = runBlocking {
        // This test will only run if both AWS and Azure have their required env vars
        // If either is missing, the test is skipped
    }
}

