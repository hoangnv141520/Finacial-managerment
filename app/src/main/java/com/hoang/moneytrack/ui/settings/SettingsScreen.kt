package com.hoang.moneytrack.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.prefs.ThemeMode
import com.hoang.moneytrack.ui.lock.PinDots
import com.hoang.moneytrack.ui.lock.PinPad
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: MoneyTrackApp, onManageWallets: () -> Unit, onManageCategories: () -> Unit) {
    val scope = rememberCoroutineScope()
    val theme by app.settings.theme.collectAsState(ThemeMode.SYSTEM)
    val secure by app.settings.secureScreen.collectAsState(true)
    val hideBalance by app.settings.hideBalance.collectAsState(false)
    val biometric by app.settings.biometricEnabled.collectAsState(false)
    var pinEnabled by remember { mutableStateOf(app.keys.hasPin()) }
    var settingPin by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.titleLarge)

        // language
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                FilterChip(
                    current.startsWith("vi"),
                    { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi")) },
                    { Text("Tiếng Việt") },
                )
                FilterChip(
                    current.startsWith("en"),
                    { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
                    { Text("English") },
                )
            }
        }

        // theme
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(theme == ThemeMode.LIGHT, { scope.launch { app.settings.setTheme(ThemeMode.LIGHT) } }, { Text(stringResource(R.string.set_theme_light)) })
                FilterChip(theme == ThemeMode.DARK, { scope.launch { app.settings.setTheme(ThemeMode.DARK) } }, { Text(stringResource(R.string.set_theme_dark)) })
                FilterChip(theme == ThemeMode.SYSTEM, { scope.launch { app.settings.setTheme(ThemeMode.SYSTEM) } }, { Text(stringResource(R.string.set_theme_system)) })
            }
        }

        // security
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.set_security), style = MaterialTheme.typography.titleSmall)
            ToggleRow(stringResource(R.string.set_pin), pinEnabled) { enable ->
                if (enable) settingPin = true else {
                    app.keys.clearPin()
                    pinEnabled = false
                }
            }
            if (pinEnabled) {
                Text(
                    stringResource(R.string.set_pin_change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp).clickable { settingPin = true },
                )
            }
            ToggleRow(stringResource(R.string.set_biometric), biometric) { scope.launch { app.settings.setBiometric(it) } }
            ToggleRow(stringResource(R.string.set_block_screenshot), secure) { scope.launch { app.settings.setSecureScreen(it) } }
            ToggleRow(stringResource(R.string.set_hide_balance), hideBalance) { scope.launch { app.settings.setHideBalance(it) } }
        }

        // data management
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Dữ liệu", style = MaterialTheme.typography.titleSmall)
            SettingRow(stringResource(R.string.manage_wallets), onManageWallets)
            SettingRow(stringResource(R.string.manage_categories), onManageCategories)
        }

        AccountSection(app)
    }

    if (settingPin) ModalBottomSheet(onDismissRequest = { settingPin = false }) {
        SetPinFlow(app) { ok ->
            if (ok) pinEnabled = true
            settingPin = false
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SetPinFlow(app: MoneyTrackApp, onDone: (Boolean) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (first == null) R.string.lock_set_pin else R.string.lock_confirm_pin),
            style = MaterialTheme.typography.titleMedium,
            color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        PinDots(pin.length)
        PinPad(
            enabled = true,
            onDigit = { d ->
                if (pin.length < 6) {
                    pin += d
                    mismatch = false
                    if (pin.length == 6) {
                        if (first == null) {
                            first = pin
                            pin = ""
                        } else if (first == pin) {
                            app.keys.setPin(pin)
                            onDone(true)
                        } else {
                            mismatch = true
                            first = null
                            pin = ""
                        }
                    }
                }
            },
            onDelete = { pin = pin.dropLast(1) },
        )
        Spacer(Modifier.height(16.dp))
    }
}


@Composable
private fun AccountSection(app: MoneyTrackApp) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(app.keys.isLoggedIn) }
    var email by remember { mutableStateOf(app.keys.accountEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(app.keys.serverUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val okMsg = stringResource(R.string.acc_synced)

    fun run(block: suspend () -> Result<Unit>) {
        busy = true
        scope.launch {
            status = block().fold({ okMsg }, { it.message?.take(120) ?: "?" })
            loggedIn = app.keys.isLoggedIn
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.acc_section), style = MaterialTheme.typography.titleSmall)
        if (!loggedIn) {
            OutlinedTextField(server, { server = it; app.keys.setServerUrl(it) },
                label = { Text(stringResource(R.string.acc_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it },
                label = { Text(stringResource(R.string.acc_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it },
                label = { Text(stringResource(R.string.acc_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { run { app.sync.auth(register = false, email = email.trim(), password = password) } },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.acc_login)) }
                OutlinedButton(
                    onClick = { run { app.sync.auth(register = true, email = email.trim(), password = password) } },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.acc_register)) }
            }
        } else {
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { run { app.sync.syncNow() } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.acc_sync_now)) }
                OutlinedButton(
                    onClick = { app.keys.clearAccount(); loggedIn = false; status = null },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.acc_logout)) }
            }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it == okMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}
