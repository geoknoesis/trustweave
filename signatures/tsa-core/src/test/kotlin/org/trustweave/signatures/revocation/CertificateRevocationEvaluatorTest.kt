package org.trustweave.signatures.revocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.signatures.revocation.CertificateRevocationEvaluator.Status
import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CertificateRevocationEvaluatorTest {
    private val root = TestCa("CN=Eval Root")
    private val intermediate = TestCa("CN=Eval Intermediate", parent = root)
    private val leafKey = TestCa.leafKey()
    private val leaf = intermediate.issue("CN=Eval Leaf", leafKey.public)
    private val now = Clock.System.now()

    private fun evaluate(
        signer: X509Certificate = leaf,
        carried: List<X509Certificate> = listOf(intermediate.cert, root.cert),
        anchors: List<X509Certificate> = emptyList(),
        evidence: RevocationEvidence = RevocationEvidence.NONE,
        authenticated: Instant? = null,
    ) = CertificateRevocationEvaluator.evaluate(signer, carried, anchors, evidence, authenticated, now, skewMillis = 300_000)

    private fun crls(vararg d: ByteArray) = RevocationEvidence(crls = d.toList())

    private fun ocsp(vararg d: ByteArray) = RevocationEvidence(ocspResponses = d.toList())

    private fun goodForBoth() = crls(intermediate.crl(), root.crl())

    @Test
    fun `every CA below the trust anchor is checked, not only the signer`() {
        val statuses = evaluate(evidence = goodForBoth())
        assertEquals(2, statuses.size, "the leaf and the intermediate; the self-signed root is the anchor")
        assertTrue(statuses.all { it is Status.Good })
    }

    @Test
    fun `a revoked intermediate is found`() {
        val revokedIntermediate = root.crl(revoked = listOf(intermediate.cert.serialNumber))
        val statuses = evaluate(evidence = crls(intermediate.crl(), revokedIntermediate))
        val revoked = statuses.first()
        assertTrue(revoked is Status.Revoked && revoked.cert == intermediate.cert, "got $statuses")
    }

    @Test
    fun `a carried certificate that is a supplied trust anchor is not itself checked, nor anything above it`() {
        // The intermediate is the anchor: only the leaf is evaluated, and the missing issuer of the intermediate
        // does not turn into an "unavailable" entry that would make REQUIRED fail forever.
        val statuses = evaluate(anchors = listOf(intermediate.cert), evidence = crls(intermediate.crl()))
        assertEquals(1, statuses.size)
        assertTrue(statuses.single() is Status.Good, "got $statuses")
    }

    @Test
    fun `an issuer that is only supplied as an anchor verifies the leaf`() {
        val statuses = evaluate(carried = emptyList(), anchors = listOf(intermediate.cert), evidence = crls(intermediate.crl()))
        assertTrue(statuses.single() is Status.Good, "got $statuses")
    }

    @Test
    fun `a response without nextUpdate vouches only while it is recent`() {
        val old = intermediate.ocsp(leaf, nextUpdate = null, thisUpdate = TestCa.hours(-24 * 365 * 5))
        val recent = intermediate.ocsp(leaf, nextUpdate = null, thisUpdate = TestCa.hours(-1))
        assertTrue(
            evaluate(
                carried = listOf(intermediate.cert),
                anchors = listOf(root.cert, intermediate.cert),
                evidence = ocsp(old),
            ).single() is Status.Unavailable,
        )
        assertTrue(
            evaluate(
                carried = listOf(intermediate.cert),
                anchors = listOf(root.cert, intermediate.cert),
                evidence = ocsp(recent),
            ).single() is Status.Good,
        )
    }

    @Test
    fun `a nextUpdate in the past makes unauthenticated evidence stale`() {
        val stale = intermediate.crl(thisUpdate = TestCa.hours(-72), nextUpdate = TestCa.hours(-48))
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = crls(stale)).single() is Status.Unavailable)
    }

    @Test
    fun `a revoked entry in a CRL with a critical extension is still honoured, but such a CRL cannot vouch`() {
        val delta = intermediate.crl(revoked = listOf(leaf.serialNumber), critical = true)
        val revoked = evaluate(anchors = listOf(intermediate.cert), evidence = crls(delta))
        assertTrue(revoked.single() is Status.Revoked, "got $revoked")
        val scopedGood = intermediate.crl(critical = true)
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = crls(scopedGood)).single() is Status.Unavailable)
    }

    @Test
    fun `removeFromCRL is not a revocation`() {
        val removed = intermediate.crl(revoked = listOf(leaf.serialNumber), reason = 8, critical = true)
        val full = intermediate.crl()
        val statuses = evaluate(anchors = listOf(intermediate.cert), evidence = crls(removed, full))
        assertTrue(statuses.single() is Status.Good, "got $statuses")
    }

    @Test
    fun `a revocation after the authenticated time does not apply, one before it does`() {
        val signedAt = now - 10.hours
        val later = intermediate.crl(revoked = listOf(leaf.serialNumber), revocationDate = TestCa.hours(-2))
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = crls(later), authenticated = signedAt).single() is Status.Good)
        val earlier = intermediate.crl(revoked = listOf(leaf.serialNumber), revocationDate = TestCa.hours(-20))
        assertTrue(
            evaluate(anchors = listOf(intermediate.cert), evidence = crls(earlier), authenticated = signedAt).single() is Status.Revoked,
        )
    }

    @Test
    fun `evidence issued before the authenticated time says nothing about it`() {
        val authenticated = now - 1.hours
        val tooOld = intermediate.crl(thisUpdate = TestCa.hours(-10), nextUpdate = TestCa.hours(24))
        assertTrue(
            evaluate(
                anchors = listOf(intermediate.cert),
                evidence = crls(tooOld),
                authenticated = authenticated,
            ).single() is Status.Unavailable,
        )
    }

    @Test
    fun `a CRL signed by another key is not accepted`() {
        val forged = intermediate.crl(signingKey = TestCa.leafKey())
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = crls(forged)).single() is Status.Unavailable)
    }

    @Test
    fun `an OCSP response for a different certificate does not cover the leaf`() {
        val other = intermediate.issue("CN=Other", TestCa.leafKey().public)
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = ocsp(intermediate.ocsp(other))).single() is Status.Unavailable)
    }

    @Test
    fun `a stray certificate in the candidates does not add a failing entry`() {
        val stray = TestCa("CN=Stray CA").cert
        val statuses = evaluate(carried = listOf(stray, intermediate.cert, root.cert), evidence = goodForBoth())
        assertEquals(2, statuses.size)
        assertTrue(statuses.all { it is Status.Good })
    }

    @Test
    fun `a re-keyed self-issued certificate is not mistaken for a root`() {
        val rekeyed = root.issue(root.subject, TestCa.leafKey().public, ca = true, usage = null)
        val statuses = evaluate(signer = rekeyed, carried = emptyList())
        assertTrue(statuses.single() is Status.Unavailable)
    }

    @Test
    fun `a revoked answer wins over a good one`() {
        val statuses =
            evaluate(
                anchors = listOf(intermediate.cert),
                evidence =
                    RevocationEvidence(
                        crls = listOf(intermediate.crl(revoked = listOf(leaf.serialNumber))),
                        ocspResponses = listOf(intermediate.ocsp(leaf)),
                    ),
            )
        assertTrue(statuses.single() is Status.Revoked)
    }

    @Test
    fun `garbage and oversized evidence is ignored`() {
        val evidence = RevocationEvidence(crls = listOf(ByteArray(10)), ocspResponses = listOf(ByteArray(10)))
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = evidence).single() is Status.Unavailable)
        val many = RevocationEvidence(crls = List(500) { ByteArray(4) } + intermediate.crl())
        // Only the first MAX_ITEMS are read, so the genuine CRL placed after them is not seen.
        assertTrue(evaluate(anchors = listOf(intermediate.cert), evidence = many).single() is Status.Unavailable)
        assertEquals(64, CertificateRevocationEvaluator.MAX_ITEMS)
    }
}
