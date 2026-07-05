package com.hoang.moneytrack

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.hoang.moneytrack.data.prefs.ThemeMode
import com.hoang.moneytrack.ui.lock.LockScreen
import com.hoang.moneytrack.ui.nav.AppNav
import com.hoang.moneytrack.ui.theme.LocalHideBalance
import com.hoang.moneytrack.ui.theme.MoneyTrackTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MoneyTrackApp

        setContent {
            val theme by app.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
            val hideBalance by app.settings.hideBalance.collectAsState(initial = false)
            val secure by app.settings.secureScreen.collectAsState(initial = true)
            val dark = when (theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(secure) {
                if (secure) window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                ) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            MoneyTrackTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalHideBalance provides hideBalance) {
                    LockGate(app) { AppNav(app) }
                }
            }
        }
    }
}

/** Blocks the app behind PIN/biometric when a PIN is set. Re-locks after 60s in background. */
@Composable
private fun LockGate(app: MoneyTrackApp, content: @Composable () -> Unit) {
    var locked by remember { mutableStateOf(app.keys.hasPin()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var backgroundedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> scope.launch {
                    if (app.keys.hasPin() && backgroundedAt > 0 &&
                        System.currentTimeMillis() - backgroundedAt > 60_000
                    ) locked = true
                }
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    if (locked) LockScreen(app, onUnlock = { locked = false }) else content()
}
