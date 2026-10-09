package org.trustweave.signatures.trustlists

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Instant

/**
 * Default ETSI TS 119 612 trust-list parser.
 *
 * DOM-based; the full EU LoTL plus every Member-State TSL fits comfortably in memory
 * (~5 MB across ~30 documents as of 2026-Q2). A streaming parser is deferred until profiling
 * indicates DOM is a bottleneck.
 *
 * Element lookup matches **namespace and local name**: structural TSL elements must be in the
 * ETSI namespace `http://uri.etsi.org/02231/v2#` (prefix-agnostic, so `tsl:` / default-namespace
 * serialisations both work), and the document element must be `TrustServiceStatusList`. Elements
 * inside the W3C XML-DSig namespace (the embedded signature) are never searched, so content
 * smuggled into `ds:Object` cannot masquerade as list data. The qualifier elements, which Member
 * States publish under several extension namespaces, are still matched by local name only.
 *
 * XML External Entity (XXE) protection is enabled via the standard hardening flags on the
 * underlying [DocumentBuilderFactory].
 */
class EtsiTrustListParser : TrustListParser {
    override fun parse(
        lotlXml: ByteArray,
        tslXmlByTerritory: Map<String, ByteArray>,
    ): TrustList {
        val lotlMeta = parseLotlMetadata(lotlXml)
        val memberStateLists =
            tslXmlByTerritory.entries
                .mapNotNull { (territory, tslBytes) ->
                    runCatching { parseMemberStateTsl(territory, tslBytes) }
                        .getOrElse { cause ->
                            throw TrustListParseException(
                                "Failed to parse TSL for territory '$territory': ${cause.message}",
                                cause,
                            )
                        }
                }

        return TrustList(
            schemeOperator = lotlMeta.schemeOperator,
            sequenceNumber = lotlMeta.sequenceNumber,
            issuedAt = lotlMeta.issuedAt,
            nextUpdateAt = lotlMeta.nextUpdateAt,
            memberStateLists = memberStateLists,
            tslPointers = lotlMeta.pointers,
        )
    }

    // -------------------------------------------------------------- LoTL metadata

    private data class LotlMetadata(
        val schemeOperator: String,
        val sequenceNumber: Int,
        val issuedAt: Instant,
        val nextUpdateAt: Instant?,
        val pointers: List<TslPointer>,
    )

    private fun parseLotlMetadata(lotlXml: ByteArray): LotlMetadata {
        val doc = parseDocument(lotlXml, where = "LoTL")
        requireTslRoot(doc, "LoTL")
        val schemeInfo =
            findFirst(doc.documentElement, "SchemeInformation")
                ?: parseError("LoTL", "missing SchemeInformation element")

        val schemeOperator =
            extractName(findFirst(schemeInfo, "SchemeOperatorName"))
                ?: parseError("LoTL", "missing or unnamed SchemeOperatorName")
        val sequenceNumber =
            textOrNull(findFirst(schemeInfo, "TSLSequenceNumber"))
                ?.trim()
                ?.toIntOrNull()
                ?: parseError("LoTL", "missing or non-integer TSLSequenceNumber")
        val issuedAt =
            textOrNull(findFirst(schemeInfo, "ListIssueDateTime"))?.let(::parseInstant)
                ?: parseError("LoTL", "missing ListIssueDateTime")
        val nextUpdateAt = parseNextUpdate(schemeInfo)
        val pointers =
            findFirst(schemeInfo, "PointersToOtherTSL")
                ?.let { findAll(it, "OtherTSLPointer") }
                ?.mapNotNull(::parsePointer)
                ?: emptyList()

        return LotlMetadata(schemeOperator, sequenceNumber, issuedAt, nextUpdateAt, pointers)
    }

    private fun parseNextUpdate(schemeInfo: Element): Instant? =
        findFirst(schemeInfo, "NextUpdate")
            ?.let { findFirst(it, "dateTime") }
            ?.let { textOrNull(it) }
            ?.let(::parseInstant)

