package org.trustweave.did.sidetree

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SidetreeLongFormTest {
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun suffixOf(suffixData: JsonObject) = b64.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(suffixData)))

    /** Returns (suffix, base64url initial state) for a consistent initial state. */
    private fun initialState(): Pair<String, String> {
        val delta = buildJsonObject { put("patches", "none") }
        val suffixData =
            buildJsonObject {
                put("deltaHash", b64.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(delta))))
                put("recoveryCommitment", "EiCommitment")
            }
        val state =
            buildJsonObject {
                put("suffixData", suffixData)
                put("delta", delta)
            }
        return suffixOf(suffixData) to b64.encodeToString(state.toString().toByteArray())
    }

    @Test
    fun `parses an anchored long form and derives the canonical short form`() {
        val (suffix, state) = initialState()
        val anchor = "hl:uEiDahaOGH1Nyjlm0zLg7j6cJ2oSGmy4Vi8k3Q4RVQqhK7Q:uoQ-CeEdodHRwczovL29yYi5leGFtcGxl"
        val parsed = assertNotNull(SidetreeLongForm.parse("did:orb", "did:orb:$anchor:$suffix:$state"))
        assertEquals("did:orb:$anchor:$suffix", parsed.canonicalDid)
        assertEquals(suffix, parsed.suffix)
        assertNull(SidetreeLongForm.verifyInitialState(parsed))
    }

    @Test
    fun `parses the un-anchored shape`() {
        val (suffix, state) = initialState()
        assertEquals("did:orb:$suffix", SidetreeLongForm.parse("did:orb", "did:orb:$suffix:$state")?.canonicalDid)
    }

    @Test
    fun `short forms and foreign namespaces are not long forms`() {
        val (suffix, _) = initialState()
        assertNull(SidetreeLongForm.parse("did:orb", "did:orb:$suffix"))
        assertNull(SidetreeLongForm.parse("did:orb", "did:orb:uAnchor:$suffix"))
        assertNull(SidetreeLongForm.parse("did:orb", "did:ion:$suffix:abc"))
    }

    @Test
    fun `an initial state that does not hash to the suffix is rejected`() {
        val (suffix, _) = initialState()
        val other = b64.encodeToString("""{"suffixData":{"recoveryCommitment":"x"}}""".toByteArray())
        assertNotNull(SidetreeLongForm.verifyInitialState(SidetreeLongForm.parse("did:orb", "did:orb:a:$suffix:$other")!!))
        assertNotNull(SidetreeLongForm.verifyInitialState(SidetreeLongForm.parse("did:orb", "did:orb:a:$suffix:!!notbase64")!!))
    }

    @Test
    fun `a delta that does not match deltaHash is rejected`() {
        val suffixData = buildJsonObject { put("deltaHash", "EiWrong") }
        val state =
            buildJsonObject {
                put("suffixData", suffixData)
                put("delta", buildJsonObject { put("patches", "x") })
            }
        val parsed =
            SidetreeLongForm.parse(
                "did:orb",
                "did:orb:${suffixOf(suffixData)}:${b64.encodeToString(state.toString().toByteArray())}",
            )!!
        assertNotNull(SidetreeLongForm.verifyInitialState(parsed))
    }
}
