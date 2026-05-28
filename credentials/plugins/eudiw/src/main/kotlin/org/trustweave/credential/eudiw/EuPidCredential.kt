package org.trustweave.credential.eudiw

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type-safe representation of an EU Person Identification Data (PID) credential.
 *
 * This data class captures the mandatory and optional claims defined in the
 * EU PID Rule Book and eIDAS 2.0 Annex V. It is format-neutral: the same object
 * can be serialized as an SD-JWT VC (`vc+sd-jwt`) or as an ISO mDoc (`mso_mdoc`)
 * by an appropriate encoder.
 *
 * Mandatory claims (per eIDAS 2.0 Annex V):
 * - [familyName], [givenName], [birthDate]
 * - [issuingAuthority], [issuingCountry]
 * - [issuanceDate], [expiryDate]
 *
 * References:
 * - EUDIW ARF: https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework
 * - EU PID Rule Book: https://github.com/eu-digital-identity-wallet/eudi-doc-pid-rule-book
 */
@Serializable
data class EuPidCredential(
    /** Current last name(s) or surname(s). */
    @SerialName("family_name") val familyName: String,

    /** Current first name(s), including middle name(s) where applicable. */
    @SerialName("given_name") val givenName: String,

    /** Date of birth. ISO 8601 full-date format (YYYY-MM-DD). */
    @SerialName("birth_date") val birthDate: String,

    /** Attestation that the holder is currently 18 years of age or older. */
    @SerialName("age_over_18") val ageOver18: Boolean? = null,

    /** Current age of the PID holder in full years. */
    @SerialName("age_in_years") val ageInYears: Int? = null,

    /** Year of birth of the PID holder. */
    @SerialName("age_birth_year") val ageBirthYear: Int? = null,

    /** Unique identifier for the PID holder within the issuing state or organization. */
    @SerialName("unique_id") val uniqueId: String? = null,

    /** Name of the administrative authority that has issued this PID. */
    @SerialName("issuing_authority") val issuingAuthority: String,

    /** Alpha-2 country code of the PID-issuing state or territory. ISO 3166-1 alpha-2. */
    @SerialName("issuing_country") val issuingCountry: String,

    /** Date of issue of the PID. ISO 8601 full-date format. */
    @SerialName("issuance_date") val issuanceDate: String,

    /** Date of expiry of the PID. ISO 8601 full-date format. */
    @SerialName("expiry_date") val expiryDate: String,

    /** A number for the PID, assigned by the issuing authority. */
    @SerialName("document_number") val documentNumber: String? = null,

    /** An administrative number assigned by the issuing authority. */
    @SerialName("administrative_number") val administrativeNumber: String? = null,

    /**
     * Nationality of the PID holder.
     * ISO 3166-1 alpha-2 country code, or a comma-separated list for dual/multi-national holders.
     */
    @SerialName("nationality") val nationality: String? = null,

    /** PID holder's gender, encoded as per ISO 5218 (0 = not known, 1 = male, 2 = female, 9 = not applicable). */
    @SerialName("gender") val gender: Int? = null,

    /** The municipality, city, town, or village where the PID holder was born. */
    @SerialName("birth_place") val birthPlace: String? = null,

    /** Country where the PID holder was born. ISO 3166-1 alpha-2. */
    @SerialName("birth_country") val birthCountry: String? = null,

    /** Full address of the place where the PID holder currently resides. */
    @SerialName("resident_address") val residentAddress: String? = null,

    /** Country where the PID holder currently resides. ISO 3166-1 alpha-2. */
    @SerialName("resident_country") val residentCountry: String? = null,

    /** Municipality, city, town, or village where the PID holder currently resides. */
    @SerialName("resident_city") val residentCity: String? = null,

    /** Postal code of the PID holder's current residence. */
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
) {

    /**
     * Converts this credential to a flat [Map] of claim name → value, suitable
     * for use as the claims payload when constructing an SD-JWT VC or mDoc.
     *
     * Only non-null fields are included. Claim names use the
     * `eu.europa.ec.eudiw.pid.1` namespace conventions as defined in
     * [EudiwConstants].
     */
    fun toClaims(): Map<String, Any> = buildMap {
        put(EudiwConstants.CLAIM_FAMILY_NAME, familyName)
        put(EudiwConstants.CLAIM_GIVEN_NAME, givenName)
        put(EudiwConstants.CLAIM_BIRTH_DATE, birthDate)
        ageOver18?.let { put(EudiwConstants.CLAIM_AGE_OVER_18, it) }
        ageInYears?.let { put(EudiwConstants.CLAIM_AGE_IN_YEARS, it) }
        ageBirthYear?.let { put(EudiwConstants.CLAIM_AGE_BIRTH_YEAR, it) }
        uniqueId?.let { put(EudiwConstants.CLAIM_UNIQUE_ID, it) }
        put(EudiwConstants.CLAIM_ISSUING_AUTHORITY, issuingAuthority)
        put(EudiwConstants.CLAIM_ISSUING_COUNTRY, issuingCountry)
        put(EudiwConstants.CLAIM_ISSUANCE_DATE, issuanceDate)
        put(EudiwConstants.CLAIM_EXPIRY_DATE, expiryDate)
        documentNumber?.let { put(EudiwConstants.CLAIM_DOCUMENT_NUMBER, it) }
        administrativeNumber?.let { put(EudiwConstants.CLAIM_ADMINISTRATIVE_NUMBER, it) }
        nationality?.let { put(EudiwConstants.CLAIM_NATIONALITY, it) }
        gender?.let { put(EudiwConstants.CLAIM_GENDER, it) }
        birthPlace?.let { put(EudiwConstants.CLAIM_BIRTH_PLACE, it) }
        birthCountry?.let { put(EudiwConstants.CLAIM_BIRTH_COUNTRY, it) }
        residentAddress?.let { put(EudiwConstants.CLAIM_RESIDENT_ADDRESS, it) }
        residentCountry?.let { put(EudiwConstants.CLAIM_RESIDENT_COUNTRY, it) }
        residentCity?.let { put(EudiwConstants.CLAIM_RESIDENT_CITY, it) }
        residentPostalCode?.let { put(EudiwConstants.CLAIM_RESIDENT_POSTAL_CODE, it) }
    }
}
