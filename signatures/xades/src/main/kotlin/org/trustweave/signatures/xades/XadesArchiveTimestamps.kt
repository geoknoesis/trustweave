package org.trustweave.signatures.xades

import org.trustweave.signatures.revocation.TimeStampTokenVerifier
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.NodeSetData
import javax.xml.crypto.OctetStreamData
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Instant

/**
 * The XAdES `ArchiveTimeStamp` unsigned property (XAdES 1.4.1, ETSI EN 319 132-1 §5.5.3.1): an RFC 3161
 * token whose message imprint is the digest of the concatenation of
 *
 * 1. the dereferenced and transformed octets of every `ds:Reference` of `ds:SignedInfo`, in order,
 * 2. the canonical form of `ds:SignedInfo`, `ds:SignatureValue` and `ds:KeyInfo`, and
 * 3. the canonical form of every unsigned signature property that precedes the `ArchiveTimeStamp`
 *    in document order (the `SignatureTimeStamp`, `CertificateValues`, `RevocationValues` and any
 *    earlier `ArchiveTimeStamp`).
 *
 * The signed properties are covered through their `ds:Reference` (item 1).
 */
internal object XadesArchiveTimestamps {
    const val XADES141_NS = "http://uri.etsi.org/01903/v1.4.1#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"

    /** Deepest element nesting an archived property may have; real properties are a handful of levels deep. */
    private const val MAX_DEPTH = 200

    sealed class Outcome {
        /** No `ArchiveTimeStamp` element at all. */
        data object None : Outcome()

        /** An archive time-stamp is present but no TSA trust anchor was configured, so it proves nothing. */
        data object NoAnchors : Outcome()

        /**
         * Every archive time-stamp verified; [genTime] is the earliest. [lastStamp] is the last one in document order:
         * only the unsigned properties before it are covered by an imprint.
         */
        data class Valid(
            val genTime: Instant,
            val lastStamp: Element,
        ) : Outcome()

        data class Invalid(
            val reason: String,
        ) : Outcome()
    }

    /** The `ds:Reference` octets a signer or verifier captured (needs the `cacheReference` property). */
    fun referenceOctets(signature: XMLSignature): List<ByteArray>? =
        signature.signedInfo.references.map { (it.digestInputStream ?: return null).readBytes() }

    /** The octets an archive time-stamp covers, given the unsigned properties [preceding] it. */
    fun imprintInput(
        signatureElement: Element,
        referenceData: List<ByteArray>,
        preceding: List<Element>,
        c14nAlgorithm: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        referenceData.forEach { out.write(it) }
        listOf("SignedInfo", "SignatureValue", "KeyInfo").forEach { name ->
            val found = childElements(signatureElement, DS_NS, name)
            require(found.size <= 1) { "more than one <ds:$name>" }
            found.singleOrNull()?.let { out.write(canonicalize(it, c14nAlgorithm)) }
        }
        preceding.forEach { out.write(canonicalize(it, c14nAlgorithm)) }
        return out.toByteArray()
    }

    fun unsignedProperties(qualifyingProperties: Element): List<Element> =
        childElements(qualifyingProperties, XADES_NS, "UnsignedProperties")
            .flatMap { childElements(it, XADES_NS, "UnsignedSignatureProperties") }
            .flatMap { elementChildren(it) }

