package com.trustweave.kms

/**
 * Constants for JSON Web Key (JWK) field names.
 * 
 * These constants should be used instead of string literals when working with JWK objects
 * to prevent typos and ensure consistency.
 * 
 * **Example Usage:**
 * ```kotlin
 * val jwk = mapOf(
 *     JwkKeys.KTY to "EC",
 *     JwkKeys.CRV to "P-256",
 *     JwkKeys.X to xCoordinate,
 *     JwkKeys.Y to yCoordinate
 * )
 * 
 * val keyType = jwk[JwkKeys.KTY]
 * ```
 */
object JwkKeys {
    /**
     * Key type (kty) - identifies the cryptographic algorithm family.
     * Common values: "EC", "RSA", "OKP", "oct"
     */
    const val KTY = "kty"
    
    /**
     * Curve name (crv) - for elliptic curve keys.
     * Common values: "P-256", "P-384", "P-521", "secp256k1", "Ed25519"
     */
    const val CRV = "crv"
    
    /**
     * X coordinate (x) - for EC and OKP keys.
     */
    const val X = "x"
    
    /**
     * Y coordinate (y) - for EC keys.
     */
    const val Y = "y"
    
    /**
     * Modulus (n) - for RSA keys.
     */
    const val N = "n"
    
    /**
     * Public exponent (e) - for RSA keys.
     */
    const val E = "e"
    
    /**
     * Private key (d) - for private keys (not typically in public JWKs).
     */
    const val D = "d"
    
    /**
     * Key ID (kid) - optional key identifier.
     */
    const val KID = "kid"
    
    /**
     * Key use (use) - intended use of the key.
     * Common values: "sig", "enc"
     */
    const val USE = "use"
    
    /**
     * Key operations (key_ops) - array of operations the key is intended for.
     */
    const val KEY_OPS = "key_ops"
    
    /**
     * Algorithm (alg) - algorithm intended for use with the key.
     */
    const val ALG = "alg"
}

/**
 * Constants for JSON Web Key (JWK) type values.
 * 
 * These constants represent the standard values for the "kty" (key type) field in JWK objects.
 */
object JwkKeyTypes {
    /**
     * Elliptic Curve key type.
     */
    const val EC = "EC"
    
    /**
     * RSA key type.
     */
    const val RSA = "RSA"
    
    /**
     * Octet Key Pair (OKP) key type - used for Ed25519, Ed448, X25519, X448.
     */
    const val OKP = "OKP"
    
    /**
     * Octet sequence key type - used for symmetric keys.
     */
    const val OCT = "oct"
}

