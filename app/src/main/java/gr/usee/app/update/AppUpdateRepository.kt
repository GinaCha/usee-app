package gr.usee.app.update

import gr.usee.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Repository responsible for checking if a newer app version is available.
 *
 * Calls `mobile/staff/checkForLatestVersion` under the global `BASE_URL` and parses
 * flexible response payloads used by backend versions.
 *
 * @author Georgia Chatzimarkaki
 */
class AppUpdateRepository {
    suspend fun checkForLatestVersion(currentVersionCode: Int): UpdatePolicy = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("version", currentVersionCode)
            .put("secretKey", BuildConfig.UPDATE_SECRET_KEY)
            .toString()

        val response = runCatching {
            postJson(path = CHECK_UPDATE_PATH, body = requestBody)
        }.getOrElse {
            return@withContext UpdatePolicy(isUpdateAvailable = false, isStoreUpdate = true)
        }

        if (response.code !in HttpURLConnection.HTTP_OK..299) {
            return@withContext UpdatePolicy(isUpdateAvailable = false, isStoreUpdate = true)
        }

        val json = response.body.toJsonObjectOrNull() ?: return@withContext UpdatePolicy(
            isUpdateAvailable = false,
            isStoreUpdate = true
        )

        val data = json.optJSONObject("data") ?: json
        val latestVersionCode = data.optFirstInt("latestVersion", "latestVersionCode", "versionCode")
        val explicitIsUpdateAvailable = data.optFirstBoolean("isUpdateAvailable", "updateAvailable", "hasUpdate")
        val isStoreUpdate = data.optFirstBoolean("isStoreUpdate", "storeUpdate", "is_store_update") ?: true

        val isUpdateAvailable = explicitIsUpdateAvailable
            ?: (latestVersionCode?.let { it > currentVersionCode } ?: false)

        UpdatePolicy(
            isUpdateAvailable = isUpdateAvailable,
            isStoreUpdate = isStoreUpdate
        )
    }

    private fun postJson(path: String, body: String): ApiResponse {
        val connection = (resolveUrl(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val statusCode = connection.responseCode
            val responseBody = connection.readBody()
            ApiResponse(code = statusCode, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveUrl(path: String): URL = URI.create(BuildConfig.BASE_URL).resolve(path).toURL()

    private fun HttpURLConnection.readBody(): String {
        val stream = errorStream ?: inputStream ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun JSONObject.optFirstInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            when {
                isNull(key) -> null
                opt(key) is Number -> optInt(key)
                else -> optString(key).toIntOrNull()
            }
        }

    private fun JSONObject.optFirstBoolean(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { key ->
            when (val value = opt(key)) {
                is Boolean -> value
                is String -> when (value.lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
                is Number -> value.toInt() != 0
                else -> null
            }
        }

    private data class ApiResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        const val CHECK_UPDATE_PATH = "mobile/staff/checkForLatestVersion"
    }
}

data class UpdatePolicy(
    val isUpdateAvailable: Boolean,
    val isStoreUpdate: Boolean
)