    /** A pointer without a `SchemeTerritory` cannot be tied to a Member State and is skipped. */
    private fun parsePointer(pointer: Element): TslPointer? {
        val territory =
            textOrNull(findFirst(pointer, "SchemeTerritory"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.uppercase()
                ?: return null
        val location = textOrNull(findFirst(pointer, "TSLLocation"))?.trim()?.ifBlank { null }
        val certs =
            findFirst(pointer, "ServiceDigitalIdentities")
                ?.let { findAll(it, "X509Certificate") }
                ?.mapNotNull { decodeBase64Certificate(textOrNull(it)) }
                ?: emptyList()
        return TslPointer(territory, location, certs)
    }

    private fun requireTslRoot(
        doc: Document,
        where: String,
    ) {
        val root = doc.documentElement
        if (root.localName != "TrustServiceStatusList" || root.namespaceURI != TSL_NS) {
            parseError(where, "document element must be {$TSL_NS}TrustServiceStatusList")
        }
    }

    // -------------------------------------------------------------- per-MS TSL

    private fun parseMemberStateTsl(
        territory: String,
        tslXml: ByteArray,
    ): MemberStateTsl {
        val doc = parseDocument(tslXml, where = "TSL($territory)")
        requireTslRoot(doc, "TSL($territory)")
        val root = doc.documentElement
        val schemeInfo =
            findFirst(root, "SchemeInformation")
                ?: parseError("TSL($territory)", "missing SchemeInformation")

        val schemeOperator =
            extractName(findFirst(schemeInfo, "SchemeOperatorName"))
                ?: parseError("TSL($territory)", "missing or unnamed SchemeOperatorName")
        val sequenceNumber =
            textOrNull(findFirst(schemeInfo, "TSLSequenceNumber"))
                ?.trim()
                ?.toIntOrNull()
                ?: parseError("TSL($territory)", "missing or non-integer TSLSequenceNumber")
        val issuedAt =
            textOrNull(findFirst(schemeInfo, "ListIssueDateTime"))?.let(::parseInstant)
                ?: parseError("TSL($territory)", "missing ListIssueDateTime")

        val nextUpdateAt = parseNextUpdate(schemeInfo)
        val schemeTerritory =
            textOrNull(findFirst(schemeInfo, "SchemeTerritory"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.uppercase()

        val tspListElem = findFirst(root, "TrustServiceProviderList")
        val trustedTsps =
            tspListElem
                ?.let { findAll(it, "TrustServiceProvider") }
                ?.map { parseTrustServiceProvider(territory, it) }
                ?: emptyList()

        return MemberStateTsl(
            territory = territory,
            schemeOperator = schemeOperator,
            sequenceNumber = sequenceNumber,
            issuedAt = issuedAt,
            trustedTsps = trustedTsps,
            nextUpdateAt = nextUpdateAt,
            schemeTerritory = schemeTerritory,
        )
    }

    private fun parseTrustServiceProvider(
        territory: String,
        tspElem: Element,
    ): TrustedTSP {
        val info =
            findFirst(tspElem, "TSPInformation")
                ?: parseError("TSL($territory)", "TrustServiceProvider missing TSPInformation")
        val name =
            extractName(findFirst(info, "TSPName"))
                ?: parseError("TSL($territory)", "TSPInformation missing TSPName")
        val tradeName = extractName(findFirst(info, "TSPTradeName"))

        val servicesElem = findFirst(tspElem, "TSPServices")
        val services =
            servicesElem
                ?.let { findAll(it, "TSPService") }
                ?.map { parseTspService(territory, it) }
                ?: emptyList()

        return TrustedTSP(name = name, tradeName = tradeName, services = services)
    }

    private fun parseTspService(
        territory: String,
        serviceElem: Element,
    ): TspService {
        val info =
            findFirst(serviceElem, "ServiceInformation")
                ?: parseError("TSL($territory)", "TSPService missing ServiceInformation")

        val serviceName =
            extractName(findFirst(info, "ServiceName"))
                ?: parseError("TSL($territory)", "ServiceInformation missing ServiceName")
        val serviceTypeUri =
            textOrNull(findFirst(info, "ServiceTypeIdentifier"))?.trim()
                ?: parseError("TSL($territory)", "ServiceInformation missing ServiceTypeIdentifier")
        val statusUri =
            textOrNull(findFirst(info, "ServiceStatus"))?.trim()
                ?: parseError("TSL($territory)", "ServiceInformation missing ServiceStatus")
        val statusStartingTime =
            textOrNull(findFirst(info, "StatusStartingTime"))
                ?.let(::parseInstant)
                ?: parseError("TSL($territory)", "ServiceInformation missing StatusStartingTime")

        val certificates =
            findFirst(info, "ServiceDigitalIdentity")
                ?.let { findAll(it, "X509Certificate") }
                ?.mapNotNull { decodeBase64Certificate(textOrNull(it)) }
                ?: emptyList()

        val qualifierUris =
            findFirst(info, "ServiceInformationExtensions")
                ?.let { findAll(it, "Qualifier", ns = null) }
                ?.mapNotNull { it.getAttribute("uri").ifBlank { null } }
                ?: emptyList()

        return TspService(
            serviceName = serviceName,
            serviceType = TspServiceType.fromUri(serviceTypeUri),
            status = TspServiceStatus.fromUri(statusUri),
            statusStartingTime = statusStartingTime,
            serviceCertificates = certificates,
            qualifierUris = qualifierUris,
        )
    }

    // -------------------------------------------------------------- DOM helpers

    private fun parseDocument(
        bytes: ByteArray,
        where: String,
    ): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // XXE hardening per OWASP cheat sheet.
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
                TrustListXml.limitDepth(this)
            }
        TrustListXml.sizeProblem(bytes)?.let { throw TrustListParseException("$where: XML rejected: $it") }
        val document =
            try {
                factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            } catch (t: Throwable) {
                throw TrustListParseException("$where: XML is not well-formed: ${t.message ?: t.javaClass.simpleName}", t)
            }
        if (TrustListXml.exceedsDepth(document.documentElement)) {
            throw TrustListParseException("$where: XML is nested more than ${TrustListXml.MAX_DEPTH} levels deep")
        }
        return document
    }

    /**
     * Depth-first descendant search by namespace and local name. Returns the first matching
     * element under [parent] (excluding [parent] itself), or null. [ns] defaults to the ETSI TSL
     * namespace; pass null to match on local name alone. XML-DSig subtrees are not searched.
     */
    private fun findFirst(
        parent: Node?,
        localName: String,
        ns: String? = TSL_NS,
    ): Element? {
        if (parent == null) return null
        val pending = ArrayDeque<Node>()
        pending.addLast(parent)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node !== parent && node is Element && matches(node, localName, ns)) return node
            // Reverse order on the stack so children are visited in document order.
            var child = node.lastChild
            while (child != null) {
                if (child is Element && child.namespaceURI != XMLDSIG_NS) pending.addLast(child)
                child = child.previousSibling
            }
        }
        return null
    }

