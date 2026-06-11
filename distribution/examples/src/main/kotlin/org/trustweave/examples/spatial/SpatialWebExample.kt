package org.trustweave.examples.spatial

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.ExampleContexts
import org.trustweave.testkit.services.TestkitWalletFactory
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.util.DigestUtils
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.wallet.Wallet
import kotlin.time.Duration.Companion.days

/**
 * Spatial Web Authorization — drone operations in controlled airspace.
 *
 * Demonstrates:
 * - Domain authority issues [ActivityAuthorizationCredential] to a drone agent DID
 * - Agent stores credential in wallet and presents to an airspace gatekeeper
 * - Gatekeeper verifies cryptographic proof plus geographic / activity policy
 * - Authorization digest anchored for audit trail
 *
 * Runnable counterpart of the reference-wallet demo at `/issuer/airspace`.
 */
fun main() = runBlocking {
    runSpatialWebDroneDemo(printSteps = true)
}

data class SpatialWebDroneDemoResult(
    val domainAuthorityDid: Did,
    val faaAuthorityDid: Did,
    val droneDid: Did,
    val operatorDid: Did,
    val domain: SpatialDomain,
    val identificationCredential: VerifiableCredential,
    val credential: VerifiableCredential,
    val anchorTxHash: String,
    val verification: VerificationResult,
    val identificationVerification: VerificationResult,
    val authorizedInDomain: Boolean,
    val droneWallet: Wallet,
    val credentialId: String,
)

