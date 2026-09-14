package org.trustweave.credential.requests

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.arrayOfObjects
import org.trustweave.credential.model.vc.subject
import org.trustweave.credential.model.vc.with
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Duration as JavaDuration

/**
 * The builders are the primary configuration API, and they were untested.
 *
 * What matters about a builder is what it refuses and what it fills in: a request that silently
 * builds without an issuer, or one that quietly drops the `VerifiableCredential` type, produces a
 * credential nobody can verify and a failure a long way from its cause.
 */
class IssuanceRequestBuilderTest {
    private val issuerDid = Did("did:example:issuer")
    private val subjectDid = Did("did:example:subject")

    private fun claim(
        request: IssuanceRequest,
        key: String,
    ) = request.credentialSubject.claims[key]

    // ---------------------------------------------------------------- required fields

    @Test
    fun `a request without an issuer is refused`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                issuanceRequest(ProofSuiteId.VC_LD) { subject(subjectDid) }
            }
        assertTrue("Issuer" in (failure.message ?: ""), failure.message ?: "")
    }

    @Test
    fun `a request without a subject is refused`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                issuanceRequest(ProofSuiteId.VC_LD) { issuer(issuerDid) }
            }
        assertTrue("Subject" in (failure.message ?: ""), failure.message ?: "")
    }

    @Test
    fun `a subject built with no id is refused`() {
        val failure = assertFailsWith<IllegalArgumentException> { subject { "name" to "Ada" } }
        assertTrue("Subject ID is required" in (failure.message ?: ""), failure.message ?: "")
    }

    // ---------------------------------------------------------------- types

    @Test
    fun `VerifiableCredential is always present and always first`() {
        val implicit =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
            }
        assertEquals(listOf("VerifiableCredential"), implicit.type.map { it.value })

        val added =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                type("DegreeCredential")
            }
        assertEquals(listOf("VerifiableCredential", "DegreeCredential"), added.type.map { it.value })
    }

    @Test
    fun `an explicit VerifiableCredential type is not duplicated`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                type("VerifiableCredential")
                type("DegreeCredential")
            }
        assertEquals(listOf("VerifiableCredential", "DegreeCredential"), request.type.map { it.value })
    }

    @Test
    fun `repeating a type does not repeat it in the request`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                types("DegreeCredential", "DegreeCredential", "AlumniCredential")
            }
        assertEquals(
            listOf("VerifiableCredential", "DegreeCredential", "AlumniCredential"),
            request.type.map { it.value },
        )
    }

    // ---------------------------------------------------------------- issuer and key

    @Test
    fun `an issuer may be given as a DID, an IRI string or an Issuer`() {
        val fromDid =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
            }
        val fromString =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer("did:example:issuer")
                subject(subjectDid)
            }
        assertEquals(fromDid.issuer.id.value, fromString.issuer.id.value)
        assertEquals("did:example:issuer", fromDid.issuer.id.value)

        val fromIssuer =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(fromDid.issuer)
                subject(subjectDid)
            }
        assertEquals(fromDid.issuer, fromIssuer.issuer)
    }

    @Test
    fun `an issuer key id may be given as a value or parsed from a string`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuerKeyId("did:example:issuer#key-1")
            }
        assertEquals("did:example:issuer#key-1", request.issuerKeyId?.value)

        val reused =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuerKeyId(request.issuerKeyId!!)
            }
        assertEquals(request.issuerKeyId, reused.issuerKeyId)
    }

    @Test
    fun `an unset key id and credential id stay null rather than becoming empty`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
            }
        assertNull(request.issuerKeyId)
        assertNull(request.id)
        assertNull(request.validFrom)
        assertNull(request.validUntil)
    }

    // ---------------------------------------------------------------- times

    @Test
    fun `explicit times are carried through`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val from = Instant.parse("2026-02-01T00:00:00Z")
        val until = Instant.parse("2026-03-01T00:00:00Z")
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuedAt(issued)
                validFrom(from)
                validUntil(until)
                id("urn:uuid:1234")
            }
        assertEquals(issued, request.issuedAt)
        assertEquals(from, request.validFrom)
        assertEquals(until, request.validUntil)
        assertEquals("urn:uuid:1234", request.id?.value)
    }

    @Test
    fun `expiresIn is measured from issuedAt, not from now`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuedAt(issued)
                expiresIn(JavaDuration.ofDays(30))
            }
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), request.validUntil)
    }

    @Test
    fun `expiresIn does not depend on where it appears in the block`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val before =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                expiresIn(JavaDuration.ofDays(1))
                issuedAt(issued)
            }
        val after =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuedAt(issued)
                expiresIn(JavaDuration.ofDays(1))
            }
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), before.validUntil)
        assertEquals(before.validUntil, after.validUntil)
    }

    @Test
    fun `an explicit validUntil wins over a duration`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val stated = Instant.parse("2026-06-01T00:00:00Z")
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid)
                issuedAt(issued)
                expiresIn(JavaDuration.ofDays(1))
                validUntil(stated)
            }
        assertEquals(stated, request.validUntil, "a caller who states the instant means the instant")
    }

    // ---------------------------------------------------------------- subject DSL

    @Test
    fun `the subject DSL carries primitives with their JSON types`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(subjectDid) {
                    "name" to "Ada"
                    "age" to 36
                    "active" to true
                }
            }
        assertEquals("Ada", claim(request, "name")?.jsonPrimitive?.content)
        assertEquals("36", claim(request, "age")?.jsonPrimitive?.content)
        assertEquals(true, (claim(request, "age") as JsonPrimitive).isString.not())
        assertEquals("true", claim(request, "active")?.jsonPrimitive?.content)
        assertEquals(subjectDid.value, request.credentialSubject.id?.value)
    }

    @Test
    fun `a subject may be addressed by IRI string`() {
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject("https://example.com/subjects/1") { "name" to "Ada" }
            }
        assertEquals("https://example.com/subjects/1", request.credentialSubject.id?.value)
    }

    @Test
    fun `a prebuilt subject may be handed over directly`() {
        val prebuilt = subject(subjectDid) { "name" to "Ada" }
        val request =
            issuanceRequest(ProofSuiteId.VC_LD) {
                issuer(issuerDid)
                subject(prebuilt)
            }
        assertEquals(prebuilt, request.credentialSubject)
    }

    @Test
    fun `nested objects can be built with to or with invoke`() {
        val viaTo =
            subject(subjectDid) {
                "degree" to {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                }
            }
        val viaInvoke =
            subject(subjectDid) {
                "degree" {
                    "type" to "BachelorDegree"
                    "name" to "Bachelor of Science"
                }
            }
        assertEquals(viaTo.claims["degree"], viaInvoke.claims["degree"])
        val degree = viaTo.claims["degree"] as JsonObject
        assertEquals("BachelorDegree", degree["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a JsonElement may be supplied directly`() {
        val built = subject(subjectDid) { "degree" to buildJsonObject { put("type", "BachelorDegree") } }
        assertEquals("BachelorDegree", (built.claims["degree"] as JsonObject)["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a list becomes a JSON array with its element types preserved`() {
        val built = subject(subjectDid) { "skills" to listOf("Kotlin", 3, true) }
        val skills = built.claims["skills"] as JsonArray
        assertEquals(3, skills.size)
        assertEquals("Kotlin", skills[0].jsonPrimitive.content)
        assertEquals("3", skills[1].jsonPrimitive.content)
        assertEquals("true", skills[2].jsonPrimitive.content)
    }

    @Test
    fun `arrayOfObjects builds an array of nested objects`() {
        val built =
            subject(subjectDid) {
                "grades" to
                    arrayOfObjects(
                        {
                            "courseCode" to "CS101"
                            "grade" to "A"
                        },
                        {
                            "courseCode" to "MATH101"
                            "grade" to "B"
                        },
                    )
            }
        val grades = built.claims["grades"] as JsonArray
        assertEquals(2, grades.size)
        assertEquals("CS101", (grades[0] as JsonObject)["courseCode"]?.jsonPrimitive?.content)
        assertEquals("B", (grades[1] as JsonObject)["grade"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a nested object coerces an unrecognised value through toString`() {
        val built = subject(subjectDid) { "meta" to { "when" to Instant.parse("2026-01-01T00:00:00Z") } }
        val meta = built.claims["meta"] as JsonObject
        assertEquals("2026-01-01T00:00:00Z", meta["when"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a nested null becomes JSON null rather than dropping the key`() {
        val built = subject(subjectDid) { "meta" to { "missing" to null } }
        val meta = built.claims["meta"] as JsonObject
        assertTrue(meta.containsKey("missing"))
    }

    @Test
    fun `setting the same key twice keeps the last value`() {
        val built =
            subject(subjectDid) {
                "name" to "first"
                "name" to "second"
            }
        assertEquals("second", built.claims["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the id may be set inside the block, and must look like an IRI`() {
        val built =
            subject {
                id("did:example:inside")
                "name" to "Ada"
            }
        assertEquals("did:example:inside", built.id?.value)

        assertFailsWith<IllegalArgumentException> { subject { id("") } }
        assertFailsWith<IllegalArgumentException> { subject { id("no-scheme") } }
    }

    // ---------------------------------------------------------------- extending a subject

    @Test
    fun `with adds properties and keeps the ones already there`() {
        val base = subject(subjectDid) { "name" to "Ada" }
        val extended = base.with { "email" to "ada@example.com" }
        assertEquals("Ada", extended.claims["name"]?.jsonPrimitive?.content)
        assertEquals("ada@example.com", extended.claims["email"]?.jsonPrimitive?.content)
        assertEquals(base.id, extended.id)
        assertEquals(1, base.claims.size, "the original subject is not mutated")
    }

    @Test
    fun `the single-property form sets one value of each supported type`() {
        val base = subject(subjectDid) { "name" to "Ada" }
        assertEquals("x", (base with "s" value "x").claims["s"]?.jsonPrimitive?.content)
        assertEquals("1", (base with "n" value 1).claims["n"]?.jsonPrimitive?.content)
        assertEquals("true", (base with "b" value true).claims["b"]?.jsonPrimitive?.content)
        assertEquals(
            "v",
            ((base with "j" value JsonPrimitive("v")).claims["j"])?.jsonPrimitive?.content,
        )
    }
}
