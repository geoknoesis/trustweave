package org.trustweave.referencewallet.ui

import java.net.URI
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.trustweave.referencewallet.lib.BiometricHelper
import org.trustweave.referencewallet.lib.CredentialDisplay
import org.trustweave.referencewallet.lib.DemoBackend
import org.trustweave.referencewallet.lib.PayloadRouter
import org.trustweave.referencewallet.lib.Storage

private sealed interface PresentStatus {
    data object Idle : PresentStatus
    data object FetchingRequest : PresentStatus
    data object BuildingVp : PresentStatus
    data object Verifying : PresentStatus
    data class Done(
        val response: DemoBackend.VerificationResponse,
        val credentialTitle: String,
        val verifierName: String,
        val sharedCount: Int,
        val totalCount: Int,
    ) : PresentStatus
    data class Error(val message: String) : PresentStatus
}

@Composable
fun PresentScreen(
    holder: WalletStateHolder,
    onScan: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val backend = remember { DemoBackend() }
    val scope = rememberCoroutineScope()

    val creds = holder.state?.credentials.orEmpty()
    var activeTarget by remember { mutableStateOf<PayloadRouter.PresentationRequestPayload?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val disclose: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
    var status by remember { mutableStateOf<PresentStatus>(PresentStatus.Idle) }
    var showManualConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(holder.pendingPresentation) {
        val pending = holder.pendingPresentation ?: return@LaunchedEffect
        activeTarget = pending
        holder.pendingPresentation = null
    }

    LaunchedEffect(creds, holder.preselectedCredentialId) {
        val preferred = holder.preselectedCredentialId
        if (preferred != null && creds.any { it.id == preferred }) {
            selectedId = preferred
            holder.preselectedCredentialId = null
        } else if (selectedId == null || creds.none { it.id == selectedId }) {
            selectedId = creds.firstOrNull()?.id
        }
    }

    // Privacy-first default: start optional claims OFF so users consciously choose what to reveal.
    LaunchedEffect(selectedId, creds) {
        val cred = creds.firstOrNull { it.id == selectedId }
        disclose.clear()
        cred?.selectivelyDisclosable?.forEach { disclose[it] = false }
    }

    val doneState = status as? PresentStatus.Done
    if (doneState != null) {
        ShareOutcomeView(
            outcome = doneState,
            onDone = {
                status = PresentStatus.Idle
                activeTarget = null
                onDone()
            },
        )
        return
    }

    val selectedCred = creds.firstOrNull { it.id == selectedId }
    val nowSeconds = remember(selectedCred) { Clock.System.now().epochSeconds }
    val selectedExpired = remember(selectedCred, nowSeconds) {
        selectedCred?.let { CredentialDisplay.statusBadge(it, nowSeconds).label == "Expired" } == true
    }
    val shareableDetails = remember(selectedCred) {
        if (selectedCred?.format == "vc+sd-jwt") {
            CredentialDisplay.extractShareableClaimDetails(selectedCred)
        } else {
            emptyList()
        }
    }
    val sharedCount = shareableDetails.count { disclose[it.name] == true }
    val totalCount = shareableDetails.size
    val requesterHost = remember(activeTarget) {
        activeTarget?.verifierUrl?.let { raw ->
            try {
                URI(raw).authority ?: raw
            } catch (_: Exception) {
                raw
            }
        }
    }

    val executeShare: () -> Unit = {
        val id = selectedId
        val cred = creds.firstOrNull { it.id == id }
        if (id != null && cred != null) {
            scope.launch {
                try {
                    status = PresentStatus.FetchingRequest
                    val req = backend.fetchPresentationRequest(activeTarget)
                    val backendIssuers = backend.backendIssuers()
                    status = PresentStatus.BuildingVp
                    val discloseSet = disclose.entries.filter { it.value }.map { it.key }.toSet()
                    val vp = holder.wallet.createPresentation(
                        credentialIds = listOf(id),
                        verifierAudience = req.audience,
                        challenge = req.nonce,
                        disclose = discloseSet,
                        backendIssuers = backendIssuers,
                    )
                    status = PresentStatus.Verifying
                    val res = backend.verify(
                        presentation = vp,
                        format = cred.format,
                        expectedNonce = req.nonce,
                        target = activeTarget,
                    )
                    val verifierLabel = requesterHost
                        ?: if (req.verifier.startsWith("did:")) "Requesting verifier" else req.verifier
                    holder.wallet.recordActivity(
                        kind = "shared",
                        title = cred.previewTitle,
                        counterparty = verifierLabel,
                        detailsShared = if (totalCount > 0) discloseSet.size else null,
                        totalDetails = if (totalCount > 0) totalCount else null,
                        success = res.valid,
                    )
                    holder.refresh()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    status = PresentStatus.Done(
                        response = res,
                        credentialTitle = cred.previewTitle,
                        verifierName = verifierLabel,
                        sharedCount = discloseSet.size,
                        totalCount = totalCount,
                    )
                } catch (e: Exception) {
                    status = PresentStatus.Error(e.message ?: "Could not complete sharing.")
                }
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
        if (creds.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Text("Nothing to share yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Add a credential to your wallet first, then scan a requester's QR code or tap NFC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onScan,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Scan QR or tap NFC")
                    }
                }
            }
            return@Column
        }

        val inFlight = status is PresentStatus.FetchingRequest ||
            status is PresentStatus.BuildingVp ||
            status is PresentStatus.Verifying

        if (requesterHost != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column {
                        Text(
                            "REQUESTED BY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            requesterHost,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        } else {
            Text(
                "Choose a credential and decide which personal details to reveal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ChooseCredentialCard(
            creds = creds,
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )

        if (shareableDetails.isNotEmpty()) {
            SelectiveDisclosureCard(
                details = shareableDetails,
                disclose = disclose,
                sharedCount = sharedCount,
            )
        }

        if (selectedExpired) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "This credential has expired",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "The requester may reject it. You can still try sharing or request a renewal from the issuer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        (status as? PresentStatus.Error)?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Sharing failed",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        err.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Button(
            onClick = {
                val cred = selectedCred ?: return@Button
                if (holder.preferences.biometricLockEnabled) {
                    val subtitle = if (totalCount > 0) {
                        "Share ${cred.previewTitle} ($sharedCount of $totalCount details)"
                    } else {
                        "Share ${cred.previewTitle}"
                    }
                    BiometricHelper.authenticate(
                        context = context,
                        title = "Confirm sharing",
                        subtitle = subtitle,
                        onSuccess = executeShare,
                        onNeedsManualConfirm = { showManualConfirmDialog = true },
                        onFailure = { msg ->
                            if (msg != null) status = PresentStatus.Error(msg)
                        },
                    )
                } else {
                    executeShare()
                }
            },
            enabled = selectedCred != null && !inFlight,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (inFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    when (status) {
                        PresentStatus.FetchingRequest -> "Reading request…"
                        PresentStatus.BuildingVp -> "Preparing proof…"
                        PresentStatus.Verifying -> "Sharing…"
                        else -> "Sharing…"
                    },
                )
            } else {
                if (holder.preferences.biometricLockEnabled) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                val ctaText = when {
                    holder.preferences.biometricLockEnabled && totalCount > 0 && sharedCount > 0 ->
                        "Confirm & share ($sharedCount ${if (sharedCount == 1) "detail" else "details"})"
                    holder.preferences.biometricLockEnabled && totalCount > 0 ->
                        "Confirm & share (proof only)"
                    holder.preferences.biometricLockEnabled ->
                        "Confirm & share"
                    totalCount > 0 && sharedCount > 0 ->
                        "Share ($sharedCount ${if (sharedCount == 1) "detail" else "details"})"
                    totalCount > 0 ->
                        "Share proof only"
                    else ->
                        "Share with requester"
                }
                Text(ctaText)
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showManualConfirmDialog && selectedCred != null) {
        AlertDialog(
            onDismissRequest = { showManualConfirmDialog = false },
            title = { Text("Confirm sharing") },
            text = {
                Text(
                    if (totalCount > 0) {
                        "Share \"${selectedCred.previewTitle}\" ($sharedCount of $totalCount details) with ${requesterHost ?: "this requester"}?"
                    } else {
                        "Share \"${selectedCred.previewTitle}\" with ${requesterHost ?: "this requester"}?"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    showManualConfirmDialog = false
                    executeShare()
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showManualConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChooseCredentialCard(
    creds: List<Storage.StoredCredential>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "CREDENTIAL TO SHARE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            creds.forEachIndexed { idx, c ->
                val selected = selectedId == c.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(c.id) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(c.previewTitle, fontWeight = FontWeight.SemiBold)
                        c.previewSubtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (idx < creds.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SelectiveDisclosureCard(
    details: List<CredentialDisplay.ShareableClaimDetail>,
    disclose: SnapshotStateMap<String, Boolean>,
    sharedCount: Int,
) {
    val totalCount = details.size
    val hiddenCount = totalCount - sharedCount

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "CHOOSE WHAT TO SHARE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Everything switched off stays completely hidden from the requester.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Sharing $sharedCount of $totalCount · $hiddenCount hidden",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = sharedCount == 0,
                        onClick = { details.forEach { disclose[it.name] = false } },
                        label = { Text("Minimal") },
                    )
                    FilterChip(
                        selected = sharedCount == totalCount,
                        onClick = { details.forEach { disclose[it.name] = true } },
                        label = { Text("All") },
                    )
                }
            }

            details.forEachIndexed { idx, item ->
                val checked = disclose[item.name] ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .toggleable(
                            value = checked,
                            onValueChange = { disclose[item.name] = it },
                            role = Role.Switch,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            item.value ?: "Included in credential",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (checked) "Will be shared" else "Stays hidden",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = checked, onCheckedChange = null)
                }
                if (idx < details.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ShareOutcomeView(
    outcome: PresentStatus.Done,
    onDone: () -> Unit,
) {
    var showTechChecks by remember { mutableStateOf(false) }
    val ok = outcome.response.valid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Text(
                    if (ok) "Shared & verified" else "Verification failed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (ok) {
                        "${outcome.credentialTitle} was verified by ${outcome.verifierName}."
                    } else {
                        "${outcome.verifierName} could not verify this presentation."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (outcome.totalCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            "Shared ${outcome.sharedCount} of ${outcome.totalCount} details · ${outcome.totalCount - outcome.sharedCount} hidden",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Done")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                TextButton(
                    onClick = { showTechChecks = !showTechChecks },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Technical verification checks (${outcome.response.checks.size})")
                        Icon(
                            if (showTechChecks) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (showTechChecks) {
                    outcome.response.checks.forEach { c ->
                        val bg = if (c.passed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                        val fg = if (c.passed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Text(if (c.passed) "✓" else "✗", color = fg, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.step, color = fg, style = MaterialTheme.typography.bodySmall)
                                    c.detail?.let {
                                        Text(it, color = fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
