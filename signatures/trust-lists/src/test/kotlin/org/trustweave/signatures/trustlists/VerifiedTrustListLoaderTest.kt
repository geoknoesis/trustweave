package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Instant

class VerifiedTrustListLoaderTest {
    private val now = Instant.parse("2026-03-01T00:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    // Fixture certificates are valid around the real wall clock, so the loader's signature
    // verifiers use the system clock while freshness uses the fixed one.
    private val loader =
        VerifiedTrustListLoader(
            clock = clock,
            lotlVerifier = DefaultLotlSignatureVerifier(),
            tslVerifier = DefaultTslSignatureVerifier(),
        )

    private val lotlSigner = TrustListFixtures.generateCaAndSigner(signerSubject = "CN=LoTL Signer")
    private val deSigner = TrustListFixtures.generateCaAndSigner(signerSubject = "CN=DE TSL Signer")
    private val rogue = TrustListFixtures.generateCaAndSigner(signerSubject = "CN=Rogue")
    private val serviceCa = TrustListFixtures.generateCaAndSigner(caSubject = "CN=QTSP CA")

    private fun lotl(
        sequence: Int = 10,
        nextUpdate: String? = "2026-09-01T00:00:00Z",
        pointers: List<String> = listOf(TrustListFixtures.pointer("DE", deSigner.signerCert)),
    ) = SignedXmlTestSupport.signEnveloped(
        TrustListFixtures.renderLotlXml(
            sequenceNumber = sequence,
            issuedAt = "2026-02-01T00:00:00Z",
            nextUpdateAt = nextUpdate,
            pointers = pointers,
        ),
        lotlSigner.signerKey,
        listOf(lotlSigner.signerCert),
    )

    private fun tsl(
        territory: String = "DE",
        sequence: Int = 5,
        nextUpdate: String? = "2026-08-01T00:00:00Z",
        declared: String = territory,
        signer: TrustListFixtures.CaAndSigner = deSigner,
        tamper: Boolean = false,
    ): ByteArray {
        val xml =
            TrustListFixtures.renderTslXml(
                territory = territory,
                schemeOperator = "BNetzA",
                tspName = "QTSP",
                services =
                    listOf(
                        TrustListFixtures.TslServiceSpec(
                            "CA",
                            TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES.uri,
                            TspServiceStatus.GRANTED.uri,
                            "2024-01-01T00:00:00Z",
                            serviceCa.caCertBase64,
                            emptyList(),
                        ),
                    ),
                sequenceNumber = sequence,
                issuedAt = "2026-02-01T00:00:00Z",
                nextUpdateAt = nextUpdate,
                declaredTerritory = declared,
            )
        val signed = SignedXmlTestSupport.signEnveloped(xml, signer.signerKey, listOf(signer.signerCert))
        return if (tamper) String(signed).replace("QTSP", "EVIL").toByteArray() else signed
    }

    private fun load(
        lotlBytes: ByteArray = lotl(),
        tsls: Map<String, ByteArray> = mapOf("DE" to tsl()),
        options: TrustListLoadOptions = TrustListLoadOptions(),
        anchors: List<X509Certificate> = listOf(lotlSigner.signerCert),
    ) = loader.load(lotlBytes, tsls, anchors, options)

    private fun assertRejected(
        reason: TrustListRejection,
        result: TrustListLoadResult,
    ) {
        assertTrue(
            result is TrustListLoadResult.Rejected && result.reason == reason,
            "expected $reason, got: $result",
        )
    }

    @Test
    fun `signed LoTL and pointer-signed TSL load`() {
        val result = load()

        assertTrue(result is TrustListLoadResult.Loaded, "got: $result")
        result as TrustListLoadResult.Loaded
        assertEquals(listOf("DE"), result.trustList.memberStateLists.map { it.territory })
        assertTrue(result.staleLists.isEmpty())
    }

    @Test
    fun `LoTL signed by an unpinned key is rejected`() {
        assertRejected(TrustListRejection.LOTL_SIGNATURE, load(anchors = listOf(rogue.signerCert)))
    }

    @Test
    fun `TSL signed by a key the LoTL pointer does not publish is rejected`() {
        assertRejected(
            TrustListRejection.TSL_SIGNATURE,
            load(tsls = mapOf("DE" to tsl(signer = rogue))),
        )
    }

    @Test
    fun `tampered TSL is rejected`() {
        assertRejected(
            TrustListRejection.TSL_SIGNATURE,
            load(tsls = mapOf("DE" to tsl(tamper = true))),
        )
    }

    @Test
    fun `territory without LoTL pointer is rejected`() {
        assertRejected(
            TrustListRejection.TERRITORY_NOT_IN_LOTL,
            load(tsls = mapOf("FR" to tsl(territory = "FR"))),
        )
    }

    @Test
    fun `TSL declaring another territory than supplied is rejected`() {
        assertRejected(
            TrustListRejection.TERRITORY_MISMATCH,
            load(tsls = mapOf("DE" to tsl(declared = "FR"))),
        )
    }

    @Test
    fun `expired TSL is rejected unless stale lists are allowed`() {
        val expired = mapOf("DE" to tsl(nextUpdate = "2026-02-15T00:00:00Z"))

        assertRejected(TrustListRejection.EXPIRED, load(tsls = expired))

        val relaxed = load(tsls = expired, options = TrustListLoadOptions(allowStale = true))
        assertTrue(relaxed is TrustListLoadResult.Loaded, "got: $relaxed")
        assertEquals(listOf("DE"), (relaxed as TrustListLoadResult.Loaded).staleLists)
    }

    @Test
    fun `expired LoTL is rejected unless stale lists are allowed`() {
        val expired = lotl(nextUpdate = "2026-02-15T00:00:00Z")

        assertRejected(TrustListRejection.EXPIRED, load(lotlBytes = expired))
        assertTrue(
            load(lotlBytes = expired, options = TrustListLoadOptions(allowStale = true))
                is TrustListLoadResult.Loaded,
        )
    }

    @Test
    fun `missing NextUpdate is treated as not provably fresh`() {
        assertRejected(
            TrustListRejection.MISSING_NEXT_UPDATE,
            load(tsls = mapOf("DE" to tsl(nextUpdate = null))),
        )
    }

    @Test
    fun `lower sequence number than previously seen is a rollback`() {
        assertRejected(
            TrustListRejection.ROLLBACK,
            load(options = TrustListLoadOptions(previousLotlSequence = 11)),
        )
        assertRejected(
            TrustListRejection.ROLLBACK,
            load(options = TrustListLoadOptions(previousTslSequences = mapOf("DE" to 6))),
        )
        assertTrue(
            load(options = TrustListLoadOptions(previousLotlSequence = 10, previousTslSequences = mapOf("DE" to 5)))
                is TrustListLoadResult.Loaded,
        )
    }
}
