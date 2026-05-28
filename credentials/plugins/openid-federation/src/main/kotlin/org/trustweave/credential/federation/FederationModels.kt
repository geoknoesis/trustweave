package org.trustweave.credential.federation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenID Federation 1.0 data models.
 *
 * An Entity Statement is a signed JWT that describes an entity within a federation.
 * Entities can be OpenID Providers, Relying Parties, or Federation Entities
 * (intermediaries and trust anchors).
 *
 * Spec: https://openid.net/specs/openid-federation-1_0.html
 */

/**
 * Entity Statement — a signed JWT describing an entity in the federation.
 *
 * When [iss] == [sub], this is a self-signed Entity Configuration published at
 * `{entityId}/.well-known/openid-federation`. When [iss] != [sub], it is a
 * Subordinate Statement issued by the [iss] entity about the [sub] entity.
 */
@Serializable
data class EntityStatement(
    /** Issuer entity identifier (URI). */
    val iss: String,
    /** Subject entity identifier (URI). */
    val sub: String,
    /** Issued at (epoch seconds). */
    val iat: Long,
    /** Expires at (epoch seconds). */
    val exp: Long,
    /** Public keys of the subject entity. */
    val jwks: FederationJwkSet,
    /** Metadata about the subject entity. */
    val metadata: EntityMetadata? = null,
    /**
     * Parent entities in the trust hierarchy that the subject trusts.
     * Present only in Entity Configurations (self-signed statements).
     */
    @SerialName("authority_hints") val authorityHints: List<String>? = null,
    /** Path-length and naming constraints imposed by an intermediate or trust anchor. */
    val constraints: PolicyConstraints? = null,
    /** Trust marks issued to the subject entity. */
    @SerialName("trust_marks") val trustMarks: List<TrustMark>? = null,
    /** Metadata policy to be applied to subordinate entities. */
    @SerialName("metadata_policy") val metadataPolicy: MetadataPolicy? = null,
)

/** JWK Set as used within OpenID Federation Entity Statements. */
@Serializable
data class FederationJwkSet(val keys: List<FederationJwk>)

/**
 * A single JSON Web Key (JWK) as represented in federation entity statements.
 * Only the fields relevant to EC and RSA keys are included.
 */
@Serializable
data class FederationJwk(
    /** Key type, e.g. "EC" or "RSA". */
    val kty: String,
    /** Intended use: "sig" (signature) or "enc" (encryption). */
    val use: String? = null,
    /** Key identifier. */
    val kid: String? = null,
    /** EC curve name, e.g. "P-256". Present for EC keys. */
    val crv: String? = null,
    /** EC public key x-coordinate (Base64url). Present for EC keys. */
    val x: String? = null,
    /** EC public key y-coordinate (Base64url). Present for EC keys. */
    val y: String? = null,
    /** RSA modulus (Base64url). Present for RSA keys. */
    val n: String? = null,
    /** RSA public exponent (Base64url). Present for RSA keys. */
    val e: String? = null,
    /** Algorithm intended for use with this key. */
    val alg: String? = null,
)

/**
 * Entity metadata, keyed by entity type.
 * An entity may expose metadata for multiple roles simultaneously.
 */
@Serializable
data class EntityMetadata(
    /** Metadata applicable when the entity acts as an OpenID Provider. */
    @SerialName("openid_provider") val openidProvider: OpenIdProviderMetadata? = null,
    /** Metadata applicable when the entity acts as an OpenID Relying Party. */
    @SerialName("openid_relying_party") val openidRelyingParty: OpenIdRelyingPartyMetadata? = null,
    /** Metadata applicable when the entity acts as a Federation Entity. */
    @SerialName("federation_entity") val federationEntity: FederationEntityMetadata? = null,
)

/** Metadata for an entity acting as an OpenID Provider. */
@Serializable
data class OpenIdProviderMetadata(
    /** Issuer URL of the OpenID Provider. */
    val issuer: String,
    /** URL of the authorization endpoint. */
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    /** URL of the token endpoint. */
    @SerialName("token_endpoint") val tokenEndpoint: String,
    /** URL of the JWK Set document. */
    @SerialName("jwks_uri") val jwksUri: String? = null,
    /** List of OAuth 2.0 response_type values that this OP supports. */
    @SerialName("response_types_supported") val responseTypesSupported: List<String>? = null,
)

/** Metadata for an entity acting as an OpenID Relying Party. */
@Serializable
data class OpenIdRelyingPartyMetadata(
    /** The client identifier. */
    @SerialName("client_id") val clientId: String,
    /** Redirection URI(s) used by the client. */
    @SerialName("redirect_uris") val redirectUris: List<String>? = null,
    /** Human-readable name of the client. */
    @SerialName("client_name") val clientName: String? = null,
)

