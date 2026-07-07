package com.hoang.moneytrack.data.sync

import androidx.room.withTransaction
import com.hoang.moneytrack.data.db.AppDatabase
import com.hoang.moneytrack.data.db.Budget
import com.hoang.moneytrack.data.db.Category
import com.hoang.moneytrack.data.db.Holding
import com.hoang.moneytrack.data.db.Recurring
import com.hoang.moneytrack.data.db.Reminder
import com.hoang.moneytrack.data.db.SavingGoal
import com.hoang.moneytrack.data.db.Txn
import com.hoang.moneytrack.data.db.Wallet
import com.hoang.moneytrack.data.security.DbKeyManager
import com.hoang.moneytrack.data.security.Pbkdf2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class DbSnapshot(
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val txns: List<Txn>,
    val recurrings: List<Recurring>,
    val budgets: List<Budget>,
    val goals: List<SavingGoal>,
    val reminders: List<Reminder>,
    val holdings: List<Holding>,
)

/** Client-side AES-GCM — server only ever sees ciphertext. Key derived from account password. */
object SyncCrypto {
    fun encrypt(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }

    fun decrypt(key: ByteArray, b64: String): ByteArray {
        val raw = Base64.getDecoder().decode(b64)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return c.doFinal(raw.copyOfRange(12, raw.size))
    }
}

// ponytail: whole-DB snapshot as ONE LWW record — per-row sync when data outgrows a single payload.
// ponytail: no refresh-token flow; access expires in 15min -> user logs in again. Add /auth/refresh when annoying.
class SyncManager(private val db: AppDatabase, private val keys: DbKeyManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dao get() = db.dao()

    suspend fun auth(register: Boolean, email: String, password: String): Result<Unit> = runCatching {
        val path = if (register) "/auth/register" else "/auth/login"
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val (code, text) = http("POST", keys.serverUrl + path, body, null)
        require(code == 200) { "HTTP $code: ${text.take(200)}" }
        val obj = json.parseToJsonElement(text).jsonObject
        keys.saveAccount(
            email = email,
            access = obj["access"]!!.jsonPrimitive.content,
            syncKey = Pbkdf2.hash(password, email.lowercase().encodeToByteArray()),
        )
    }

    /** Pull newer server snapshot (import = replace local), then push ours. LWW at DB granularity. */
    suspend fun syncNow(): Result<Unit> = runCatching {
        val token = checkNotNull(keys.accessToken) { "not logged in" }
        val key = checkNotNull(keys.syncKey) { "not logged in" }

        val (pc, pt) = http("GET", "${keys.serverUrl}/sync/pull?since=${keys.lastSync}", null, token)
        require(pc == 200) { "HTTP $pc: ${pt.take(200)}" }
        val pull = json.parseToJsonElement(pt).jsonObject
        pull["records"]!!.jsonArray.map { it.jsonObject }
            .firstOrNull { it["uuid"]!!.jsonPrimitive.content == FULL }
            ?.let { rec ->
                val plain = SyncCrypto.decrypt(key, rec["payload"]!!.jsonPrimitive.content)
                import(json.decodeFromString(plain.decodeToString()))
            }

        val payload = SyncCrypto.encrypt(key, json.encodeToString(export()).encodeToByteArray())
        val push = buildJsonObject {
            put("records", buildJsonArray {
                add(buildJsonObject {
                    put("table", "db"); put("uuid", FULL)
                    put("payload", payload); put("updatedAt", System.currentTimeMillis())
                })
            })
        }.toString()
        val (sc, st) = http("POST", "${keys.serverUrl}/sync/push", push, token)
        require(sc == 200) { "HTTP $sc: ${st.take(200)}" }
        keys.lastSync = pull["serverTime"]!!.jsonPrimitive.long
    }

    private suspend fun export() = DbSnapshot(
        dao.allWallets(), dao.allCategories(), dao.allTxns(), dao.allRecurrings(),
        dao.allBudgets(), dao.allGoals(), dao.allReminders(), dao.allHoldings(),
    )

    private suspend fun import(s: DbSnapshot) = db.withTransaction {
        dao.clearTxns(); dao.clearRecurrings(); dao.clearBudgets(); dao.clearGoals()
        dao.clearReminders(); dao.clearHoldings(); dao.clearCategories(); dao.clearWallets()
        s.wallets.forEach { dao.insertWallet(it) }
        s.categories.forEach { dao.insertCategory(it) }
        s.txns.forEach { dao.insertTxn(it) }
        s.recurrings.forEach { dao.insertRecurring(it) }
        s.budgets.forEach { dao.insertBudget(it) }
        s.goals.forEach { dao.insertGoal(it) }
        s.reminders.forEach { dao.insertReminder(it) }
        s.holdings.forEach { dao.insertHolding(it) }
    }

    private suspend fun http(method: String, url: String, body: String?, token: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            code to text
        }

    private companion object { const val FULL = "full" }
}
