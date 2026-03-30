package gr.usee.app.auth

import gr.usee.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64
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
                    // Backend can respond with HTTP 200 while still signaling a logical
                    // authentication failure through a non-SUCCESS `code` field.
                    // In that case, treat the login as unsuccessful.
                    if (!isSuccessfulLoginResponse(response.body)) {
                        return@withContext LoginResult.Failure(
                            message = extractErrorMessage(response.body)
                                ?: "Login unsuccessful. Please check your credentials and try again."
                        )
                    }

                    val bearerToken = extractBearerToken(response.authorizationHeader, response.body)
                    val authCookie = extractAuthCookie(response.setCookieHeaders, response.body)
                    val role = extractRole(
                        responseBody = response.body,
                        bearerToken = bearerToken
                    )

                    return@withContext LoginResult.Success(
                        displayName = extractDisplayName(
                            responseBody = response.body,
                            fallbackUsername = sanitizedCredentials.username
                        ),
                        role = role,
                        bearerToken = bearerToken,
                        authCookie = authCookie
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
            ApiResponse(
                code = statusCode,
                body = responseBody,
                authorizationHeader = connection.getHeaderField("Authorization"),
                setCookieHeaders = connection.getHeaderFields()["Set-Cookie"].orEmpty()
            )
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

    /**
     * Determines whether login should be considered successful from payload semantics,
     * not just HTTP status code.
     *
     * Rule: if top-level or nested `code` exists and is not `SUCCESS`, login is treated
     * as failed. If no `code` is present, behavior falls back to HTTP status handling.
     */
    private fun isSuccessfulLoginResponse(responseBody: String): Boolean {
        val json = responseBody.toJsonObjectOrNull() ?: return true
        val code = json.optMeaningfulString("code")
            ?: json.optJSONObject("data")?.optMeaningfulString("code")
            ?: return true

        return code.equals("SUCCESS", ignoreCase = true)
    }

    private fun extractBearerToken(authorizationHeader: String?, responseBody: String): String? {
        val headerToken = authorizationHeader
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }
        if (!headerToken.isNullOrBlank()) {
            return headerToken
        }

        val json = responseBody.toJsonObjectOrNull() ?: return null
        val data = json.optJSONObject("data") ?: json
        return data.optMeaningfulString("token", "accessToken", "jwt")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractAuthCookie(setCookieHeaders: List<String>, responseBody: String): String? {
        val headerCookie = setCookieHeaders.firstNotNullOfOrNull { header ->
            val cookiePart = header.substringBefore(';').trim()
            cookiePart.takeIf { it.startsWith("auth_token=") }
                ?.substringAfter("auth_token=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        if (!headerCookie.isNullOrBlank()) {
            return headerCookie
        }

        val json = responseBody.toJsonObjectOrNull() ?: return null
        val data = json.optJSONObject("data") ?: json
        return data.optMeaningfulString("auth_token", "authToken", "cookie")
    }

    private fun extractRole(responseBody: String, bearerToken: String?): String {
        // 1. Try extracting role from JWT payload
        val tokenRole = bearerToken?.let { token ->
            runCatching {
                val payloadPart = token.split('.').getOrNull(1) ?: return@runCatching null
                val decoded = Base64.getUrlDecoder().decode(payloadPart)
                val payloadJson = JSONObject(String(decoded, Charsets.UTF_8))
                payloadJson.optMeaningfulString("role", "userRole", "type")
                    ?: payloadJson.optJSONArray("role")
                        ?.optString(0)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        if (!tokenRole.isNullOrBlank()) {
            return unquoteStringifiedJson(tokenRole).toString()
        }

        // 2. Try extracting role from response body
        val json = responseBody.toJsonObjectOrNull()
        val data = json?.optJSONObject("data") ?: json

        val bodyRole = data?.optMeaningfulString("role", "userRole", "type")
            ?: data?.optJSONObject("user")?.optMeaningfulString("role", "userRole", "type")

        return unquoteStringifiedJson(bodyRole) ?: "CUSTOMER"
    }

    /**
     * Unquotes stringified JSON strings. For example:
     * - Input: `"\"ADMIN\""` → Output: `"ADMIN"`
     * - Input: `"ADMIN"` → Output: `"ADMIN"`
     * - Input: `null` → Output: `null`
     *
     * This handles cases where backend sends role as a JSON-encoded string value.
     */
    private fun unquoteStringifiedJson(value: String?): String? {
        if (value.isNullOrBlank()) {
            return value
        }

        return runCatching {
            val trimmed = value.trim()
            // If value is wrapped in quotes and starts/ends with \", parse as JSON string
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                JSONObject("""{"v":$trimmed}""").optString("v").takeIf { it.isNotBlank() }
                    ?: value
            } else {
                value
            }
        }.getOrElse { value }
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
        val body: String,
        val authorizationHeader: String?,
        val setCookieHeaders: List<String>
    )

    private companion object {
        val loginPaths = listOf(
            "firebase/auth/login"
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
    data class Success(
        val displayName: String,
        val role: String,
        val bearerToken: String?,
        val authCookie: String?
    ) : LoginResult
    data class Failure(val message: String) : LoginResult
}
