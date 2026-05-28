package org.trustweave.conformance

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@Tag("conformance")
@Tag("vc-data-model-2.0")
class VcDataModel20ConformanceTest {

    private fun buildTestVc(
        types: List<String> = listOf("VerifiableCredential"),
        issuerDid: String = "did:key:z6MkTestIssuer",
        validUntil: kotlinx.datetime.Instant? = null,
        subjectId: String = "did:key:z6MkTestSubject",
        name: String? = null,
        description: String? = null,
    ): VerifiableCredential {
        val now = Clock.System.now()
        return VerifiableCredential(
            context = listOf("https://www.w3.org/ns/credentials/v2", "https://www.w3.org/2018/credentials/v1"),
            type = types.map { CredentialType.Custom(it) },
            issuer = Issuer.IriIssuer(Iri(issuerDid)),
            issuanceDate = now,
            validFrom = now,
            validUntil = validUntil,
            credentialSubject = CredentialSubject(id = Iri(subjectId), claims = emptyMap()),
            name = name,
            description = description,
        )
    }

    @Test
    fun `TC-01 VC context includes W3C VC 2_0 URI as first entry`() {
        val vc = buildTestVc()
        assertTrue(vc.context.isNotEmpty())
        assertEquals("https://www.w3.org/ns/credentials/v2", vc.context.first())
    }

    @Test
    fun `TC-02 VC type includes VerifiableCredential`() {
        val vc = buildTestVc(types = listOf("VerifiableCredential", "UniversityDegreeCredential"))
        assertTrue(vc.type.any { it.value == "VerifiableCredential" })
    }

    @Test
    fun `TC-03 VC issuer is present`() {
        val vc = buildTestVc()
        assertNotNull(vc.issuer)
        assertTrue(vc.issuer.id.value.isNotBlank())
    }

    @Test
    fun `TC-04 VC validFrom is present and parseable`() {
        val vc = buildTestVc()
        assertNotNull(vc.validFrom)
    }

    @Test
    fun `TC-05 VC credentialSubject has id`() {
        val vc = buildTestVc(subjectId = "did:key:z6MkHolder")
        assertNotNull(vc.credentialSubject.id)
        assertEquals("did:key:z6MkHolder", vc.credentialSubject.id?.value)
    }

    @Test
    fun `TC-06 expired VC fails isValid check via validUntil`() {
        val past = Clock.System.now().minus(1.hours)
        val vc = buildTestVc(validUntil = past)
        assertFalse(vc.isValid())
    }

    @Test
    fun `TC-07 VC name and description survive construction`() {
        val vc = buildTestVc(name = "Test Degree", description = "A degree credential")
        assertEquals("Test Degree", vc.name)
        assertEquals("A degree credential", vc.description)
    }

    @Test
    fun `TC-08 VC serialises and deserialises correctly`() {
        val vc = buildTestVc()
        val json = Json { serializersModule = SerializationModule.default; ignoreUnknownKeys = true }
        val serialized = json.encodeToString(VerifiableCredential.serializer(), vc)
        val parsed = json.decodeFromString(VerifiableCredential.serializer(), serialized)
        assertEquals(vc.context, parsed.context)
        assertEquals(vc.issuer.id.value, parsed.issuer.id.value)
    }
}
