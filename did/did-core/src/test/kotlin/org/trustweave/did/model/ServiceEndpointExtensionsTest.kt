package org.trustweave.did.model

import kotlin.test.*

/**
 * Tests for ServiceEndpointExtensions.
 *
 * Verifies type-safe access to service endpoints now that
 * [DidService.serviceEndpoint] is the sealed [ServiceEndpoint] type.
 */
class ServiceEndpointExtensionsTest {

    @Test
    fun `test serviceEndpointTyped with URL string`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.Url("https://example.com")
        )

        val typed = service.serviceEndpointTyped()
        assertTrue(typed is ServiceEndpoint.Url)
        assertEquals("https://example.com", typed.url)
    }

    @Test
    fun `test serviceEndpointTyped with object map`() {
        val endpointMap = mapOf(
            "uri" to "https://example.com",
            "routingKeys" to listOf("key1", "key2")
        )
        val service = DidService(
            id = "service-1",
            type = listOf("DIDCommMessaging"),
            serviceEndpoint = ServiceEndpoint.ObjectEndpoint(endpointMap)
        )

        val typed = service.serviceEndpointTyped()
        assertTrue(typed is ServiceEndpoint.ObjectEndpoint)
        assertEquals("https://example.com", typed.data["uri"])
        assertEquals(listOf("key1", "key2"), typed.data["routingKeys"])
    }

    @Test
    fun `test serviceEndpointTyped with array`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.of(
                listOf(
                    "https://example.com/endpoint1",
                    "https://example.com/endpoint2"
                )
            )
        )

        val typed = service.serviceEndpointTyped()
        assertTrue(typed is ServiceEndpoint.ArrayEndpoint)
        assertEquals(2, typed.endpoints.size)
        assertTrue(typed.endpoints[0] is ServiceEndpoint.Url)
        assertTrue(typed.endpoints[1] is ServiceEndpoint.Url)
    }

    @Test
    fun `test serviceEndpointTyped with mixed array`() {
        val service = DidService(
            id = "service-1",
            type = listOf("CustomService"),
            serviceEndpoint = ServiceEndpoint.of(
                listOf(
                    "https://example.com/endpoint1",
                    mapOf("uri" to "https://example.com/endpoint2", "type" to "custom")
                )
            )
        )

        val typed = service.serviceEndpointTyped()
        assertTrue(typed is ServiceEndpoint.ArrayEndpoint)
        assertEquals(2, typed.endpoints.size)
        assertTrue(typed.endpoints[0] is ServiceEndpoint.Url)
        assertTrue(typed.endpoints[1] is ServiceEndpoint.ObjectEndpoint)
    }

    @Test
    fun `test serviceEndpointAsUrl with URL string`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.Url("https://example.com")
        )

        val url = service.serviceEndpointAsUrl()
        assertNotNull(url)
        assertEquals("https://example.com", url)
    }

    @Test
    fun `test serviceEndpointAsUrl with object returns null`() {
        val service = DidService(
            id = "service-1",
            type = listOf("DIDCommMessaging"),
            serviceEndpoint = ServiceEndpoint.ObjectEndpoint(mapOf("uri" to "https://example.com"))
        )

        val url = service.serviceEndpointAsUrl()
        assertNull(url)
    }

    @Test
    fun `test serviceEndpointAsObject with object map`() {
        val endpointMap = mapOf(
            "uri" to "https://example.com",
            "routingKeys" to listOf("key1")
        )
        val service = DidService(
            id = "service-1",
            type = listOf("DIDCommMessaging"),
            serviceEndpoint = ServiceEndpoint.ObjectEndpoint(endpointMap)
        )

        val obj = service.serviceEndpointAsObject()
        assertNotNull(obj)
        assertEquals("https://example.com", obj["uri"])
        assertEquals(listOf("key1"), obj["routingKeys"])
    }

    @Test
    fun `test serviceEndpointAsObject with URL returns null`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.Url("https://example.com")
        )

        val obj = service.serviceEndpointAsObject()
        assertNull(obj)
    }

    @Test
    fun `test serviceEndpointAsArray with array`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.of(listOf("https://example.com/1", "https://example.com/2"))
        )

        val array = service.serviceEndpointAsArray()
        assertNotNull(array)
        assertEquals(2, array.size)
        assertEquals("https://example.com/1", array[0])
        assertEquals("https://example.com/2", array[1])
    }

    @Test
    fun `test serviceEndpointAsArray with URL returns null`() {
        val service = DidService(
            id = "service-1",
            type = listOf("LinkedDomains"),
            serviceEndpoint = ServiceEndpoint.Url("https://example.com")
        )

        val array = service.serviceEndpointAsArray()
        assertNull(array)
    }

    @Test
    fun `test ServiceEndpoint toAny conversion`() {
        val urlEndpoint = ServiceEndpoint.Url("https://example.com")
        assertEquals("https://example.com", urlEndpoint.toAny())

        val objectEndpoint = ServiceEndpoint.ObjectEndpoint(
            mapOf("uri" to "https://example.com")
        )
        val obj = objectEndpoint.toAny()
        assertTrue(obj is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        assertEquals("https://example.com", (obj as Map<String, Any?>)["uri"])

        val arrayEndpoint = ServiceEndpoint.ArrayEndpoint(
            listOf(ServiceEndpoint.Url("https://example.com/1"))
        )
        val arr = arrayEndpoint.toAny()
        assertTrue(arr is List<*>)
        assertEquals("https://example.com/1", (arr as List<*>)[0])
    }

    @Test
    fun `test ServiceEndpoint nested array conversion`() {
        val nestedArray = ServiceEndpoint.ArrayEndpoint(
            listOf(
                ServiceEndpoint.Url("https://example.com/1"),
                ServiceEndpoint.ObjectEndpoint(mapOf("uri" to "https://example.com/2"))
            )
        )

        val converted = nestedArray.toAny()
        assertTrue(converted is List<*>)
        assertEquals(2, (converted as List<*>).size)
        assertEquals("https://example.com/1", converted[0])
        assertTrue(converted[1] is Map<*, *>)
    }

    @Test
    fun `test ServiceEndpoint of with invalid type throws`() {
        // A number is not a valid service endpoint type.
        assertFailsWith<IllegalArgumentException> {
            ServiceEndpoint.of(12345)
        }
    }

    @Test
    fun `test ServiceEndpoint ofOrNull with array containing invalid items returns null`() {
        val endpointArray = listOf(
            "https://example.com",
            12345 // Invalid item in array
        )

        // Should return null because the array contains an invalid item.
        assertNull(ServiceEndpoint.ofOrNull(endpointArray))
    }
}
