package com.hoang.moneytrack.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/** Keys + PIN + sync account live in EncryptedSharedPreferences (master key in Android Keystore). */
class DbKeyManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // --- DB encryption key ---

    fun getOrCreateDbKey(): ByteArray {
        prefs.getString(KEY_DB, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    // --- PIN (PBKDF2 hash, never plaintext) ---

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(Pbkdf2.hash(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = Base64.decode(prefs.getString(KEY_PIN_SALT, null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_PIN_HASH, null) ?: return false, Base64.NO_WRAP)
        return Pbkdf2.hash(pin, salt).contentEquals(expected)
    }

    // --- Sync account storage ---

    /** Base URL of the sync server, e.g. "https://api.example.com" */
    val serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trimEnd('/')).apply()
    }

    /** JWT access token returned by /auth/login or /auth/register. Null if not logged in. */
    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    /** 32-byte AES key derived from the account password via PBKDF2. Null if not logged in. */
    val syncKey: ByteArray?
        get() = prefs.getString(KEY_SYNC_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    /** Unix-ms timestamp of the last successful pull from the server. 0 = never synced. */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value).apply() }

    /**
     * Persist account credentials returned by /auth/login or /auth/register.
     * [syncKey] is PBKDF2(password, email) — never transmitted, derived locally.
     */
    fun saveAccount(email: String, access: String, syncKey: ByteArray) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_SYNC_KEY, Base64.encodeToString(syncKey, Base64.NO_WRAP))
            .apply()
    }

    fun clearAccount() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_SYNC_KEY)
            .putLong(KEY_LAST_SYNC, 0L)
            .apply()
    }

    val accountEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)

    val isLoggedIn: Boolean
        get() = accessToken != null

    private companion object {
        const val KEY_DB = "db_key"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_SYNC_KEY = "sync_key"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_EMAIL = "account_email"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000" // emulator loopback; set real https URL in Settings
    }
}

/**
 * PBKDF2-HMAC-SHA256 helper.
 * internal so SyncManager (same module) can import it directly.
 */
internal object Pbkdf2 {
    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 210_000, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }
}
