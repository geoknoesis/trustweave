package org.trustweave.signatures.jades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.signatures.jades.JadesValidationResult.Invalid
import org.trustweave.signatures.jades.internal.AlgorithmMapping
import org.trustweave.signatures.jades.internal.EcdsaSignatureConversion
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds

/** Verifier for the JAdES B-B and B-T profiles. Pure: never makes network calls. */
interface JadesVerifier {
    suspend fun verify(jadesSerialized: String, options: JadesVerificationOptions): JadesValidationResult
}

/** Default [JadesVerifier] implementation. */
class DefaultJadesVerifier : JadesVerifier {

    override suspend fun verify(
        jadesSerialized: String,
        options: JadesVerificationOptions,
    ): JadesValidationResult = withContext(Dispatchers.IO) {
        // 1. Parse the wire bytes.
        val parsed = try {
            parseFlattenedOrCompact(jadesSerialized)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed(t.message ?: "input is not valid JAdES")
        }

        // 2. Decode the protected header and reconstruct the JadesHeader value object.
        val protectedHeaderBytes = try {
            Base64.getUrlDecoder().decode(parsed.protectedB64u)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("protected header is not base64url: ${t.message}")
        }
        val headerJson = try {
            Json.parseToJsonElement(String(protectedHeaderBytes, Charsets.UTF_8)).jsonObject
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("protected header is not valid JSON: ${t.message}")
        }
        val header = try {
            decodeHeader(headerJson)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed(t.message ?: "protected header is missing required JAdES params")
        }

        // 3. Profile + algorithm validation.
        if (header.alg !in options.acceptedAlgorithms) {
            return@withContext Invalid.BadSignature("alg '${header.alg}' is not in acceptedAlgorithms")
        }
        val mapping = AlgorithmMapping.forJoseAlg(header.alg)
            ?: return@withContext Invalid.BadSignature("alg '${header.alg}' has no MVP mapping")

        val foundProfile = inferFoundProfile(parsed.unsigned)
        if (!foundProfile.atLeast(options.requiredProfile)) {
            return@withContext Invalid.WrongProfile(found = foundProfile, required = options.requiredProfile)
        }

        // 4. Decode signer cert chain.
        val chainDer = header.x5c?.map { Base64.getDecoder().decode(it) }
            ?: return@withContext Invalid.Malformed("protected header lacks x5c chain")
        val chain = try {
            chainDer.map { decodeCertificate(it) }
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("x5c contains an invalid certificate: ${t.message}")
        }
        val signerCert = chain.first()

        // 5. Signature-bytes validation (mathematical).
        val signatureRaw = try {
            Base64.getUrlDecoder().decode(parsed.signatureB64u)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("signature is not base64url: ${t.message}")
        }
        val signingInput = "${parsed.protectedB64u}.${parsed.payloadB64u}".toByteArray(Charsets.UTF_8)
        val signatureValid = verifyMath(
            signature = signatureRaw,
            signingInput = signingInput,
            signerCert = signerCert,
            joseAlg = header.alg,
            rawSize = mapping.rawSize,
        )
        if (!signatureValid) {
            return@withContext Invalid.BadSignature("cryptographic verification failed")
        }

        // 6. Signing-time / cert-validity check.
        val signingTime = try {
            Instant.parse(header.sigT)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("sigT is not a valid ISO 8601 instant: ${header.sigT}")
        }
        if (!options.allowExpiredCertificateAtSigningTime) {
            val notAfter = signerCert.notAfter.toInstant().toKotlinInstant()
            val notBefore = signerCert.notBefore.toInstant().toKotlinInstant()
            val skew = options.maxClockSkew.inWholeMilliseconds.milliseconds
            if (signingTime > notAfter + skew) {
                return@withContext Invalid.CertificateExpired(notAfter)
            }
            if (signingTime + skew < notBefore) {
                return@withContext Invalid.CertificateExpired(notAfter)
            }
        }

        // 7. Trust-anchor resolution.
        val trustMatch = options.trustAnchorResolver.resolve(signerCert, chain.drop(1))
        if (trustMatch is org.trustweave.signatures.trustlists.TrustAnchorMatch.NotTrusted) {
            return@withContext Invalid.UntrustedSigner(signerCert)
        }

        // 8. Signature time-stamp validation (B-T and above).
        var sigTstInstant: Instant? = null
        if (foundProfile.atLeast(JadesProfile.B_T)) {
            val tokenResult = validateSigTst(
                sigTsts = parsed.unsigned.sigTst,
                signatureBytes = signatureRaw,
                signingTime = signingTime,
                maxClockSkew = options.maxClockSkew,
            )
            when (tokenResult) {
                is SigTstResult.Ok -> sigTstInstant = tokenResult.genTime
                is SigTstResult.Missing -> return@withContext Invalid.MissingTimeStamp(tokenResult.reason)
                is SigTstResult.Mismatch -> return@withContext Invalid.TimeStampMismatch(tokenResult.reason)
            }
        }

        // 8b. Long-term-validation data (B-LT and above): structural check that the embedded
        // certificates parse as X.509 and that revocation entries carry a recognised type.
        if (foundProfile.atLeast(JadesProfile.B_LT)) {
            val ltCheck = validateLongTermData(parsed.unsigned)
            if (ltCheck != null) return@withContext ltCheck
        }

        // 8c. Archival time-stamp (B-LTA only): re-derive the canonical archival imprint and
        // confirm it matches the embedded `arcTst` token's message-imprint.
        var arcTstInstant: Instant? = null
        if (foundProfile == JadesProfile.B_LTA) {
            val arcResult = validateArcTst(
                arcTsts = parsed.unsigned.arcTst,
                signatureB64u = parsed.signatureB64u,
                sigTst = parsed.unsigned.sigTst,
                xVals = parsed.unsigned.xVals,
                rVals = parsed.unsigned.rVals,
            )
            when (arcResult) {
                is SigTstResult.Ok -> arcTstInstant = arcResult.genTime
                is SigTstResult.Missing -> return@withContext Invalid.MissingTimeStamp("arcTst: ${arcResult.reason}")
                is SigTstResult.Mismatch -> return@withContext Invalid.TimeStampMismatch("arcTst: ${arcResult.reason}")
            }
        }

        // 9. Decode payload + return Valid.
        val payloadBytes = try {
            Base64.getUrlDecoder().decode(parsed.payloadB64u)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("payload is not base64url")
        }
        val payloadJson = try {
            Json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8))
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("payload is not valid JSON: ${t.message}")
        }

        JadesValidationResult.Valid(
            header = header,
            payload = payloadJson,
            trust = trustMatch,
            signingTime = signingTime,
            signatureTimeStamp = sigTstInstant,
            foundProfile = foundProfile,
            xValsCount = parsed.unsigned.xVals.size,
            rValsCount = parsed.unsigned.rVals.size,
            archivalTimeStamp = arcTstInstant,
        )
    }

    // ---------------------------------------------------------------- profile inference

    private fun inferFoundProfile(unsigned: JadesUnsignedProperties): JadesProfile {
        if (unsigned.arcTst.isNotEmpty()) return JadesProfile.B_LTA
        if (unsigned.xVals.isNotEmpty() || unsigned.rVals.isNotEmpty()) return JadesProfile.B_LT
        if (unsigned.sigTst.isNotEmpty()) return JadesProfile.B_T
        return JadesProfile.B_B
    }

    // ---------------------------------------------------------------- parsing

    private data class Parsed(
        val protectedB64u: String,
        val payloadB64u: String,
        val signatureB64u: String,
        val unsigned: JadesUnsignedProperties,
    )

    private fun parseFlattenedOrCompact(serialized: String): Parsed {
        val trimmed = serialized.trim()
        return if (trimmed.startsWith("{")) parseFlattened(trimmed) else parseCompact(trimmed)
    }

    private fun parseFlattened(serialized: String): Parsed {
        val obj = Json.parseToJsonElement(serialized).jsonObject
        val protectedB64u = obj["protected"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'protected'")
        val payloadB64u = obj["payload"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'payload'")
        val signatureB64u = obj["signature"]?.jsonPrimitive?.content
            ?: error("JAdES JSON Flattened: missing 'signature'")
        val unsigned = parseUnsigned(obj["header"] as? JsonObject)
        return Parsed(protectedB64u, payloadB64u, signatureB64u, unsigned)
    }

    private fun parseCompact(serialized: String): Parsed {
        val parts = serialized.split('.')
        require(parts.size == 3) { "JWS Compact serialization must have three parts" }
        return Parsed(
            protectedB64u = parts[0],
            payloadB64u = parts[1],
            signatureB64u = parts[2],
            unsigned = JadesUnsignedProperties(),
        )
    }

    private fun parseUnsigned(headerObj: JsonObject?): JadesUnsignedProperties {
        val etsiU = headerObj?.get("etsiU") as? JsonArray ?: return JadesUnsignedProperties()
        val sigTsts = mutableListOf<EncodedTimeStampToken>()
        val arcTsts = mutableListOf<EncodedTimeStampToken>()
        val xVals = mutableListOf<EncodedCertificate>()
        val rVals = mutableListOf<EncodedRevocationData>()
        etsiU.forEach { entry ->
            val obj = entry as? JsonObject ?: return@forEach
            parseTimeStampEntry(obj["sigTst"] as? JsonObject)?.let(sigTsts::add)
            parseTimeStampEntry(obj["arcTst"] as? JsonObject)?.let(arcTsts::add)
            (obj["xVals"] as? JsonArray)?.forEach { x ->
                val cert = (x as? JsonObject)?.get("x509Cert")?.jsonPrimitive?.content
                if (cert != null) xVals += EncodedCertificate(certB64 = cert)
            }
            (obj["rVals"] as? JsonArray)?.forEach { r ->
                val rObj = r as? JsonObject ?: return@forEach
                val type = rObj["type"]?.jsonPrimitive?.content ?: return@forEach
                val data = rObj["val"]?.jsonPrimitive?.content ?: return@forEach
                val producedAt = rObj["producedAt"]?.jsonPrimitive?.content
                rVals += EncodedRevocationData(type = type, dataB64 = data, producedAt = producedAt)
            }
        }
        return JadesUnsignedProperties(
            sigTst = sigTsts,
            xVals = xVals,
            rVals = rVals,
            arcTst = arcTsts,
        )
    }

    private fun parseTimeStampEntry(obj: JsonObject?): EncodedTimeStampToken? {
        if (obj == null) return null
        val tokens = (obj["tstTokens"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("val")?.jsonPrimitive?.content }
            ?: return null
        val canonAlg = obj["canonAlg"]?.jsonPrimitive?.content
        return EncodedTimeStampToken(tstTokensB64 = tokens, canonAlg = canonAlg)
    }

    // ---------------------------------------------------------------- header decode

    private fun decodeHeader(headerJson: JsonObject): JadesHeader {
        val alg = headerJson["alg"]?.jsonPrimitive?.content
            ?: error("protected header missing 'alg'")
        val sigT = headerJson["sigT"]?.jsonPrimitive?.content
            ?: error("protected header missing 'sigT'")
        val x5tS256 = headerJson["x5t#S256"]?.jsonPrimitive?.content
            ?: error("protected header missing 'x5t#S256'")
        val kid = headerJson["kid"]?.jsonPrimitive?.content
        val typ = headerJson["typ"]?.jsonPrimitive?.content
        val cty = headerJson["cty"]?.jsonPrimitive?.content
        val crit = (headerJson["crit"] as? JsonArray)?.map { it.jsonPrimitive.content }
        val x5c = (headerJson["x5c"] as? JsonArray)?.map { it.jsonPrimitive.content }

        val handled = setOf("alg", "sigT", "x5t#S256", "kid", "typ", "cty", "crit", "x5c")
        val additional = headerJson.filterKeys { it !in handled }
        return JadesHeader(
            alg = alg,
            kid = kid,
            typ = typ,
            cty = cty,
            crit = crit,
            sigT = sigT,
            x5tS256 = x5tS256,
            x5c = x5c,
            additional = additional,
        )
    }

    // ---------------------------------------------------------------- signature math

    private fun verifyMath(
        signature: ByteArray,
        signingInput: ByteArray,
        signerCert: X509Certificate,
        joseAlg: String,
        rawSize: Int,
    ): Boolean {
        val mapping = AlgorithmMapping.forJoseAlg(joseAlg) ?: return false
        val jcaSignature = if (joseAlg.startsWith("ES")) {
            try {
                EcdsaSignatureConversion.rawToDer(signature, rawSize)
            } catch (t: Throwable) {
                return false
            }
        } else {
            signature
        }
        return try {
            val verifier = Signature.getInstance(mapping.jcaScheme)
            verifier.initVerify(signerCert.publicKey)
            verifier.update(signingInput)
            verifier.verify(jcaSignature)
        } catch (t: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- sigTst validation

    private sealed class SigTstResult {
        data class Ok(val genTime: Instant) : SigTstResult()
        data class Missing(val reason: String) : SigTstResult()
        data class Mismatch(val reason: String) : SigTstResult()
    }

    private fun validateSigTst(
        sigTsts: List<EncodedTimeStampToken>,
        signatureBytes: ByteArray,
        signingTime: Instant,
        maxClockSkew: kotlin.time.Duration,
    ): SigTstResult {
        val firstEntry = sigTsts.firstOrNull()
            ?: return SigTstResult.Missing("etsiU.sigTst array was empty")
        val firstTokenB64 = firstEntry.tstTokensB64.firstOrNull()
            ?: return SigTstResult.Missing("sigTst.tstTokens array was empty")
        val tokenBytes = try {
            Base64.getDecoder().decode(firstTokenB64)
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("sigTst token is not valid base64: ${t.message}")
        }
        val bcToken = try {
            org.bouncycastle.tsp.TimeStampToken(
                org.bouncycastle.cms.CMSSignedData(tokenBytes),
            )
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("sigTst token is not a valid CMS SignedData: ${t.message}")
        }
        val expectedImprint = MessageDigest.getInstance("SHA-256").digest(signatureBytes)
        val actualImprint = bcToken.timeStampInfo.messageImprintDigest
        if (!expectedImprint.contentEquals(actualImprint)) {
            return SigTstResult.Mismatch("sigTst messageImprint does not match SHA-256(signature)")
        }
        val tsaGenTime = bcToken.timeStampInfo.genTime.toInstant().toKotlinInstant()
        // Soft sanity-check: TSA gen-time should be at or near the claimed sigT.
        val deltaMs = kotlin.math.abs(tsaGenTime.toEpochMilliseconds() - signingTime.toEpochMilliseconds())
        if (deltaMs > maxClockSkew.inWholeMilliseconds) {
            return SigTstResult.Mismatch(
                "TSA genTime ($tsaGenTime) is more than ${maxClockSkew} away from sigT ($signingTime)",
            )
        }
        return SigTstResult.Ok(tsaGenTime)
    }

    // ---------------------------------------------------------------- long-term-validation data

    /**
     * Structural validation of B-LT material:
     * - every xVals entry must parse as a valid X.509 cert,
     * - every rVals entry must declare a recognised type (`CRL` or `OCSP`) and parse as base64.
     *
     * Returns null when the LT data is well-formed; an [Invalid.Malformed] when not. The
     * cryptographic checks (CRL signature, OCSP signer chain) are intentionally deferred to the
     * full ETSI EN 319 102-1 validation pipeline (`signatures:etsi-validation`).
     */
    private fun validateLongTermData(unsigned: JadesUnsignedProperties): JadesValidationResult.Invalid? {
        unsigned.xVals.forEachIndexed { idx, x ->
            val bytes = try {
                Base64.getDecoder().decode(x.certB64)
            } catch (t: Throwable) {
                return Invalid.Malformed("xVals[$idx] is not valid base64: ${t.message}")
            }
            try {
                decodeCertificate(bytes)
            } catch (t: Throwable) {
                return Invalid.Malformed("xVals[$idx] is not a valid X.509 certificate: ${t.message}")
            }
        }
        unsigned.rVals.forEachIndexed { idx, r ->
            if (r.type !in RECOGNISED_REVOCATION_TYPES) {
                return Invalid.Malformed("rVals[$idx] has unknown type '${r.type}'; expected one of $RECOGNISED_REVOCATION_TYPES")
            }
            try {
                Base64.getDecoder().decode(r.dataB64)
            } catch (t: Throwable) {
                return Invalid.Malformed("rVals[$idx] is not valid base64: ${t.message}")
            }
        }
        return null
    }

    // ---------------------------------------------------------------- arcTst validation

    private fun validateArcTst(
        arcTsts: List<EncodedTimeStampToken>,
        signatureB64u: String,
        sigTst: List<EncodedTimeStampToken>,
        xVals: List<EncodedCertificate>,
        rVals: List<EncodedRevocationData>,
    ): SigTstResult {
        val firstEntry = arcTsts.firstOrNull()
            ?: return SigTstResult.Missing("etsiU contains no arcTst entry")
        val firstTokenB64 = firstEntry.tstTokensB64.firstOrNull()
            ?: return SigTstResult.Missing("arcTst.tstTokens array was empty")
        val tokenBytes = try {
            Base64.getDecoder().decode(firstTokenB64)
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("arcTst token is not valid base64: ${t.message}")
        }
        val bcToken = try {
            org.bouncycastle.tsp.TimeStampToken(
                org.bouncycastle.cms.CMSSignedData(tokenBytes),
            )
        } catch (t: Throwable) {
            return SigTstResult.Mismatch("arcTst token is not a valid CMS SignedData: ${t.message}")
        }

        // Reconstruct the canonical archival imprint input the signer used.
        val sb = StringBuilder().apply {
            append(signatureB64u).append('.')
            sigTst.forEach { it.tstTokensB64.forEach { tok -> append(tok).append(',') } }
            append('.')
            xVals.forEach { append(it.certB64).append(',') }
            append('.')
            rVals.forEach { append(it.type).append(':').append(it.dataB64).append(',') }
        }
        val expectedImprint = MessageDigest.getInstance("SHA-256")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        val actualImprint = bcToken.timeStampInfo.messageImprintDigest
        if (!expectedImprint.contentEquals(actualImprint)) {
            return SigTstResult.Mismatch("arcTst messageImprint does not match SHA-256(signature || sigTst || xVals || rVals)")
        }
        val genTime = bcToken.timeStampInfo.genTime.toInstant().toKotlinInstant()
        return SigTstResult.Ok(genTime)
    }

    // ---------------------------------------------------------------- cert helpers

    private fun decodeCertificate(der: ByteArray): X509Certificate =
        CERT_FACTORY.generateCertificate(ByteArrayInputStream(der)) as X509Certificate

    companion object {
        private val CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")
        private val RECOGNISED_REVOCATION_TYPES = setOf("CRL", "OCSP")
    }
}
