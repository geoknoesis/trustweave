package org.trustweave.credential.avpmicro

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSmokeTest {
    @Test
    fun `module loads`() {
        assertEquals("ecdsa-jcs-2022", AvpMicro.CRYPTOSUITE)
    }
}