/** Metadata for an entity acting as a Federation Entity (intermediate or trust anchor). */
@Serializable
data class FederationEntityMetadata(
    /** Human-readable name of the organization operating the entity. */
    @SerialName("organization_name") val organizationName: String? = null,
    /** URL of the entity's homepage. */
    @SerialName("homepage_uri") val homepageUri: String? = null,
    /** URL of the entity's logo. */
    @SerialName("logo_uri") val logoUri: String? = null,
    /** URL of the entity's privacy policy. */
    @SerialName("policy_uri") val policyUri: String? = null,
    /** URL of the federation_fetch endpoint, used to retrieve Subordinate Statements. */
    @SerialName("federation_fetch_endpoint") val federationFetchEndpoint: String? = null,
    /** URL of the federation_list endpoint, used to list subordinate entities. */
    @SerialName("federation_list_endpoint") val federationListEndpoint: String? = null,
)

/**
 * Constraints that limit which downstream entities and paths are trusted.
 * Defined by an intermediate or trust anchor in their Subordinate Statements.
 */
@Serializable
data class PolicyConstraints(
    /**
     * Maximum number of intermediate federation entities that may appear between
     * this entity and the leaf entity in a trust chain.
     */
    @SerialName("max_path_length") val maxPathLength: Int? = null,
    /** Constraints on entity identifier name forms. */
    @SerialName("naming_constraints") val namingConstraints: NamingConstraints? = null,
)

/** Permitted and excluded name patterns for entity identifiers. */
@Serializable
data class NamingConstraints(
    /** Permitted name patterns (URI prefixes or DNS names). */
    val permitted: List<String>? = null,
    /** Excluded name patterns. */
    val excluded: List<String>? = null,
)

/**
 * A Trust Mark — evidence that a Trust Mark Issuer has verified that a specific
 * entity complies with a particular trust framework or profile.
 */
@Serializable
data class TrustMark(
    /** The Trust Mark type identifier (URI). */
    val id: String,
    /** The Trust Mark itself as a signed JWT. */
    @SerialName("trust_mark") val trustMark: String,
)

/**
 * Metadata policy to be applied to subordinate entities.
 *
 * Each key in the nested maps is a metadata claim name; the value is a
 * [PolicyOperator] describing how the claim value must be merged or constrained.
 */
@Serializable
data class MetadataPolicy(
    /** Policy applied to OpenID Provider metadata claims. */
    @SerialName("openid_provider") val openidProvider: Map<String, PolicyOperator>? = null,
    /** Policy applied to OpenID Relying Party metadata claims. */
    @SerialName("openid_relying_party") val openidRelyingParty: Map<String, PolicyOperator>? = null,
)

/**
 * A single metadata policy operator entry for a specific claim.
 *
 * Operators are applied in order: value → add → default → essential → one_of → subset_of.
 * See §5.1 of the OpenID Federation spec for full semantics.
 */
@Serializable
data class PolicyOperator(
    /** Overrides the claim value with this fixed value. */
    val value: JsonElement? = null,
    /** Adds these values to the claim (for array claims). */
    val add: JsonElement? = null,
    /** Sets the claim to this value if it is absent. */
    val default: JsonElement? = null,
    /** Whether the claim must be present in the resulting metadata. */
    val essential: Boolean? = null,
    /** The claim value must be exactly one of these values. */
    @SerialName("one_of") val oneOf: List<JsonElement>? = null,
    /** The claim value must be a subset of these values. */
    @SerialName("subset_of") val subsetOf: List<JsonElement>? = null,
)

/**
 * Trust Chain — an ordered list of Entity Statement JWTs forming a verified path
 * from a leaf entity up to a trust anchor.
 *
 * The first element is the leaf's Entity Configuration (self-signed).
 * Subsequent elements are Subordinate Statements issued by each superior entity.
 * The last element is the trust anchor's Subordinate Statement (or self-signed
 * Entity Configuration if the trust anchor is the direct superior).
 */
data class TrustChain(
    /** Signed JWT strings ordered from leaf to trust anchor. */
    val statements: List<String>,
    /** Entity identifier of the trust anchor at the top of the chain. */
    val trustAnchorId: String,
    /** Entity identifier of the leaf entity at the bottom of the chain. */
    val leafEntityId: String,
)

/** Result of attempting to resolve a [TrustChain] for a given entity. */
sealed class TrustChainResolutionResult {
    /**
     * A complete, valid trust chain was found.
     *
     * @param chain The resolved trust chain.
     * @param verifiedAt Epoch seconds at which the chain was verified.
     */
    data class Success(val chain: TrustChain, val verifiedAt: Long) : TrustChainResolutionResult()

    /**
     * Trust chain resolution failed.
     *
     * @param reason Human-readable description of the failure.
     * @param entityId The entity identifier for which resolution was attempted.
     */
    data class Failure(val reason: String, val entityId: String) : TrustChainResolutionResult()
}
