package org.trustweave.did.model

import kotlin.test.*

/**
 * Tests for ServiceEndpointExtensions.
 *
 * Verifies type-safe access to service endpoints.
 */
class ServiceEndpointExtensionsTest {

    @Test
    fun `test serviceEndpointTyped with URL string`() {
        val service = DidService(
            id = "service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        val typed = service.serviceEndpointTyped()
        assertNotNull(typed)
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
            type = "DIDCommMessaging",
            serviceEndpoint = endpointMap
        )

        val typed = service.serviceEndpointTyped()
        assertNotNull(typed)
        assertTrue(typed is ServiceEndpoint.ObjectEndpoint)
        assertEquals("https://example.com", typed.data["uri"])
        assertEquals(listOf("key1", "key2"), typed.data["routingKeys"])
    }

    @Test
    fun `test serviceEndpointTyped with array`() {
        val endpointArray = listOf(
            "https://example.com/endpoint1",
            "https://example.com/endpoint2"
        )
        val service = DidService(
            id = "service-1",
            type = "LinkedDomains",
            serviceEndpoint = endpointArray
        )

        val typed = service.serviceEndpointTyped()
        assertNotNull(typed)
        assertTrue(typed is ServiceEndpoint.ArrayEndpoint)
        assertEquals(2, typed.endpoints.size)
        assertTrue(typed.endpoints[0] is ServiceEndpoint.Url)
        assertTrue(typed.endpoints[1] is ServiceEndpoint.Url)
    }

    @Test
    fun `test serviceEndpointTyped with mixed array`() {
        val endpointArray = listOf(
            "https://example.com/endpoint1",
            mapOf("uri" to "https://example.com/endpoint2", "type" to "custom")
        )
        val service = DidService(
            id = "service-1",
            type = "CustomService",
            serviceEndpoint = endpointArray
        )

        val typed = service.serviceEndpointTyped()
        assertNotNull(typed)
        assertTrue(typed is ServiceEndpoint.ArrayEndpoint)
        assertEquals(2, typed.endpoints.size)
        assertTrue(typed.endpoints[0] is ServiceEndpoint.Url)
        assertTrue(typed.endpoints[1] is ServiceEndpoint.ObjectEndpoint)
    }

    @Test
    fun `test serviceEndpointAsUrl with URL string`() {
        val service = DidService(
            id = "service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        val url = service.serviceEndpointAsUrl()
        assertNotNull(url)
        assertEquals("https://example.com", url)
    }

    @Test
    fun `test serviceEndpointAsUrl with object returns null`() {
        val service = DidService(
            id = "service-1",
            type = "DIDCommMessaging",
            serviceEndpoint = mapOf("uri" to "https://example.com")
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
            type = "DIDCommMessaging",
            serviceEndpoint = endpointMap
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
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
        )

        val obj = service.serviceEndpointAsObject()
        assertNull(obj)
    }

    @Test
    fun `test serviceEndpointAsArray with array`() {
        val endpointArray = listOf("https://example.com/1", "https://example.com/2")
        val service = DidService(
            id = "service-1",
            type = "LinkedDomains",
            serviceEndpoint = endpointArray
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
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com"
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
        assertEquals("https://example.com/1", (converted as List<*>)[0])
        assertTrue((converted as List<*>)[1] is Map<*, *>)
    }

    @Test
    fun `test serviceEndpointTyped with invalid type returns null`() {
        // Using a number which is not a valid service endpoint type
        val service = DidService(
            id = "service-1",
            type = "Test",
            serviceEndpoint = 12345  // Invalid type
        )

        val typed = service.serviceEndpointTyped()
        assertNull(typed)
    }

    @Test
    fun `test serviceEndpointTyped with array containing invalid items`() {
        val endpointArray = listOf(
            "https://example.com",
            12345  // Invalid item in array
        )
        val service = DidService(
            id = "service-1",
            type = "Test",
            serviceEndpoint = endpointArray
        )

        // Should return null because array contains invalid items
        val typed = service.serviceEndpointTyped()
        assertNull(typed)
    }
}

