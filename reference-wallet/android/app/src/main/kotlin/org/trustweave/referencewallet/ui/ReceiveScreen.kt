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
    data class Success(val cred: Storage.StoredCredential, val format: String, val disclosable: List<String>) : ReceiveStatus
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

    LaunchedEffect(Unit) { holderDid = wallet.bootstrap().holder.did }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Receive a credential", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The demo issuer will sign a Bachelor of Science credential as an SD-JWT VC with each personal claim marked selectively disclosable. At presentation time you choose which claims to reveal.",
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
                                    val stored = wallet.store(
                                        credential = offer.credential,
                                        format = offer.format,
                                        selectivelyDisclosable = offer.selectivelyDisclosable,
                                    )
                                    status = ReceiveStatus.Success(stored, offer.format, offer.selectivelyDisclosable)
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
            is ReceiveStatus.Success -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Received and stored", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(4.dp))
                    Text("${s.cred.previewTitle} (${s.format})")
                    s.cred.previewSubtitle?.let { Text(it) }
                    if (s.disclosable.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Selectively-disclosable:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.disclosable.joinToString(", "), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDone) { Text("View in wallet") }
                }
            }
            is ReceiveStatus.Error -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Failed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(s.message)
                }
            }
            else -> Unit
        }
    }
}
