package gr.usee.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the authenticated backend session.
 *
 * Stores bearer token and auth cookie (`auth_token`) securely at rest.
 *
 * @author Georgia Chatzimarkaki
 */
class SecureSessionStore(context: Context) {
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(session: AuthSession) {
        securePrefs.edit()
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_BEARER_TOKEN, session.bearerToken)
            .putString(KEY_AUTH_COOKIE, session.authCookie)
            .apply()
    }

    fun clear() {
        securePrefs.edit()
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_BEARER_TOKEN)
            .remove(KEY_AUTH_COOKIE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "secure_session_prefs"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ROLE = "role"
        const val KEY_BEARER_TOKEN = "bearer_token"
        const val KEY_AUTH_COOKIE = "auth_cookie"
    }
}

data class AuthSession(
    val displayName: String,
    val role: String,
    val bearerToken: String?,
    val authCookie: String?,
    val mobileDashboard: MobileDashboard? = null
)
