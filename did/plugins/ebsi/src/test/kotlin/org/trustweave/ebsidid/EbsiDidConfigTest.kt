package org.trustweave.ebsidid

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EbsiDidConfigTest {
    @Test
    fun `toString redacts the bearer token`() {
        val text = EbsiDidConfig(EbsiDidConfig.PILOT_URL, EbsiNetwork.PILOT, bearerToken = "eyJ.SECRET.TOKEN").toString()
        assertFalse("SECRET" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue(EbsiDidConfig.PILOT_URL in text, text)
    }

    @Test
    fun `resolve-only config prints null token`() {
        assertTrue("bearerToken=null" in EbsiDidConfig(EbsiDidConfig.PILOT_URL, EbsiNetwork.PILOT).toString())
    }
}
