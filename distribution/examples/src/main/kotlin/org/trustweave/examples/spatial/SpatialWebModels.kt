package org.trustweave.examples.spatial

import kotlinx.serialization.Serializable
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

@Serializable
data class SpatialDomain(
    val domainId: String,
    val domainDid: String,
    val authorityDid: String,
    val boundary: BoundingBox,
    val allowedActivities: List<String>,
    val description: String,
)

@Serializable
data class ActivityAuthorizationAnchor(
    val agentDid: String,
    val activityType: String,
    val domainDid: String,
    val domainId: String,
    val credentialDigest: String,
)

/** San Francisco Bay controlled airspace — matches the reference-wallet demo domain. */
fun demoSfBayAirspaceDomain(authorityDid: Did): SpatialDomain =
    SpatialDomain(
        domainId = "demo-sf-airspace",
        domainDid = authorityDid.value,
        authorityDid = authorityDid.value,
        boundary = BoundingBox(
            minLat = 37.5,
            maxLat = 38.0,
            minLon = -122.6,
            maxLon = -122.2,
        ),
        allowedActivities = listOf("data-collection", "monitoring", "inspection"),
        description = "San Francisco Bay Area controlled airspace (demo)",
    )

/**
 * Policy check: credential subject, activity, domain, geographic boundary, and allowed activities.
 * Cryptographic proof validity must be verified separately via [org.trustweave.trust.TrustWeave.verify].
 */
fun checkDomainAuthorization(
    agentDid: Did,
    activityType: String,
    domain: SpatialDomain,
    credential: VerifiableCredential,
    currentLocation: Pair<Double, Double>,
): Boolean {
    val subjectId = credential.credentialSubject.id?.value ?: return false
    if (subjectId != agentDid.value) return false

    val auth = credential.credentialSubject.claims["authorization"]?.jsonObject ?: return false
    if (auth.stringClaim("activityType") != activityType) return false
    if (auth.stringClaim("domainId") != domain.domainId) return false
    if (!domain.allowedActivities.contains(activityType)) return false

    val (lat, lon) = currentLocation
    val box = domain.boundary
    val inBoundary = lat >= box.minLat &&
        lat <= box.maxLat &&
        lon >= box.minLon &&
        lon <= box.maxLon
    if (!inBoundary) return false

    return true
}

private fun JsonObject.stringClaim(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull

private fun JsonElement?.stringClaim(key: String): String? =
    (this as? JsonObject)?.stringClaim(key)
