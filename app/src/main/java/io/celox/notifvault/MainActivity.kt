package io.celox.notifvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import io.celox.notifvault.ui.theme.ScreenTransitions
import io.celox.notifvault.ui.theme.rememberReducedMotion
import io.celox.notifvault.ui.theme.springPressed
import kotlinx.coroutines.flow.map
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
import androidx.compose.runtime.collectAsState
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
import io.celox.notifvault.ui.theme.ThemeMode
import io.celox.notifvault.util.PermissionUtils

/**
 * Uses FragmentActivity so BiometricPrompt works.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: VaultViewModel = viewModel()
            val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by vm.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
            NotifVaultTheme(mode = themeMode, dynamicColor = dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // null until DataStore has answered. Defaulting to `false` rendered the vault
                    // (chat list with previews) for a frame before the lock replaced it.
                    val biometricOn by remember { vm.settings.biometricLock.map<Boolean, Boolean?> { it } }
                        .collectAsStateWithLifecycle(initialValue = null)
                    // In the ViewModel: survives rotation, and switching the lock on from inside
                    // the open app marks the session unlocked *before* the setting lands — so
                    // the owner is not thrown out on the spot (a cold start stays locked).
                    // collectAsState, not …WithLifecycle: the latter pauses while stopped and could
                    // hand the first frame after returning a stale "unlocked".
                    val unlocked by vm.unlocked.collectAsState()

                    // With the lock on, the window is secure: the recents thumbnail is taken
                    // *before* ON_STOP re-locks, so without this the app switcher showed chats.
                    LaunchedEffect(biometricOn) {
                        if (biometricOn == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }

                    // Re-lock when the app leaves the foreground — a lock that only ever asks
                    // once (and then stays open until the process dies) protects nothing.
                    val lockLifecycle = LocalLifecycleOwner.current
                    DisposableEffect(lockLifecycle, biometricOn) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (biometricOn == true && event == Lifecycle.Event.ON_STOP) vm.lock()
                        }
                        lockLifecycle.lifecycle.addObserver(observer)
                        onDispose { lockLifecycle.lifecycle.removeObserver(observer) }
                    }

                    val on = biometricOn ?: return@Surface
                    val locked = on && !unlocked
                    // The vault stays composed underneath the lock. Removing it (as before) threw
                    // away the navigation and every registered ActivityResult launcher the moment
                    // the file picker sent the app to the background — so with the lock on, an
                    // export's result was dropped and only an empty file remained.
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxSize().then(
                                // Hidden from TalkBack while locked: an overlay only covers pixels.
                                if (locked) Modifier.clearAndSetSemantics { } else Modifier
                            )
                        ) { AppNav(vm) }
                        AnimatedVisibility(
                            visible = locked,
                            enter = EnterTransition.None, // never fade in over readable content
                            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
                        ) {
                            LockScreen(onAuthenticate = { onError ->
                                promptBiometric(onSuccess = { vm.unlock() }, onError = onError)
                            })
                        }
                    }
                }
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit, onError: (String?) -> Unit) {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (LockPolicy.skipAuthentication(canAuth)) { onSuccess(); return }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onError(if (cancelled) null else errString.toString())
                }
            })
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.lock_prompt_title))
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
        }.onFailure { onError(it.message) }
    }
}

@Composable
private fun LockScreen(onAuthenticate: (onError: (String?) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? android.app.Activity
    // Back must not reach the NavHost underneath while locked.
    BackHandler { activity?.moveTaskToBack(true) }

    // Prompt once per lock, and only once the activity is resumed — a prompt started while the
    // app is still stopped is dropped by the system, and prompting on every resume would loop
    // (the system prompt itself pauses the activity on some devices).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var prompted by remember { mutableStateOf(false) }
    LaunchedEffect(state.isAtLeast(Lifecycle.State.RESUMED)) {
        if (state.isAtLeast(Lifecycle.State.RESUMED) && !prompted) {
            prompted = true
            onAuthenticate { error = it }
        }
    }

    // Opaque and input-consuming: the vault is composed right underneath.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Rounded.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(stringResource(R.string.lock_locked_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                val interaction = remember { MutableInteractionSource() }
                Button(
                    onClick = { error = null; onAuthenticate { error = it } },
                    interactionSource = interaction,
                    modifier = Modifier.springPressed(interaction)
                ) { Text(stringResource(R.string.lock_unlock)) }
            }
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

    val transitions = ScreenTransitions(MaterialTheme.motionScheme, rememberReducedMotion())
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { transitions.enter() },
        exitTransition = { transitions.exit() },
        popEnterTransition = { transitions.popEnter() },
        popExitTransition = { transitions.popExit() }
    ) {
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
