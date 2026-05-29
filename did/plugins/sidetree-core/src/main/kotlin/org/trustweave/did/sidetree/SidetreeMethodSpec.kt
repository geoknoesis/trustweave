package org.trustweave.did.sidetree

/**
 * Per-method Sidetree wire configuration. Different DID methods built on Sidetree
 * (ION, Orb, ...) share the operation construction logic but differ on the DID
 * namespace prefix and the HTTP routes their nodes expose.
 *
 * @param namespace DID method namespace, including a trailing colon — e.g.
 *                  `"did:ion:"`, `"did:orb:"`.
 * @param operationsPath HTTP path that accepts operations via POST. ION uses
 *                       `"/operations"`; Orb uses `"/sidetree/v1/operations"`.
 * @param identifiersPath HTTP path that resolves DIDs via GET, with the DID
 *                        appended. ION uses `"/identifiers"`; Orb uses
 *                        `"/sidetree/v1/identifiers"`.
 */
data class SidetreeMethodSpec(
    val namespace: String,
    val operationsPath: String,
    val identifiersPath: String,
) {
    init {
        require(namespace.endsWith(":")) { "Sidetree namespace must end with ':' (got '$namespace')" }
        require(operationsPath.startsWith("/")) { "operationsPath must be a path starting with '/'" }
        require(identifiersPath.startsWith("/")) { "identifiersPath must be a path starting with '/'" }
    }

    companion object {
        val ION: SidetreeMethodSpec = SidetreeMethodSpec(
            namespace = "did:ion:",
            operationsPath = "/operations",
            identifiersPath = "/identifiers",
        )

        val ORB: SidetreeMethodSpec = SidetreeMethodSpec(
            namespace = "did:orb:",
            operationsPath = "/sidetree/v1/operations",
            identifiersPath = "/sidetree/v1/identifiers",
        )
    }
}
