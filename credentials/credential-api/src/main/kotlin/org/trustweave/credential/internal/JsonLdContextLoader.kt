package org.trustweave.credential.internal

import com.github.jsonldjava.core.DocumentLoader
import com.github.jsonldjava.core.JsonLdError
import com.github.jsonldjava.core.RemoteDocument
import com.github.jsonldjava.utils.JsonUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline-first JSON-LD context loader for canonicalization.
 *
 * **Security rationale:** JSON-LD canonicalization resolves every `@context` URL of a
 * document. Fetching contexts over the network at signing/verification time is both a
 * determinism hazard (a changed remote context silently changes the signed bytes) and an
 * SSRF/availability hazard. This loader therefore:
 *
 * 1. Serves the core W3C credential and security-suite contexts from classpath resources
 *    bundled with this module (see `org/trustweave/credential/contexts/`).
 * 2. Allows additional contexts to be registered programmatically via [registerContext]
 *    (e.g. issuer-specific claim vocabularies).
 * 3. **Disables remote context fetching by default.** It can be re-enabled explicitly by
 *    setting the system property [ALLOW_REMOTE_CONTEXTS_PROPERTY] to `true`.
 *
 * **Note on the bundled context files:** the jsonld-java library only implements JSON-LD
 * 1.0 context processing and rejects the official W3C context documents outright (they use
 * JSON-LD 1.1 features such as `@version`, `@protected`, type-scoped contexts and `@json`).
 * The bundled resources are therefore JSON-LD 1.0-compatible derivations of the official
 * documents: every term-to-IRI mapping is preserved, with type-scoped term definitions
 * flattened to the top level and 1.1-only keywords removed. Signing and verification both
 * use these same bundled contexts, so canonicalization is deterministic and self-consistent.
 */
internal object JsonLdContextLoader {

    /**
     * System property that re-enables remote JSON-LD context fetching when set to `true`.
     * Disabled by default for security.
     */
    const val ALLOW_REMOTE_CONTEXTS_PROPERTY = "org.trustweave.credential.jsonld.allowRemoteContexts"

    private const val RESOURCE_BASE = "/org/trustweave/credential/contexts"

    private val bundledContextResources: Map<String, String> = mapOf(
        CredentialConstants.VcContexts.VC_1_1 to "$RESOURCE_BASE/credentials-v1.jsonld",
        CredentialConstants.VcContexts.VC_2_0 to "$RESOURCE_BASE/credentials-v2.jsonld",
        CredentialConstants.SecuritySuites.ED25519_2020_V1 to "$RESOURCE_BASE/ed25519-2020-v1.jsonld",
        CredentialConstants.SecuritySuites.JSON_WEB_SIGNATURE_2020_V1 to "$RESOURCE_BASE/jws-2020-v1.jsonld",
        CredentialConstants.SecuritySuites.DATA_INTEGRITY_V2 to "$RESOURCE_BASE/data-integrity-v2.jsonld"
    )

    private val bundledContexts: Map<String, Any> by lazy {
        bundledContextResources.mapValues { (url, resourcePath) ->
            val stream = JsonLdContextLoader::class.java.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException(
                    "Bundled JSON-LD context resource not found on classpath: $resourcePath (for $url)"
                )
            stream.use { JsonUtils.fromInputStream(it) }
        }
    }

    private val registeredContexts = ConcurrentHashMap<String, Any>()

    /**
     * Register an additional JSON-LD context document so it can be resolved offline.
     *
     * Use this for issuer-specific claim vocabularies referenced from credential
     * `@context` arrays. Registration is process-wide.
     *
     * @param url The context URL exactly as it appears in `@context`
     * @param contextJson The full JSON context document as a string
     */
    fun registerContext(url: String, contextJson: String) {
        registeredContexts[url] = JsonUtils.fromString(contextJson)
    }

    /**
     * Create a [DocumentLoader] that resolves bundled and registered contexts offline
     * and refuses remote fetching unless explicitly enabled.
     */
    fun createDocumentLoader(): DocumentLoader = OfflineFirstDocumentLoader()

    private fun isRemoteLoadingAllowed(): Boolean =
        System.getProperty(ALLOW_REMOTE_CONTEXTS_PROPERTY)?.equals("true", ignoreCase = true) == true

    private class OfflineFirstDocumentLoader : DocumentLoader() {
        override fun loadDocument(url: String): RemoteDocument {
            JsonLdContextLoader.bundledContexts[url]?.let { return RemoteDocument(url, it) }
            JsonLdContextLoader.registeredContexts[url]?.let { return RemoteDocument(url, it) }
            if (!JsonLdContextLoader.isRemoteLoadingAllowed()) {
                throw JsonLdError(
                    JsonLdError.Error.LOADING_REMOTE_CONTEXT_FAILED,
                    "Remote JSON-LD context loading is disabled for security. Context '$url' is not " +
                        "bundled and has not been registered. Register it via " +
                        "JsonLdContextLoader.registerContext(url, json) or set the system property " +
                        "-D${JsonLdContextLoader.ALLOW_REMOTE_CONTEXTS_PROPERTY}=true to allow remote fetching."
                )
            }
            return super.loadDocument(url)
        }
    }
}
