package org.trustweave.credential.pex

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class PresentationDefinitionMatcherHardeningTest {
    private fun cred(claims: Map<String, JsonElement>) =
        VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.from(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri("did:example:subject", claims),
        )

    private fun matches(
        path: String,
        filter: kotlinx.serialization.json.JsonObject?,
        c: VerifiableCredential,
    ): Int {
        val def =
            PresentationDefinition(
                id = "d",
                inputDescriptors =
                    listOf(
                        InputDescriptor(id = "i", constraints = Constraints(fields = listOf(Field(path = listOf(path), filter = filter)))),
                    ),
            )
        return PresentationDefinitionMatcher.match(def, listOf(c))["i"]!!.size
    }

    @Test
    fun `string claim does not satisfy numeric predicates`() {
        val c = cred(mapOf("age" to JsonPrimitive("42")))
        assertEquals(0, matches("$.credentialSubject.age", buildJsonObject { put("type", "number") }, c))
        assertEquals(0, matches("$.credentialSubject.age", buildJsonObject { put("minimum", 18) }, c))
        assertEquals(0, matches("$.credentialSubject.age", buildJsonObject { put("const", 42) }, c))
    }

    @Test
    fun `real number still satisfies numeric predicates`() {
        val c = cred(mapOf("age" to JsonPrimitive(42)))
        assertEquals(1, matches("$.credentialSubject.age", buildJsonObject { put("minimum", 18) }, c))
        assertEquals(1, matches("$.credentialSubject.age", buildJsonObject { put("type", "number") }, c))
        assertEquals(0, matches("$.credentialSubject.age", buildJsonObject { put("minimum", 50) }, c))
    }

    @Test
    fun `string true is not a boolean`() {
        val c = cred(mapOf("flag" to JsonPrimitive("true")))
        assertEquals(0, matches("$.credentialSubject.flag", buildJsonObject { put("type", "boolean") }, c))
    }

    @Test
    fun `jsonpath regex filter expressions are not evaluated`() {
        val c = cred(mapOf("name" to JsonPrimitive("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!")))
        val start = System.nanoTime()
        assertEquals(0, matches("$.credentialSubject[?(@ =~ /(a+)+\$/)]", null, c))
        assertEquals(0, matches("$..[?(@.name =~ /(a+)+\$/)]", null, c))
        assert((System.nanoTime() - start) / 1_000_000 < 2000)
    }
}
