package org.trustweave.conformance

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.pex.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlinx.datetime.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("conformance")
@Tag("presentation-exchange")
class PresentationExchangeConformanceTest {

    private fun buildVc(type: String, claims: Map<String, String> = emptyMap()): VerifiableCredential {
        val now = Clock.System.now()
        return VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.Custom("VerifiableCredential"), CredentialType.Custom(type)),
            issuer = Issuer.IriIssuer(Iri("did:key:z6MkIssuer")),
            issuanceDate = now,
            validFrom = now,
            credentialSubject = CredentialSubject(
                id = Iri("did:key:z6MkSubject"),
                claims = claims.mapValues { (_, v) -> JsonPrimitive(v) },
            ),
        )
    }

    @Test
    fun `TC-01 unconstrained descriptor matches any credential`() {
        val definition = PresentationDefinition(
            id = "pd-1",
            inputDescriptors = listOf(InputDescriptor(id = "id-1")),
        )
        val vc = buildVc("DegreeCredential")
        val matches = PresentationDefinitionMatcher.match(definition, listOf(vc))
        assertEquals(1, matches["id-1"]?.size, "Unconstrained descriptor must match the credential")
    }

    @Test
    fun `TC-02 field const filter matches when value equals const`() {
        val definition = PresentationDefinition(
            id = "pd-2",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-2",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.degree"),
                                filter = buildJsonObject { put("const", "Bachelor") },
                            )
                        )
                    ),
                )
            ),
        )
        val matching = buildVc("DegreeCredential", mapOf("degree" to "Bachelor"))
        val nonMatching = buildVc("DegreeCredential", mapOf("degree" to "Master"))
        val matches = PresentationDefinitionMatcher.match(definition, listOf(matching, nonMatching))
        assertEquals(1, matches["id-2"]?.size)
    }

    @Test
    fun `TC-03 field const filter does NOT match when value differs`() {
        val definition = PresentationDefinition(
            id = "pd-3",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-3",
                    constraints = Constraints(
                        fields = listOf(Field(
                            path = listOf("$.credentialSubject.level"),
                            filter = buildJsonObject { put("const", "Gold") }
                        ))
                    ),
                )
            ),
        )
        val vc = buildVc("MembershipCredential", mapOf("level" to "Silver"))
        val matches = PresentationDefinitionMatcher.match(definition, listOf(vc))
        assertTrue(matches["id-3"]?.isEmpty() ?: true)
    }

    @Test
    fun `TC-04 buildSubmission maps credentials to descriptors`() {
        val definition = PresentationDefinition(
            id = "pd-4",
            inputDescriptors = listOf(InputDescriptor(id = "id-4")),
        )
        val vc = buildVc("PassportCredential")
        val matches = PresentationDefinitionMatcher.match(definition, listOf(vc))
        val submission = PresentationDefinitionMatcher.buildSubmission(definition, matches)
        assertEquals("pd-4", submission.definitionId)
        assertTrue(submission.descriptorMap.any { it.id == "id-4" })
    }

    @Test
    fun `TC-05 empty wallet yields no matches`() {
        val definition = PresentationDefinition(
            id = "pd-5",
            inputDescriptors = listOf(InputDescriptor(id = "id-5")),
        )
        val matches = PresentationDefinitionMatcher.match(definition, emptyList())
        assertTrue(matches["id-5"]?.isEmpty() ?: true)
    }
}
