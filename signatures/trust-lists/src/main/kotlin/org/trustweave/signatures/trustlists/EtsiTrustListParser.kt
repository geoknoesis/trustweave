package org.trustweave.signatures.trustlists

import kotlinx.datetime.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Default ETSI TS 119 612 trust-list parser.
 *
 * DOM-based; the full EU LoTL plus every Member-State TSL fits comfortably in memory
 * (~5 MB across ~30 documents as of 2026-Q2). A streaming parser is deferred until profiling
 * indicates DOM is a bottleneck.
 *
 * Element lookup is by **local name only** — the parser ignores XML namespace URIs. ETSI TSL
 * documents declare a fixed default namespace (`http://uri.etsi.org/02231/v2#`) plus the W3C
 * XML-DSig namespace for embedded signatures, but production-grade verifiers must be tolerant
 * of slight namespace variations across Member States, and a local-name match is the simplest
 * way to achieve that without dropping into XPath/XSLT machinery.
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
        val memberStateLists = tslXmlByTerritory.entries
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
        )
    }

    // -------------------------------------------------------------- LoTL metadata

    private data class LotlMetadata(
        val schemeOperator: String,
        val sequenceNumber: Int,
        val issuedAt: Instant,
        val nextUpdateAt: Instant?,
    )

    private fun parseLotlMetadata(lotlXml: ByteArray): LotlMetadata {
        val doc = parseDocument(lotlXml, where = "LoTL")
        val schemeInfo = findFirst(doc.documentElement, "SchemeInformation")
            ?: parseError("LoTL", "missing SchemeInformation element")

        val schemeOperator = extractName(findFirst(schemeInfo, "SchemeOperatorName"))
            ?: parseError("LoTL", "missing or unnamed SchemeOperatorName")
        val sequenceNumber = textOrNull(findFirst(schemeInfo, "TSLSequenceNumber"))?.trim()
            ?.toIntOrNull()
            ?: parseError("LoTL", "missing or non-integer TSLSequenceNumber")
        val issuedAt = textOrNull(findFirst(schemeInfo, "ListIssueDateTime"))?.let(::parseInstant)
            ?: parseError("LoTL", "missing ListIssueDateTime")
        val nextUpdateAt = findFirst(schemeInfo, "NextUpdate")
            ?.let { findFirst(it, "dateTime") }
            ?.let { textOrNull(it) }
            ?.let(::parseInstant)

        return LotlMetadata(schemeOperator, sequenceNumber, issuedAt, nextUpdateAt)
    }

    // -------------------------------------------------------------- per-MS TSL

    private fun parseMemberStateTsl(territory: String, tslXml: ByteArray): MemberStateTsl {
        val doc = parseDocument(tslXml, where = "TSL($territory)")
        val root = doc.documentElement
        val schemeInfo = findFirst(root, "SchemeInformation")
            ?: parseError("TSL($territory)", "missing SchemeInformation")

        val schemeOperator = extractName(findFirst(schemeInfo, "SchemeOperatorName"))
            ?: parseError("TSL($territory)", "missing or unnamed SchemeOperatorName")
        val sequenceNumber = textOrNull(findFirst(schemeInfo, "TSLSequenceNumber"))?.trim()
            ?.toIntOrNull()
            ?: parseError("TSL($territory)", "missing or non-integer TSLSequenceNumber")
        val issuedAt = textOrNull(findFirst(schemeInfo, "ListIssueDateTime"))?.let(::parseInstant)
            ?: parseError("TSL($territory)", "missing ListIssueDateTime")

        val tspListElem = findFirst(root, "TrustServiceProviderList")
        val trustedTsps = tspListElem
            ?.let { findAll(it, "TrustServiceProvider") }
            ?.map { parseTrustServiceProvider(territory, it) }
            ?: emptyList()

        return MemberStateTsl(
            territory = territory,
            schemeOperator = schemeOperator,
            sequenceNumber = sequenceNumber,
            issuedAt = issuedAt,
            trustedTsps = trustedTsps,
        )
    }

    private fun parseTrustServiceProvider(territory: String, tspElem: Element): TrustedTSP {
        val info = findFirst(tspElem, "TSPInformation")
            ?: parseError("TSL($territory)", "TrustServiceProvider missing TSPInformation")
        val name = extractName(findFirst(info, "TSPName"))
            ?: parseError("TSL($territory)", "TSPInformation missing TSPName")
        val tradeName = extractName(findFirst(info, "TSPTradeName"))

        val servicesElem = findFirst(tspElem, "TSPServices")
        val services = servicesElem
            ?.let { findAll(it, "TSPService") }
            ?.map { parseTspService(territory, it) }
            ?: emptyList()

        return TrustedTSP(name = name, tradeName = tradeName, services = services)
    }

    private fun parseTspService(territory: String, serviceElem: Element): TspService {
        val info = findFirst(serviceElem, "ServiceInformation")
            ?: parseError("TSL($territory)", "TSPService missing ServiceInformation")

        val serviceName = extractName(findFirst(info, "ServiceName"))
            ?: parseError("TSL($territory)", "ServiceInformation missing ServiceName")
        val serviceTypeUri = textOrNull(findFirst(info, "ServiceTypeIdentifier"))?.trim()
            ?: parseError("TSL($territory)", "ServiceInformation missing ServiceTypeIdentifier")
        val statusUri = textOrNull(findFirst(info, "ServiceStatus"))?.trim()
            ?: parseError("TSL($territory)", "ServiceInformation missing ServiceStatus")
        val statusStartingTime = textOrNull(findFirst(info, "StatusStartingTime"))
            ?.let(::parseInstant)
            ?: parseError("TSL($territory)", "ServiceInformation missing StatusStartingTime")

        val certificates = findFirst(info, "ServiceDigitalIdentity")
            ?.let { findAll(it, "X509Certificate") }
            ?.mapNotNull { decodeBase64Certificate(textOrNull(it)) }
            ?: emptyList()

        val qualifierUris = findFirst(info, "ServiceInformationExtensions")
            ?.let { findAll(it, "Qualifier") }
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

    private fun parseDocument(bytes: ByteArray, where: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE hardening per OWASP cheat sheet.
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (t: Throwable) {
            throw TrustListParseException("$where: XML is not well-formed: ${t.message}", t)
        }
    }

    /**
     * Depth-first descendant search by local name. Returns the first matching element under
     * [parent] (excluding [parent] itself), or null.
     */
    private fun findFirst(parent: Node?, localName: String): Element? {
        if (parent == null) return null
        val children: NodeList = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (child.localName == localName || child.tagName == localName) return child
                val nested = findFirst(child, localName)
                if (nested != null) return nested
            }
        }
        return null
    }

    /**
     * Find direct or descendant matches of [localName] under [parent].
     *
     * Used for repeated child elements (`OtherTSLPointer`, `TrustServiceProvider`, etc.).
     * To keep semantics predictable the search descends until an instance is found at any
     * depth — but does NOT descend into matched elements themselves, so e.g. nested
     * `Qualifications` blocks don't double-count `Qualifier` elements.
     */
    private fun findAll(parent: Node?, localName: String): List<Element> {
        if (parent == null) return emptyList()
        val result = mutableListOf<Element>()
        collectMatches(parent, localName, result)
        return result
    }

    private fun collectMatches(node: Node, localName: String, into: MutableList<Element>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (child.localName == localName || child.tagName == localName) {
                    into.add(child)
                } else {
                    collectMatches(child, localName, into)
                }
            }
        }
    }

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
        val der = try {
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

    private fun parseError(where: String, msg: String): Nothing {
        throw TrustListParseException("$where: $msg")
    }

    companion object {
        private val CERT_FACTORY: CertificateFactory = CertificateFactory.getInstance("X.509")
    }
}
