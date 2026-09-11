package org.trustweave.credential.internal

import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.uri.UriUtils
import com.apicatalog.rdf.canon.RdfCanon
import com.apicatalog.rdf.nquads.NQuadsWriter
import jakarta.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test

/**
 * SCRATCH adversarial test — NOT part of the suite. Compares the guard predicate
 * UriUtils.isAbsoluteUri(id) against whether a claim triple actually survives JsonLd.toRdf
 * for a credentialSubject with that id. A row where guard==true but the claim is DROPPED is a
 * residual BYPASS (Blocking). Delete after running.
 */
class ScratchUriEquivalenceTest {
    /** Run toRdf directly (NOT through the guard) and return the canonical N-Quads. */
    private fun rawNQuads(subjectId: String?): String {
        val doc =
            buildJsonObject {
                put(
                    "@context",
                    buildJsonArray {
                        add("https://www.w3.org/2018/credentials/v1")
                        add(buildJsonObject { put("name", "https://schema.org/name") })
                    },
                )
                put("type", buildJsonArray { add("VerifiableCredential") })
                put("issuer", "did:key:test")
                put(
                    "credentialSubject",
                    buildJsonObject {
                        if (subjectId != null) put("id", subjectId)
                        put("name", "ScratchClaimValue")
                    },
                )
            }
        val jakarta = Json.createReader(StringReader(doc.toString())).use { it.readObject() }
        return try {
            val canon = RdfCanon.create("SHA-256")
            JsonLd
                .toRdf(JsonDocument.of(jakarta))
                .loader(JsonLdContextLoader.createDocumentLoader())
                .provide(canon)
            val w = StringWriter()
            canon.provide(NQuadsWriter(w))
            w.toString()
        } catch (e: Exception) {
            "<<toRdf threw: " + e.javaClass.simpleName + ": " + e.message + ">>"
        }
    }

    @Test
    fun compareGuardToToRdf() {
        val candidates =
            listOf(
                // prior bypass list
                "urn:has space",
                "did:key:abc def",
                "urn:a^b",
                "urn:a\"b",
                "urn:a`b",
                "urn:a{b}",
                "urn:a|b",
                "urn:a\\b",
                "http://exa mple/x",
                "urn:uuid:with space",
                // valid ids
                "urn:uuid:9bc8be44-7abc-4d29-a8f8-1e2c3d4e5f6a",
                "https://example.com/x",
                "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
                // new adversarial probes
                "urn:a%20b",
                "urn:a%00b",
                "https://例え.jp/p", // non-ASCII Unicode host
                "urn:uuid:café", // non-ASCII in body
                "tag:example.com,2026:thing",
                "thisisaverylongschemenamethatkeepsgoingandgoing:body",
                "URN:UUID:9bc8be44-7abc-4d29-a8f8-1e2c3d4e5f6a",
                "https://x/3#list",
                "urn:",
                "urn:uuid:leadingctrl", // leading control char
                "urn:uuid:trailingctrl", // trailing control char
                " urn:uuid:leadingspace", // leading ASCII space
                "urn:uuid:trailingspace ", // trailing ASCII space
                "//host/path",
                ":foo",
                "#frag",
                "subjects/123",
                "urn:a<b>",
                "urn:a b\tc",
                "mailto:a@b.com",
                "did:web:example.com%3A8443:path",
                "https://[::1]/x", // IPv6 literal
                "https://exa<mple/x",
                "data:text/plain,hi",
                "ht!tp://x/y", // illegal scheme char
                "1http://x/y", // scheme starting with digit
                "urn:uuid:" + "a".repeat(2000), // very long body
            )

        val rows = StringBuilder()
        rows.append("\n========== SCRATCH GUARD vs toRdf EQUIVALENCE ==========\n")
        var bypassFound = 0
        var overReject = 0
        for (id in candidates) {
            val guardOk = UriUtils.isAbsoluteUri(id)
            val nq = rawNQuads(id)
            val claimPresent = nq.contains("ScratchClaimValue")
            val threw = nq.startsWith("<<toRdf threw")
            val label =
                when {
                    guardOk && !claimPresent && !threw -> {
                        bypassFound++
                        "*** BYPASS (guard OK, claim DROPPED) ***"
                    }
                    guardOk && threw -> "guard OK but toRdf THREW (canonicalize fails-closed -> safe)"
                    !guardOk && claimPresent -> {
                        overReject++
                        "OVER-REJECT (guard rejects, toRdf keeps -> safe)"
                    }
                    else -> "match"
                }
            val safeId =
                id
                    .replace("\t", "\\t")
                    .replace("\n", "\\n")
                    .map { if (it.code < 0x20) "?" else it.toString() }
                    .joinToString("")
            rows.append(
                "id=[%-42s] guardOK=%-5s claim=%-5s threw=%-5s | %s\n"
                    .format(safeId.take(42), guardOk, claimPresent, threw, label),
            )
        }
        rows.append("--------------------------------------------------------\n")
        rows.append("BYPASS count (guard OK but claim dropped) = $bypassFound\n")
        rows.append("OVER-REJECT count (safe)                  = $overReject\n")
        rows.append("========================================================\n")
        println(rows.toString())
    }
}
