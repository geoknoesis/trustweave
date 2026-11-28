package org.trustweave.referencewallet.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.trustweave.referencewallet.BuildConfig
import org.trustweave.referencewallet.lib.BiometricHelper
import org.trustweave.referencewallet.lib.CredentialDisplay
import org.trustweave.referencewallet.lib.DemoBackend
import org.trustweave.referencewallet.lib.HolderKey

@Composable
fun SettingsScreen(
    holder: WalletStateHolder,
    onWalletReset: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val backend = remember { DemoBackend() }
    val scope = rememberCoroutineScope()

    val walletState = holder.state ?: return
    val prefs = holder.preferences
    val configuredIssuers = remember { holder.wallet.configuredIssuers().toList() }
    val acceptedIssuers = holder.acceptedIssuers

    var showWalletId by remember { mutableStateOf(false) }
    var copiedWalletId by remember { mutableStateOf(false) }
    var showRestoreBox by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingRestoreIssuer by remember { mutableStateOf<String?>(null) }
    var confirmResetStep by remember { mutableStateOf(0) }

    val walletIdQr = remember(walletState.holder.did) {
        val payload = """{"v":1,"type":"trustweave-holder","did":"${walletState.holder.did}"}"""
        generateQrImageBitmap(payload, 440)
    }

    SecureScreenEffect(enabled = showRestoreBox)

    fun runRestore(confirmedIssuer: String? = null) {
        scope.launch {
            try {
                val backendIssuers = backend.backendIssuers()
                val res = holder.wallet.restoreCredentialBackup(
                    rawJson = restoreJson,
                    backendIssuers = backendIssuers,
                    confirmedIssuer = confirmedIssuer,
                )
                if (res.untrustedIssuers.isNotEmpty() && confirmedIssuer == null) {
                    pendingRestoreIssuer = res.untrustedIssuers.first()
                    return@launch
                }
                holder.refresh()
                restoreJson = ""
                showRestoreBox = false
                notice = "Restored ${res.restored} credential(s)${if (res.skipped > 0) " (${res.skipped} skipped)" else ""}."
            } catch (e: Exception) {
                notice = e.message ?: "Restore failed."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        notice?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        SectionHeader("SECURITY & PRIVACY")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Require device unlock", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ask for fingerprint, face unlock, or screen lock before sharing or resetting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = prefs.biometricLockEnabled,
                        onCheckedChange = { enabled ->
                            holder.updatePreferences { it.copy(biometricLockEnabled = enabled) }
                        },
                    )
                }

                HorizontalDivider()

                val keyLevel = remember(walletState.keySource, walletState.holder.keystoreAlias) {
                    HolderKey.inspectSecurityLevel(walletState.keySource, walletState.holder.keystoreAlias)
                }
                val (keyTitle, keySubtitle, hardwareBacked) = when (keyLevel) {
                    HolderKey.KeySecurityLevel.STRONGBOX -> Triple(
                        "Hardware-backed key (StrongBox)",
                        "Verified via Android KeyInfo: your signing key is stored in dedicated StrongBox secure hardware on this device.",
                        true,
                    )
                    HolderKey.KeySecurityLevel.TRUSTED_ENVIRONMENT -> Triple(
                        "Hardware-backed key (Trusted Execution Environment)",
                        "Verified via Android KeyInfo: your signing key is protected inside this device's hardware TEE.",
                        true,
                    )
                    HolderKey.KeySecurityLevel.SOFTWARE_KEYSTORE -> Triple(
                        "Android Keystore key (software-backed)",
                        "Managed by Android Keystore on this device, reported as software-backed rather than dedicated hardware.",
                        false,
                    )
                    HolderKey.KeySecurityLevel.SOFTWARE_STORAGE -> Triple(
                        "Encrypted on-device key (software signing)",
                        "Your signing key is encrypted at rest in EncryptedSharedPreferences. Keystore Ed25519 requires supported Android 13+ devices.",
                        false,
                    )
                    HolderKey.KeySecurityLevel.UNKNOWN -> Triple(
                        "Android Keystore key (level unverified)",
                        "Managed by Android Keystore on this device; hardware provenance could not be verified from KeyInfo.",
                        false,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null,
                        tint = if (hardwareBacked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(keyTitle, fontWeight = FontWeight.SemiBold)
                        Text(
                            keySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionHeader("TRUSTED ISSUERS")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Credentials are accepted only from built-in issuers, this wallet's configured backend, or issuers you explicitly confirmed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (configuredIssuers.isEmpty() && acceptedIssuers.isEmpty()) {
                    Text(
                        "No custom issuers confirmed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                configuredIssuers.forEach { did ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Issuer · ${CredentialDisplay.shortDidFingerprint(did)}", fontWeight = FontWeight.Medium)
                            Text(did, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                "Built-in",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                acceptedIssuers.forEach { did ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Issuer · ${CredentialDisplay.shortDidFingerprint(did)}", fontWeight = FontWeight.Medium)
                            Text(did, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { holder.removeAcceptedIssuer(did) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        SectionHeader("BACKUP & RECOVERY")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Exports your credential records only — your private signing key stays on this device. If you lose this phone, create a new wallet and ask issuers to reissue your credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val exported = holder.wallet.exportCredentialBackup()
                            holder.refresh()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "TrustWeave Credential Backup")
                                putExtra(Intent.EXTRA_TEXT, exported)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export credential backup"))
                            notice = "Credential backup prepared (no private keys included)."
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export backup")
                    }
                    OutlinedButton(
                        onClick = { showRestoreBox = !showRestoreBox },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Restore")
                    }
                }
                if (showRestoreBox) {
                    OutlinedTextField(
                        value = restoreJson,
                        onValueChange = { restoreJson = it },
                        placeholder = { Text("Paste credential backup JSON…") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { runRestore() },
                        enabled = restoreJson.isNotBlank(),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Verify & restore")
                    }
                }
            }
        }

        SectionHeader("MY WALLET ID")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = { showWalletId = !showWalletId },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Show Wallet ID QR")
                        Icon(
                            if (showWalletId) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (showWalletId) {
                    if (walletIdQr != null) {
                        Image(
                            bitmap = walletIdQr,
                            contentDescription = "Your Wallet ID QR code",
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(192.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                        )
                    }
                    Text(
                        "Show this QR only if an issuer or verifier asks to identify your wallet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            walletState.holder.did,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(walletState.holder.did))
                            copiedWalletId = true
                            scope.launch {
                                delay(2000)
                                copiedWalletId = false
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Icon(
                            if (copiedWalletId) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(if (copiedWalletId) "Copied!" else "Copy Wallet ID")
                    }
                }
            }
        }

        SectionHeader("APPEARANCE & ADVANCED")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Material You dynamic color", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Match app surfaces to your Android wallpaper theme.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.useDynamicColor,
                            onCheckedChange = { enabled ->
                                holder.updatePreferences { it.copy(useDynamicColor = enabled) }
                            },
                        )
                    }
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Developer mode", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Show configured backend endpoint details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = prefs.developerMode,
                        onCheckedChange = { enabled ->
                            holder.updatePreferences { it.copy(developerMode = enabled) }
                        },
                    )
                }
                if (prefs.developerMode) {
                    Text(
                        "Backend: ${BuildConfig.DEMO_BACKEND_BASE_URL}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { confirmResetStep = 1 },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Reset wallet")
        }

        Spacer(Modifier.height(16.dp))
    }

    pendingRestoreIssuer?.let { issuerDid ->
        UnrecognizedIssuerDialog(
            issuerDid = issuerDid,
            onCancel = { pendingRestoreIssuer = null },
            onTrustAndAdd = {
                pendingRestoreIssuer = null
                runRestore(confirmedIssuer = issuerDid)
            },
        )
    }

    if (confirmResetStep == 1) {
        AlertDialog(
            onDismissRequest = { confirmResetStep = 0 },
            title = { Text("Reset wallet?") },
            text = {
                Text("This permanently removes all stored credentials, trusted issuers, and your on-device signing key.")
            },
            confirmButton = {
                TextButton(onClick = { confirmResetStep = 2 }) {
                    Text("Continue", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetStep = 0 }) { Text("Cancel") }
            },
        )
    } else if (confirmResetStep == 2) {
        AlertDialog(
            onDismissRequest = { confirmResetStep = 0 },
            title = { Text("Final confirmation") },
            text = {
                Text("This cannot be undone. You will need issuers to reissue your credentials after resetting.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetStep = 0
                        val doReset = {
                            holder.resetWallet()
                            onWalletReset()
                        }
                        if (prefs.biometricLockEnabled) {
                            BiometricHelper.authenticate(
                                context = context,
                                title = "Confirm wallet reset",
                                subtitle = "Authenticate to permanently erase this wallet",
                                onSuccess = doReset,
                                onNeedsManualConfirm = doReset, // User already completed two explicit confirmation dialogs.
                                onFailure = { msg -> if (msg != null) notice = msg },
                            )
                        } else {
                            doReset()
                        }
                    },
                ) {
                    Text("Reset wallet", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetStep = 0 }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}
