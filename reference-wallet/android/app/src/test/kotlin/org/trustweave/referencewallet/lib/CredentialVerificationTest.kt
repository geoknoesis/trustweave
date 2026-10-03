package org.trustweave.referencewallet.lib

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Pure JVM tests of the import profile: issuer trust, holder binding and validity checks. */
class CredentialVerificationTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun registerProvider() {
            if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
        }
    }

    private val now = 1_800_000_000L
    private val issuerKey = Crypto.generateEd25519()
    private val issuerDid = Crypto.publicKeyToDidKey(issuerKey.publicKey)
    private val holderDid = Crypto.publicKeyToDidKey(Crypto.generateEd25519().publicKey)
    private val trusting = IssuerTrustPolicy.allowList(setOf(issuerDid))

    private fun vcJwt(
        sub: String = holderDid,
        extra: String = "",
        signWith: ByteArray = issuerKey.privateKey,
    ): String {
        val payload =
            """{"iss":"$issuerDid","sub":"$sub","iat":${now - 10},"vc":{"type":["VerifiableCredential","Demo"]}$extra}"""
        return Crypto.signJwsCompact(payload, signWith, "$issuerDid#${issuerDid.removePrefix("did:key:")}")
    }

    private fun verify(
        compact: String,
        policy: IssuerTrustPolicy = trusting,
        requireBinding: Boolean = false,
    ) = CredentialVerification.verifyImportedCredential(
        compact = compact,
        format = "vc+jwt",
        holderDid = holderDid,
        nowEpochSeconds = now,
        issuerPolicy = policy,
        requireHolderKeyBinding = requireBinding,
    )

    private fun assertRejected(
        message: String,
        block: () -> Unit,
    ) {
        val e = assertThrows(CredentialVerification.RejectedCredentialException::class.java) { block() }
        assertTrue("expected '$message' in '${e.message}'", e.message!!.contains(message))
    }

    @Test
    fun `a signed credential from a trusted issuer bound to the holder is accepted`() {
        verify(vcJwt())
    }

    @Test
    fun `a validly signed credential from an untrusted issuer is rejected`() {
        assertRejected("not trusted") { verify(vcJwt(), IssuerTrustPolicy.allowList(setOf("did:key:z6MkOther"))) }
    }

    @Test
    fun `the default policy trusts nobody`() {
        assertRejected("not trusted") {
            CredentialVerification.verifyImportedCredential(vcJwt(), "vc+jwt", holderDid, now)
        }
    }

    @Test
    fun `a credential signed by a different key than the issuer DID is rejected`() {
        val other = Crypto.generateEd25519()
        assertRejected("signature is invalid") { verify(vcJwt(signWith = other.privateKey)) }
    }

    @Test
    fun `a credential bound to another holder is rejected`() {
        assertRejected("not to this wallet") { verify(vcJwt(sub = "did:key:z6MkSomeoneElse")) }
    }

    @Test
    fun `an expired credential is rejected`() {
        assertRejected("expired") { verify(vcJwt(extra = ""","exp":${now - 1}""")) }
    }

    @Test
    fun `plain VC-JWT without a cnf binding is rejected only when key binding is required`() {
        verify(vcJwt())
        assertRejected("holder key binding") { verify(vcJwt(), requireBinding = true) }
        verify(vcJwt(extra = ""","cnf":{"kid":"$holderDid"}"""), requireBinding = true)
        assertRejected("holder key binding") {
            verify(vcJwt(extra = ""","cnf":{"kid":"did:key:z6MkOther"}"""), requireBinding = true)
        }
    }

    @Test
    fun `the allow list matches exactly and a copy is taken`() {
        val mutable = mutableSetOf("did:key:a")
        val policy = IssuerTrustPolicy.allowList(mutable)
        mutable.add("did:key:b")
        assertTrue(policy.isTrusted("did:key:a"))
        assertTrue(!policy.isTrusted("did:key:b"))
        assertTrue(!policy.isTrusted("did:key:a#key"))
    }
}
