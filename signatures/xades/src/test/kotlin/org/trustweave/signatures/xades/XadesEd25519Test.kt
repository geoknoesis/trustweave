package org.trustweave.signatures.xades

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import java.security.KeyPairGenerator

class XadesEd25519Test {
    @Test
    fun `signing with an Ed25519 certificate fails explicitly instead of degrading`() =
        runBlocking<Unit> {
            val ca = TestCa()
            val edKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val chain = listOf(ca.issue(edKey.public, "CN=Ed25519 Signer").encoded, ca.caCert.encoded)
            val e =
                shouldThrow<XadesSignerException> {
                    DefaultXadesSigner(TestKms(), edKey.private).sign(
                        XadesSigningRequest(
                            profile = XadesProfile.B_B,
                            keyId = KeyId("ed"),
                            document = XadesForge.sampleDocument(),
                            signerCertificateChain = chain,
                        ),
                    )
                }
            e.message!! shouldContain "Ed25519"
        }
}
