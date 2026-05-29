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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var status by remember { mutableStateOf<PresentStatus>(PresentStatus.Idle) }

    LaunchedEffect(Unit) {
        wallet.bootstrap()
        val all = wallet.list()
        creds = all
        if (all.isNotEmpty()) selectedId = all.first().id
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (creds.isEmpty()) {
            EmptyState()
        } else {
            ChooseCard(creds = creds, selectedId = selectedId, onSelect = { selectedId = it })
            ActionsRow(
                enabled = selectedId != null && (status is PresentStatus.Idle || status is PresentStatus.Done || status is PresentStatus.Error),
                status = status,
                onPresent = {
                    val id = selectedId ?: return@ActionsRow
                    scope.launch {
                        try {
                            status = PresentStatus.FetchingRequest
                            val req = backend.fetchPresentationRequest()
                            status = PresentStatus.BuildingVp
                            val vp = wallet.createPresentation(listOf(id), req.audience, req.nonce)
                            status = PresentStatus.Verifying
                            val res = backend.verify(vp, req.nonce)
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
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("No credentials to present.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Receive one from the Receive tab first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChooseCard(
    creds: List<Storage.StoredCredential>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Present a credential", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "The wallet builds a Verifiable Presentation containing the selected credential, signs it with your holder DID, and posts it to the demo verifier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            creds.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedId == c.id, onClick = { onSelect(c.id) })
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.previewTitle, fontWeight = FontWeight.SemiBold)
                        c.previewSubtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsRow(
    enabled: Boolean,
    status: PresentStatus,
    onPresent: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Button(onClick = onPresent, enabled = enabled) {
                Text(
                    when (status) {
                        PresentStatus.FetchingRequest -> "Fetching verifier request…"
                        PresentStatus.BuildingVp -> "Building presentation…"
                        PresentStatus.Verifying -> "Verifying…"
                        else -> "Present to demo verifier"
                    },
                )
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
                    Icon(
                        if (response.valid) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                    )
                    Spacer(Modifier.height(0.dp))
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
            response.checks.forEach { c ->
                CheckRow(c)
            }

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
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "Phase 2 sends ALL claims in the VC. Selective disclosure (only the specific facts the verifier asked for) is Phase 2.5 — that's when the disclosed/withheld split becomes visible here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(c: DemoBackend.VerificationCheck) {
    val passColor = MaterialTheme.colorScheme.secondary
    val failColor = MaterialTheme.colorScheme.error
    val tint = if (c.passed) passColor else failColor
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
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
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
