package org.trustweave.credential.pex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DIF Presentation Exchange v2.0 — top-level Presentation Definition.
 *
 * A Presentation Definition is a JSON object that describes proof requirements
 * requested by a verifier. It contains one or more [InputDescriptor] entries
 * that specify which credentials (or claims) must be presented.
 *
 * Spec: https://identity.foundation/presentation-exchange/spec/v2.0.0/
 */
@Serializable
data class PresentationDefinition(
    val id: String,
    val name: String? = null,
    val purpose: String? = null,
    @SerialName("input_descriptors") val inputDescriptors: List<InputDescriptor>,
    val format: Format? = null,
    @SerialName("submission_requirements") val submissionRequirements: List<SubmissionRequirement>? = null,
)

/**
 * Describes a single credential requirement within a [PresentationDefinition].
 */
@Serializable
data class InputDescriptor(
    val id: String,
    val name: String? = null,
    val purpose: String? = null,
    val format: Format? = null,
    val constraints: Constraints? = null,
    val group: List<String>? = null,
)

/**
 * Field-level constraints attached to an [InputDescriptor].
 */
@Serializable
data class Constraints(
    val fields: List<Field>? = null,
    @SerialName("limit_disclosure") val limitDisclosure: LimitDisclosure? = null,
    @SerialName("subject_is_issuer") val subjectIsIssuer: Optionality? = null,
    @SerialName("is_holder") val isHolder: List<HolderSubject>? = null,
    @SerialName("same_subject") val sameSubject: List<SameSubject>? = null,
)

/**
 * A single JSONPath-based field constraint within [Constraints].
 */
@Serializable
data class Field(
    val path: List<String>,
    val id: String? = null,
    val purpose: String? = null,
    val name: String? = null,
    /** JSON Schema filter applied to the value at each [path]. */
    val filter: JsonObject? = null,
    val optional: Boolean = false,
    val predicate: Optionality? = null,
)

/**
 * A holder's response to a Presentation Definition, mapping each [InputDescriptor]
 * to the credential(s) that satisfy it.
 */
@Serializable
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id") val definitionId: String,
    @SerialName("descriptor_map") val descriptorMap: List<DescriptorMap>,
)

/**
 * Maps a single [InputDescriptor] to the path of the credential in the VP that satisfies it.
 */
@Serializable
data class DescriptorMap(
    val id: String,
    val format: String,
    val path: String,
    @SerialName("path_nested") val pathNested: DescriptorMap? = null,
)

/**
 * Supported credential/proof format constraints for a [PresentationDefinition] or [InputDescriptor].
 *
 * Both the legacy DIF PEX identifiers (`jwt_vc`, `jwt_vp`) and the OID4VP v1.0 registered
 * identifiers (`jwt_vc_json`, `jwt_vp_json`) are supported, as presentation definitions in
 * the wild use either spelling.
 */
@Serializable
data class Format(
    @SerialName("jwt_vc") val jwtVc: AlgorithmConstraint? = null,
    @SerialName("jwt_vc_json") val jwtVcJson: AlgorithmConstraint? = null,
    @SerialName("jwt_vp") val jwtVp: AlgorithmConstraint? = null,
    @SerialName("jwt_vp_json") val jwtVpJson: AlgorithmConstraint? = null,
    @SerialName("ldp_vc") val ldpVc: ProofTypeConstraint? = null,
    @SerialName("ldp_vp") val ldpVp: ProofTypeConstraint? = null,
    @SerialName("vc+sd-jwt") val sdJwtVc: AlgorithmConstraint? = null,
    @SerialName("mso_mdoc") val msoMdoc: AlgorithmConstraint? = null,
)

/** Algorithm (alg) constraint for JWT-based formats. */
@Serializable
data class AlgorithmConstraint(val alg: List<String>)

/** Proof-type constraint for Linked Data Proof formats. */
@Serializable
data class ProofTypeConstraint(
    @SerialName("proof_type") val proofType: List<String>,
)

/**
 * Submission requirement that selects input descriptors from groups according to a [SubmissionRule].
 */
@Serializable
data class SubmissionRequirement(
    val rule: SubmissionRule,
    val from: String? = null,
    @SerialName("from_nested") val fromNested: List<SubmissionRequirement>? = null,
    val count: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val name: String? = null,
    val purpose: String? = null,
)

/** Rule that governs how many credentials from a group satisfy a [SubmissionRequirement]. */
@Serializable
enum class SubmissionRule {
    @SerialName("all") ALL,
    @SerialName("pick") PICK,
}

/** Disclosure limit for selective disclosure credentials. */
@Serializable
enum class LimitDisclosure {
    @SerialName("required") REQUIRED,
    @SerialName("preferred") PREFERRED,
}

/** Whether a constraint is required or merely preferred. */
@Serializable
enum class Optionality {
    @SerialName("required") REQUIRED,
    @SerialName("preferred") PREFERRED,
}

/** Asserts that certain fields belong to the holder. */
@Serializable
data class HolderSubject(
    @SerialName("field_id") val fieldId: List<String>,
    val directive: Optionality,
)

/** Asserts that certain fields share the same subject. */
@Serializable
data class SameSubject(
    @SerialName("field_id") val fieldId: List<String>,
    val directive: Optionality,
)
