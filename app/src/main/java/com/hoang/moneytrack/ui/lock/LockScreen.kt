package com.hoang.moneytrack.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.ui.theme.Jakarta
import kotlinx.coroutines.delay

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_SECONDS = 30

@Composable
fun LockScreen(app: MoneyTrackApp, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var lockoutUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val biometricEnabled by app.settings.biometricEnabled.collectAsState(initial = false)
    val context = LocalContext.current

    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    fun submit(candidate: String) {
        if (app.keys.checkPin(candidate)) {
            onUnlock()
        } else {
            error = true
            attempts++
            if (attempts >= MAX_ATTEMPTS) {
                lockoutUntil = System.currentTimeMillis() + LOCKOUT_SECONDS * 1000
                attempts = 0
            }
        }
        pin = ""
    }

    LaunchedEffect(biometricEnabled) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        if (!biometricEnabled) return@LaunchedEffect
        val bm = BiometricManager.from(context)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) return@LaunchedEffect
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlock()
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.lock_biometric_title))
                .setNegativeButtonText(context.getString(R.string.cancel))
                .build(),
        )
    }

    val inLockout = now < lockoutUntil
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔒", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    inLockout -> stringResource(R.string.lock_too_many, ((lockoutUntil - now) / 1000).toInt() + 1)
                    error -> stringResource(R.string.lock_wrong)
                    else -> stringResource(R.string.lock_enter_pin)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Jakarta),
                color = if (error || inLockout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(pin.length)
            Spacer(Modifier.height(32.dp))
            PinPad(
                enabled = !inLockout,
                onDigit = {
                    if (pin.length < 6) {
                        pin += it
                        error = false
                        if (pin.length == 6) submit(pin)
                    }
                },
                onDelete = { pin = pin.dropLast(1) },
            )
        }
    }
}

@Composable
fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) { i ->
            Box(
                Modifier.size(14.dp).background(
                    if (i < filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            )
        }
    }
}

@Composable
fun PinPad(enabled: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                row.forEach { key ->
                    TextButton(
                        onClick = { if (key == "⌫") onDelete() else if (key.isNotEmpty()) onDigit(key) },
                        enabled = enabled && key.isNotEmpty(),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Text(key, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
