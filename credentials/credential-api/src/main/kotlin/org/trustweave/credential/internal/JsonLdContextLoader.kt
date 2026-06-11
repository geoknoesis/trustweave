package org.trustweave.credential.internal

import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdErrorCode
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.DocumentLoaderOptions
import com.apicatalog.jsonld.loader.SchemeRouter
import java.io.StringReader
import java.net.URI
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
 * The bundled context files are the **official, unmodified W3C context documents**
 * (JSON-LD 1.1). They are processed by titanium-json-ld, a conformant JSON-LD 1.1
 * processor, so the canonical N-Quads produced here are interoperable with other
 * conformant verifiers.
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

    private val bundledContexts: Map<String, Document> by lazy {
        bundledContextResources.mapValues { (url, resourcePath) ->
            val stream = JsonLdContextLoader::class.java.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException(
                    "Bundled JSON-LD context resource not found on classpath: $resourcePath (for $url)"
                )
            stream.use { input ->
                JsonDocument.of(input).also { it.documentUrl = URI.create(url) }
            }
        }
    }

    private val registeredContexts = ConcurrentHashMap<String, Document>()

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
        registeredContexts[url] = JsonDocument.of(StringReader(contextJson)).also {
            it.documentUrl = URI.create(url)
        }
    }

    /**
     * Create a [DocumentLoader] that resolves bundled and registered contexts offline
     * and refuses remote fetching unless explicitly enabled.
     */
    fun createDocumentLoader(): DocumentLoader = OfflineFirstDocumentLoader

    private fun isRemoteLoadingAllowed(): Boolean =
        System.getProperty(ALLOW_REMOTE_CONTEXTS_PROPERTY)?.equals("true", ignoreCase = true) == true

    private object OfflineFirstDocumentLoader : DocumentLoader {
        override fun loadDocument(url: URI, options: DocumentLoaderOptions): Document {
            val key = url.toString()
            JsonLdContextLoader.bundledContexts[key]?.let { return it }
            JsonLdContextLoader.registeredContexts[key]?.let { return it }
            if (!JsonLdContextLoader.isRemoteLoadingAllowed()) {
                throw JsonLdError(
                    JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                    "Remote JSON-LD context loading is disabled for security. Context '$url' is not " +
                        "bundled and has not been registered. Register it via " +
                        "JsonLdContexts.register(url, json) or set the system property " +
                        "-D${JsonLdContextLoader.ALLOW_REMOTE_CONTEXTS_PROPERTY}=true to allow remote fetching."
                )
            }
            // Explicitly enabled remote loading: delegate to titanium's default scheme
            // router (HTTP/HTTPS via java.net.http, file URIs via FileLoader).
            return SchemeRouter.defaultInstance().loadDocument(url, options)
        }
    }
}