    fun evaluate(
        qualifyingProperties: Element,
        signatureElement: Element,
        referenceData: List<ByteArray>?,
        anchors: List<X509Certificate>,
        c14nAlgorithms: Set<String>,
        signatureTimeStamp: Instant?,
    ): Outcome {
        val properties = unsignedProperties(qualifyingProperties)
        val stamps = properties.filter { it.namespaceURI == XADES141_NS && it.localName == "ArchiveTimeStamp" }
        if (stamps.isEmpty()) return Outcome.None
        if (anchors.isEmpty()) return Outcome.NoAnchors
        if (referenceData == null) return Outcome.Invalid("the signed data objects are not available to recompute the archive imprint")
        var previous: Instant? = signatureTimeStamp
        var earliest: Instant? = null
        for (stamp in stamps) {
            val preceding = properties.takeWhile { it !== stamp }
            when (val one = verifyOne(stamp, preceding, signatureElement, referenceData, anchors, c14nAlgorithms)) {
                is Outcome.Valid -> {
                    if (previous != null && one.genTime < previous) {
                        return Outcome.Invalid("archive time-stamp (${one.genTime}) predates the time-stamp it covers ($previous)")
                    }
                    previous = one.genTime
                    if (earliest == null) earliest = one.genTime
                }
                is Outcome.Invalid -> return one
                else -> return Outcome.Invalid("unexpected archive time-stamp state")
            }
        }
        return Outcome.Valid(earliest!!, stamps.last())
    }

    private fun verifyOne(
        stamp: Element,
        preceding: List<Element>,
        signatureElement: Element,
        referenceData: List<ByteArray>,
        anchors: List<X509Certificate>,
        c14nAlgorithms: Set<String>,
    ): Outcome {
        val c14n =
            childElements(stamp, DS_NS, "CanonicalizationMethod").singleOrNull()?.getAttribute("Algorithm")
                ?: CanonicalizationMethod.INCLUSIVE
        if (c14n !in c14nAlgorithms) return Outcome.Invalid("unsupported archive time-stamp canonicalization '$c14n'")
        val encapsulated = childElements(stamp, XADES_NS, "EncapsulatedTimeStamp")
        if (encapsulated.size != 1) {
            return Outcome.Invalid("ArchiveTimeStamp must carry exactly one EncapsulatedTimeStamp (XMLTimeStamp is unsupported)")
        }
        val tokenBytes =
            try {
                Base64.getMimeDecoder().decode(encapsulated.single().textContent.trim())
            } catch (_: IllegalArgumentException) {
                return Outcome.Invalid("archive EncapsulatedTimeStamp is not base64")
            }
        val verified =
            when (val r = TimeStampTokenVerifier.verify(tokenBytes, anchors)) {
                is TimeStampTokenVerifier.Result.Invalid -> return Outcome.Invalid("archive time-stamp: ${r.reason}")
                is TimeStampTokenVerifier.Result.Valid -> r
            }
        val jca =
            TimeStampTokenVerifier.digestFor(verified.imprintOid)
                ?: return Outcome.Invalid("unsupported archive time-stamp digest ${verified.imprintOid}")
        val expected =
            try {
                MessageDigest.getInstance(jca).digest(imprintInput(signatureElement, referenceData, preceding, c14n))
            } catch (t: Exception) {
                return Outcome.Invalid("could not canonicalise the archived signature: ${t.message}")
            } catch (_: StackOverflowError) {
                return Outcome.Invalid("could not canonicalise the archived signature: nesting too deep")
            }
        if (!MessageDigest.isEqual(expected, verified.imprintDigest)) {
            return Outcome.Invalid("archive time-stamp message imprint does not match the signature and its unsigned properties")
        }
        return Outcome.Valid(verified.genTime, stamp)
    }

