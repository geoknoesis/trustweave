package org.trustweave.signatures.jades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.signatures.jades.internal.AlgorithmMapping
import org.trustweave.signatures.jades.internal.EcdsaSignatureConversion
import org.trustweave.signatures.tsa.BouncyCastleTsaClient
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.tsa.TsaHashAlgorithm
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json

/**
 * Builds a JAdES signature in JWS JSON Serialization (flattened) form.
 *
 * The signing key never leaves the configured [KeyManagementService] — for HSM-backed deployments
 * the key material can sit inside a QSCD (see `kms:plugins:pkcs11`) and only the signing input
 * crosses the boundary.
 */
interface JadesSigner {
    /**
     * Sign [payloadJson] under [request].
     *
     * @throws JadesSignerException on any failure: KMS unavailable, key not found, TSA failure,
     *         malformed cert chain, or unsupported algorithm.
     */
    suspend fun sign(payloadJson: JsonElement, request: JadesSigningRequest): JadesSignature
}

/** Thrown by [DefaultJadesSigner] on unrecoverable failures during signing. */
class JadesSignerException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Default [JadesSigner] implementation.
 *
 * @param kms              Key management service holding the signing key.
 * @param tsaClientFactory Optional factory the signer calls to obtain a [TsaClient] when the
 *                         signing request asks for [JadesProfile.B_T]. Defaults to
 *                         [BouncyCastleTsaClient]; override in tests for a stub.
 */