    /**
     * Find matches of [localName] under [parent].
     *
     * Used for repeated child elements (`OtherTSLPointer`, `TrustServiceProvider`, etc.).
     * To keep semantics predictable the search descends until an instance is found at any
     * depth — but does NOT descend into matched elements themselves, so e.g. nested
     * `Qualifications` blocks don't double-count `Qualifier` elements.
     */
    private fun findAll(
        parent: Node?,
        localName: String,
        ns: String? = TSL_NS,
    ): List<Element> {
        if (parent == null) return emptyList()
        val result = mutableListOf<Element>()
        val pending = ArrayDeque<Node>()
        pending.addLast(parent)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node !== parent) {
                if (node !is Element || node.namespaceURI == XMLDSIG_NS) continue
                if (matches(node, localName, ns)) {
                    result.add(node)
                    continue
                }
            }
            // Reverse order on the stack so children are visited in document order.
            var child = node.lastChild
            while (child != null) {
                pending.addLast(child)
                child = child.previousSibling
            }
        }
        return result
    }

    private fun matches(
        el: Element,
        localName: String,
        ns: String?,
    ): Boolean = el.localName == localName && (ns == null || el.namespaceURI == ns)

    private fun textOrNull(elem: Element?): String? = elem?.textContent

    /**
     * Most ETSI "Name" wrappers (e.g. `SchemeOperatorName`, `TSPName`) carry one or more
     * `<Name xml:lang="…">…</Name>` children, one per language. We prefer the `en` variant and
     * fall back to the first available `Name`.
     */
    private fun extractName(wrapper: Element?): String? {
        if (wrapper == null) return null
        val names = findAll(wrapper, "Name")
        if (names.isEmpty()) return null
        val english = names.firstOrNull { it.getAttribute("xml:lang") == "en" }
        val pick = english ?: names.firstOrNull { it.getAttribute("lang") == "en" } ?: names[0]
        return pick.textContent?.trim()?.ifBlank { null }
    }

    private fun parseInstant(raw: String): Instant {
        val trimmed = raw.trim()
        return try {
            Instant.parse(trimmed)
        } catch (t: Throwable) {
            throw TrustListParseException("Invalid ISO-8601 instant '$trimmed': ${t.message}", t)
        }
    }

    private fun decodeBase64Certificate(text: String?): X509Certificate? {
        if (text == null) return null
        // ETSI XML inserts arbitrary whitespace inside base64 X509Certificate blocks.
        val clean = text.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return null
        val der =
            try {
                Base64.getDecoder().decode(clean)
            } catch (t: IllegalArgumentException) {
                throw TrustListParseException("X509Certificate element contains malformed base64", t)
            }
        return try {
            CERT_FACTORY.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        } catch (t: Throwable) {
            throw TrustListParseException("X509Certificate element is not a valid DER cert", t)
        }
    }

    private fun parseError(
        where: String,
        msg: String,
    ): Nothing = throw TrustListParseException("$where: $msg")

    companion object {
        private const val TSL_NS = "http://uri.etsi.org/02231/v2#"
        private const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
        private val CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")
    }
}
