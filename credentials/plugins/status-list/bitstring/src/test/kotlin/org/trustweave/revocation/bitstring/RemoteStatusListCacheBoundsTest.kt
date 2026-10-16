package org.trustweave.revocation.bitstring

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The status list cache honours the credential's own validity and one host cannot churn out another's lists. */
class RemoteStatusListCacheBoundsTest {
    private var now = Instant.fromEpochSeconds(1_800_000_000)
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }
    private val json =
        Json {
            serializersModule = SerializationModule.default
            ignoreUnknownKeys = true
        }
    private val acceptAll =
        StatusListCredentialVerifier { vc ->
            VerificationResult.Valid(vc, vc.issuer.id, vc.credentialSubject.id, now, null)
        }

    private val encoded: String =
        "u" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArrayOutputStream().use { out ->
                    GZIPOutputStream(out).use { it.write(ByteArray(16_384)) }
                    out.toByteArray()
                },
            )

    private class RecordingFetcher(
        val body: (String) -> String,
    ) : StatusListCredentialFetcher {
        val calls = mutableMapOf<String, Int>()

        override suspend fun fetch(url: URI): String {
            calls.merge(url.toString(), 1, Int::plus)
            return body(url.toString())
        }
    }

    private fun vc(
        url: String,
        validUntil: Instant? = null,
    ) = json.encodeToString(
        VerifiableCredential.serializer(),
        VerifiableCredential(
            id = CredentialId(url),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("BitstringStatusListCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:remote-issuer")),
            issuanceDate = now,
            validUntil = validUntil,
            credentialSubject =
                CredentialSubject(
                    id = Iri("$url#list"),
                    claims =
                        mapOf(
                            "type" to JsonPrimitive("BitstringStatusList"),
                            "statusPurpose" to JsonPrimitive("revocation"),
                            "encodedList" to JsonPrimitive(encoded),
                        ),
                ),
        ),
    )

    @Test
    fun `a list is not served from cache past its own validUntil`() =
        runBlocking<Unit> {
            val url = "https://status.example.org/lists/1"
            val fetcher = RecordingFetcher { vc(it, validUntil = now + 30.seconds) }
            val resolver = RemoteStatusListResolver(acceptAll, fetcher, cacheTtl = 5.minutes, clock = clock)
            resolver.resolve(url)
            now += 10.seconds
            resolver.resolve(url)
            assertEquals(1, fetcher.calls[url], "still within validUntil: served from cache")
            now += 25.seconds
            resolver.resolve(url)
            assertEquals(2, fetcher.calls[url], "past validUntil: must be fetched (and re-verified) again")
        }

    @Test
    fun `one host spraying distinct URLs does not evict another host's cached list`() =
        runBlocking<Unit> {
            val victim = "https://victim.example.org/lists/1"
            val fetcher = RecordingFetcher { vc(it) }
            val resolver = RemoteStatusListResolver(acceptAll, fetcher, cacheTtl = 5.minutes, maxCacheEntries = 64, clock = clock)
            resolver.resolve(victim)
            repeat(200) { resolver.resolve("https://attacker.example.net/lists/$it") }
            resolver.resolve(victim)
            assertEquals(1, fetcher.calls[victim], "the victim's list must still be cached")
        }
}