    /**
     * Canonical bytes of [element] as a sub-tree of its document. The element is copied into a detached
     * document with the namespace declarations in scope, then canonicalised by the JDK's own
     * implementation (the JDK has no public sub-tree entry point).
     */
    fun canonicalize(
        element: Element,
        algorithm: String,
    ): ByteArray {
        require(!exceedsDepth(element)) { "unsigned property is nested more than $MAX_DEPTH levels deep" }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val detached = factory.newDocumentBuilder().newDocument()
        val copy = detached.importNode(element, true) as Element
        detached.appendChild(copy)
        var ancestor: Node? = element.parentNode
        while (ancestor is Element) {
            val attrs = ancestor.attributes
            for (i in 0 until attrs.length) {
                val a = attrs.item(i)
                if ((a.nodeName == "xmlns" || a.nodeName.startsWith("xmlns:")) && !copy.hasAttribute(a.nodeName)) {
                    copy.setAttributeNS(XMLNS_NS, a.nodeName, a.nodeValue)
                }
            }
            ancestor = ancestor.parentNode
        }
        declareUsedNamespaces(copy, emptyMap())
        val nodes = mutableListOf<Node>()
        val pending = ArrayDeque<Node>()
        pending.addLast(copy)
        while (pending.isNotEmpty()) {
            val n = pending.removeLast()
            nodes += n
            if (n is Element) {
                for (i in 0 until n.attributes.length) nodes += n.attributes.item(i)
            }
            // Pushed in reverse so the node set stays in document order.
            var c = n.lastChild
            while (c != null) {
                pending.addLast(c)
                c = c.previousSibling
            }
        }
        val method =
            XMLSignatureFactory.getInstance("DOM").newCanonicalizationMethod(algorithm, null as C14NMethodParameterSpec?)
        val context: XMLCryptoContext =
            DOMValidateContext(
                object : KeySelector() {
                    override fun select(
                        keyInfo: KeyInfo?,
                        purpose: Purpose?,
                        method: AlgorithmMethod?,
                        context: XMLCryptoContext?,
                    ): KeySelectorResult = throw UnsupportedOperationException("no key is needed to canonicalise")
                },
                copy,
            )
        val result =
            method.transform(
                object : NodeSetData<Node> {
                    override fun iterator(): MutableIterator<Node> = nodes.iterator()
                },
                context,
            )
        return (result as OctetStreamData).octetStream.readBytes()
    }

    /**
     * Adds the `xmlns` declaration an element or attribute needs but that a DOM built with `createElementNS`
     * does not carry, so the canonical form is the same as for the serialised and re-parsed document.
     */
    private fun declareUsedNamespaces(
        root: Element,
        inherited: Map<String, String>,
    ) {
        val pending = ArrayDeque<Pair<Element, Map<String, String>>>()
        pending.addLast(root to inherited)
        while (pending.isNotEmpty()) {
            val (element, outer) = pending.removeLast()
            val scope = outer.toMutableMap()
            val attrs = element.attributes
            for (i in 0 until attrs.length) {
                val a = attrs.item(i)
                if (a.nodeName == "xmlns") {
                    scope[""] = a.nodeValue
                } else if (a.nodeName.startsWith("xmlns:")) {
                    scope[a.nodeName.removePrefix("xmlns:")] = a.nodeValue
                }
            }

            fun declare(
                prefix: String,
                uri: String,
            ) {
                if ((scope[prefix] ?: "") == uri) return
                element.setAttributeNS(XMLNS_NS, if (prefix.isEmpty()) "xmlns" else "xmlns:$prefix", uri)
                scope[prefix] = uri
            }
            declare(element.prefix ?: "", element.namespaceURI ?: "")
            val plain = (0 until attrs.length).map { attrs.item(it) }.filter { !it.nodeName.startsWith("xmlns") && it.prefix != null }
            plain.forEach { declare(it.prefix, it.namespaceURI ?: "") }
            var c = element.firstChild
            while (c != null) {
                if (c is Element) pending.addLast(c to scope)
                c = c.nextSibling
            }
        }
    }

    private fun exceedsDepth(root: Element): Boolean {
        val pending = ArrayDeque<Pair<Element, Int>>()
        pending.addLast(root to 1)
        while (pending.isNotEmpty()) {
            val (element, depth) = pending.removeLast()
            if (depth > MAX_DEPTH) return true
            var c = element.firstChild
            while (c != null) {
                if (c is Element) pending.addLast(c to depth + 1)
                c = c.nextSibling
            }
        }
        return false
    }

    private fun childElements(
        parent: Element,
        ns: String,
        localName: String,
    ): List<Element> = elementChildren(parent).filter { it.namespaceURI == ns && it.localName == localName }

    private fun elementChildren(parent: Element): List<Element> {
        val out = mutableListOf<Element>()
        var c = parent.firstChild
        while (c != null) {
            if (c is Element) out += c
            c = c.nextSibling
        }
        return out
    }
}
