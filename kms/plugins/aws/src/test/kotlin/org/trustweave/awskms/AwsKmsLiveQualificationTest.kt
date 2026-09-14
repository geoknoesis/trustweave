package org.trustweave.awskms

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeys
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec
import software.amazon.awssdk.awscore.exception.AwsServiceException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Instant
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Read-only qualification against explicitly identified, pre-provisioned AWS KMS keys.
 *
 * The dedicated workflow supplies short-lived credentials through GitHub OIDC. This test
 * deliberately has no key lifecycle calls: the qualification role only needs DescribeKey,
 * GetPublicKey and Sign on the primary and replacement keys. The denied key must be covered by
 * an explicit IAM deny (or otherwise be outside the role's grants).
 *
 * ## What the evidence may claim
 *
 * Every boolean in the evidence file is derived from an assertion that ran in this method. None is
 * a literal. That distinction is the point of the file: a check written as a constant records an
 * intention, and a gate reading it back inherits the intention rather than verifying anything.
 *
 * The denial check is where this mattered most. Asserting only that signing with the denied key
 * returned some [SignResult.Failure] is satisfied by a mistyped ARN, a wrong region or a transient
 * network fault, none of which demonstrates that IAM refused anything. So it asserts the specific
 * shape instead: an AWS `AccessDeniedException`, and never `KeyNotFound` — which is exactly what
 * "denied" and "absent" would otherwise be unable to tell apart.
 */
@EnabledIfEnvironmentVariable(named = "TRUSTWEAVE_LIVE_CUSTODY", matches = "true")
class AwsKmsLiveQualificationTest {
    @Test
    fun `identified AWS KMS keys qualify signing denial restart and replacement recovery`() = runBlocking<Unit> { qualify() }

    private suspend fun qualify() {
        val startedAt = Instant.now()
        val checks = ObservedChecks()
        val region = requiredEnvironment("AWS_REGION")
        val primaryArn = requiredKeyArn("TRUSTWEAVE_AWS_KMS_PRIMARY_KEY_ARN", region)
        val replacementArn = requiredKeyArn("TRUSTWEAVE_AWS_KMS_REPLACEMENT_KEY_ARN", region)
        val deniedArn = requiredKeyArn("TRUSTWEAVE_AWS_KMS_DENIED_KEY_ARN", region)
        assertEquals(3, setOf(primaryArn, replacementArn, deniedArn).size, "qualification key ARNs must be distinct")

        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val alteredChallenge = challenge.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val config =
            AwsKmsConfig
                .builder()
                .region(region)
                .cacheTtlSeconds(30)
                .build()

        val primary =
            AwsKeyManagementService(config).use { kms ->
                val handle = assertIs<GetPublicKeyResult.Success>(kms.getPublicKey(KeyId(primaryArn))).keyHandle
                checks.record(
                    "providerIdentity",
                    handle.id.value == primaryArn && handle.algorithm == Algorithm.P256.name,
                    "provider must resolve the exact primary key ARN as a P-256 key",
                )
                val signature = assertIs<SignResult.Success>(kms.sign(handle.id, challenge, Algorithm.P256)).signature
                val publicKey = publicKey(requireNotNull(handle.publicKeyJwk))
                checks.record(
                    "independentSignatureVerification",
                    verify(publicKey, challenge, signature),
                    "primary signature must verify independently",
                )
                checks.record(
                    "alteredMessageRejected",
                    !verify(publicKey, alteredChallenge, signature),
                    "signature must reject an altered challenge",
                )
                QualifiedKey(handle.id.value, fingerprint(requireNotNull(handle.publicKeyJwk)))
            }

        val denied = AwsKeyManagementService(config).use { kms -> kms.sign(KeyId(deniedArn), challenge, Algorithm.P256) }
        // KeyNotFound is exactly what a mistyped ARN or a wrong region produces, so it can never
        // be evidence of a denial. The run has to show the provider reached AWS and was refused.
        assertFalse(
            denied is SignResult.Failure.KeyNotFound,
            "KeyNotFound means the denied ARN or region is wrong, not that IAM denied anything",
        )
        val denialError = assertIs<SignResult.Failure.Error>(denied, "unprivileged key access must fail closed")
        val denialErrorCode =
            (denialError.cause as? AwsServiceException)?.awsErrorDetails()?.errorCode() ?: NO_AWS_ERROR_CODE
        checks.record(
            "unauthorizedKeyRejected",
            denialErrorCode == ACCESS_DENIED,
            "signing the denied key must fail with $ACCESS_DENIED, not $denialErrorCode; " +
                "a transport or configuration fault does not demonstrate IAM enforcement",
        )

        val replacement =
            AwsKeyManagementService(config).use { kms ->
                val handle = assertIs<GetPublicKeyResult.Success>(kms.getPublicKey(KeyId(replacementArn))).keyHandle
                assertEquals(replacementArn, handle.id.value, "provider must resolve the exact replacement key ARN")
                assertEquals(Algorithm.P256.name, handle.algorithm, "qualification keys must be P-256")
                val signature = assertIs<SignResult.Success>(kms.sign(handle.id, challenge, Algorithm.P256)).signature
                assertTrue(verify(publicKey(requireNotNull(handle.publicKeyJwk)), challenge, signature))
                QualifiedKey(handle.id.value, fingerprint(requireNotNull(handle.publicKeyJwk)))
            }
        checks.record(
            "replacementKeyDistinct",
            primary.fingerprint != replacement.fingerprint,
            "replacement must use distinct key material",
        )

        // A new client models an application restart and proves that the old public binding
        // remains resolvable for historical verification after the active binding changes.
        AwsKeyManagementService(config).use { restarted ->
            val recovered = assertIs<GetPublicKeyResult.Success>(restarted.getPublicKey(KeyId(primaryArn))).keyHandle
            checks.record(
                "restartContinuity",
                recovered.id.value == primaryArn,
                "a restarted client must resolve the same primary key ARN",
            )
            checks.record(
                "historicalPublicKeyResolvable",
                primary.fingerprint == fingerprint(requireNotNull(recovered.publicKeyJwk)),
                "a restarted client must still resolve the historical public key",
            )
        }

        writeEvidence(
            startedAt = startedAt,
            region = region,
            primaryArn = primary.id,
            replacementArn = replacement.id,
            deniedArn = deniedArn,
            primaryFingerprint = primary.fingerprint,
            replacementFingerprint = replacement.fingerprint,
            denialType = denied::class.simpleName ?: "Failure",
            denialErrorCode = denialErrorCode,
            checks = checks,
        )
    }

    /**
     * The checks this run actually made, so the evidence reports observations rather than intent.
     *
     * [record] asserts and stores in one step: a check cannot reach the evidence without having
     * been asserted, and a check that failed stops the run before any evidence is written.
     */
    private class ObservedChecks {
        private val observed = LinkedHashMap<String, Boolean>()

        fun record(
            name: String,
            passed: Boolean,
            message: String,
        ) {
            observed[name] = passed
            assertTrue(passed, message)
        }

        /** Renders the recorded checks, refusing to emit a file that is missing any of them. */
        fun render(): String {
            val missing = REQUIRED_CHECKS - observed.keys
            check(missing.isEmpty()) { "the qualification did not observe: ${missing.sorted()}" }
            return observed.entries.joinToString(",\n") { (name, passed) -> "    \"$name\": $passed" }
        }
    }

    private fun requiredEnvironment(name: String): String =
        requireNotNull(System.getenv(name)?.takeIf(String::isNotBlank)) { "$name is required" }

    private fun requiredKeyArn(
        name: String,
        region: String,
    ): String {
        val value = requiredEnvironment(name)
        require(KEY_ARN.matches(value)) { "$name must be an immutable AWS KMS key ARN" }
        require(value.split(':')[3] == region) { "$name region must match AWS_REGION" }
        return value
    }

    private fun publicKey(jwk: Map<String, Any?>): PublicKey {
        require(jwk[JwkKeys.KTY] == "EC" && jwk[JwkKeys.CRV] == "P-256") { "expected an EC P-256 JWK" }
        val decoder = Base64.getUrlDecoder()
        val x = BigInteger(1, decoder.decode(jwk[JwkKeys.X] as String))
        val y = BigInteger(1, decoder.decode(jwk[JwkKeys.Y] as String))
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        val spec = parameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), spec))
    }

    private fun verify(
        key: PublicKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(message)
            verify(EcdsaSignatureCodec.p1363ToDer(signature))
        }

    private fun fingerprint(jwk: Map<String, Any?>): String {
        val canonical = "${jwk[JwkKeys.KTY]}|${jwk[JwkKeys.CRV]}|${jwk[JwkKeys.X]}|${jwk[JwkKeys.Y]}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun writeEvidence(
        startedAt: Instant,
        region: String,
        primaryArn: String,
        replacementArn: String,
        deniedArn: String,
        primaryFingerprint: String,
        replacementFingerprint: String,
        denialType: String,
        denialErrorCode: String,
        checks: ObservedChecks,
    ) {
        val output = Path.of(System.getProperty("trustweave.custody.evidenceDir", "build/qualification"))
        output.createDirectories()
        val commit = System.getenv("GITHUB_SHA") ?: "local"
        val runId = System.getenv("GITHUB_RUN_ID") ?: "local"
        val completedAt = Instant.now()
        Files.writeString(
            output.resolve("aws-kms-custody.json"),
            """{
  "schemaVersion": 1,
  "provider": "aws-kms",
  "commit": "${json(commit)}",
  "workflowRunId": "${json(runId)}",
  "region": "${json(region)}",
  "startedAt": "$startedAt",
  "completedAt": "$completedAt",
  "resources": {
    "primaryKeyArn": "${json(primaryArn)}",
    "replacementKeyArn": "${json(replacementArn)}",
    "deniedKeyArn": "${json(deniedArn)}"
  },
  "fingerprints": {
    "primarySha256": "$primaryFingerprint",
    "replacementSha256": "$replacementFingerprint"
  },
  "checks": {
${checks.render()}
  },
  "denialResultType": "${json(denialType)}",
  "denialErrorCode": "${json(denialErrorCode)}"
}
""",
        )
    }

    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private data class QualifiedKey(
        val id: String,
        val fingerprint: String,
    )

    companion object {
        private val KEY_ARN = Regex("^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[0-9a-fA-F-]{36}$")

        /** The AWS error code that means IAM refused the call, as opposed to anything else. */
        private const val ACCESS_DENIED = "AccessDeniedException"

        /** Recorded when the failure carried no AWS error code at all — a transport fault. */
        private const val NO_AWS_ERROR_CODE = "none"

        /** Every check the custody evidence gate requires; the run must observe all of them. */
        private val REQUIRED_CHECKS =
            setOf(
                "providerIdentity",
                "independentSignatureVerification",
                "alteredMessageRejected",
                "unauthorizedKeyRejected",
                "restartContinuity",
                "replacementKeyDistinct",
                "historicalPublicKeyResolvable",
            )
    }
}
