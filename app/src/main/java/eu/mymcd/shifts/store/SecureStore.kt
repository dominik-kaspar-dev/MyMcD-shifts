package eu.mymcd.shifts.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountMeta(
    val id: String,
    val email: String,
    val fullName: String,
    val userId: Long
)

/** Encrypted multi-account credential / session / shift storage (Android Keystore). */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mymcd_secure", Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANG, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    var activeAccountId: String
        get() = prefs.getString(KEY_ACTIVE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    fun accountIds(): List<String> {
        val raw = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccountIds(ids: List<String>) {
        prefs.edit().putString(KEY_ACCOUNTS, JSONArray(ids).toString()).apply()
    }

    fun createAccount(email: String): String {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val ids = accountIds().toMutableList()
        ids.add(id)
        saveAccountIds(ids)
        setEmail(id, email)
        if (activeAccountId.isBlank()) activeAccountId = id
        return id
    }

    fun ensureAccountForEmail(email: String): String {
        accountIds().firstOrNull { getEmail(it).equals(email, ignoreCase = true) }?.let { return it }
        return createAccount(email)
    }

    fun deleteAccount(id: String) {
        val ids = accountIds().filterNot { it == id }
        saveAccountIds(ids)
        listOf(
            kEmail(id), kPassword(id), kCookie(id), kUserId(id), kFullName(id),
            kShifts(id), kPrev(id), kUpdate(id), kError(id)
        ).forEach { prefs.edit().remove(it).apply() }
        if (activeAccountId == id) {
            activeAccountId = ids.firstOrNull() ?: ""
        }
    }

    fun accounts(): List<AccountMeta> = accountIds().mapNotNull { id ->
        val email = getEmail(id)
        if (email.isBlank()) null
        else AccountMeta(id, email, getFullName(id), getUserId(id))
    }

    fun setEmail(id: String, v: String) = prefs.edit().putString(kEmail(id), v).apply()
    fun getEmail(id: String): String = prefs.getString(kEmail(id), "") ?: ""

    fun savePassword(id: String, plain: String) = putEnc(kPassword(id), plain)
    fun getPassword(id: String): String = getEnc(kPassword(id)) ?: ""

    fun saveCookie(id: String, cookie: String) = putEnc(kCookie(id), cookie)
    fun getCookie(id: String): String = getEnc(kCookie(id)) ?: ""

    fun setUserId(id: String, v: Long) = prefs.edit().putLong(kUserId(id), v).apply()
    fun getUserId(id: String): Long = prefs.getLong(kUserId(id), -1L)

    fun setFullName(id: String, v: String) = prefs.edit().putString(kFullName(id), v).apply()
    fun getFullName(id: String): String = prefs.getString(kFullName(id), "") ?: ""

    fun setShifts(id: String, json: String) = putEnc(kShifts(id), json)
    fun getShifts(id: String): String = getEnc(kShifts(id)) ?: ""

    fun setPreviousShifts(id: String, json: String) = putEnc(kPrev(id), json)
    fun getPreviousShifts(id: String): String = getEnc(kPrev(id)) ?: ""

    fun setLastUpdate(id: String, ms: Long) = prefs.edit().putLong(kUpdate(id), ms).apply()
    fun getLastUpdate(id: String): Long = prefs.getLong(kUpdate(id), 0L)

    fun setError(id: String, msg: String) = prefs.edit().putString(kError(id), msg).apply()
    fun getError(id: String): String = prefs.getString(kError(id), "") ?: ""

    fun hasCredentials(id: String): Boolean =
        getEmail(id).isNotBlank() && getPassword(id).isNotBlank()

    fun anyAccountConfigured(): Boolean = accountIds().any { hasCredentials(it) }

    /** Removes one account's credentials/session but keeps shift history until delete. */
    fun clearAccountSession(id: String) {
        prefs.edit()
            .remove(kPassword(id))
            .remove(kCookie(id))
            .remove(kUserId(id))
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun kEmail(id: String) = "email_$id"
    private fun kPassword(id: String) = "password_$id"
    private fun kCookie(id: String) = "cookie_$id"
    private fun kUserId(id: String) = "user_id_$id"
    private fun kFullName(id: String) = "full_name_$id"
    private fun kShifts(id: String) = "shifts_$id"
    private fun kPrev(id: String) = "prev_shifts_$id"
    private fun kUpdate(id: String) = "last_update_$id"
    private fun kError(id: String) = "last_error_$id"

    private fun putEnc(key: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    private fun getEnc(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return decrypt(raw)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = try {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val body = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mymcd_shifts_store"
        private const val KEY_LANG = "lang"
        private const val KEY_ACTIVE = "active_account"
        private const val KEY_ACCOUNTS = "account_list"
    }
}
