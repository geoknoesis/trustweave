package org.trustweave.referencewallet.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.trustweave.referencewallet.lib.BiometricHelper
import org.trustweave.referencewallet.lib.NdefParser
import org.trustweave.referencewallet.lib.PayloadRouter

class MainActivity : ComponentActivity() {

    private var stateHolderRef: WalletStateHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialPayload = extractPayloadFromIntent(intent)
        setContent {
            val holder = remember {
                WalletStateHolder(this).also {
                    it.refresh()
                    if (!initialPayload.isNullOrBlank()) it.incomingDeepLink = initialPayload
                    stateHolderRef = it
                }
            }
            TrustWeaveTheme(useDynamicColor = holder.preferences.useDynamicColor) {
                WalletApp(holder = holder)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val payload = extractPayloadFromIntent(intent)
        if (!payload.isNullOrBlank()) {
            stateHolderRef?.incomingDeepLink = payload
        }
    }

    private fun extractPayloadFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.mapNotNull { it as? NdefMessage }?.toTypedArray()
            }
            for (msg in messages.orEmpty()) {
                for (record in msg.records) {
                    if (record.tnf == NdefRecord.TNF_WELL_KNOWN) {
                        val text = when {
                            record.type.contentEquals(NdefRecord.RTD_URI) -> NdefParser.decodeUriPayload(record.payload)
                            record.type.contentEquals(NdefRecord.RTD_TEXT) -> NdefParser.decodeTextPayload(record.payload)
                            else -> null
                        }
                        if (!text.isNullOrBlank()) return text
                    }
                }
            }
        }
        return intent.dataString
    }
}

/** Temporarily enables Android FLAG_SECURE while sensitive credential or backup views are visible. */
@Composable
fun SecureScreenEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (enabled) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletApp(holder: WalletStateHolder) {
    val context = LocalContext.current
    val walletState = holder.state
    if (walletState != null && !holder.preferences.onboardingComplete) {
        OnboardingScreen(
            keySource = walletState.keySource,
            onComplete = { biometricEnabled ->
                holder.updatePreferences {
                    it.copy(onboardingComplete = true, biometricLockEnabled = biometricEnabled)
                }
            },
        )
        return
    }

    val nav = rememberNavController()
    val tabs = listOf(
        Tab("home", "Wallet", Icons.Outlined.AccountBalanceWallet),
        Tab("scan", "Scan", Icons.Outlined.QrCodeScanner),
        Tab("activity", "Activity", Icons.Outlined.History),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isRootTab = currentRoute in setOf("home", "scan", "activity", null)
    val hideTopBar = currentRoute == "scan"
    var publicVerifyUrl by remember { mutableStateOf<String?>(null) }
    var isAppLocked by remember { mutableStateOf(false) }

    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme && !hideTopBar
        }
    }

    // Lock wallet on return from background when device security & lock preference are active.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, holder.preferences.biometricLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                holder.preferences.onboardingComplete &&
                holder.preferences.biometricLockEnabled &&
                BiometricHelper.isDeviceSecurityAvailable(context)
            ) {
                isAppLocked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Actually parse & route incoming OS deep links and NFC NDEF intents (never drops payloads!).
    LaunchedEffect(holder.incomingDeepLink) {
        val link = holder.incomingDeepLink ?: return@LaunchedEffect
        holder.incomingDeepLink = null
        when (val routed = PayloadRouter.classify(link)) {
            is PayloadRouter.RoutedPayload.Offer -> {
                holder.pendingOffer = routed.offer
                nav.navigate("receive") { launchSingleTop = true }
            }
            is PayloadRouter.RoutedPayload.Presentation -> {
                holder.pendingPresentation = routed.request
                nav.navigate("present") { launchSingleTop = true }
            }
            is PayloadRouter.RoutedPayload.PublicVerify -> {
                publicVerifyUrl = routed.url
            }
            else -> {
                navigateToTab(nav, "scan")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = if (hideTopBar) Color(0xFF0B1120) else MaterialTheme.colorScheme.background,
            topBar = {
            if (!hideTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                "settings" -> "Settings & security"
                                "activity" -> "Activity"
                                "receive" -> "Add credential"
                                "present" -> "Review & share"
                                else -> "TrustWeave"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (!isRootTab) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (isRootTab) {
                            IconButton(onClick = { nav.navigate("settings") }) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "Settings and security",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        bottomBar = {
            if (isRootTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route ||
                            backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) navigateToTab(nav, tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        AppNavHost(nav = nav, holder = holder, contentPadding = inner)
    }

        if (isAppLocked) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(84.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("TrustWeave", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Wallet locked for your privacy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            BiometricHelper.authenticate(
                                context = context,
                                title = "Unlock TrustWeave",
                                subtitle = "Authenticate to view your credentials",
                                onSuccess = { isAppLocked = false },
                                onNeedsManualConfirm = { isAppLocked = false },
                                onFailure = { /* stay locked */ },
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Unlock wallet")
                    }
                }
            }
        }
    }

    publicVerifyUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { publicVerifyUrl = null },
            title = { Text("Web verification link") },
            text = { Text("This link is meant to be viewed in a web browser. Open it now?") },
            confirmButton = {
                Button(onClick = {
                    publicVerifyUrl = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Open in browser") }
            },
            dismissButton = {
                TextButton(onClick = { publicVerifyUrl = null }) { Text("Cancel") }
            },
        )
    }
}

private fun navigateToTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    holder: WalletStateHolder,
    contentPadding: PaddingValues,
) {
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.padding(contentPadding),
    ) {
        composable("home") {
            HomeScreen(
                holder = holder,
                onScan = { navigateToTab(nav, "scan") },
                onPresent = { credId ->
                    holder.preselectedCredentialId = credId
                    nav.navigate("present") { launchSingleTop = true }
                },
            )
        }
        composable("scan") {
            ScanScreen(
                onOfferScanned = { offer ->
                    holder.pendingOffer = offer
                    nav.navigate("receive") { launchSingleTop = true }
                },
                onPresentationScanned = { request ->
                    holder.pendingPresentation = request
                    nav.navigate("present") { launchSingleTop = true }
                },
                onOpenSettings = { nav.navigate("settings") },
                onDemoReceive = { nav.navigate("receive") { launchSingleTop = true } },
            )
        }
        composable("receive") {
            ReceiveScreen(
                holder = holder,
                onViewWallet = {
                    nav.popBackStack("home", inclusive = false)
                },
                onShareCredential = { credId ->
                    holder.preselectedCredentialId = credId
                    nav.navigate("present") {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("present") {
            PresentScreen(
                holder = holder,
                onScan = {
                    nav.popBackStack("home", inclusive = false)
                    navigateToTab(nav, "scan")
                },
                onDone = {
                    nav.popBackStack("home", inclusive = false)
                },
            )
        }
        composable("activity") {
            ActivityScreen(holder = holder)
        }
        composable("settings") {
            SettingsScreen(
                holder = holder,
                onWalletReset = {
                    nav.popBackStack("home", inclusive = false)
                },
            )
        }
    }
}