suspend fun runSpatialWebDroneDemo(printSteps: Boolean = false): SpatialWebDroneDemoResult {
    fun step(msg: String) {
        if (printSteps) println(msg)
    }

    step("=== Spatial Web — Drone Airspace Authorization ===\n")
    step("Step 1: Configuring TrustWeave...")
    val kms = InMemoryKeyManagementService()
    val trustWeave = TrustWeave.build {
        factories(walletFactory = TestkitWalletFactory())
        keys { custom(kms) }
        did { method(KEY) {} }
        anchor { chain("algorand:testnet") { inMemory() } }
        credentials { defaultProofType(ProofType.Ed25519Signature2020) }
    }
    step("✓ TrustWeave configured")

    step("\nStep 2: Creating domain authority, FAA authority, and drone DIDs...")
    val (domainAuthorityDid, _) = trustWeave.createDidWithKey().getOrThrow()
    val (faaAuthorityDid, _) = trustWeave.createDidWithKey().getOrThrow()
    val droneDid = trustWeave.createDid().getOrThrowDid()
    val operatorDid = trustWeave.createDid().getOrThrowDid()
    step("Domain authority: ${domainAuthorityDid.value}")
    step("FAA authority: ${faaAuthorityDid.value}")
    step("Drone agent: ${droneDid.value}")
    step("Operator: ${operatorDid.value}")

    step("\nStep 3: FAA issues drone identification credential (with photo metadata)...")
    val identificationCredential = trustWeave.issue {
        additionalOption(ExampleContexts.OPTION_KEY, ExampleContexts.contexts)
        credential {
            id("https://demo-faa.trustweave.example/registry/${droneDid.value.substringAfterLast(":")}")
            type("VerifiableCredential", "DroneIdentificationCredential")
            issuer(faaAuthorityDid)
            subject {
                id(droneDid)
                "registrationNumber" to "FA3N8X2K91"
                "issuingAuthority" to "Federal Aviation Administration"
                "make" to "DJI"
                "model" to "Mavic 3 Enterprise"
                "serialNumber" to "SN-M3E-88421"
                "weightClass" to "Category 2"
                "callsign" to "SF-BAY-ALPHA"
                "dronePhotoUrl" to "https://demo-faa.trustweave.example/drones/DRONE-001.svg"
                "dronePhotoDigest" to "uE8demoPhotoDigestPlaceholderForDRONE001"
            }
            issued(Clock.System.now())
            expires(365.days)
        }
        signedBy(faaAuthorityDid)
    }.getOrThrow()
    step("✓ FAA identification issued (registration FA3N8X2K91, photo URL embedded)")

    step("\nStep 4: Defining controlled airspace domain...")
    val airspaceDomain = demoSfBayAirspaceDomain(domainAuthorityDid)
    step("Domain: ${airspaceDomain.domainId}")
    step("  Boundary: (${airspaceDomain.boundary.minLat}, ${airspaceDomain.boundary.minLon}) → " +
        "(${airspaceDomain.boundary.maxLat}, ${airspaceDomain.boundary.maxLon})")

    step("\nStep 5: Issuing activity authorization credential to drone...")
    val issuedCredential = trustWeave.issue {
        additionalOption(ExampleContexts.OPTION_KEY, ExampleContexts.contexts)
        credential {
            id("https://demo-sf-airspace.trustweave.example/authorizations/${droneDid.value.substringAfterLast(":")}")
            type("VerifiableCredential", "ActivityAuthorizationCredential", "SpatialWebCredential")
            issuer(domainAuthorityDid)
            subject {
                id(droneDid)
                "authorization" {
                    "agentDid" to droneDid.value
                    "activityType" to "data-collection"
                    "domainDid" to airspaceDomain.domainDid
                    "domainId" to airspaceDomain.domainId
                    "operatorDid" to operatorDid.value
                    "constraints" {
                        "maxAltitudeFt" to "400"
                        "maxDuration" to "PT2H"
                        "callsign" to "SF-BAY-ALPHA"
                    }
                }
            }
            issued(Clock.System.now())
            expires(30.days)
        }
        signedBy(domainAuthorityDid)
    }.getOrThrow()
    step("✓ Credential issued (proof present: ${issuedCredential.proof != null})")

    step("\nStep 6: Anchoring authorization digest...")
    val credentialJson = Json { ignoreUnknownKeys = true }
        .encodeToJsonElement(VerifiableCredential.serializer(), issuedCredential)
    val credentialDigest = DigestUtils.sha256DigestMultibase(credentialJson)
    val anchorPayload = buildJsonObject {
        put("agentDid", droneDid.value)
        put("activityType", "data-collection")
        put("domainId", airspaceDomain.domainId)
        put("credentialDigest", credentialDigest)
    }
    val anchorResult = trustWeave.blockchains.anchor(
        data = anchorPayload,
        serializer = JsonElement.serializer(),
        chainId = "algorand:testnet",
    )
    step("✓ Anchored: ${anchorResult.ref.txHash}")

    step("\nStep 7: Storing credentials in drone agent wallet...")
    val droneWallet = trustWeave.wallet {
        id("drone-wallet-${droneDid.value.substringAfterLast(":")}")
        holder(droneDid.value)
        enablePresentation()
    }.getOrThrow()
    droneWallet.store(identificationCredential)
    val credentialId = droneWallet.store(issuedCredential)
    step("✓ Stored FAA identification + airspace authorization")

    step("\nStep 8: Verifying credentials...")
    val identificationVerification = trustWeave.verify {
        credential(identificationCredential)
        checkExpiration()
    }
    when (identificationVerification) {
        is VerificationResult.Valid -> step("✅ FAA identification valid")
        is VerificationResult.Invalid -> step("❌ FAA identification failed: ${identificationVerification.allErrors.joinToString()}")
    }

    val verificationResult = trustWeave.verify {
        credential(issuedCredential)
        checkExpiration()
    }
    when (verificationResult) {
        is VerificationResult.Valid -> step("✅ Cryptographic verification passed")
        is VerificationResult.Invalid -> step("❌ Verification failed: ${verificationResult.allErrors.joinToString()}")
    }

    step("\nStep 9: Checking domain authorization at San Francisco coordinates...")
    val currentLocation = 37.7749 to -122.4194
    val authorized = checkDomainAuthorization(
        agentDid = droneDid,
        activityType = "data-collection",
        domain = airspaceDomain,
        credential = issuedCredential,
        currentLocation = currentLocation,
    )
    step(if (authorized) "✅ Drone authorized for data-collection in ${airspaceDomain.domainId}" else "❌ Not authorized")

    step("\nStep 10: Creating presentation for airspace gatekeeper...")
    when (
        val presentation = trustWeave.presentationResult {
            holder(droneDid)
            credentials(issuedCredential)
            challenge("airspace-gate-${Clock.System.now().epochSeconds}")
        }
    ) {
        is PresentationResult.Success -> {
            step("✓ Presentation created for holder ${presentation.presentation.holder}")
        }
        is PresentationResult.Failure -> {
            step("Presentation failed: ${presentation.errors.joinToString()}")
        }
    }

    step("\n=== Scenario Complete ===")
    return SpatialWebDroneDemoResult(
        domainAuthorityDid = domainAuthorityDid,
        faaAuthorityDid = faaAuthorityDid,
        droneDid = droneDid,
        operatorDid = operatorDid,
        domain = airspaceDomain,
        identificationCredential = identificationCredential,
        credential = issuedCredential,
        anchorTxHash = anchorResult.ref.txHash,
        verification = verificationResult,
        identificationVerification = identificationVerification,
        authorizedInDomain = authorized,
        droneWallet = droneWallet,
        credentialId = credentialId,
    )
}

/**
 * Build an activity authorization credential — used by tests and programmatic issuance.
 */
fun buildDroneAuthorizationCredential(
    issuerDid: Did,
    droneDid: Did,
    operatorDid: Did,
    domain: SpatialDomain,
    activityType: String = "data-collection",
    callsign: String = "SF-BAY-ALPHA",
): VerifiableCredential =
    credential {
        id("https://demo-sf-airspace.trustweave.example/authorizations/${droneDid.value.substringAfterLast(":")}")
        type("VerifiableCredential", "ActivityAuthorizationCredential", "SpatialWebCredential")
        issuer(issuerDid)
        subject {
            id(droneDid)
            "authorization" {
                "agentDid" to droneDid.value
                "activityType" to activityType
                "domainDid" to domain.domainDid
                "domainId" to domain.domainId
                "operatorDid" to operatorDid.value
                "constraints" {
                    "maxAltitudeFt" to "400"
                    "maxDuration" to "PT2H"
                    "callsign" to callsign
                }
            }
        }
        issued(Clock.System.now())
        expires(30.days)
    }
