package org.trustweave.hashicorpkms

import com.bettercloud.vault.api.Logical
import com.bettercloud.vault.response.LogicalResponse
import com.bettercloud.vault.rest.RestResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaultPublicKeyResponseTest {
    private fun response(data: String) =
        LogicalResponse(
            RestResponse(200, "application/json", """{"data":$data}""".toByteArray()),
            0,
            Logical.logicalOperations.readV1,
        )

    @Test
    fun `real driver preserves nested versioned keys only in structured data`() {
        val parsed = response("""{"latest_version":2,"keys":{"1":{"public_key":"old"},"2":{"public_key":"current"}}}""")
        assertEquals(String::class.java, parsed.data["keys"]!!.javaClass)
        assertEquals("current", VaultPublicKeyResponse.extract(parsed))
    }

    @Test
    fun `missing malformed and ambiguous versions never fall back to an old key`() {
        for (data in listOf(
            "{}",
            """{"keys":{"1":{"public_key":"old"}}}""",
            """{"latest_version":"1","keys":{"1":{"public_key":"old"}}}""",
            """{"latest_version":1.5,"keys":{"1":{"public_key":"old"}}}""",
            """{"latest_version":0,"keys":{}}""",
            """{"latest_version":2,"keys":{"1":{"public_key":"old"}}}""",
            """{"latest_version":1,"keys":"not-an-object"}""",
            """{"latest_version":1,"keys":{"1":{"public_key":null}}}""",
            """{"latest_version":1,"keys":{"1":{"public_key":" "}}}""",
        )) {
            assertNull(VaultPublicKeyResponse.extract(response(data)), data)
        }
    }
}
