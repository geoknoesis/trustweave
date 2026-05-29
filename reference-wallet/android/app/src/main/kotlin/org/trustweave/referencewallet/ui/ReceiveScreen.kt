package org.trustweave.referencewallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.trustweave.referencewallet.lib.DemoBackend
import org.trustweave.referencewallet.lib.Storage
import org.trustweave.referencewallet.lib.Wallet

private sealed interface ReceiveStatus {
    data object Idle : ReceiveStatus
    data object Requesting : ReceiveStatus
    data class Success(val cred: Storage.StoredCredential) : ReceiveStatus
    data class Error(val message: String) : ReceiveStatus
}

@Composable
fun ReceiveScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val wallet = remember { Wallet(context) }
    val backend = remember { DemoBackend() }
    val scope = rememberCoroutineScope()

    var holderDid by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ReceiveStatus>(ReceiveStatus.Idle) }

    LaunchedEffect(Unit) {
        holderDid = wallet.bootstrap().holder.did
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Receive a credential", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The demo issuer will sign a Bachelor of Science credential addressed to your holder DID and return it as a Verifiable Credential JWT.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                holderDid?.let { did ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("SUBJECT DID (YOU)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(did, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                status = ReceiveStatus.Requesting
                                try {
                                    val offer = backend.receiveCredential(did)
                                    val stored = wallet.store(offer.credential)
                                    status = ReceiveStatus.Success(stored)
                                } catch (e: Exception) {
                                    status = ReceiveStatus.Error(e.message ?: e::class.simpleName ?: "unknown")
                                }
                            }
                        },
                        enabled = status !is ReceiveStatus.Requesting,
                    ) {
                        if (status is ReceiveStatus.Requesting) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.height(8.dp))
                            Text("Requesting…")
                        } else {
                            Text("Receive demo credential")
                        }
                    }
                }
            }
        }

        when (val s = status) {
            is ReceiveStatus.Success -> CalloutCard(
                title = "Received and stored",
                body = "${s.cred.previewTitle} · ${s.cred.previewSubtitle ?: ""}",
                primaryAction = "View in wallet" to onDone,
                tint = MaterialTheme.colorScheme.secondary,
            )
            is ReceiveStatus.Error -> CalloutCard(
                title = "Failed",
                body = s.message,
                tint = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
    }
}

@Composable
private fun CalloutCard(
    title: String,
    body: String,
    primaryAction: Pair<String, () -> Unit>? = null,
    tint: androidx.compose.ui.graphics.Color,
) {
    Card(colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.1f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = tint)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            primaryAction?.let { (label, action) ->
                Spacer(Modifier.height(12.dp))
                Button(onClick = action) { Text(label) }
            }
        }
    }
}
