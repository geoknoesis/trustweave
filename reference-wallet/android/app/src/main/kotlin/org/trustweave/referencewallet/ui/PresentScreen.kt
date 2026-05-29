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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.referencewallet.lib.DemoBackend
import org.trustweave.referencewallet.lib.Storage
import org.trustweave.referencewallet.lib.Wallet

private sealed interface PresentStatus {
    data object Idle : PresentStatus
    data object FetchingRequest : PresentStatus
    data object BuildingVp : PresentStatus
    data object Verifying : PresentStatus
    data class Done(val response: DemoBackend.VerificationResponse) : PresentStatus
    data class Error(val message: String) : PresentStatus
}

@Composable
fun PresentScreen() {
    val context = LocalContext.current
    val wallet = remember { Wallet(context) }
    val backend = remember { DemoBackend() }
    val scope = rememberCoroutineScope()

    var creds by remember { mutableStateOf<List<Storage.StoredCredential>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val disclose: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
    var status by remember { mutableStateOf<PresentStatus>(PresentStatus.Idle) }

    LaunchedEffect(Unit) {
        wallet.bootstrap()
        val all = wallet.list()
        creds = all
        if (all.isNotEmpty()) selectedId = all.first().id
    }

    // Reset disclosure selection when chosen credential changes.
    LaunchedEffect(selectedId, creds) {
        val cred = creds.firstOrNull { it.id == selectedId }
        disclose.clear()
        cred?.selectivelyDisclosable?.forEach { disclose[it] = true }  // default: reveal all
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (creds.isEmpty()) {
            EmptyState()
        } else {
            val selectedCred = creds.firstOrNull { it.id == selectedId }
            ChooseCard(creds = creds, selectedId = selectedId, onSelect = { selectedId = it })
            if (selectedCred?.format == "vc+sd-jwt" && selectedCred.selectivelyDisclosable.isNotEmpty()) {
                DiscloseCard(claims = selectedCred.selectivelyDisclosable, disclose = disclose)
            }
            ActionsRow(
                enabled = selectedId != null && (status is PresentStatus.Idle || status is PresentStatus.Done || status is PresentStatus.Error),
                status = status,
                onPresent = {
                    val id = selectedId ?: return@ActionsRow
                    val cred = creds.firstOrNull { it.id == id } ?: return@ActionsRow
                    scope.launch {
                        try {
                            status = PresentStatus.FetchingRequest
                            val req = backend.fetchPresentationRequest()
                            status = PresentStatus.BuildingVp
                            val discloseSet = disclose.entries.filter { it.value }.map { it.key }.toSet()
                            val vp = wallet.createPresentation(listOf(id), req.audience, req.nonce, discloseSet)
                            status = PresentStatus.Verifying
                            val res = backend.verify(presentation = vp, format = cred.format, expectedNonce = req.nonce)
                            status = PresentStatus.Done(res)
                        } catch (e: Exception) {
                            status = PresentStatus.Error(e.message ?: e::class.simpleName ?: "unknown")
                        }
                    }
                },
            )
            when (val s = status) {
                is PresentStatus.Done -> VerificationResult(s.response)
                is PresentStatus.Error -> ErrorCard(s.message)
                else -> Unit
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("No credentials to present.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Receive one from the Receive tab first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChooseCard(creds: List<Storage.StoredCredential>, selectedId: String?, onSelect: (String) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Present a credential", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            creds.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedId == c.id, onClick = { onSelect(c.id) })
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.previewTitle, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(6.dp))
                            Text("(${c.format})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        c.previewSubtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscloseCard(claims: List<String>, disclose: SnapshotStateMap<String, Boolean>) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Disclose to verifier", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tick the claims you're willing to reveal. Unticked claims stay hashed inside the credential — the verifier sees only that the issuer signed something, not what.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            claims.forEach { name ->
                val checked = disclose[name] ?: false
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { disclose[name] = it })
                    Text(
                        name,
                        fontFamily = FontFamily.Monospace,
                        color = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsRow(enabled: Boolean, status: PresentStatus, onPresent: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Button(onClick = onPresent, enabled = enabled) {
                Text(when (status) {
                    PresentStatus.FetchingRequest -> "Fetching verifier request…"
                    PresentStatus.BuildingVp -> "Building presentation…"
                    PresentStatus.Verifying -> "Verifying…"
                    else -> "Present to demo verifier"
                })
            }
            if (status is PresentStatus.FetchingRequest || status is PresentStatus.BuildingVp || status is PresentStatus.Verifying) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun VerificationResult(response: DemoBackend.VerificationResponse) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Verification result", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val statusColor = if (response.valid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (response.valid) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, contentDescription = null, tint = statusColor)
                    Text(
                        if (response.valid) "Presentation verified." else "Verification failed.",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Checklist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            response.checks.forEach { c -> CheckRow(c) }

            response.credentials?.takeIf { it.isNotEmpty() }?.let { creds ->
                Spacer(Modifier.height(16.dp))
                Text("What the verifier saw", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                creds.forEach { cred ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("type: ${cred.type.joinToString(", ")}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            cred.disclosedClaims.forEach { (k, v) ->
                                val rendered = (v as? JsonPrimitive)?.content ?: v.toString()
                                Text("$k: $rendered", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            cred.withheldClaimNames?.takeIf { it.isNotEmpty() }?.let { wh ->
                                Spacer(Modifier.height(6.dp))
                                Text("withheld:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                wh.forEach { Text("  $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CheckRow(c: DemoBackend.VerificationCheck) {
    val tint = if (c.passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Surface(color = tint.copy(alpha = 0.08f), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Text(if (c.passed) "✓" else "✗", color = tint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(c.step, style = MaterialTheme.typography.bodyMedium)
                c.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
        Column(Modifier.padding(16.dp)) {
            Text("Failed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(message)
        }
    }
}
