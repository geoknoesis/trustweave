package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultTrustAnchorResolverTest {

    private val parser = EtsiTrustListParser()

    @Test
    fun `signer issued by trusted CA resolves to QualifiedActive`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val trustList = parseTrustList(
            ca = ca,
            statusUri = TspServiceStatus.GRANTED.uri,
            qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
        )

        val resolver = DefaultTrustAnchorResolver(trustList)
        val match = resolver.resolve(ca.signerCert, emptyList())

        assertTrue(match is TrustAnchorMatch.QualifiedActive, "got $match")
        match as TrustAnchorMatch.QualifiedActive
        assertEquals("D-Trust GmbH", match.tspName)
        assertEquals("DE", match.territory)
        assertTrue(match.qcWithSscd)
        assertTrue(match.qcForESig)
    }

    @Test
    fun `QC_SSCD_STATUS_AS_IN_CERT also counts as qcWithSscd`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val trustList = parseTrustList(
            ca = ca,
            statusUri = TspServiceStatus.GRANTED.uri,
            qualifierUris = listOf(QualifierUris.QC_SSCD_STATUS_AS_IN_CERT),
        )
        val resolver = DefaultTrustAnchorResolver(trustList)
        val match = resolver.resolve(ca.signerCert, emptyList())
        assertTrue(match is TrustAnchorMatch.QualifiedActive)
        assertTrue((match as TrustAnchorMatch.QualifiedActive).qcWithSscd)
        assertFalse(match.qcForESig)
    }

    @Test
    fun `signer not issued by any listed CA resolves to NotTrusted`() {
        val listedCa = TrustListFixtures.generateCaAndSigner()
        val unrelatedCa = TrustListFixtures.generateCaAndSigner(
            caSubject = "CN=Unrelated CA",
            signerSubject = "CN=Unrelated Signer",
        )
        val trustList = parseTrustList(
            ca = listedCa,
            statusUri = TspServiceStatus.GRANTED.uri,
            qualifierUris = emptyList(),
        )

        val resolver = DefaultTrustAnchorResolver(trustList)
        val match = resolver.resolve(unrelatedCa.signerCert, emptyList())

        assertEquals(TrustAnchorMatch.NotTrusted, match)
    }

    @Test
    fun `withdrawn CA returns QualifiedWithdrawn`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val withdrawnAt = "2025-06-01T00:00:00Z"
        val trustList = parseTrustList(
            ca = ca,
            statusUri = TspServiceStatus.WITHDRAWN.uri,
            qualifierUris = listOf(QualifierUris.QC_FOR_ESIG),
            statusStartingTime = withdrawnAt,
        )
        val resolver = DefaultTrustAnchorResolver(trustList)
        val match = resolver.resolve(ca.signerCert, emptyList())

        assertTrue(match is TrustAnchorMatch.QualifiedWithdrawn, "got $match")
        match as TrustAnchorMatch.QualifiedWithdrawn
        assertEquals("D-Trust GmbH", match.tspName)
        assertEquals(withdrawnAt, match.withdrawnAt.toString())
    }

    @Test
    fun `empty trust list always resolves to NotTrusted`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val empty = TrustList(
            schemeOperator = "Test",
            sequenceNumber = 1,
            issuedAt = kotlinx.datetime.Clock.System.now(),
            nextUpdateAt = null,
            memberStateLists = emptyList(),
        )
        val resolver = DefaultTrustAnchorResolver(empty)
        assertEquals(TrustAnchorMatch.NotTrusted, resolver.resolve(ca.signerCert, emptyList()))
    }

    @Test
    fun `non-CA-QC services (e g qualified TSAs) do not anchor a signature`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val tsl = TrustListFixtures.renderTslXml(
            territory = "EU",
            schemeOperator = "Demo",
            tspName = "Demo TSA Operator",
            services = listOf(
                TrustListFixtures.TslServiceSpec(
                    serviceName = "Qualified TSA",
                    serviceTypeUri = TspServiceType.QUALIFIED_TIMESTAMP.uri,
                    statusUri = TspServiceStatus.GRANTED.uri,
                    statusStartingTime = "2024-01-01T00:00:00Z",
                    caCertBase64 = ca.caCertBase64,
                    qualifierUris = emptyList(),
                ),
            ),
        )
        val trustList = parser.parse(TrustListFixtures.renderLotlXml(), mapOf("EU" to tsl))

        val resolver = DefaultTrustAnchorResolver(trustList)
        // Even though the cert IS the TSA cert, the service type is TSA/QTST not CA/QC, so it
        // cannot anchor a signing cert. The resolver should report NotTrusted.
        assertEquals(TrustAnchorMatch.NotTrusted, resolver.resolve(ca.signerCert, emptyList()))
    }

    // ---------------------------------------------------------------- helpers

    private fun parseTrustList(
        ca: TrustListFixtures.CaAndSigner,
        statusUri: String,
        qualifierUris: List<String>,
        statusStartingTime: String = "2024-01-01T00:00:00Z",
    ): TrustList {
        val lotl = TrustListFixtures.renderLotlXml()
        val tsl = TrustListFixtures.renderTslXml(
            territory = "DE",
            schemeOperator = "Bundesnetzagentur",
            tspName = "D-Trust GmbH",
            services = listOf(
                TrustListFixtures.TslServiceSpec(
                    serviceName = "D-Trust Root CA",
                    serviceTypeUri = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES.uri,
                    statusUri = statusUri,
                    statusStartingTime = statusStartingTime,
                    caCertBase64 = ca.caCertBase64,
                    qualifierUris = qualifierUris,
                ),
            ),
        )
        return parser.parse(lotl, mapOf("DE" to tsl))
    }
}
