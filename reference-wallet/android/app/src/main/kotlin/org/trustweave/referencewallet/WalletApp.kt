package org.trustweave.referencewallet

import android.app.Application
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Application entry point. Registers Bouncy Castle once at process start so the rest of
 * the app can use Ed25519 KeyPairGenerator / Signature primitives via the JCA API.
 *
 * NOTE: We register the BC provider at position 1 to take precedence over Android's
 * built-in BouncyCastle (which is a stripped-down fork that lacks Ed25519 on older API
 * levels). On Android 28+ Conscrypt is the default JCE — BC sits alongside it for the
 * algorithms Conscrypt doesn't cover.
 */
class WalletApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Remove any pre-registered Android BouncyCastle then add the upstream one.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
