package org.trustweave.signatures.tsa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.util.Selector
import org.bouncycastle.util.Store
import org.trustweave.core.net.TransportSecurity
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.toKotlinInstant
import org.bouncycastle.tsp.TimeStampToken as BcTimeStampToken

/**
 * Default [TsaClient] implementation backed by Bouncy Castle's `tsp` package and OkHttp.
 *
 * Builds an RFC 3161 `TimeStampReq` via [TimeStampRequestGenerator], posts it as
 * `application/timestamp-query`, parses the response into a [BcTimeStampToken], validates the
 * response against the original request (nonce, message imprint), optionally enforces a
 * policy OID, and always verifies the token's CMS signature. The signer certificate must be pinned
 * ([TsaConfig.trustedSignerCertificates]) and/or chain to a [TsaConfig.trustAnchors] entry; with
 * neither configured every token is rejected unless [TsaConfig.allowUnanchored] is set.
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

    private val anchorCerts: List<X509Certificate> by lazy {
        config.trustAnchors.map { JCA_CERT_CONVERTER.getCertificate(X509CertificateHolder(it)) }
    }

    override suspend fun requestTimeStamp(
        digest: ByteArray,
        hashAlgorithm: TsaHashAlgorithm,
        nonce: ByteArray?,
    ): TimeStampToken =
        withContext(Dispatchers.IO) {
            require(digest.size == hashAlgorithm.digestSizeBytes) {
                "digest size ${digest.size} does not match ${hashAlgorithm.name}'s expected " +
                    "${hashAlgorithm.digestSizeBytes} bytes"
            }

            checkEndpoint()
            val effectiveNonce = resolveNonce(nonce)
            val tsRequest = buildRequest(digest, hashAlgorithm, effectiveNonce)

            val httpUrl =
                config.endpointUrl.toHttpUrlOrNull()
                    ?: throw TsaException("TSA endpointUrl is not a valid URL")
            val requestBuilder =
                Request
                    .Builder()
                    .url(httpUrl)
                    .post(tsRequest.encoded.toRequestBody(TIMESTAMP_QUERY))
                    .header("Content-Type", "application/timestamp-query")
                    .header("Accept", "application/timestamp-reply")
            if (config.username != null && config.password != null) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(config.username, config.password),
                )
            }

            val response =
                try {
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
                val body =
                    resp.body
                        ?: throw TsaException("TSA ${config.endpointUrl} returned empty body")
                val declared = body.contentLength()
                if (declared > MAX_RESPONSE_BYTES) {
                    throw TsaException("TSA ${config.endpointUrl} response exceeds $MAX_RESPONSE_BYTES bytes")
                }
                val responseBytes =
                    try {
                        val source = body.source()
                        source.request(MAX_RESPONSE_BYTES + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) {
                            throw TsaException("TSA ${config.endpointUrl} response exceeds $MAX_RESPONSE_BYTES bytes")
                        }
                        source.buffer.readByteArray()
                    } catch (io: IOException) {
                        throw TsaException("TSA response from ${config.endpointUrl} could not be read: ${io.message}", io)
                    }
                if (responseBytes.isEmpty()) throw TsaException("TSA ${config.endpointUrl} returned empty body")

                val tsResponse =
                    try {
                        TimeStampResponse(responseBytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        throw TsaException(
                            "TSA ${config.endpointUrl}: response is not a valid TimeStampResponse",
                            t,
                        )
                    }

                try {
                    tsResponse.validate(tsRequest)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    throw TsaException(
                        "TSA ${config.endpointUrl}: response failed validation against request " +
                            "(nonce/imprint/algorithm mismatch)",
                        t,
                    )
                }

                val bcToken: BcTimeStampToken =
                    tsResponse.timeStampToken
                        ?: throw TsaException(
                            "TSA ${config.endpointUrl}: response carried no token. " +
                                "status=${tsResponse.status} statusString='${tsResponse.statusString ?: ""}' " +
                                "failInfo=${tsResponse.failInfo?.intValue() ?: -1}",
                        )

                verifyPolicyIfRequired(bcToken)
                verifySigner(bcToken)
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
    ) = TimeStampRequestGenerator()
        .apply {
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

    private fun checkEndpoint() {
        try {
            val scheme =
                java.net
                    .URI(config.endpointUrl)
                    .scheme
                    ?.lowercase()
            TransportSecurity.requireSecureForPublicHosts(config.endpointUrl, "TSA requests and credentials")
            if (scheme != "https" && config.username != null) {
                throw IllegalArgumentException("HTTP basic authentication must not be sent over plaintext http")
            }
        } catch (e: IllegalArgumentException) {
            throw TsaException("TSA endpointUrl rejected: ${e.message}", e)
        } catch (e: java.net.URISyntaxException) {
            throw TsaException("TSA endpointUrl is not a valid URL", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun verifySigner(token: BcTimeStampToken) {
        val signerId = token.sid
        val pinned = pinnedCerts.firstOrNull { signerId.match(it) }
        if (pinnedCerts.isNotEmpty() && pinned == null) {
            throw TsaException(
                "TSA signer certificate (issuer=${signerId.issuer}, serial=${signerId.serialNumber}) " +
                    "did not match any entry in trustedSignerCertificates",
            )
        }
        val embedded =
            (token.certificates as Store<X509CertificateHolder>)
                .getMatches(signerId as Selector<X509CertificateHolder>)
                .firstOrNull()
        // A pinned certificate is authoritative; otherwise the signer must be embedded in the token.
        val holder =
            pinned ?: embedded
                ?: throw TsaException("TSA token carries no signer certificate and none is pinned")
        if (pinned != null && embedded != null && !pinned.encoded.contentEquals(embedded.encoded)) {
            throw TsaException("TSA embedded signer certificate differs from the pinned certificate")
        }
        val cert = JCA_CERT_CONVERTER.getCertificate(holder)

        val genTime = token.timeStampInfo.genTime
        try {
            cert.checkValidity(genTime)
        } catch (t: java.security.GeneralSecurityException) {
            throw TsaException("TSA signer certificate was not valid at genTime $genTime", t)
        }
        val eku = holder.extensions?.let { ExtendedKeyUsage.fromExtensions(it) }
        if (eku == null || !eku.hasKeyPurposeId(KeyPurposeId.id_kp_timeStamping)) {
            throw TsaException("TSA signer certificate lacks the id-kp-timeStamping extended key usage")
        }

        // Signature (and RFC 3161 signing-certificate binding) is verified unconditionally.
        try {
            token.validate(JcaSimpleSignerInfoVerifierBuilder().setProvider(BC_PROVIDER_NAME).build(cert))
        } catch (t: Exception) {
            throw TsaException("TSA token signature verification failed: ${t.message}", t)
        }

        when {
            config.trustAnchors.isNotEmpty() -> verifyChain(cert, token, genTime)
            pinned != null -> Unit
            config.allowUnanchored -> Unit
            else -> throw TsaException(
                "TSA signer is not anchored: configure trustedSignerCertificates or trustAnchors " +
                    "(or set allowUnanchored = true to accept any embedded signer)",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun verifyChain(
        leaf: X509Certificate,
        token: BcTimeStampToken,
        genTime: Date,
    ) {
        try {
            val intermediates =
                (token.certificates as Store<X509CertificateHolder>)
                    .getMatches(null)
                    .map { JCA_CERT_CONVERTER.getCertificate(it) }
            val params =
                PKIXBuilderParameters(
                    anchorCerts.map { TrustAnchor(it, null) }.toSet(),
                    X509CertSelector().apply { certificate = leaf },
                )
            params.isRevocationEnabled = false
            params.date = genTime
            params.addCertStore(
                CertStore.getInstance("Collection", CollectionCertStoreParameters(intermediates + leaf)),
            )
            CertPathBuilder.getInstance("PKIX").build(params)
        } catch (t: Exception) {
            throw TsaException("TSA signer certificate does not chain to a configured trust anchor: ${t.message}", t)
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
    private fun extractTsaSubject(token: BcTimeStampToken): String =
        runCatching {
            val store = token.certificates as Store<X509CertificateHolder>
            val selector = token.sid as Selector<X509CertificateHolder>
            store
                .getMatches(selector)
                .firstOrNull()
                ?.subject
                ?.toString()
                .orEmpty()
        }.getOrDefault("")

    companion object {
        private const val BC_PROVIDER_NAME = "BC"
        private const val MAX_RESPONSE_BYTES = 1L * 1024 * 1024
        private val TIMESTAMP_QUERY = "application/timestamp-query".toMediaType()
        private val SECURE_RANDOM = SecureRandom()
        private val JCA_CERT_CONVERTER = JcaX509CertificateConverter().setProvider(BC_PROVIDER_NAME)

        init {
            if (Security.getProvider(BC_PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        fun defaultHttpClient(config: TsaConfig): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}
