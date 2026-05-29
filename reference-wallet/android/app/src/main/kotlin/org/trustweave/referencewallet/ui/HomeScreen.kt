package org.trustweave.referencewallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.trustweave.referencewallet.lib.Storage
import org.trustweave.referencewallet.lib.Wallet

@Composable
fun HomeScreen(
    onReceive: () -> Unit,
    onPresent: () -> Unit,
) {
    val context = LocalContext.current
    val wallet = remember { Wallet(context) }
    var state by remember { mutableStateOf<Wallet.State?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Storage.StoredCredential?>(null) }

    LaunchedEffect(Unit) {
        state = wallet.bootstrap()
    }

    val s = state ?: run {
        Column(Modifier.padding(16.dp)) { Text("Initialising wallet…") }
        return
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IdentityPanel(holder = s.holder)
        CredentialsPanel(
            credentials = s.credentials,
            onReceive = onReceive,
            onPresent = onPresent,
            onDelete = { confirmDelete = it },
        )
        DangerZonePanel(onReset = { confirmReset = true })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset wallet?") },
            text = { Text("Wipes your holder identity AND every stored credential. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    wallet.reset()
                    state = wallet.bootstrap()
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { cred ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete credential?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    wallet.deleteCredential(cred.id)
                    state = s.copy(credentials = wallet.list())
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IdentityPanel(holder: Storage.HolderIdentity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("Your wallet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("HOLDER DID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(holder.did, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            val keystoreBound = holder.keySource == "keystore"
            Surface(
                color = if (keystoreBound) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        if (keystoreBound) "🔒 Key in Android Keystore" else "🔑 Key in EncryptedSharedPreferences",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (keystoreBound) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (keystoreBound) {
                            "Ed25519 private key lives inside AndroidKeyStore. Signing happens there; the bytes never enter app memory."
                        } else {
                            "Ed25519 seed lives encrypted in EncryptedSharedPreferences (Keystore wraps the storage key, not the signing key). Keystore-bound Ed25519 is API 33+ only; this device falls back to software."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialsPanel(
    credentials: List<Storage.StoredCredential>,
    onReceive: () -> Unit,
    onPresent: () -> Unit,
    onDelete: (Storage.StoredCredential) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Credentials (${credentials.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (credentials.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No credentials yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onReceive) { Text("Receive a demo credential") }
                }
            } else {
                credentials.forEach { cred ->
                    CredentialCard(cred = cred, onDelete = { onDelete(cred) })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReceive) { Text("Receive another") }
                    OutlinedButton(onClick = onPresent) { Text("Present") }
                }
            }
        }
    }
}

@Composable
private fun CredentialCard(cred: Storage.StoredCredential, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(cred.previewTitle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                cred.previewSubtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "issued by ${cred.issuerDid.take(30)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DangerZonePanel(onReset: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Wipe the wallet — irrecoverable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Reset wallet") }
        }
    }
}
