package org.trustweave.referencewallet.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.trustweave.referencewallet.lib.PayloadRouter
import org.trustweave.referencewallet.lib.Storage
import org.trustweave.referencewallet.lib.Wallet

/**
 * Activity-scoped reactive Compose state holder for the Android wallet.
 * Holds live credentials, activity, issuers, preferences, and pending QR/NFC/deep-link payloads.
 */
class WalletStateHolder(context: Context) {
    val wallet: Wallet = Wallet(context.applicationContext)

    var state by mutableStateOf<Wallet.State?>(null)
        private set
    var preferences by mutableStateOf(Storage.WalletPreferences())
        private set
    var activity by mutableStateOf<List<Storage.ActivityEntry>>(emptyList())
        private set
    var acceptedIssuers by mutableStateOf<List<String>>(emptyList())
        private set
    var preselectedCredentialId by mutableStateOf<String?>(null)
    var pendingOffer by mutableStateOf<PayloadRouter.IncomingOffer?>(null)
    var pendingPresentation by mutableStateOf<PayloadRouter.PresentationRequestPayload?>(null)
    var incomingDeepLink by mutableStateOf<String?>(null)

    fun refresh() {
        state = wallet.bootstrap()
        preferences = wallet.loadPreferences()
        activity = wallet.loadActivity()
        acceptedIssuers = wallet.acceptedIssuers()
    }

    fun updatePreferences(transform: (Storage.WalletPreferences) -> Storage.WalletPreferences) {
        val next = transform(preferences)
        wallet.savePreferences(next)
        preferences = next
    }

    fun deleteCredential(id: String) {
        wallet.deleteCredential(id)
        refresh()
    }

    fun removeAcceptedIssuer(did: String) {
        wallet.removeAcceptedIssuer(did)
        refresh()
    }

    fun clearActivity() {
        wallet.clearActivity()
        refresh()
    }

    fun resetWallet() {
        wallet.reset()
        preselectedCredentialId = null
        pendingOffer = null
        pendingPresentation = null
        refresh()
    }
}
