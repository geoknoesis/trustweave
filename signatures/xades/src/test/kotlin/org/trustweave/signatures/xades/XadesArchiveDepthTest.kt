package org.trustweave.signatures.xades

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.w3c.dom.Element
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.parsers.DocumentBuilderFactory

/** Canonicalising a hostile, absurdly nested unsigned property must fail cleanly instead of overflowing the stack. */
class XadesArchiveDepthTest {
    private val ns = "http://uri.etsi.org/01903/v1.3.2#"

    private fun nested(depth: Int): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().newDocument()
        val root = doc.createElementNS(ns, "xades:Root")
        doc.appendChild(root)
        var current = root
        repeat(depth) {
            val next = doc.createElementNS(ns, "xades:N")
            current.appendChild(next)
            current = next
        }
        return root
    }

    @Test
    fun `an absurdly deep element is refused with IllegalArgumentException`() {
        val failure =
            assertThrows<IllegalArgumentException> {
                XadesArchiveTimestamps.canonicalize(nested(60_000), CanonicalizationMethod.INCLUSIVE)
            }
        assertTrue(failure.message!!.contains("deep"), failure.message)
    }

    @Test
    fun `ordinary nesting still canonicalises`() {
        val bytes = XadesArchiveTimestamps.canonicalize(nested(20), CanonicalizationMethod.INCLUSIVE)
        assertEquals(20, String(bytes).split("<xades:N").size - 1)
    }
}
