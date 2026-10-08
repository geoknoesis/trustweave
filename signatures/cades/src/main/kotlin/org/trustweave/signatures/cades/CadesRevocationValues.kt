package org.trustweave.signatures.cades

import kotlinx.coroutines.CancellationException
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.esf.RevocationValues
import org.bouncycastle.asn1.ocsp.BasicOCSPResponse
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.ocsp.OCSPResponse
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus
import org.bouncycastle.asn1.ocsp.ResponseBytes
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.CertificateList
import org.bouncycastle.cert.X509CRLHolder
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.trustweave.signatures.revocation.CertificateRevocationEvaluator
import org.trustweave.signatures.revocation.RevocationEvidence

/** The embedded revocation data of a CMS signature is not well-formed. */
internal class MalformedRevocationValuesException(
    message: String,
) : Exception(message)

/**
 * Reads the revocation evidence a CAdES signature carries itself:
 *
 * - the `revocationValues` unsigned attribute (`id-aa-ets-revocationValues`, ETSI EN 319 122-1 §5.3.4), whose
 *   `crlVals` are CRLs and whose `ocspVals` are `BasicOCSPResponse` structures (re-wrapped here into the
 *   `OCSPResponse` form the evaluator takes);
 * - the RFC 5652 `SignedData.crls` field: plain CRLs and `id-ri-ocsp-response` other-revocation-info.
 *
 * Only structure is checked here; every item is verified against the issuing CA by the evaluator before use. At most
 * [CertificateRevocationEvaluator.MAX_ITEMS] items of each kind are kept and an item larger than
 * [CertificateRevocationEvaluator.MAX_ITEM_BYTES] is dropped (so it can never supply a "good" status). Data that cannot
 * be parsed is not skipped: it raises [MalformedRevocationValuesException] and the caller refuses the signature.
 */
internal object CadesRevocationValues {
    fun embedded(
        cms: CMSSignedData,
        signerInfo: SignerInformation,
    ): RevocationEvidence {
        val crls = mutableListOf<ByteArray>()
        val ocsp = mutableListOf<ByteArray>()
        try {
            collectSignedDataFields(cms, crls, ocsp)
            collectAttributes(signerInfo, crls, ocsp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: MalformedRevocationValuesException) {
            throw e
        } catch (t: Throwable) {
            throw MalformedRevocationValuesException("embedded revocation data is malformed: ${t.message ?: t.javaClass.simpleName}")
        }
        return RevocationEvidence(
            crls.take(CertificateRevocationEvaluator.MAX_ITEMS),
            ocsp.take(CertificateRevocationEvaluator.MAX_ITEMS),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectSignedDataFields(
        cms: CMSSignedData,
        crls: MutableList<ByteArray>,
        ocsp: MutableList<ByteArray>,
    ) {
        for (holder in cms.getCRLs().getMatches(null) as Collection<X509CRLHolder>) {
            // Re-parse: a holder built over garbage would otherwise only fail later, silently.
            add(crls, CertificateList.getInstance(holder.encoded).getEncoded(ASN1Encoding.DER))
        }
        val others = cms.getOtherRevocationInfo(CMSObjectIdentifiers.id_ri_ocsp_response)
        for (item in others.getMatches(null) as Collection<ASN1Encodable>) {
            val response = OCSPResponse.getInstance(item)
            add(ocsp, response.getEncoded(ASN1Encoding.DER))
        }
    }

    private fun collectAttributes(
        signerInfo: SignerInformation,
        crls: MutableList<ByteArray>,
        ocsp: MutableList<ByteArray>,
    ) {
        val attributes = signerInfo.unsignedAttributes ?: return
        val all = attributes.getAll(PKCSObjectIdentifiers.id_aa_ets_revocationValues)
        for (i in 0 until all.size()) {
            val attribute =
                org.bouncycastle.asn1.cms.Attribute
                    .getInstance(all.get(i))
            val values = attribute.attrValues
            if (values.size() == 0) throw MalformedRevocationValuesException("revocationValues attribute is empty")
            for (v in 0 until values.size()) {
                val rv = RevocationValues.getInstance(values.getObjectAt(v))
                rv.crlVals?.forEach { add(crls, it.getEncoded(ASN1Encoding.DER)) }
                rv.ocspVals?.forEach { add(ocsp, wrap(it).getEncoded(ASN1Encoding.DER)) }
            }
        }
    }

    private fun wrap(basic: BasicOCSPResponse): OCSPResponse =
        OCSPResponse(
            OCSPResponseStatus(OCSPResponseStatus.SUCCESSFUL),
            ResponseBytes(OCSPObjectIdentifiers.id_pkix_ocsp_basic, DEROctetString(basic)),
        )

    private fun add(
        into: MutableList<ByteArray>,
        bytes: ByteArray,
    ) {
        if (bytes.size <= CertificateRevocationEvaluator.MAX_ITEM_BYTES) into += bytes
    }
}
