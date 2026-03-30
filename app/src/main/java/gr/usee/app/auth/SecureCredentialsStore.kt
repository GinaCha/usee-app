package gr.usee.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Represents one saved account used for secure quick-login.
 *
 * @author Georgia Chatzimarkaki
 */
data class SavedCredential(
    val username: String,
    val password: String
)

/**
 * Encrypted local storage for login credentials.
 *
 * Uses `EncryptedSharedPreferences` with a `MasterKey` so usernames and passwords
 * are encrypted at rest and can be reused for one-tap login.
 *
 * @author Georgia Chatzimarkaki
 */
class SecureCredentialsStore(context: Context) {
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(username: String, password: String) {
        val sanitizedUsername = username.trim()
        if (sanitizedUsername.isBlank() || password.isBlank()) {
            return
        }

        val usernames = securePrefs.getStringSet(KEY_USERNAMES, emptySet()).orEmpty().toMutableSet()
        usernames.remove(sanitizedUsername)
        usernames.add(sanitizedUsername)

        securePrefs.edit()
            .putStringSet(KEY_USERNAMES, usernames)
            .putString(passwordKey(sanitizedUsername), password)
            .apply()
    }

    /**
     * Returns all saved accounts sorted by username.
     */
    fun getAll(): List<SavedCredential> {
        val usernames = securePrefs.getStringSet(KEY_USERNAMES, emptySet()).orEmpty()
        return usernames.sorted().mapNotNull { username ->
            val password = securePrefs.getString(passwordKey(username), null)
            if (password.isNullOrBlank()) {
                null
            } else {
                SavedCredential(username = username, password = password)
            }
        }
    }

    private fun passwordKey(username: String): String = "password_$username"

    private companion object {
        const val PREFS_NAME = "secure_login_prefs"
        const val KEY_USERNAMES = "saved_usernames"
    }
}
