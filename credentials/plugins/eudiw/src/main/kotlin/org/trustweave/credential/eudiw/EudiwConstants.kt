package org.trustweave.credential.eudiw

/**
 * Constants for the EU Digital Identity Wallet (EUDIW) Architecture Reference Framework (ARF).
 *
 * Covers:
 * - EU PID (Person Identification Data) claim names and namespaces
 * - mDL doctype
 * - OID4VCI / OID4VP format and profile identifiers
 * - eIDAS 2.0 / EUDIW ARF credential type identifiers
 *
 * References:
 * - EUDIW ARF: https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework
 * - EU PID Rule Book: https://github.com/eu-digital-identity-wallet/eudi-doc-pid-rule-book
 * - ISO/IEC 18013-5 (mDL)
 */
object EudiwConstants {

    // -------------------------------------------------------------------------
    // EU PID namespace and doctype
    // -------------------------------------------------------------------------

    /** ISO mDL / mDoc doctype for EU Person Identification Data. */
    const val PID_DOCTYPE = "eu.europa.ec.eudiw.pid.1"

    /** Namespace for EU PID claims within an mDoc document. */
    const val PID_NAMESPACE = "eu.europa.ec.eudiw.pid.1"

    // -------------------------------------------------------------------------
    // Credential type identifiers
    // -------------------------------------------------------------------------

    /** W3C VC context URL for EUDIW credentials (W3C VC Data Model 2.0). */
    const val EUDIW_CONTEXT = "https://www.w3.org/ns/credentials/v2"

    /** VC `type` value for the EU PID credential (ARF §6.3). */
    const val PID_VC_TYPE = "EudiPersonIdentificationData"

    /** ISO/IEC 18013-5 mDL doctype. */
    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"

    // -------------------------------------------------------------------------
    // OID4VCI credential format identifiers (ARF §6.3)
    // -------------------------------------------------------------------------

    /** SD-JWT VC format identifier, as used in OID4VCI `credential_configurations_supported`. */
    const val CREDENTIAL_FORMAT_SD_JWT_VC = "vc+sd-jwt"

    /** ISO mDoc / mDL format identifier, as used in OID4VCI `credential_configurations_supported`. */
    const val CREDENTIAL_FORMAT_MSO_MDOC = "mso_mdoc"

    // -------------------------------------------------------------------------
    // EU PID claim names (eu.europa.ec.eudiw.pid.1 namespace)
    // -------------------------------------------------------------------------

    /** Current last name(s) or surname(s). */
    const val CLAIM_FAMILY_NAME = "family_name"

    /** Current first name(s), including middle name(s) where applicable. */
    const val CLAIM_GIVEN_NAME = "given_name"

    /** Day, month, and year on which the PID holder was born. ISO 8601 date. */
    const val CLAIM_BIRTH_DATE = "birth_date"

    /** Attests that the PID holder is currently an adult (18+). */
    const val CLAIM_AGE_OVER_18 = "age_over_18"

    /** Current age of the PID holder in full years. */
    const val CLAIM_AGE_IN_YEARS = "age_in_years"

    /** Year of birth of the PID holder. */
    const val CLAIM_AGE_BIRTH_YEAR = "age_birth_year"

    /** Unique identifier for the PID holder within the issuing state. */
    const val CLAIM_UNIQUE_ID = "unique_id"

    /** Last name(s) or surname(s) of the PID holder at the time of birth. */
    const val CLAIM_FAMILY_NAME_BIRTH = "family_name_birth"

    /** First name(s), including middle name(s), of the PID holder at the time of birth. */
    const val CLAIM_GIVEN_NAME_BIRTH = "given_name_birth"

    /** The municipality, city, town, or village where the PID holder was born. */
    const val CLAIM_BIRTH_PLACE = "birth_place"

    /** Country where the PID holder was born. ISO 3166-1 alpha-2. */
    const val CLAIM_BIRTH_COUNTRY = "birth_country"

    /** State, province, district, or local area where the PID holder was born. */
    const val CLAIM_BIRTH_STATE = "birth_state"

    /** Municipality, city, town, or village where the PID holder was born. */
    const val CLAIM_BIRTH_CITY = "birth_city"

    /** Full address of the place where the PID holder currently resides. */
    const val CLAIM_RESIDENT_ADDRESS = "resident_address"

    /** Country where the PID holder currently resides. ISO 3166-1 alpha-2. */
    const val CLAIM_RESIDENT_COUNTRY = "resident_country"

    /** State, province, district, or local area where the PID holder currently resides. */
    const val CLAIM_RESIDENT_STATE = "resident_state"

    /** Municipality, city, town, or village where the PID holder currently resides. */
    const val CLAIM_RESIDENT_CITY = "resident_city"

    /** Postal code of the place where the PID holder currently resides. */
    const val CLAIM_RESIDENT_POSTAL_CODE = "resident_postal_code"

    /** Street of the place where the PID holder currently resides. */
    const val CLAIM_RESIDENT_STREET = "resident_street"

    /** House number of the place where the PID holder currently resides. */
    const val CLAIM_RESIDENT_HOUSE_NUMBER = "resident_house_number"

    /** PID holder's gender. Encoded as per ISO 5218. */
    const val CLAIM_GENDER = "gender"

    /** Nationality of the PID holder. ISO 3166-1 alpha-2. */
    const val CLAIM_NATIONALITY = "nationality"

    /** Date of issue of the PID. ISO 8601 date. */
    const val CLAIM_ISSUANCE_DATE = "issuance_date"

    /** Date of expiry of the PID. ISO 8601 date. */
    const val CLAIM_EXPIRY_DATE = "expiry_date"

    /** Name of the administrative authority that issued the PID. */
    const val CLAIM_ISSUING_AUTHORITY = "issuing_authority"

    /** A number for the PID, assigned by the issuing authority. */
    const val CLAIM_DOCUMENT_NUMBER = "document_number"

    /** Administrative number assigned by the issuing authority. */
    const val CLAIM_ADMINISTRATIVE_NUMBER = "administrative_number"

    /** Alpha-2 country code of the issuing state. ISO 3166-1 alpha-2. */
    const val CLAIM_ISSUING_COUNTRY = "issuing_country"

    /** Country subdivision code of the jurisdiction that issued the PID. ISO 3166-2. */
    const val CLAIM_ISSUING_JURISDICTION = "issuing_jurisdiction"

    /** A reproduction of the PID holder's portrait. */
    const val CLAIM_PORTRAIT = "portrait"

    /** Date when the portrait was captured. ISO 8601 date. */
    const val CLAIM_PORTRAIT_CAPTURE_DATE = "portrait_capture_date"

    /** Driving privileges of the PID holder (mDL specific). */
    const val CLAIM_DRIVING_PRIVILEGES = "driving_privileges"

    /** Distinguishing sign of the issuing country (UN sign). */
    const val CLAIM_UN_DISTINGUISHING_SIGN = "un_distinguishing_sign"
}
