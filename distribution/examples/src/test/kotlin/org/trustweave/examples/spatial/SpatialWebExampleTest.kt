package org.trustweave.examples.spatial

import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the Spatial Web drone airspace authorization scenario.
 */
@Tag("spatial-web")
class SpatialWebExampleTest {

    @Test
    fun `drone receives authorization and passes domain policy check`() = runBlocking {
        val result = runSpatialWebDroneDemo(printSteps = false)

        assertTrue(result.verification is VerificationResult.Valid)
        assertTrue(result.identificationVerification is VerificationResult.Valid)
        assertTrue(result.authorizedInDomain)
        assertNotNull(result.credential.proof)
        assertNotNull(result.identificationCredential.proof)
        assertTrue(result.anchorTxHash.isNotBlank())
        assertTrue(result.droneWallet.getStatistics().totalCredentials >= 2)
        assertTrue(result.credentialId.isNotBlank())
    }

    @Test
    fun `drone outside domain boundary is denied`() = runBlocking {
        val result = runSpatialWebDroneDemo(printSteps = false)
        // Los Angeles — outside SF Bay bounding box
        val denied = checkDomainAuthorization(
            agentDid = result.droneDid,
            activityType = "data-collection",
            domain = result.domain,
            credential = result.credential,
            currentLocation = 34.0522 to -118.2437,
        )
        assertTrue(!denied)
    }

    @Test
    fun `wrong activity type is denied`() = runBlocking {
        val result = runSpatialWebDroneDemo(printSteps = false)
        val denied = checkDomainAuthorization(
            agentDid = result.droneDid,
            activityType = "transportation",
            domain = result.domain,
            credential = result.credential,
            currentLocation = 37.7749 to -122.4194,
        )
        assertTrue(!denied)
    }
}
