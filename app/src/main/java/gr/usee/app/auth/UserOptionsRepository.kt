package gr.usee.app.auth

import gr.usee.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

/**
 * Retrieves dashboard options for the authenticated user.
 *
 * Endpoint: `GET firebase/auth/dashboard`
 * Header: `Authorization: Bearer <token>`
 *
 * Expected payload shape:
 * `response.data.returnobject = { homePageURL, mobileMenuEntries }`
 *
 * @author Georgia Chatzimarkaki
 */
class UserOptionsRepository {
    suspend fun fetchUserOptions(userToken: String): UserOptionsResult = withContext(Dispatchers.IO) {
        if (userToken.isBlank()) {
            return@withContext UserOptionsResult.Failure("Missing authentication token.")
        }

        val response = try {
            getJson(path = DASHBOARD_PATH, bearerToken = userToken)
        } catch (exception: Exception) {
            return@withContext UserOptionsResult.Failure(exception.toUserMessage())
        }

        if (response.code !in HttpURLConnection.HTTP_OK..299) {
            val message = extractErrorMessage(response.body)
                ?: "Dashboard options request failed with status ${response.code}."
            return@withContext UserOptionsResult.Failure(message)
        }

        val root = response.body.toJsonObjectOrNull()
            ?: return@withContext UserOptionsResult.Failure("Invalid dashboard response.")

        val data = root.optJSONObject("data") ?: root
        val dashboardJson = data.optJSONObject("returnobject")
            ?: data.optJSONObject("mobileDashboard")
            ?: data.optJSONObject("dashboard")
            ?: data

        val menuArray = dashboardJson.optJSONArray("mobileMenuEntries")
            ?: dashboardJson.optJSONArray("menuEntries")
            ?: dashboardJson.optJSONArray("entries")
            ?: JSONArray()

        val entries = buildList {
            for (index in 0 until menuArray.length()) {
                val entryJson = menuArray.optJSONObject(index) ?: continue
                val redirectURL = entryJson.optMeaningfulString("redirectURL", "redirectUrl", "url", "path")
                val label = entryJson.optMeaningfulString("label", "title", "name")
                if (redirectURL.isNullOrBlank() || label.isNullOrBlank()) {
                    continue
                }

                val iconValue = when (val icon = entryJson.opt("icon")) {
                    is JSONObject -> {
                        val name = icon.optMeaningfulString("name", "value", "icon")
                        val expoPackage = icon.optMeaningfulString("expoPackage", "package", "iconPackage")
                        if (name.isNullOrBlank()) {
                            null
                        } else {
                            ExpoIcon(expoPackage = expoPackage, name = name)
                        }
                    }

                    is String -> {
                        val normalizedName = icon.trim().takeIf { it.isNotBlank() }
                        normalizedName?.let { ExpoIcon(expoPackage = null, name = it) }
                    }

                    else -> null
                }

                add(
                    MobileMenuEntry(
                        icon = iconValue,
                        label = label,
                        redirectURL = redirectURL
                    )
                )
            }
        }

        val homePageURL = dashboardJson.optMeaningfulString("homePageURL", "homePageUrl", "homeUrl")
            ?: entries.firstOrNull()?.redirectURL

        UserOptionsResult.Success(
            dashboard = MobileDashboard(
                mobileMenuEntries = entries,
                homePageURL = homePageURL
            )
        )
    }

    private fun getJson(path: String, bearerToken: String): ApiResponse {
        val connection = (resolveUrl(path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            doInput = true
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }

        return try {
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

    private fun extractErrorMessage(responseBody: String): String? {
        val json = responseBody.toJsonObjectOrNull() ?: return null
        val data = json.optJSONObject("data") ?: json
        return data.optMeaningfulString("message", "error", "detail")
    }

    private fun String.toJsonObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    private fun JSONObject.optMeaningfulString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

    private fun Exception.toUserMessage(): String = when (this) {
        is UnknownHostException -> "The backend server could not be reached."
        is SocketTimeoutException -> "The dashboard options request timed out."
        is IOException -> "A network error occurred while loading dashboard options."
        else -> message ?: "Unexpected dashboard options error."
    }

    private data class ApiResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        const val DASHBOARD_PATH = "firebase/auth/dashboard"
    }
}

/**
 * Dashboard payload used by the mobile app to render accessible pages.
 *
 * @author Georgia Chatzimarkaki
 */
data class MobileDashboard(
    val mobileMenuEntries: List<MobileMenuEntry>,
    val homePageURL: String?
)

/**
 * One user-accessible menu item returned by backend dashboard options.
 *
 * @author Georgia Chatzimarkaki
 */
data class MobileMenuEntry(
    val icon: ExpoIcon?,
    val label: String,
    val redirectURL: String
)

/**
 * Icon descriptor used by the mobile dashboard contract.
 *
 * @author Georgia Chatzimarkaki
 */
data class ExpoIcon(
    val expoPackage: String?,
    val name: String
)

sealed interface UserOptionsResult {
    data class Success(val dashboard: MobileDashboard) : UserOptionsResult
    data class Failure(val message: String) : UserOptionsResult
}
