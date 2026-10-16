package org.trustweave.googlekms

import com.google.api.gax.core.NoCredentialsProvider
import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.AsymmetricSignResponse
import com.google.cloud.kms.v1.CryptoKey
import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm
import com.google.cloud.kms.v1.GetCryptoKeyRequest
import com.google.cloud.kms.v1.GetPublicKeyRequest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyManagementServiceSettings
import com.google.cloud.kms.v1.PublicKey
import com.google.protobuf.ByteString
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ServerCalls
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.SignResult
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What `AsymmetricSign` actually receives, and the JWK fidelity of Google public keys, against an in-process KMS. */
class GoogleSignRequestTest {
    private lateinit var server: Server
    private lateinit var client: KeyManagementServiceClient
    private lateinit var kms: GoogleCloudKeyManagementService
    private val signRequests = CopyOnWriteArrayList<AsymmetricSignRequest>()
    private var keyAlgorithm: CryptoKeyVersionAlgorithm = CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256

    private val versionName = "projects/p/locations/global/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1"

    @BeforeEach
    fun setUp() {
        fun <Req : com.google.protobuf.Message, Resp : com.google.protobuf.Message> method(
            name: String,
            request: Req,
            response: Resp,
        ) = MethodDescriptor
            .newBuilder<Req, Resp>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("google.cloud.kms.v1.KeyManagementService/$name")
            .setRequestMarshaller(ProtoUtils.marshaller(request))
            .setResponseMarshaller(ProtoUtils.marshaller(response))
            .build()

        fun <Req, Resp> unary(handler: (Req) -> Resp) =
            ServerCalls.asyncUnaryCall<Req, Resp> { request, observer ->
                observer.onNext(handler(request))
                observer.onCompleted()
            }

        val service =
            ServerServiceDefinition
                .builder("google.cloud.kms.v1.KeyManagementService")
                .addMethod(
                    method("GetCryptoKey", GetCryptoKeyRequest.getDefaultInstance(), CryptoKey.getDefaultInstance()),
                    unary<GetCryptoKeyRequest, CryptoKey> { request ->
                        CryptoKey
                            .newBuilder()
                            .setName(request.name)
                            .setPrimary(CryptoKeyVersion.newBuilder().setName(versionName).build())
                            .build()
                    },
                ).addMethod(
                    method("GetPublicKey", GetPublicKeyRequest.getDefaultInstance(), PublicKey.getDefaultInstance()),
                    unary<GetPublicKeyRequest, PublicKey> { request ->
                        PublicKey
                            .newBuilder()
                            .setName(request.name)
                            .setAlgorithm(keyAlgorithm)
                            .build()
                    },
                ).addMethod(
                    method("AsymmetricSign", AsymmetricSignRequest.getDefaultInstance(), AsymmetricSignResponse.getDefaultInstance()),
                    unary<AsymmetricSignRequest, AsymmetricSignResponse> { request ->
                        signRequests += request
                        AsymmetricSignResponse.newBuilder().setSignature(ByteString.copyFrom(ByteArray(64) { 1 })).build()
                    },
                ).build()
        server =
            ServerBuilder
                .forPort(0)
                .addService(service)
                .build()
                .start()
        client =
            KeyManagementServiceClient.create(
                KeyManagementServiceSettings
                    .newBuilder()
                    .setEndpoint("127.0.0.1:${server.port}")
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setTransportChannelProvider(
                        KeyManagementServiceSettings
                            .defaultGrpcTransportProviderBuilder()
                            .setChannelConfigurator { it.usePlaintext() }
                            .build(),
                    ).build(),
            )
        kms = GoogleCloudKeyManagementService(GoogleKmsConfig(projectId = "p", location = "global", keyRing = "r"), client)
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdownNow()
    }

    private fun signWith(
        googleAlgorithm: CryptoKeyVersionAlgorithm,
        data: ByteArray,
    ): AsymmetricSignRequest {
        signRequests.clear()
        keyAlgorithm = googleAlgorithm
        // EC signatures are 64 bytes (P1363) for P-256; other sizes pass through unchanged.
        runBlocking { assertIs<SignResult.Success>(kms.sign(KeyId("k"), data, null as Algorithm?)) }
        return signRequests.single()
    }

    @Test
    fun `EC and RSA key versions are sent a digest matching the key algorithm`() {
        val data = "payload".toByteArray()

        fun expected(hash: String) = ByteString.copyFrom(MessageDigest.getInstance(hash).digest(data))

        val p256 = signWith(CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256, data)
        assertTrue(p256.data.isEmpty && p256.hasDigest(), "P-256 must send a digest, not data")
        assertEquals(expected("SHA-256"), p256.digest.sha256)

        assertEquals(expected("SHA-384"), signWith(CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384, data).digest.sha384)
        assertEquals(expected("SHA-256"), signWith(CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256, data).digest.sha256)
        assertEquals(expected("SHA-256"), signWith(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256, data).digest.sha256)
        assertEquals(expected("SHA-256"), signWith(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256, data).digest.sha256)
        assertEquals(expected("SHA-512"), signWith(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA512, data).digest.sha512)
    }

    @Test
    fun `pure Ed25519 is sent the data itself`() {
        val request = signWith(CryptoKeyVersionAlgorithm.valueOf("EC_SIGN_ED25519"), "payload".toByteArray())
        assertEquals(ByteString.copyFromUtf8("payload"), request.data)
        assertTrue(!request.hasDigest())
    }

    @Test
    fun `RSA JWK modulus and exponent carry no leading sign byte`() {
        for (bits in listOf(2048, 3072)) {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
            val jwk = AlgorithmMapping.publicKeyToJwk(pair.public.encoded, Algorithm.RSA(bits))
            assertEquals(bits / 8, Base64.getUrlDecoder().decode(jwk["n"] as String).size, "RSA-$bits modulus length")
            assertEquals(3, Base64.getUrlDecoder().decode(jwk["e"] as String).size)
        }
    }

    @Test
    fun `Ed25519 JWK x is the raw 32-byte key`() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jwk = AlgorithmMapping.publicKeyToJwk(pair.public.encoded, Algorithm.Ed25519)
        val x = Base64.getUrlDecoder().decode(jwk["x"] as String)
        assertEquals(32, x.size)
        assertEquals(
            pair.public.encoded
                .copyOfRange(12, 44)
                .toList(),
            x.toList(),
        )
    }
}