class DefaultJadesSigner(
    private val kms: KeyManagementService,
    private val tsaClientFactory: (org.trustweave.signatures.tsa.TsaConfig) -> TsaClient = ::BouncyCastleTsaClient,
) : JadesSigner {

    override suspend fun sign(
        payloadJson: JsonElement,
        request: JadesSigningRequest,
    ): JadesSignature = withContext(Dispatchers.IO) {
        val publicKeyHandle = when (val r = kms.getPublicKey(request.keyId)) {
            is org.trustweave.kms.results.GetPublicKeyResult.Success -> r.keyHandle
            else -> throw JadesSignerException("Cannot resolve public key for ${request.keyId}: $r")
        }
        val joseAlg = AlgorithmMapping.forAlgorithm(
            org.trustweave.kms.Algorithm.parse(publicKeyHandle.algorithm)
                ?: throw JadesSignerException("Unknown key algorithm '${publicKeyHandle.algorithm}'"),
        ) ?: throw JadesSignerException(
            "Algorithm '${publicKeyHandle.algorithm}' has no MVP JOSE mapping",
        )
        val mapping = AlgorithmMapping.forJoseAlg(joseAlg)
            ?: throw JadesSignerException("Internal: no mapping for JOSE alg $joseAlg")

        val signingTime = request.signingTime ?: Clock.System.now()
        val header = buildHeader(joseAlg, request, signingTime)
        val protectedHeaderJson = encodeHeader(header)
        val protectedHeaderB64u = base64Url(protectedHeaderJson.toByteArray(Charsets.UTF_8))
        val payloadB64u = base64Url(JSON.encodeToString(JsonElement.serializer(), payloadJson).toByteArray(Charsets.UTF_8))

        val signingInput = "$protectedHeaderB64u.$payloadB64u".toByteArray(Charsets.UTF_8)

        val rawSignatureBytes = when (val r = kms.sign(request.keyId, signingInput, mapping.algorithm)) {
            is SignResult.Success -> jcaToJws(r.signature, joseAlg, mapping)
            else -> throw JadesSignerException("KMS sign failed: $r")
        }
        val signatureB64u = base64Url(rawSignatureBytes)

        val unsigned = buildUnsignedProperties(request, rawSignatureBytes, signatureB64u)

        val serializedFlattened = serializeFlattened(
            protectedHeaderB64u = protectedHeaderB64u,
            payloadB64u = payloadB64u,
            signatureB64u = signatureB64u,
            unsigned = unsigned,
        )

        JadesSignature(
            protectedHeaderB64u = protectedHeaderB64u,
            payloadB64u = payloadB64u,
            signatureB64u = signatureB64u,
            unsigned = unsigned,
            serializedFlattened = serializedFlattened,
        )
    }

    // ---------------------------------------------------------------- header

    private fun buildHeader(
        joseAlg: String,
        request: JadesSigningRequest,
        signingTime: Instant,
    ): JadesHeader {
        val signerCert = request.signerCertificateChain.first()
        val x5tS256 = base64Url(MessageDigest.getInstance("SHA-256").digest(signerCert))
        // x5c per RFC 7515 §4.1.6: each entry is base64 (NOT base64URL) of DER cert bytes
        val x5c = request.signerCertificateChain.map { Base64.getEncoder().encodeToString(it) }
        return JadesHeader(
            alg = joseAlg,
            kid = request.keyId.value,
            typ = "JAdES",
            cty = request.contentType,
            sigT = signingTime.toString(),
            x5tS256 = x5tS256,
            x5c = x5c,
            additional = request.additionalHeaders,
        )
    }

    private fun encodeHeader(header: JadesHeader): String {
        // JSON property order is preserved in kotlinx.serialization buildJsonObject; the verifier
        // does NOT depend on a specific order because it reconstructs the header from the wire
        // bytes (which are themselves the canonical signing input).
        val obj = buildJsonObject {
            put("alg", JsonPrimitive(header.alg))
            header.kid?.let { put("kid", JsonPrimitive(it)) }
            header.typ?.let { put("typ", JsonPrimitive(it)) }
            header.cty?.let { put("cty", JsonPrimitive(it)) }
            header.crit?.let { crit ->
                put("crit", kotlinx.serialization.json.JsonArray(crit.map(::JsonPrimitive)))
            }
            put("sigT", JsonPrimitive(header.sigT))
            put("x5t#S256", JsonPrimitive(header.x5tS256))
            header.x5c?.let { chain ->
                put("x5c", kotlinx.serialization.json.JsonArray(chain.map(::JsonPrimitive)))
            }
            header.additional.forEach { (k, v) -> put(k, v) }
        }
        return JSON.encodeToString(JsonObject.serializer(), obj)
    }

    // ---------------------------------------------------------------- signature shape

    private fun jcaToJws(
        jcaSignatureBytes: ByteArray,
        joseAlg: String,
        mapping: AlgorithmMapping.Mapping,
    ): ByteArray {
        return if (joseAlg.startsWith("ES")) {
            // JCA returned DER; JWS expects raw R||S of fixed length.
            EcdsaSignatureConversion.derToRaw(jcaSignatureBytes, mapping.rawSize)
        } else {
            // Ed25519: JCA already returns raw 64-byte signature.
            jcaSignatureBytes
        }
    }

    // ---------------------------------------------------------------- unsigned properties

    /**
     * Build the etsiU container based on the requested profile.
     *
     * Profile gating is strict: B-B emits nothing, B-T emits sigTst, B-LT extends with
     * xVals+rVals, B-LTA extends with arcTst over the prior three.
     */
    private suspend fun buildUnsignedProperties(
        request: JadesSigningRequest,
        signatureBytes: ByteArray,
        signatureB64u: String,
    ): JadesUnsignedProperties {
        if (request.profile == JadesProfile.B_B) {
            return JadesUnsignedProperties()
        }
        val tsa = tsaClientFactory(
            request.tsaConfig
                ?: throw JadesSignerException(
                    "tsaConfig is required for JadesProfile.${request.profile.name}",
                ),
        )
        val sigTst = listOf(stampDigest(tsa, sha256(signatureBytes)))
        if (request.profile == JadesProfile.B_T) {
            return JadesUnsignedProperties(sigTst = sigTst)
        }
        // B-LT and B-LTA both require validationData (enforced in JadesSigningRequest.init).
        val vd = request.validationData!!
        val xVals = vd.completeCertificateChain.map { der ->
            EncodedCertificate(certB64 = Base64.getEncoder().encodeToString(der))
        }
        val rVals = vd.revocationData
        if (request.profile == JadesProfile.B_LT) {
            return JadesUnsignedProperties(sigTst = sigTst, xVals = xVals, rVals = rVals)
        }
        // B-LTA: archival timestamp covers signature + sigTst + xVals + rVals.
        val archivalImprint = sha256(buildArchivalDigestInput(signatureB64u, sigTst, xVals, rVals))
        val arcTst = listOf(stampDigest(tsa, archivalImprint))
        return JadesUnsignedProperties(
            sigTst = sigTst,
            xVals = xVals,
            rVals = rVals,
            arcTst = arcTst,
        )
    }

    private suspend fun stampDigest(tsa: TsaClient, digest: ByteArray): EncodedTimeStampToken {
        val token = try {
            tsa.requestTimeStamp(digest, TsaHashAlgorithm.SHA_256)
        } catch (t: Throwable) {
            throw JadesSignerException("TSA request failed: ${t.message}", t)
        }
        return EncodedTimeStampToken(
            tstTokensB64 = listOf(Base64.getEncoder().encodeToString(token.encoded)),
            canonAlg = null,
        )
    }

    /**
     * Canonical input bytes for the archival time-stamp (TS 119 182-1 §5.3.6, MVP profile):
     *
     * `signatureB64u || "."" || sigTst-tokens-b64 || "." || xVals-cert-b64s || "." || rVals-b64s`
     *
     * Concatenation with `.` separators avoids ambiguity between an empty section and a separator
     * byte. The format is internal — verifiers reconstruct it identically.
     */
    private fun buildArchivalDigestInput(
        signatureB64u: String,
        sigTst: List<EncodedTimeStampToken>,
        xVals: List<EncodedCertificate>,
        rVals: List<EncodedRevocationData>,
    ): ByteArray {
        val sb = StringBuilder().apply {
            append(signatureB64u).append('.')
            sigTst.forEach { it.tstTokensB64.forEach { tok -> append(tok).append(',') } }
            append('.')
            xVals.forEach { append(it.certB64).append(',') }
            append('.')
            rVals.forEach { append(it.type).append(':').append(it.dataB64).append(',') }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    // ---------------------------------------------------------------- JSON Flattened wire format

    private fun serializeFlattened(
        protectedHeaderB64u: String,
        payloadB64u: String,
        signatureB64u: String,
        unsigned: JadesUnsignedProperties,
    ): String {
        val obj = buildJsonObject {
            put("protected", JsonPrimitive(protectedHeaderB64u))
            put("payload", JsonPrimitive(payloadB64u))
            put("signature", JsonPrimitive(signatureB64u))
            if (unsigned.sigTst.isNotEmpty()) {
                put("header", buildEtsiU(unsigned))
            }
        }
        return JSON.encodeToString(JsonObject.serializer(), obj)
    }

    private fun buildEtsiU(unsigned: JadesUnsignedProperties): JsonObject = buildJsonObject {
        val etsiU = kotlinx.serialization.json.buildJsonArray {
            unsigned.sigTst.forEach { entry -> add(buildSigTstObject(entry)) }
            if (unsigned.xVals.isNotEmpty()) add(buildXValsObject(unsigned.xVals))
            if (unsigned.rVals.isNotEmpty()) add(buildRValsObject(unsigned.rVals))
            unsigned.arcTst.forEach { entry -> add(buildArcTstObject(entry)) }
        }
        put("etsiU", etsiU)
    }

    private fun buildSigTstObject(entry: EncodedTimeStampToken): JsonObject = buildJsonObject {
        put(
            "sigTst",
            buildJsonObject {
                put(
                    "tstTokens",
                    kotlinx.serialization.json.buildJsonArray {
                        entry.tstTokensB64.forEach { b64 ->
                            add(buildJsonObject { put("val", JsonPrimitive(b64)) })
                        }
                    },
                )
                entry.canonAlg?.let { put("canonAlg", JsonPrimitive(it)) }
            },
        )
    }

    private fun buildXValsObject(xVals: List<EncodedCertificate>): JsonObject = buildJsonObject {
        put(
            "xVals",
            kotlinx.serialization.json.buildJsonArray {
                xVals.forEach { x ->
                    add(buildJsonObject { put("x509Cert", JsonPrimitive(x.certB64)) })
                }
            },
        )
    }

    private fun buildRValsObject(rVals: List<EncodedRevocationData>): JsonObject = buildJsonObject {
        put(
            "rVals",
            kotlinx.serialization.json.buildJsonArray {
                rVals.forEach { r ->
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive(r.type))
                            put("val", JsonPrimitive(r.dataB64))
                            r.producedAt?.let { put("producedAt", JsonPrimitive(it)) }
                        },
                    )
                }
            },
        )
    }

    private fun buildArcTstObject(entry: EncodedTimeStampToken): JsonObject = buildJsonObject {
        put(
            "arcTst",
            buildJsonObject {
                put(
                    "tstTokens",
                    kotlinx.serialization.json.buildJsonArray {
                        entry.tstTokensB64.forEach { b64 ->
                            add(buildJsonObject { put("val", JsonPrimitive(b64)) })
                        }
                    },
                )
                entry.canonAlg?.let { put("canonAlg", JsonPrimitive(it)) }
            },
        )
    }

    // ---------------------------------------------------------------- companion

    companion object {
        private val JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            // No pretty-print: the protected-header bytes are the signing input, and any
            // re-formatting would invalidate the signature.
        }

        internal fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
