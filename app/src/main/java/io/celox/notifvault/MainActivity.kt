package io.celox.notifvault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.ui.ConversationScreen
import io.celox.notifvault.ui.FlaggedScreen
import io.celox.notifvault.ui.HomeScreen
import io.celox.notifvault.ui.OnboardingScreen
import io.celox.notifvault.ui.SettingsScreen
import io.celox.notifvault.ui.VaultViewModel
import io.celox.notifvault.ui.theme.NotifVaultTheme
import io.celox.notifvault.util.PermissionUtils

/**
 * Uses FragmentActivity so BiometricPrompt works.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotifVaultTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: VaultViewModel = viewModel()
                    val biometricOn by vm.settings.biometricLock.collectAsStateWithLifecycle(initialValue = false)
                    // rememberSaveable so a config change (rotation) doesn't re-lock the app.
                    var unlocked by rememberSaveable { mutableStateOf(false) }

                    // Re-lock when the app leaves the foreground — a lock that only ever asks
                    // once (and then stays open until the process dies) protects nothing.
                    val lockLifecycle = LocalLifecycleOwner.current
                    DisposableEffect(lockLifecycle, biometricOn) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (biometricOn && event == Lifecycle.Event.ON_STOP) unlocked = false
                        }
                        lockLifecycle.lifecycle.addObserver(observer)
                        onDispose { lockLifecycle.lifecycle.removeObserver(observer) }
                    }

                    if (biometricOn && !unlocked) {
                        LockScreen(onAuthenticate = { promptBiometric { unlocked = true } })
                    } else {
                        AppNav(vm)
                    }
                }
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit) {
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) { onSuccess(); return }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Kleene Petze entsperren")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }
}

@Composable
private fun LockScreen(onAuthenticate: () -> Unit) {
    // Prompt once on entry; the button lets the user retry after a cancel/failure
    // instead of being stuck on this screen forever.
    LaunchedEffect(Unit) { onAuthenticate() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔒 Kleene Petze ist gesperrt", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onAuthenticate) { Text("Entsperren") }
        }
    }
}

@Composable
private fun AppNav(vm: VaultViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission/battery state is granted in system Settings, i.e. outside the app.
    // Re-read it on every ON_RESUME so returning from Settings reflects the new state
    // immediately — without this the user had to fully kill and relaunch the app.
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasNotificationAccess(context)) }
    var batteryOptimized by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = PermissionUtils.hasNotificationAccess(context)
                batteryOptimized = PermissionUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start destination is decided once, on first composition.
    val start = remember { if (PermissionUtils.hasNotificationAccess(context)) "home" else "onboarding" }

    NavHost(navController = nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(
                hasAccess = hasAccess,
                batteryOptimized = batteryOptimized,
                onGrantAccess = { PermissionUtils.openNotificationAccessSettings(context) },
                onBattery = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                onContinue = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
            )
        }
        composable("home") {
            HomeScreen(
                vm = vm,
                hasAccess = hasAccess,
                onOpenConversation = { key, pkg ->
                    nav.navigate("chat/${enc(key)}/${enc(pkg)}")
                },
                onOpenFlagged = { nav.navigate("flagged") },
                onOpenSettings = { nav.navigate("settings") },
                onGrantAccess = { PermissionUtils.openNotificationAccessSettings(context) }
            )
        }
        composable("chat/{key}/{pkg}") { entry ->
            ConversationScreen(
                vm = vm,
                conversationKey = entry.arguments?.getString("key").orEmpty(),
                pkg = entry.arguments?.getString("pkg").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
        composable("flagged") {
            FlaggedScreen(
                vm = vm,
                onOpenConversation = { key, pkg ->
                    nav.navigate("chat/${enc(key)}/${enc(pkg)}")
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

// Navigation Uri-decodes route arguments itself, so encode exactly once with Uri.encode
// (percent-encoding; no '+' form encoding) and read the framework-decoded value as-is.
// A second URLDecoder pass corrupted keys containing '+' and crashed on a bare '%'.
private fun enc(s: String) = android.net.Uri.encode(s)
