package org.trustweave.signatures.pades

/**
 * Produces a PAdES signature embedded inside a PDF document.
 *
 * # Why this interface ships with a not-implemented default
 *
 * PAdES signing fundamentally requires a PDF library: the producer must (a) compute the document's
 * byte-range, (b) embed a CMS / CAdES SignedData blob inside a `/Sig` dictionary, (c) re-write the
 * `/ByteRange` array after the placeholder is filled, and (d) preserve incremental-update semantics
 * so existing PDF readers still trust the file. The de-facto industry library is Apache PDFBox; it
 * adds ~6 MB of jars and a non-trivial dependency surface that TrustWeave deliberately does not
 * pull in for the MVP.
 *
 * The MVP therefore ships the interface and data model but not a working implementation. When a
 * real PAdES use case appears (e.g. notarised eIDAS QES invoices, signed CO2-emission declarations
 * for the EU CBAM regime), add a new module `signatures:pades-pdfbox` and supply a real
 * implementation behind the same interface. See
 * `docs/architecture/eidas-qes-design.md` §13 for the planned roadmap.
 */
interface PadesSigner {
    /**
     * Sign [request].
     *
     * @throws NotImplementedError when called on [NotImplementedPadesSigner].
     */
    suspend fun sign(request: PadesSigningRequest): PadesSignature
}

/**
 * Placeholder [PadesSigner] that throws [NotImplementedError] on every call.
 *
 * This exists so that callers can wire the PAdES module at compile time and discover the missing
 * implementation at runtime rather than via a `ClassNotFoundException`. Replace with a real
 * PDFBox-backed implementation when the planned `signatures:pades-pdfbox` module is added.
 */
class NotImplementedPadesSigner : PadesSigner {
    override suspend fun sign(request: PadesSigningRequest): PadesSignature {
        throw NotImplementedError(
            "PAdES requires the optional Apache PDFBox dependency — " +
                "see docs/architecture/eidas-qes-design.md §12",
        )
    }
}
