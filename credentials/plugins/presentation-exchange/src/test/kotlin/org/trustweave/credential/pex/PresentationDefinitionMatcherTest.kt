package org.trustweave.credential.pex

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresentationDefinitionMatcherTest {

    // -------------------------------------------------------------------------
    // JSON serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `PresentationDefinition serializes and deserializes to JSON`() {
        val definition = PresentationDefinition(
            id = "pd-test-001",
            name = "Test Definition",
            purpose = "Verify identity",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-edu",
                    name = "Education Credential",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.type"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    put("const", "EducationCredential")
                                },
                            )
                        )
                    )
                )
            ),
            format = Format(
                jwtVc = AlgorithmConstraint(alg = listOf("EdDSA", "ES256")),
                ldpVc = ProofTypeConstraint(proofType = listOf("Ed25519Signature2020")),
            ),
            submissionRequirements = listOf(
                SubmissionRequirement(
                    rule = SubmissionRule.PICK,
                    from = "A",
                    count = 1,
                )
            )
        )

        val json = Json { prettyPrint = false; encodeDefaults = false }
        val encoded = json.encodeToString(PresentationDefinition.serializer(), definition)
        val decoded = json.decodeFromString(PresentationDefinition.serializer(), encoded)

        assertEquals(definition, decoded)
        assertTrue(encoded.contains("input_descriptors"))
        assertTrue(encoded.contains("submission_requirements"))
    }

    @Test
    fun `PresentationSubmission serializes and deserializes to JSON`() {
        val submission = PresentationSubmission(
            id = "sub-001",
            definitionId = "pd-001",
            descriptorMap = listOf(
                DescriptorMap(
                    id = "id-edu",
                    format = "ldp_vc",
                    path = "$.verifiableCredential[0]",
                    pathNested = DescriptorMap(
                        id = "id-inner",
                        format = "jwt_vc",
                        path = "$.vc",
                    )
                )
            )
        )

        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(PresentationSubmission.serializer(), submission)
        val decoded = json.decodeFromString(PresentationSubmission.serializer(), encoded)

        assertEquals(submission, decoded)
        assertTrue(encoded.contains("definition_id"))
        assertTrue(encoded.contains("descriptor_map"))
        assertTrue(encoded.contains("path_nested"))
    }

    @Test
    fun `Format serializes with correct snake_case field names`() {
        val format = Format(
            jwtVc = AlgorithmConstraint(listOf("EdDSA")),
            jwtVp = AlgorithmConstraint(listOf("ES256")),
            sdJwtVc = AlgorithmConstraint(listOf("ES256")),
            msoMdoc = AlgorithmConstraint(listOf("ES256")),
        )
        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(Format.serializer(), format)

        assertTrue(encoded.contains("\"jwt_vc\""))
        assertTrue(encoded.contains("\"jwt_vp\""))
        assertTrue(encoded.contains("\"vc+sd-jwt\""))
        assertTrue(encoded.contains("\"mso_mdoc\""))
    }

    @Test
    fun `SubmissionRule enum serializes to lowercase`() {
        val json = Json.Default
        val all = json.encodeToString(SubmissionRule.serializer(), SubmissionRule.ALL)
        val pick = json.encodeToString(SubmissionRule.serializer(), SubmissionRule.PICK)
        assertEquals("\"all\"", all)
        assertEquals("\"pick\"", pick)
    }

    @Test
    fun `LimitDisclosure enum serializes to lowercase`() {
        val json = Json.Default
        val required = json.encodeToString(LimitDisclosure.serializer(), LimitDisclosure.REQUIRED)
        val preferred = json.encodeToString(LimitDisclosure.serializer(), LimitDisclosure.PREFERRED)
        assertEquals("\"required\"", required)
        assertEquals("\"preferred\"", preferred)
    }

    // -------------------------------------------------------------------------
    // Matching — no constraints
    // -------------------------------------------------------------------------

    @Test
    fun `match returns all credentials when descriptor has no constraints`() {
        val definition = PresentationDefinition(
            id = "pd-open",
            inputDescriptors = listOf(InputDescriptor(id = "id-any"))
        )
        val credentials = listOf(
            makeCredential("EducationCredential"),
            makeCredential("EmploymentCredential"),
        )

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        assertEquals(1, result.size)
        assertEquals(2, result["id-any"]?.size)
    }

    @Test
    fun `match returns all credentials when descriptor constraints have no fields`() {
        val definition = PresentationDefinition(
            id = "pd-empty-fields",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-empty",
                    constraints = Constraints(fields = emptyList())
                )
            )
        )
        val credentials = listOf(makeCredential("EducationCredential"))

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        assertEquals(1, result["id-empty"]?.size)
    }

    // -------------------------------------------------------------------------
    // Matching — type const filter
    // -------------------------------------------------------------------------

    @Test
    fun `match filters credentials by type const filter`() {
        val definition = PresentationDefinition(
            id = "pd-edu",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-edu",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.type"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    put("const", "EducationCredential")
                                },
                            )
                        )
                    )
                )
            )
        )
        val credentials = listOf(
            makeCredential("EducationCredential"),
            makeCredential("EmploymentCredential"),
        )

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        val matched = result["id-edu"]
        assertNotNull(matched)
        assertEquals(1, matched.size)
        assertTrue(matched.first().type.any { it.value == "EducationCredential" })
    }

    @Test
    fun `match returns empty list when no credentials satisfy type const filter`() {
        val definition = PresentationDefinition(
            id = "pd-degree",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-degree",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.type"),
                                filter = buildJsonObject { put("const", "DegreeCredential") },
                            )
                        )
                    )
                )
            )
        )
        val credentials = listOf(makeCredential("EducationCredential"))

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        assertEquals(0, result["id-degree"]?.size)
    }

    // -------------------------------------------------------------------------
    // Matching — type enum filter
    // -------------------------------------------------------------------------

    @Test
    fun `match filters credentials by type enum filter`() {
        val definition = PresentationDefinition(
            id = "pd-enum",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-enum",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.type"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    putJsonArray("enum") {
                                        add(kotlinx.serialization.json.JsonPrimitive("EducationCredential"))
                                        add(kotlinx.serialization.json.JsonPrimitive("DegreeCredential"))
                                    }
                                },
                            )
                        )
                    )
                )
            )
        )
        val credentials = listOf(
            makeCredential("EducationCredential"),
            makeCredential("EmploymentCredential"),
        )

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        val matched = result["id-enum"]
        assertNotNull(matched)
        assertEquals(1, matched.size)
        assertTrue(matched.first().type.any { it.value == "EducationCredential" })
    }

    // -------------------------------------------------------------------------
    // Matching — optional fields
    // -------------------------------------------------------------------------

    @Test
    fun `match ignores optional fields during filtering`() {
        val definition = PresentationDefinition(
            id = "pd-optional",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-opt",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.type"),
                                filter = buildJsonObject { put("const", "DegreeCredential") },
                                optional = true,
                            )
                        )
                    )
                )
            )
        )
        val credentials = listOf(makeCredential("EducationCredential"))

        val result = PresentationDefinitionMatcher.match(definition, credentials)

        // Optional field — all credentials pass
        assertEquals(1, result["id-opt"]?.size)
    }

    // -------------------------------------------------------------------------
    // buildSubmission
    // -------------------------------------------------------------------------

    @Test
    fun `buildSubmission produces correct submission for matched credentials`() {
        val definition = PresentationDefinition(
            id = "pd-sub",
            inputDescriptors = listOf(
                InputDescriptor(id = "id-a"),
                InputDescriptor(id = "id-b"),
            )
        )
        val credentials = listOf(makeCredential("EducationCredential"))
        val matches = mapOf(
            "id-a" to credentials,
            "id-b" to emptyList(),
        )

        val submission = PresentationDefinitionMatcher.buildSubmission(definition, matches)

        assertEquals("pd-sub", submission.definitionId)
        assertEquals(1, submission.descriptorMap.size)
        assertEquals("id-a", submission.descriptorMap.first().id)
        assertEquals("ldp_vc", submission.descriptorMap.first().format)
        assertNull(submission.descriptorMap.first().pathNested)
    }

    @Test
    fun `buildSubmission has a unique non-empty id`() {
        val definition = PresentationDefinition(
            id = "pd-id",
            inputDescriptors = listOf(InputDescriptor(id = "id-x"))
        )
        val matches = mapOf("id-x" to listOf(makeCredential("EducationCredential")))

        val s1 = PresentationDefinitionMatcher.buildSubmission(definition, matches)
        val s2 = PresentationDefinitionMatcher.buildSubmission(definition, matches)

        assertTrue(s1.id.isNotEmpty())
        // Two calls must produce different IDs (UUID-based)
        assertTrue(s1.id != s2.id)
    }

    // -------------------------------------------------------------------------
    // Matching — full JSONPath evaluation
    // -------------------------------------------------------------------------

    @Test
    fun `match evaluates credentialSubject id path`() {
        val definition = PresentationDefinition(
            id = "pd-subject",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-subject",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.id"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    put("const", "did:example:alice")
                                },
                            )
                        )
                    )
                )
            )
        )
        val alice = makeCredentialWithSubject("did:example:alice", "EducationCredential")
        val bob = makeCredentialWithSubject("did:example:bob", "EducationCredential")

        val result = PresentationDefinitionMatcher.match(definition, listOf(alice, bob))

        assertEquals(1, result["id-subject"]?.size)
        assertEquals("did:example:alice", result["id-subject"]!!.first().credentialSubject.id!!.value)
    }

    @Test
    fun `match evaluates credentialSubject claim path with string filter`() {
        val definition = PresentationDefinition(
            id = "pd-claim",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-claim",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.degree"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    put("const", "Bachelor")
                                },
                            )
                        )
                    )
                )
            )
        )
        val bachelor = makeCredentialWithClaims("EducationCredential", mapOf("degree" to JsonPrimitive("Bachelor")))
        val master = makeCredentialWithClaims("EducationCredential", mapOf("degree" to JsonPrimitive("Master")))

        val result = PresentationDefinitionMatcher.match(definition, listOf(bachelor, master))

        assertEquals(1, result["id-claim"]?.size)
        assertEquals("Bachelor", result["id-claim"]!!.first().credentialSubject.claims["degree"]?.let {
            (it as? JsonPrimitive)?.content
        })
    }

    @Test
    fun `match evaluates numeric range filter`() {
        val definition = PresentationDefinition(
            id = "pd-age",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-age",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.age"),
                                filter = buildJsonObject {
                                    put("type", "number")
                                    put("minimum", 18)
                                    put("maximum", 65)
                                },
                            )
                        )
                    )
                )
            )
        )
        val adult = makeCredentialWithClaims("IDCredential", mapOf("age" to JsonPrimitive(25)))
        val minor = makeCredentialWithClaims("IDCredential", mapOf("age" to JsonPrimitive(15)))
        val senior = makeCredentialWithClaims("IDCredential", mapOf("age" to JsonPrimitive(70)))

        val result = PresentationDefinitionMatcher.match(definition, listOf(adult, minor, senior))

        assertEquals(1, result["id-age"]?.size)
        assertEquals(
            25L,
            (result["id-age"]!!.first().credentialSubject.claims["age"] as? JsonPrimitive)?.longOrNull
        )
    }

    @Test
    fun `match evaluates pattern filter on string claim`() {
        val definition = PresentationDefinition(
            id = "pd-pattern",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-pattern",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.email"),
                                filter = buildJsonObject {
                                    put("type", "string")
                                    put("pattern", "^[^@]+@example\\.com$")
                                },
                            )
                        )
                    )
                )
            )
        )
        val matching = makeCredentialWithClaims("ContactCredential", mapOf("email" to JsonPrimitive("alice@example.com")))
        val nonMatching = makeCredentialWithClaims("ContactCredential", mapOf("email" to JsonPrimitive("alice@other.com")))

        val result = PresentationDefinitionMatcher.match(definition, listOf(matching, nonMatching))

        assertEquals(1, result["id-pattern"]?.size)
    }

    @Test
    fun `match returns no match when required path is absent from credential`() {
        val definition = PresentationDefinition(
            id = "pd-missing",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-missing",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.nonExistentField"),
                            )
                        )
                    )
                )
            )
        )
        val credential = makeCredential("EducationCredential")

        val result = PresentationDefinitionMatcher.match(definition, listOf(credential))

        assertEquals(0, result["id-missing"]?.size)
    }

    @Test
    fun `match uses first resolved path from multiple alternatives`() {
        val definition = PresentationDefinition(
            id = "pd-alt",
            inputDescriptors = listOf(
                InputDescriptor(
                    id = "id-alt",
                    constraints = Constraints(
                        fields = listOf(
                            Field(
                                path = listOf("$.credentialSubject.licenseNumber", "$.credentialSubject.id"),
                            )
                        )
                    )
                )
            )
        )
        // This credential has no licenseNumber but has id — second path resolves
        val credential = makeCredential("DriverLicenseCredential")

        val result = PresentationDefinitionMatcher.match(definition, listOf(credential))

        assertEquals(1, result["id-alt"]?.size)
    }

    @Test
    fun `vcToDocument produces traversable document`() {
        val vc = makeCredentialWithClaims(
            "EmploymentCredential",
            mapOf("jobTitle" to JsonPrimitive("Engineer"), "yearsExperience" to JsonPrimitive(5))
        )
        val doc = PresentationDefinitionMatcher.vcToDocument(vc)

        val types = doc["type"] as? List<*>
        assertNotNull(types)
        assertTrue(types.contains("EmploymentCredential"))

        @Suppress("UNCHECKED_CAST")
        val subject = doc["credentialSubject"] as? Map<String, Any?>
        assertNotNull(subject)
        assertEquals("Engineer", subject["jobTitle"])
        assertEquals(5L, subject["yearsExperience"])
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeCredential(vararg types: String): VerifiableCredential =
        VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = buildList {
                add(CredentialType.VerifiableCredential)
                types.forEach { add(CredentialType.fromString(it)) }
            },
            issuer = Issuer.from(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri("did:example:subject"),
        )

    private fun makeCredentialWithSubject(subjectId: String, vararg types: String): VerifiableCredential =
        VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = buildList {
                add(CredentialType.VerifiableCredential)
                types.forEach { add(CredentialType.fromString(it)) }
            },
            issuer = Issuer.from(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri(subjectId),
        )

    private fun makeCredentialWithClaims(
        type: String,
        claims: Map<String, kotlinx.serialization.json.JsonElement>,
    ): VerifiableCredential =
        VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.fromString(type)),
            issuer = Issuer.from(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri("did:example:subject", claims),
        )
}
