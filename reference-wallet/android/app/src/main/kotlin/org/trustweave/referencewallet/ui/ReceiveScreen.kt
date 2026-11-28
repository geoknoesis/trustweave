package org.trustweave.referencewallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.trustweave.referencewallet.lib.CredentialDisplay
import org.trustweave.referencewallet.lib.DemoBackend
import org.trustweave.referencewallet.lib.PayloadRouter
import org.trustweave.referencewallet.lib.Storage
import org.trustweave.referencewallet.lib.UntrustedIssuerException
import org.trustweave.referencewallet.lib.Wallet

private sealed interface ReceiveStatus {
    data object Idle : ReceiveStatus
    data object Requesting : ReceiveStatus
    data class Success(val cred: Storage.StoredCredential) : ReceiveStatus
    data class ConfirmIssuer(
        val issuerDid: String,
        val offer: DemoBackend.CredentialOffer,
        val backendIssuers: Set<String>,
    ) : ReceiveStatus
    data class Error(val message: String) : ReceiveStatus
}

@Composable
fun ReceiveScreen(
    holder: WalletStateHolder,
    onViewWallet: () -> Unit,
    onShareCredential: (String) -> Unit,
) {
    val backend = remember { DemoBackend() }
    val scope = rememberCoroutineScope()
    val holderDid = holder.state?.holder?.did

    var status by remember { mutableStateOf<ReceiveStatus>(ReceiveStatus.Idle) }

    fun executeClaim(incoming: PayloadRouter.IncomingOffer?) {
        val did = holderDid ?: return
        scope.launch {
            status = ReceiveStatus.Requesting
            try {
                val offer = if (incoming != null) {
                    backend.claimIncomingOffer(incoming, did)
                } else {
                    backend.receiveCredential(did)
                }
                val backendIssuers = backend.backendIssuers()
                status = importOffer(
                    wallet = holder.wallet,
                    offer = offer,
                    backendIssuers = backendIssuers,
                    confirmedIssuer = null,
                    onStored = { holder.refresh() },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = ReceiveStatus.Error(
                    e.message ?: "Could not reach the issuer. Check your connection and try again.",
                )
            }
        }
    }

    // Automatically claim any QR / NFC / OS deep-link offer handed off from Scan or MainActivity.
    LaunchedEffect(holder.pendingOffer, holderDid) {
        val incoming = holder.pendingOffer ?: return@LaunchedEffect
        if (holderDid == null) return@LaunchedEffect
        holder.pendingOffer = null
        executeClaim(incoming)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = status) {
            is ReceiveStatus.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Text(
                            "Credential added",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            s.cred.previewTitle,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        s.cred.previewSubtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                status = ReceiveStatus.Idle
                                onViewWallet()
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("View in wallet")
                        }
                        OutlinedButton(
                            onClick = {
                                val id = s.cred.id
                                status = ReceiveStatus.Idle
                                onShareCredential(id)
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Share now")
                        }
                    }
                }
            }
            else -> {
                Text(
                    "Verify and save a signed credential privately on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                Icons.Outlined.AddCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Text(
                            "Receive from issuer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Your wallet checks the issuer signature before saving anything. When sharing later, you decide which details to reveal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { executeClaim(null) },
                            enabled = holderDid != null && status !is ReceiveStatus.Requesting,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            if (status is ReceiveStatus.Requesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.size(10.dp))
                                Text("Verifying credential…")
                            } else {
                                Text("Receive sample credential")
                            }
                        }
                    }
                }

                if (s is ReceiveStatus.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "Could not add credential",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }

    val confirm = status as? ReceiveStatus.ConfirmIssuer
    if (confirm != null) {
        UnrecognizedIssuerDialog(
            issuerDid = confirm.issuerDid,
            onCancel = { status = ReceiveStatus.Idle },
            onTrustAndAdd = {
                status = try {
                    importOffer(
                        wallet = holder.wallet,
                        offer = confirm.offer,
                        backendIssuers = confirm.backendIssuers,
                        confirmedIssuer = confirm.issuerDid,
                        onStored = { holder.refresh() },
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ReceiveStatus.Error(e.message ?: "Verification failed.")
                }
            },
        )
    }
}

@Composable
fun UnrecognizedIssuerDialog(
    issuerDid: String,
    onCancel: () -> Unit,
    onTrustAndAdd: () -> Unit,
) {
    var showFullDid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        },
        title = { Text("Unrecognized issuer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This wallet does not automatically recognize the organization that signed this credential. Only continue if you directly expected it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "ISSUER KEY FINGERPRINT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            CredentialDisplay.shortDidFingerprint(issuerDid),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
                TextButton(
                    onClick = { showFullDid = !showFullDid },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (showFullDid) "Hide full cryptographic ID" else "Inspect full cryptographic ID")
                        Icon(
                            if (showFullDid) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (showFullDid) {
                    Text(
                        issuerDid,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onTrustAndAdd) { Text("Trust issuer & add") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

private fun importOffer(
    wallet: Wallet,
    offer: DemoBackend.CredentialOffer,
    backendIssuers: Set<String>,
    confirmedIssuer: String?,
    onStored: () -> Unit,
): ReceiveStatus =
    try {
        val stored = wallet.store(
            credential = offer.credential,
            format = offer.format,
            selectivelyDisclosable = offer.selectivelyDisclosable,
            backendIssuers = backendIssuers,
            confirmedIssuer = confirmedIssuer,
        )
        onStored()
        ReceiveStatus.Success(stored)
    } catch (e: UntrustedIssuerException) {
        ReceiveStatus.ConfirmIssuer(e.issuerDid, offer, backendIssuers)
    }
