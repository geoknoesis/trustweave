package org.trustweave.did.orb

/**
 * Configuration for the did:orb DID method implementation.
 *
 * Orb is a TrustBloc / DIF project that implements the Sidetree protocol with
 * federated batching and anchoring (typically via ActivityPub / CAS-backed
 * witnesses). It exposes the standard Sidetree REST endpoints:
 *
 *  - POST `{baseUrl}/sidetree/v1/operations` — submit a create/update/recover/deactivate
 *  - GET  `{baseUrl}/sidetree/v1/identifiers/{did}` — resolve a DID
 *
 * **Example usage:**
 * ```kotlin
 * val cfg = OrbDidConfig(baseUrl = "https://orb.example.com")
 * val method = OrbDidMethod(kms, cfg)
 * ```
 *
 * @property baseUrl Base URL of the Orb node (without trailing slash).
 * @property namespace The DID namespace produced by the Orb node. Defaults to
 *                     `did:orb`. Some operators run private namespaces (e.g.
 *                     `did:orb:uAAA...`); pass them here.
 * @property operationsPath Path component used to POST operations. Defaults to
 *                          `/sidetree/v1/operations` per the Sidetree REST API.
 * @property identifiersPath Path component used to GET identifiers. Defaults to
 *                           `/sidetree/v1/identifiers`.
 * @property authHeader Optional HTTP header pair (name, value) for authenticated
 *                      Orb deployments. `null` for anonymous access.
 * @property timeoutSeconds HTTP client timeout in seconds (default 30).
 * @property anchorOrigin Value emitted as `anchorOrigin` in create and recover
 *                        operations. Defaults to [baseUrl]. Override when the
 *                        Orb node is reachable from the JVM at a different URL
 *                        than the one it advertises (e.g. inside Docker where
 *                        the JVM talks to `localhost:<random>` but Orb's
 *                        `--allowed-origins` lists `host.docker.internal:48326`).
 *                        Whatever value you pass MUST also appear in Orb's
 *                        `--allowed-origins`.
 * @property operatorId Stable identifier of the Orb node operator. Used to
 *                      build the [org.trustweave.anchor.payment.AssetRef.OperatorCredit]
 *                      asset reference surfaced through fee estimation and
 *                      anchor results. Defaults to the host component of
 *                      [baseUrl] (or [baseUrl] itself when no host can be
 *                      parsed). Operators with multiple base URLs (blue/green,
 *                      Docker host bridging) should set this explicitly so
 *                      credit accounting remains stable.
 */
data class OrbDidConfig(
    val baseUrl: String,
    val namespace: String = DEFAULT_NAMESPACE,
    val operationsPath: String = DEFAULT_OPERATIONS_PATH,
    val identifiersPath: String = DEFAULT_IDENTIFIERS_PATH,
    val authHeader: Pair<String, String>? = null,
    val timeoutSeconds: Long = 30L,
    val anchorOrigin: String? = null,
    val operatorId: String? = null,
) {

    init {
        require(baseUrl.isNotBlank()) { "OrbDidConfig.baseUrl must not be blank" }
        require(namespace.startsWith("did:")) {
            "OrbDidConfig.namespace must start with 'did:' (got '$namespace')"
        }
    }

    /** Fully qualified URL for the operations endpoint. */
    val operationsUrl: String get() = "${baseUrl.trimEnd('/')}$operationsPath"

    /** Fully qualified URL for the identifiers endpoint (without DID suffix). */
    val identifiersUrl: String get() = "${baseUrl.trimEnd('/')}$identifiersPath"

    /**
     * Effective operator id — explicit [operatorId] when set, otherwise the
     * host component of [baseUrl]. Falls back to the trimmed [baseUrl] when
     * URI parsing fails (defensive; init already rejects blank values).
     */
    val effectiveOperatorId: String
        get() = operatorId ?: runCatching { java.net.URI(baseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: baseUrl.trimEnd('/')

    /**
     * CAIP-2-style chain identifier for this Orb node. Synthesised as
     * `orb:<operator-id>` so fee accounting and ledger entries stay scoped
     * per operator.
     */
    val chainId: String get() = "orb:$effectiveOperatorId"

    companion object {
        const val DEFAULT_NAMESPACE: String = "did:orb"
        const val DEFAULT_OPERATIONS_PATH: String = "/sidetree/v1/operations"
        const val DEFAULT_IDENTIFIERS_PATH: String = "/sidetree/v1/identifiers"

        /**
         * Creates an [OrbDidConfig] from a property map (used by the SPI provider).
         *
         * Recognised keys: `baseUrl` (required), `namespace`, `operationsPath`,
         * `identifiersPath`, `authHeaderName` + `authHeaderValue`, `timeoutSeconds`.
         */
        fun fromMap(map: Map<String, Any?>): OrbDidConfig {
            val baseUrl = map["baseUrl"] as? String
                ?: map["orbBaseUrl"] as? String
                ?: throw IllegalArgumentException(
                    "did:orb requires 'baseUrl' (Orb node URL) in DidCreationOptions.additionalProperties",
                )
            val authName = map["authHeaderName"] as? String
            val authValue = map["authHeaderValue"] as? String
            val authHeader: Pair<String, String>? =
                if (authName != null && authValue != null) authName to authValue else null
            return OrbDidConfig(
                baseUrl = baseUrl,
                namespace = map["namespace"] as? String ?: DEFAULT_NAMESPACE,
                operationsPath = map["operationsPath"] as? String ?: DEFAULT_OPERATIONS_PATH,
                identifiersPath = map["identifiersPath"] as? String ?: DEFAULT_IDENTIFIERS_PATH,
                authHeader = authHeader,
                timeoutSeconds = (map["timeoutSeconds"] as? Number)?.toLong() ?: 30L,
                anchorOrigin = map["anchorOrigin"] as? String,
                operatorId = map["operatorId"] as? String,
            )
        }
    }
}
