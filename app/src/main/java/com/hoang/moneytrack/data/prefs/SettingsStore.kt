package com.hoang.moneytrack.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val hideBalanceKey = booleanPreferencesKey("hide_balance")
    private val secureScreenKey = booleanPreferencesKey("secure_screen")
    private val biometricKey = booleanPreferencesKey("biometric")

    val theme: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.valueOf(it[themeKey] ?: ThemeMode.SYSTEM.name)
    }
    val hideBalance: Flow<Boolean> = context.dataStore.data.map { it[hideBalanceKey] ?: false }
    val secureScreen: Flow<Boolean> = context.dataStore.data.map { it[secureScreenKey] ?: true }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[biometricKey] ?: false }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setHideBalance(v: Boolean) = context.dataStore.edit { it[hideBalanceKey] = v }
    suspend fun setSecureScreen(v: Boolean) = context.dataStore.edit { it[secureScreenKey] = v }
    suspend fun setBiometric(v: Boolean) = context.dataStore.edit { it[biometricKey] = v }
}
