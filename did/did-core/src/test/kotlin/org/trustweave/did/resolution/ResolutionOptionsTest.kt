package org.trustweave.did.resolution

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.resolver.DidErrorType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolutionOptionsTest {

    @Test
    fun `EMPTY is empty`() {
        assertTrue(ResolutionOptions.EMPTY.isEmpty())
    }

    @Test
    fun `any populated option makes it non-empty`() {
        assertFalse(ResolutionOptions(accept = "application/did").isEmpty())
        assertFalse(ResolutionOptions(expandRelativeUrls = true).isEmpty())
        assertFalse(ResolutionOptions(noCache = true).isEmpty())
    }

    @Test
    fun `accept and expandRelativeUrls are not method specific`() {
        val options = ResolutionOptions(accept = "application/did", expandRelativeUrls = true)
        assertTrue(options.methodSpecificOptions().isEmpty())
    }

    @Test
    fun `versionId versionTime and noCache are method specific`() {
        assertEquals(setOf("versionId"), ResolutionOptions(versionId = "3").methodSpecificOptions())
        assertEquals(
            setOf("versionTime"),
            ResolutionOptions(versionTime = Instant.parse("2021-05-10T17:00:00Z")).methodSpecificOptions()
        )
        assertEquals(setOf("noCache"), ResolutionOptions(noCache = true).methodSpecificOptions())
    }

    @Test
    fun `versionId and versionTime are mutually exclusive`() {
        val error = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
        ).validate()
        assertEquals(DidErrorType.INVALID_OPTIONS, error?.type)
    }

    @Test
    fun `valid options produce no error`() {
        assertNull(ResolutionOptions(accept = "application/did").validate())
    }

    @Test
    fun `an unparseable versionTime is rejected by fromQueryParameters`() {
        val options = ResolutionOptions.fromQueryParameters(mapOf("versionTime" to "not-a-date"))
        assertEquals(DidErrorType.INVALID_OPTIONS, options.validate()?.type)
    }

    @Test
    fun `fromQueryParameters reads the spec-defined options`() {
        val options = ResolutionOptions.fromQueryParameters(
            mapOf(
                "versionId" to "3",
                "expandRelativeUrls" to "true",
                "noCache" to "true",
                "blockHeight" to "9001"
            )
        )
        assertEquals("3", options.versionId)
        assertTrue(options.expandRelativeUrls)
        assertTrue(options.noCache)
        assertEquals("9001", options.additional["blockHeight"])
    }
}
