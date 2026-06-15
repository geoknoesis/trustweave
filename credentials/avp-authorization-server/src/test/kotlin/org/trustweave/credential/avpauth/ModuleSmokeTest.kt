package org.trustweave.credential.avpauth

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSmokeTest {
    @Test fun `module loads`() = assertEquals("avp-authorization-server", SERVICE_NAME)
}
