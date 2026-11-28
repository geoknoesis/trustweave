package org.trustweave.referencewallet.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import org.trustweave.referencewallet.lib.CredentialDisplay
import org.trustweave.referencewallet.lib.Storage

private val PASS_ACCENTS = listOf(
    Color(0xFF1E3A8A),
    Color(0xFF0F766E),
    Color(0xFF6D28D9),
    Color(0xFF0369A1),
    Color(0xFFB45309),
)

private fun passAccentColor(title: String): Color {
    var hash = 0
    for (i in title.indices) hash = (hash + title[i].code * (i + 1)) % PASS_ACCENTS.size
    return PASS_ACCENTS[hash.coerceIn(PASS_ACCENTS.indices)]
}

@Composable
private fun rememberPortraitBitmap(cred: Storage.StoredCredential): ImageBitmap? =
    remember(cred.id, cred.credential) {
        val bytes = CredentialDisplay.extractPortraitBytes(cred) ?: return@remember null
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    holder: WalletStateHolder,
    onScan: () -> Unit,
    onPresent: (String?) -> Unit,
) {
    val walletState = holder.state ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Opening your wallet…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var detailCred by remember { mutableStateOf<Storage.StoredCredential?>(null) }
    var confirmDelete by remember { mutableStateOf<Storage.StoredCredential?>(null) }
    val nowSeconds = remember(walletState.credentials) { Clock.System.now().epochSeconds }

    val credentials = walletState.credentials
    val expiredCount = remember(credentials, nowSeconds) {
        credentials.count { CredentialDisplay.statusBadge(it, nowSeconds).label == "Expired" }
    }
    val expiringSoonCount = remember(credentials, nowSeconds) {
        credentials.count { CredentialDisplay.statusBadge(it, nowSeconds).label == "Expiring soon" }
    }
    val filtered = remember(credentials, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            credentials
        } else {
            credentials.filter { c ->
                c.previewTitle.lowercase().contains(q) ||
                    (c.previewSubtitle?.lowercase()?.contains(q) == true) ||
                    c.type.any { it.lowercase().contains(q) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "My credentials",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (credentials.isEmpty()) {
                    "Private IDs, diplomas, and passes on this device."
                } else {
                    "${credentials.size} credential${if (credentials.size == 1) "" else "s"} ready to present."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expiredCount > 0 || expiringSoonCount > 0) {
                val containerColor = if (expiredCount > 0) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                }
                val contentColor = if (expiredCount > 0) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
                Surface(
                    color = containerColor,
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
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = contentColor)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (expiredCount > 0) {
                                    "$expiredCount credential${if (expiredCount == 1) "" else "s"} expired"
                                } else {
                                    "$expiringSoonCount credential${if (expiringSoonCount == 1) "" else "s"} expiring soon"
                                },
                                color = contentColor,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (expiredCount > 0) {
                                    "Expired credentials may be declined by verifiers. Request a renewal from the issuer."
                                } else {
                                    "Check expiration dates below so you can renew before they lapse."
                                },
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (credentials.size >= 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search credentials…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (credentials.isEmpty()) {
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
                            modifier = Modifier.size(68.dp),
                        ) {
                            Icon(
                                Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                        Text(
                            "Your wallet is ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Keep your verified IDs, diplomas, and passes in one private place. Scan a QR code or tap an NFC reader to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onScan,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Scan QR or tap NFC")
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No matching credentials", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try another search term.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                filtered.forEach { cred ->
                    CredentialPassCard(
                        cred = cred,
                        nowEpochSeconds = nowSeconds,
                        onClick = { detailCred = cred },
                    )
                }
            }

            Spacer(Modifier.height(84.dp))
        }

        if (credentials.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan / Tap") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    detailCred?.let { cred ->
        CredentialDetailSheet(
            cred = cred,
            nowEpochSeconds = nowSeconds,
            onDismiss = { detailCred = null },
            onShare = {
                detailCred = null
                onPresent(cred.id)
            },
            onDelete = { confirmDelete = cred },
        )
    }

    confirmDelete?.let { cred ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove \"${cred.previewTitle}\"?") },
            text = {
                Text("This removes the credential from this device. You will need the issuer to reissue it if you want it back.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        holder.deleteCredential(cred.id)
                        if (detailCred?.id == cred.id) detailCred = null
                        confirmDelete = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun credentialDomainIcon(types: List<String>, title: String): ImageVector {
    val haystack = "${types.joinToString(" ")} $title".lowercase()
    return when {
        Regex("degree|diploma|university|student|education|bachelor|master").containsMatchIn(haystack) ->
            Icons.Outlined.School
        Regex("faa|drone|airspace|pilot|aviation|uas|flight").containsMatchIn(haystack) ->
            Icons.Outlined.Flight
        Regex("identity|passport|driver|license|licence|pid|cac|employee|member").containsMatchIn(haystack) ->
            Icons.Outlined.Badge
        Regex("clearance|security|auth|access|permit|gate").containsMatchIn(haystack) ->
            Icons.Outlined.VerifiedUser
        Regex("cert|award|badge|accomplishment").containsMatchIn(haystack) ->
            Icons.Outlined.WorkspacePremium
        else -> Icons.Outlined.CreditCard
    }
}

@Composable
private fun badgeContainerAndContent(tone: CredentialDisplay.BadgeTone): Pair<Color, Color> =
    when (tone) {
        CredentialDisplay.BadgeTone.SUCCESS ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        CredentialDisplay.BadgeTone.INFO ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CredentialDisplay.BadgeTone.WARNING ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CredentialDisplay.BadgeTone.DANGER ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CredentialDisplay.BadgeTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Material 3 Wallet Pass Card with tinted institution header band, portrait support, and status pill. */
@Composable
private fun CredentialPassCard(
    cred: Storage.StoredCredential,
    nowEpochSeconds: Long,
    onClick: () -> Unit,
) {
    val badge = remember(cred, nowEpochSeconds) { CredentialDisplay.statusBadge(cred, nowEpochSeconds) }
    val (pillBg, pillFg) = badgeContainerAndContent(badge.tone)
    val issuerLabel = remember(cred) { CredentialDisplay.formatIssuerLabel(cred.issuerDid, cred.issuerName) }
    val accent = remember(cred.previewTitle) { passAccentColor(cred.previewTitle) }
    val portrait = rememberPortraitBitmap(cred)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        credentialDomainIcon(cred.type, cred.previewTitle),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        issuerLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = pillBg, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        badge.label,
                        color = pillFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (portrait != null) {
                    Image(
                        bitmap = portrait,
                        contentDescription = "Cardholder portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 46.dp, height = 58.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        cred.previewTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    cred.previewSubtitle?.let { sub ->
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Added ${CredentialDisplay.formatRelativeTime(cred.receivedAt, nowEpochSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialDetailSheet(
    cred: Storage.StoredCredential,
    nowEpochSeconds: Long,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    SecureScreenEffect(enabled = true)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val claims = remember(cred) { CredentialDisplay.extractClaimsForDisplay(cred) }
    val badge = remember(cred, nowEpochSeconds) { CredentialDisplay.statusBadge(cred, nowEpochSeconds) }
    val (pillBg, pillFg) = badgeContainerAndContent(badge.tone)
    val issuerLabel = remember(cred) { CredentialDisplay.formatIssuerLabel(cred.issuerDid, cred.issuerName) }
    val portrait = rememberPortraitBitmap(cred)
    var showTech by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (portrait != null) {
                    Image(
                        bitmap = portrait,
                        contentDescription = "Cardholder portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            credentialDomainIcon(cred.type, cred.previewTitle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cred.previewTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    cred.previewSubtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = pillBg, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            badge.label,
                            color = pillFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Button(
                onClick = onShare,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Share this credential")
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ISSUED BY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(issuerLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }

            if (claims.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        claims.forEach { claim ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        claim.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (claim.shareable) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(999.dp),
                                        ) {
                                            Text(
                                                "Selectable",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                SelectionContainer {
                                    Text(claim.value, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            TextButton(
                onClick = { showTech = !showTech },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Technical details")
                    Icon(
                        if (showTech) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            if (showTech) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Format: ${cred.format}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("Issuer ID: ${cred.issuerDid}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("Holder ID: ${cred.subjectDid}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Remove from wallet", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
