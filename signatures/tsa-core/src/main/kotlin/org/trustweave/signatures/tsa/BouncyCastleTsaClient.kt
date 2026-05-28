package org.trustweave.signatures.tsa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinInstant
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.util.Selector
import org.bouncycastle.util.Store
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.security.Security
import java.util.concurrent.TimeUnit
import org.bouncycastle.tsp.TimeStampToken as BcTimeStampToken

/**
 * Default [TsaClient] implementation backed by Bouncy Castle's `tsp` package and OkHttp.
 *
 * Builds an RFC 3161 `TimeStampReq` via [TimeStampRequestGenerator], posts it as
 * `application/timestamp-query`, parses the response into a [BcTimeStampToken], validates the
 * response against the original request (nonce, message imprint), optionally enforces a
 * policy OID, and optionally verifies the signer certificate against a pin-list.
 *
 * Thread-safety: instances are safe for concurrent use; OkHttp clients are shared and
 * Bouncy Castle's TSP types are immutable post-construction.
 *
 * The Bouncy Castle JCE provider is registered lazily on first use of this class.
 */
class BouncyCastleTsaClient(
    private val config: TsaConfig,
    private val httpClient: OkHttpClient = defaultHttpClient(config),
) : TsaClient {

    private val pinnedCerts: List<X509CertificateHolder> by lazy {
        config.trustedSignerCertificates.map { X509CertificateHolder(it) }
    }

    override suspend fun requestTimeStamp(
        digest: ByteArray,
        hashAlgorithm: TsaHashAlgorithm,
        nonce: ByteArray?,
    ): TimeStampToken = withContext(Dispatchers.IO) {
        require(digest.size == hashAlgorithm.digestSizeBytes) {
            "digest size ${digest.size} does not match ${hashAlgorithm.name}'s expected " +
                "${hashAlgorithm.digestSizeBytes} bytes"
        }

        val effectiveNonce = resolveNonce(nonce)
        val tsRequest = buildRequest(digest, hashAlgorithm, effectiveNonce)

        val requestBuilder = Request.Builder()
            .url(config.endpointUrl)
            .post(tsRequest.encoded.toRequestBody(TIMESTAMP_QUERY))
            .header("Content-Type", "application/timestamp-query")
            .header("Accept", "application/timestamp-reply")
        if (config.username != null && config.password != null) {
            requestBuilder.header(
                "Authorization",
                Credentials.basic(config.username, config.password),
            )
        }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (io: IOException) {
            throw TsaException("TSA request to ${config.endpointUrl} failed: ${io.message}", io)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw TsaException(
                    "TSA ${config.endpointUrl} returned HTTP ${resp.code} ${resp.message}",
                )
            }
            val responseBytes = resp.body?.bytes()
                ?: throw TsaException("TSA ${config.endpointUrl} returned empty body")

            val tsResponse = try {
                TimeStampResponse(responseBytes)
            } catch (t: Throwable) {
                throw TsaException(
                    "TSA ${config.endpointUrl}: response is not a valid TimeStampResponse",
                    t,
                )
            }

            try {
                tsResponse.validate(tsRequest)
            } catch (t: Throwable) {
                throw TsaException(
                    "TSA ${config.endpointUrl}: response failed validation against request " +
                        "(nonce/imprint/algorithm mismatch)",
                    t,
                )
            }

            val bcToken: BcTimeStampToken = tsResponse.timeStampToken
                ?: throw TsaException(
                    "TSA ${config.endpointUrl}: response carried no token. " +
                        "status=${tsResponse.status} statusString='${tsResponse.statusString ?: ""}' " +
                        "failInfo=${tsResponse.failInfo?.intValue() ?: -1}",
                )

            verifyPolicyIfRequired(bcToken)
            verifySignerCertificateIfPinned(bcToken)
            buildValueObject(bcToken, hashAlgorithm)
        }
    }

    private fun resolveNonce(nonce: ByteArray?): BigInteger? {
        if (nonce != null) {
            require(nonce.isNotEmpty()) { "nonce must be non-empty if provided" }
            return BigInteger(1, nonce)
        }
        if (!config.includeNonce) return null
        // RFC 3161 §2.4.1 recommends a 64-bit (or larger) random nonce.
        // Use 63 bits and force positive so BigInteger sign matches the ASN.1 INTEGER expectation.
        return BigInteger(63, SECURE_RANDOM).let { if (it.signum() == 0) BigInteger.ONE else it }
    }

    private fun buildRequest(
        digest: ByteArray,
        hashAlgorithm: TsaHashAlgorithm,
        nonce: BigInteger?,
    ) = TimeStampRequestGenerator().apply {
        setCertReq(true)
        config.expectedPolicyOid?.let { setReqPolicy(ASN1ObjectIdentifier(it)) }
    }.let { generator ->
        val oid = ASN1ObjectIdentifier(hashAlgorithm.oid)
        if (nonce != null) generator.generate(oid, digest, nonce) else generator.generate(oid, digest)
    }

    private fun verifyPolicyIfRequired(token: BcTimeStampToken) {
        val expected = config.expectedPolicyOid ?: return
        val actual = token.timeStampInfo.policy.id
        if (expected != actual) {
            throw TsaException(
                "TSA returned policy OID '$actual' but configuration required '$expected'",
            )
        }
    }

    private fun verifySignerCertificateIfPinned(token: BcTimeStampToken) {
        if (pinnedCerts.isEmpty()) return

        val signerId = token.sid
        val matched = pinnedCerts.firstOrNull { holder -> signerId.match(holder) }
            ?: throw TsaException(
                "TSA signer certificate (issuer=${signerId.issuer}, serial=${signerId.serialNumber}) " +
                    "did not match any entry in trustedSignerCertificates",
            )

        val verifier = JcaSimpleSignerInfoVerifierBuilder()
            .setProvider(BC_PROVIDER_NAME)
            .build(JCA_CERT_CONVERTER.getCertificate(matched))

        val signatureValid = try {
            token.isSignatureValid(verifier)
        } catch (t: Throwable) {
            throw TsaException("TSA token signature verification failed: ${t.message}", t)
        }
        if (!signatureValid) {
            throw TsaException("TSA token signature did not verify against the pinned certificate")
        }
    }

    private fun buildValueObject(
        token: BcTimeStampToken,
        hashAlgorithm: TsaHashAlgorithm,
    ): TimeStampToken {
        val info = token.timeStampInfo
        return TimeStampToken(
            encoded = token.encoded,
            genTime = info.genTime.toInstant().toKotlinInstant(),
            tsaSubject = extractTsaSubject(token),
            messageImprintAlgorithm = hashAlgorithm,
            messageImprint = info.messageImprintDigest,
            serialNumber = info.serialNumber.toByteArray(),
            policyOid = info.policy.id,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTsaSubject(token: BcTimeStampToken): String = runCatching {
        val store = token.certificates as Store<X509CertificateHolder>
        val selector = token.sid as Selector<X509CertificateHolder>
        store.getMatches(selector).firstOrNull()?.subject?.toString().orEmpty()
    }.getOrDefault("")

    companion object {
        private const val BC_PROVIDER_NAME = "BC"
        private val TIMESTAMP_QUERY = "application/timestamp-query".toMediaType()
        private val SECURE_RANDOM = SecureRandom()
        private val JCA_CERT_CONVERTER = JcaX509CertificateConverter().setProvider(BC_PROVIDER_NAME)

        init {
            if (Security.getProvider(BC_PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        fun defaultHttpClient(config: TsaConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
