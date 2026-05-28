package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EtsiTrustListParserTest {

    private val parser = EtsiTrustListParser()

    @Test
    fun `parses LoTL metadata`() {
        val lotl = TrustListFixtures.renderLotlXml(
            schemeOperator = "European Commission",
            sequenceNumber = 789,
            issuedAt = "2026-03-15T00:00:00Z",
            nextUpdateAt = "2026-09-15T00:00:00Z",
        )
        val trustList = parser.parse(lotl, emptyMap())

        assertEquals("European Commission", trustList.schemeOperator)
        assertEquals(789, trustList.sequenceNumber)
        assertEquals("2026-03-15T00:00:00Z", trustList.issuedAt.toString())
        assertEquals("2026-09-15T00:00:00Z", trustList.nextUpdateAt?.toString())
        assertTrue(trustList.memberStateLists.isEmpty())
    }

    @Test
    fun `nextUpdate is null when absent`() {
        val lotl = TrustListFixtures.renderLotlXml(nextUpdateAt = null)
        val trustList = parser.parse(lotl, emptyMap())
        assertNull(trustList.nextUpdateAt)
    }

    @Test
    fun `parses a per-MS TSL with one CA QC service`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val lotl = TrustListFixtures.renderLotlXml()
        val tsl = TrustListFixtures.renderTslXml(
            territory = "DE",
            schemeOperator = "Bundesnetzagentur",
            tspName = "D-Trust GmbH",
            services = listOf(
                TrustListFixtures.TslServiceSpec(
                    serviceName = "D-Trust Root CA 1",
                    serviceTypeUri = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES.uri,
                    statusUri = TspServiceStatus.GRANTED.uri,
                    statusStartingTime = "2024-01-01T00:00:00Z",
                    caCertBase64 = ca.caCertBase64,
                    qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
                ),
            ),
        )

        val trustList = parser.parse(lotl, mapOf("DE" to tsl))

        assertEquals(1, trustList.memberStateLists.size)
        val ms = trustList.memberStateLists.single()
        assertEquals("DE", ms.territory)
        assertEquals("Bundesnetzagentur", ms.schemeOperator)
        assertEquals(1, ms.trustedTsps.size)

        val tsp = ms.trustedTsps.single()
        assertEquals("D-Trust GmbH", tsp.name)
        assertEquals(1, tsp.services.size)

        val svc = tsp.services.single()
        assertEquals(TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES, svc.serviceType)
        assertEquals(TspServiceStatus.GRANTED, svc.status)
        assertEquals("2024-01-01T00:00:00Z", svc.statusStartingTime.toString())
        assertEquals(1, svc.serviceCertificates.size)
        assertEquals(
            ca.caCert.subjectX500Principal,
            svc.serviceCertificates.single().subjectX500Principal,
        )
        assertTrue(QualifierUris.QC_WITH_SSCD in svc.qualifierUris)
        assertTrue(QualifierUris.QC_FOR_ESIG in svc.qualifierUris)
    }

    @Test
    fun `maps unknown service-type URI to OTHER`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val lotl = TrustListFixtures.renderLotlXml()
        val tsl = TrustListFixtures.renderTslXml(
            territory = "EU",
            schemeOperator = "Demo",
            tspName = "Demo TSP",
            services = listOf(
                TrustListFixtures.TslServiceSpec(
                    serviceName = "Custom Service",
                    serviceTypeUri = "http://example.com/svctype/Custom",
                    statusUri = TspServiceStatus.GRANTED.uri,
                    statusStartingTime = "2024-01-01T00:00:00Z",
                    caCertBase64 = ca.caCertBase64,
                    qualifierUris = emptyList(),
                ),
            ),
        )

        val trustList = parser.parse(lotl, mapOf("EU" to tsl))
        val svc = trustList.memberStateLists.single().trustedTsps.single().services.single()
        assertEquals(TspServiceType.OTHER, svc.serviceType)
    }

    @Test
    fun `parses multiple member states in supplied order`() {
        val caDe = TrustListFixtures.generateCaAndSigner()
        val caFr = TrustListFixtures.generateCaAndSigner(
            caSubject = "CN=FR CA",
            signerSubject = "CN=FR Signer",
        )
        val lotl = TrustListFixtures.renderLotlXml()
        val tsls = linkedMapOf(
            "DE" to TrustListFixtures.renderTslXml(
                "DE", "BNetzA", "D-Trust",
                listOf(grantedQcService("D-Trust CA", caDe.caCertBase64)),
            ),
            "FR" to TrustListFixtures.renderTslXml(
                "FR", "ANSSI", "DocuSign France",
                listOf(grantedQcService("DocuSign FR CA", caFr.caCertBase64)),
            ),
        )

        val trustList = parser.parse(lotl, tsls)

        assertEquals(2, trustList.memberStateLists.size)
        assertEquals(listOf("DE", "FR"), trustList.memberStateLists.map { it.territory })
    }

    @Test
    fun `surfaces a malformed LoTL as TrustListParseException`() {
        val ex = assertThrows<TrustListParseException> {
            parser.parse("not xml".toByteArray(), emptyMap())
        }
        assertTrue(ex.message!!.contains("LoTL"), "got: ${ex.message}")
    }

    @Test
    fun `surfaces a malformed TSL with the territory in the error message`() {
        val lotl = TrustListFixtures.renderLotlXml()
        val ex = assertThrows<TrustListParseException> {
            parser.parse(lotl, mapOf("XX" to "not xml".toByteArray()))
        }
        assertTrue(ex.message!!.contains("XX"), "got: ${ex.message}")
    }

    @Test
    fun `rejects LoTL missing TSLSequenceNumber`() {
        val brokenLotl = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
              <SchemeInformation>
                <SchemeOperatorName><Name xml:lang="en">EC</Name></SchemeOperatorName>
                <ListIssueDateTime>2026-01-01T00:00:00Z</ListIssueDateTime>
              </SchemeInformation>
            </TrustServiceStatusList>
        """.trimIndent().trim().toByteArray()

        val ex = assertThrows<TrustListParseException> {
            parser.parse(brokenLotl, emptyMap())
        }
        assertTrue(ex.message!!.contains("TSLSequenceNumber"), "got: ${ex.message}")
    }

    @Test
    fun `tolerates non-en Name elements by picking the first available`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val tsl = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
              <SchemeInformation>
                <TSLSequenceNumber>1</TSLSequenceNumber>
                <SchemeOperatorName>
                  <Name xml:lang="de">Bundesnetzagentur</Name>
                </SchemeOperatorName>
                <SchemeTerritory>DE</SchemeTerritory>
                <ListIssueDateTime>2026-01-01T00:00:00Z</ListIssueDateTime>
              </SchemeInformation>
              <TrustServiceProviderList>
                <TrustServiceProvider>
                  <TSPInformation>
                    <TSPName><Name xml:lang="de">D-Trust GmbH</Name></TSPName>
                  </TSPInformation>
                  <TSPServices>
                    ${
                        renderInlineService(
                            "D-Trust CA",
                            ca.caCertBase64,
                        )
                    }
                  </TSPServices>
                </TrustServiceProvider>
              </TrustServiceProviderList>
            </TrustServiceStatusList>
        """.trimIndent().trim().toByteArray()

        val lotl = TrustListFixtures.renderLotlXml()
        val trustList = parser.parse(lotl, mapOf("DE" to tsl))
        val ms = trustList.memberStateLists.single()
        assertEquals("Bundesnetzagentur", ms.schemeOperator)
        assertEquals("D-Trust GmbH", ms.trustedTsps.single().name)
    }

    // ---------------------------------------------------------------- helpers

    private fun grantedQcService(name: String, caCertBase64: String): TrustListFixtures.TslServiceSpec =
        TrustListFixtures.TslServiceSpec(
            serviceName = name,
            serviceTypeUri = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES.uri,
            statusUri = TspServiceStatus.GRANTED.uri,
            statusStartingTime = "2024-01-01T00:00:00Z",
            caCertBase64 = caCertBase64,
            qualifierUris = listOf(QualifierUris.QC_FOR_ESIG),
        )

    private fun renderInlineService(name: String, caCertBase64: String): String = """
        <TSPService>
          <ServiceInformation>
            <ServiceTypeIdentifier>${TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES.uri}</ServiceTypeIdentifier>
            <ServiceName><Name xml:lang="en">$name</Name></ServiceName>
            <ServiceDigitalIdentity>
              <DigitalId>
                <X509Certificate>$caCertBase64</X509Certificate>
              </DigitalId>
            </ServiceDigitalIdentity>
            <ServiceStatus>${TspServiceStatus.GRANTED.uri}</ServiceStatus>
            <StatusStartingTime>2024-01-01T00:00:00Z</StatusStartingTime>
          </ServiceInformation>
        </TSPService>
    """.trimIndent()
}
