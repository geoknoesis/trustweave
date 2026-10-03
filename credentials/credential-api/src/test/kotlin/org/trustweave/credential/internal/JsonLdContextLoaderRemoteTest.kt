package org.trustweave.credential.internal

import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.DocumentLoaderOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.net.URI

class JsonLdContextLoaderRemoteTest {
    private var fetches = 0
    private val original = JsonLdContextLoader.remoteLoader

    private val fakeLoader =
        DocumentLoader { url: URI, _: DocumentLoaderOptions ->
            fetches++
            JsonDocument.of(StringReader("""{"@context":{"x":"https://example.org/x"}}""")).also {
                it.documentUrl = url
            } as Document
        }

    @BeforeEach
    fun setUp() {
        System.setProperty(JsonLdContextLoader.ALLOW_REMOTE_CONTEXTS_PROPERTY, "true")
        System.clearProperty(JsonLdContextLoader.ALLOW_HTTP_CONTEXTS_PROPERTY)
        JsonLdContextLoader.remoteLoader = fakeLoader
        JsonLdContextLoader.clearRemoteContextCache()
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty(JsonLdContextLoader.ALLOW_REMOTE_CONTEXTS_PROPERTY)
        System.clearProperty(JsonLdContextLoader.ALLOW_HTTP_CONTEXTS_PROPERTY)
        JsonLdContextLoader.remoteLoader = original
        JsonLdContextLoader.clearRemoteContextCache()
    }

    private fun load(url: String) = JsonLdContextLoader.createDocumentLoader().loadDocument(URI.create(url), DocumentLoaderOptions())

    @Test
    fun `file URIs are refused even with remote loading enabled`() {
        val e = shouldThrow<JsonLdError> { load("file:///etc/passwd") }
        e.message!! shouldContain "only https"
        fetches shouldBe 0
    }

    @Test
    fun `jar URIs are refused`() {
        shouldThrow<JsonLdError> { load("jar:file:/tmp/x.jar!/ctx.jsonld") }
        fetches shouldBe 0
    }

    @Test
    fun `http requires its own opt-in`() {
        shouldThrow<JsonLdError> { load("http://example.org/ctx") }
        fetches shouldBe 0
        System.setProperty(JsonLdContextLoader.ALLOW_HTTP_CONTEXTS_PROPERTY, "true")
        load("http://example.org/ctx")
        fetches shouldBe 1
    }

    @Test
    fun `https contexts are fetched once and then served from cache`() {
        load("https://example.org/ctx")
        load("https://example.org/ctx")
        fetches shouldBe 1
    }

    @Test
    fun `cache is bounded`() {
        repeat(JsonLdContextLoader.REMOTE_CACHE_MAX_ENTRIES + 10) { load("https://example.org/ctx/$it") }
        JsonLdContextLoader.remoteContextCacheSize() shouldBe JsonLdContextLoader.REMOTE_CACHE_MAX_ENTRIES
    }

    @Test
    fun `remote loading stays disabled by default`() {
        System.clearProperty(JsonLdContextLoader.ALLOW_REMOTE_CONTEXTS_PROPERTY)
        shouldThrow<JsonLdError> { load("https://example.org/ctx") }
        fetches shouldBe 0
    }
}
