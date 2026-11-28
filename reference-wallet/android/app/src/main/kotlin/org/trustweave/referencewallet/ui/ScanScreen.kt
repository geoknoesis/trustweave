package org.trustweave.referencewallet.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.trustweave.referencewallet.lib.NdefParser
import org.trustweave.referencewallet.lib.PayloadRouter

/**
 * 1-Tap Live Unified Scan / Tap screen for native Android.
 *
 * Opens the live CameraX QR scanner immediately with Flashlight toggle, Android NFC
 * Reader Mode sheet (with Cancel & Turn-on-NFC shortcut), and manual link/code entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onOfferScanned: (PayloadRouter.IncomingOffer) -> Unit,
    onPresentationScanned: (PayloadRouter.PresentationRequestPayload) -> Unit,
    onOpenSettings: () -> Unit,
    onDemoReceive: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var decodeSuccess by remember { mutableStateOf(false) }
    var manualSheetOpen by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var nfcListening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var publicVerifyPromptUrl by remember { mutableStateOf<String?>(null) }
    var lastScanAt by remember { mutableLongStateOf(0L) }

    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }

    fun routeRawPayload(raw: String, flashCamera: Boolean = false): Boolean {
        val routed = PayloadRouter.classify(raw)
        return when (routed) {
            is PayloadRouter.RoutedPayload.Offer -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                notice = null
                if (flashCamera) {
                    decodeSuccess = true
                    scope.launch {
                        delay(200)
                        decodeSuccess = false
                        onOfferScanned(routed.offer)
                    }
                } else {
                    onOfferScanned(routed.offer)
                }
                true
            }
            is PayloadRouter.RoutedPayload.Presentation -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                notice = null
                if (flashCamera) {
                    decodeSuccess = true
                    scope.launch {
                        delay(200)
                        decodeSuccess = false
                        onPresentationScanned(routed.request)
                    }
                } else {
                    onPresentationScanned(routed.request)
                }
                true
            }
            is PayloadRouter.RoutedPayload.PublicVerify -> {
                publicVerifyPromptUrl = routed.url
                true
            }
            is PayloadRouter.RoutedPayload.HolderDid -> {
                notice = "This is a Wallet ID QR. Scan a credential offer or a requester's verification QR instead."
                false
            }
            is PayloadRouter.RoutedPayload.Unknown -> {
                notice = routed.reason
                false
            }
        }
    }

    // Foreground Android NFC Reader Mode lifecycle.
    DisposableEffect(nfcListening, activity, nfcAdapter) {
        if (nfcListening && activity != null && nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
            nfcAdapter.enableReaderMode(
                activity,
                { tag ->
                    val ndef = Ndef.get(tag)
                    var decoded: String? = null
                    if (ndef != null) {
                        try {
                            ndef.connect()
                            val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
                            for (record in msg?.records.orEmpty()) {
                                if (record.tnf == NdefRecord.TNF_WELL_KNOWN) {
                                    decoded = when {
                                        record.type.contentEquals(NdefRecord.RTD_URI) ->
                                            NdefParser.decodeUriPayload(record.payload)
                                        record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                                            NdefParser.decodeTextPayload(record.payload)
                                        else -> null
                                    }
                                    if (!decoded.isNullOrBlank()) break
                                }
                            }
                        } catch (_: Exception) {
                            // handled below
                        } finally {
                            try {
                                ndef.close()
                            } catch (_: Exception) {
                                // ignore
                            }
                        }
                    }
                    activity.runOnUiThread {
                        nfcListening = false
                        if (!decoded.isNullOrBlank()) {
                            routeRawPayload(decoded)
                        } else {
                            notice = "This NFC tag does not contain a credential or verification link."
                        }
                    }
                },
                flags,
                null,
            )
        }
        onDispose {
            if (activity != null && nfcAdapter != null) {
                try {
                    nfcAdapter.disableReaderMode(activity)
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Scan or tap",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Row {
                if (hasCameraPermission) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(
                            if (torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight",
                            tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White,
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings and security", tint = Color.White)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCameraPermission && !manualSheetOpen) {
                QrCameraViewfinder(
                    torchEnabled = torchEnabled,
                    hintText = "Point at an issuer offer or verification QR",
                    decodeSuccess = decodeSuccess,
                    onQrDecoded = { text ->
                        val now = System.currentTimeMillis()
                        if (now - lastScanAt > 1500L) {
                            lastScanAt = now
                            routeRawPayload(text, flashCamera = true)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Text("Allow camera to scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Point your camera at any issuer offer or verification QR code — the wallet routes it automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Allow camera")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            notice?.let { msg ->
                Surface(
                    color = Color(0xFF450A0A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5))
                        Text(msg, color = Color(0xFFFEE2E2), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (nfcAdapter != null) {
                if (nfcAdapter.isEnabled) {
                    Button(
                        onClick = {
                            notice = null
                            nfcListening = true
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Tap an NFC reader")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Nfc, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Turn on NFC in settings", color = Color.White)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        notice = null
                        manualSheetOpen = true
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Enter link", color = Color.White)
                }
                TextButton(
                    onClick = onDemoReceive,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Demo issuer", color = Color(0xFF93C5FD))
                }
            }
        }
    }

    if (nfcListening) {
        ModalBottomSheet(
            onDismissRequest = { nfcListening = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        Icons.Outlined.Nfc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                Text("Ready to scan NFC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Hold the back of your phone near the NFC reader or tag until you feel a vibration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = { nfcListening = false },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    if (manualSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { manualSheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Enter link or code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Paste a credential offer link, OpenID4VCI URI, or verification request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("https://… or openid-credential-offer://…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (routeRawPayload(manualInput)) {
                            manualInput = ""
                            manualSheetOpen = false
                        }
                    },
                    enabled = manualInput.isNotBlank(),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Continue")
                }
            }
        }
    }

    publicVerifyPromptUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { publicVerifyPromptUrl = null },
            title = { Text("Web verification link") },
            text = { Text("This link is meant to be viewed in a web browser. Open it now?") },
            confirmButton = {
                Button(onClick = {
                    publicVerifyPromptUrl = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Open in browser") }
            },
            dismissButton = {
                TextButton(onClick = { publicVerifyPromptUrl = null }) { Text("Cancel") }
            },
        )
    }
}
