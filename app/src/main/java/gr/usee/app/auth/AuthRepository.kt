package gr.usee.app.auth

import gr.usee.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

/**
 * Small authentication repository responsible for talking to the backend login API.
 *
 * The repository tries a short list of common login paths so the app can tolerate
 * small backend route differences without changing the UI layer.
 *
 * @author Georgia Chatzimarkaki
 */
class AuthRepository {
    suspend fun login(credentials: LoginCredentials): LoginResult = withContext(Dispatchers.IO) {
        val sanitizedCredentials = credentials.sanitized()
        val requestBody = JSONObject()
            .put("username", sanitizedCredentials.username)
            .put("password", sanitizedCredentials.password)
            .toString()

        for (path in loginPaths) {
            val response = try {
                postJson(path = path, body = requestBody)
            } catch (exception: Exception) {
                return@withContext LoginResult.Failure(exception.toUserMessage())
            }

            when {
                response.code == HttpURLConnection.HTTP_NOT_FOUND -> continue
                response.code in HttpURLConnection.HTTP_OK..299 -> {
                    return@withContext LoginResult.Success(
                        displayName = extractDisplayName(
                            responseBody = response.body,
                            fallbackUsername = sanitizedCredentials.username
                        )
                    )
                }
                else -> {
                    return@withContext LoginResult.Failure(
                        message = extractErrorMessage(response.body)
                            ?: "Login failed with status ${response.code}."
                    )
                }
            }
        }

        LoginResult.Failure(
            message = "No supported login endpoint was found under ${BuildConfig.BASE_URL}."
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

    private fun extractDisplayName(responseBody: String, fallbackUsername: String): String {
        val json = responseBody.toJsonObjectOrNull() ?: return fallbackUsername
        val directName = json.optMeaningfulString("displayName", "name", "username")
        if (directName != null) {
            return directName
        }

        val nestedUser = json.optJSONObject("user")
            ?: json.optJSONObject("data")?.optJSONObject("user")
            ?: json.optJSONObject("data")

        return nestedUser?.optMeaningfulString("displayName", "name", "username")
            ?: fallbackUsername
    }

    private fun extractErrorMessage(responseBody: String): String? {
        val jsonMessage = responseBody.toJsonObjectOrNull()?.let { json ->
            json.optMeaningfulString("message", "error", "detail")
                ?: json.optJSONObject("errors")?.optMeaningfulString("message", "error", "detail")
        }

        if (!jsonMessage.isNullOrBlank()) {
            return jsonMessage
        }

        val plainMessage = responseBody.trim()
        return plainMessage.takeIf { it.isNotBlank() }?.take(200)
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun JSONObject.optMeaningfulString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

    private fun Exception.toUserMessage(): String = when (this) {
        is UnknownHostException -> "The backend server could not be reached."
        is SocketTimeoutException -> "The login request timed out. Please try again."
        is IOException -> "A network error occurred while contacting the backend."
        else -> message ?: "Unexpected login error."
    }

    private data class ApiResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        val loginPaths = listOf(
            "auth/login",
            "login",
            "api/auth/login",
            "api/login",
            "v1/auth/login",
            "api/v1/auth/login"
        )
    }
}

data class LoginCredentials(
    val username: String,
    val password: String
) {
    fun sanitized(): LoginCredentials = copy(username = username.trim())
}

sealed interface LoginResult {
    data class Success(val displayName: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}
