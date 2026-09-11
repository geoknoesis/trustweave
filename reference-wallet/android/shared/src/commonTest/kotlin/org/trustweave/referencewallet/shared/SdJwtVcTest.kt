package org.trustweave.referencewallet.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SdJwtVcTest {
    @Test
    fun headerValuesAreSerializedAsStrings() {
        for (value in listOf("normal", "quote\"value", "back\\slash", "\u00e9")) {
            val jws = SdJwtVc.signCompactJws("{}", value, value) { byteArrayOf(1) }
            val header = Json.parseToJsonElement(Base64Url.decodeString(jws.substringBefore('.'))).jsonObject
            assertEquals(setOf("alg", "kid", "typ"), header.keys)
            assertEquals("EdDSA", header["alg"]!!.jsonPrimitive.content)
            assertEquals(value, header["kid"]!!.jsonPrimitive.content)
            assertEquals(value, header["typ"]!!.jsonPrimitive.content)
        }
    }
}
