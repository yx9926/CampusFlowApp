package com.campus.auth

import com.campus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.SocketTimeoutException

class ApiFailure(val status: Int, message: String, val code: String = "") : RuntimeException(message)

data class CampusAccount(val username: String, val role: String)

interface AuthRepository {
    suspend fun login(username: String, password: String): CampusAccount
    suspend fun register(username: String, password: String)
    suspend fun logout()
}

object AuthValidation {
    fun username(value: String): String? =
        if (Regex("^[A-Za-z0-9_]{3,64}$").matches(value)) null
        else "用户名须为 3—64 个字母、数字或下划线"

    fun password(value: String): String? =
        if (value.length >= 8 && value.toByteArray(Charsets.UTF_8).size <= 72) null
        else "密码至少 8 个字符且最多 72 个 UTF-8 字节"
}

class HttpAuthRepository(serverAddress: String) : AuthRepository {
    private val origin = URI(serverAddress.trim().trimEnd('/'))
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private var csrfHeader: String? = null
    private var csrfToken: String? = null

    init {
        require(origin.scheme == "https" || (BuildConfig.DEBUG && origin.scheme == "http")) {
            "服务连接配置无效"
        }
        require(!origin.host.isNullOrBlank() && origin.userInfo == null &&
            origin.query == null && origin.fragment == null &&
            (origin.path.isNullOrBlank() || origin.path == "/")) {
            "服务连接配置无效"
        }
    }

    override suspend fun login(username: String, password: String): CampusAccount {
        refreshCsrf()
        val form = "username=" + encode(username) + "&password=" + encode(password)
        request("/login", "POST", form, "application/x-www-form-urlencoded; charset=UTF-8")
        // 后端登录会轮换 CSRF 令牌；Cookie 会话与新令牌必须一起保留。
        refreshCsrf()
        val account = JSONObject(request("/me"))
        return CampusAccount(account.getString("username"), account.getString("role"))
    }

    override suspend fun register(username: String, password: String) {
        refreshCsrf()
        request("/register", "POST", JSONObject()
            .put("username", username)
            .put("password", password)
            .toString())
    }

    override suspend fun logout() {
        request("/logout", "POST", "")
        cookies.cookieStore.removeAll()
        csrfHeader = null
        csrfToken = null
    }

    private suspend fun refreshCsrf() {
        val token = JSONObject(request("/csrf"))
        csrfHeader = token.getString("headerName")
        csrfToken = token.getString("token")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun request(path: String, method: String = "GET", body: String? = null,
        contentType: String = "application/json; charset=UTF-8"): String =
        api("/auth" + path, method, body, contentType)

    suspend fun api(
        path: String,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json; charset=UTF-8",
    ): String = withContext(Dispatchers.IO) {
        val uri = origin.resolve("/api" + path)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            cookies.get(uri, emptyMap()).forEach { (name, values) ->
                connection.setRequestProperty(name, values.joinToString("; "))
            }
            if (method != "GET") {
                check(!csrfHeader.isNullOrBlank() && !csrfToken.isNullOrBlank()) {
                    "安全令牌不可用，请重新提交"
                }
                connection.setRequestProperty(csrfHeader!!, csrfToken!!)
                connection.setRequestProperty("Content-Type", contentType)
                connection.doOutput = true
                connection.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            cookies.put(uri, connection.headerFields.filterKeys { it != null })
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val responseError = runCatching { JSONObject(text) }.getOrNull()
                val message = responseError?.optString("message")
                throw ApiFailure(status, message?.takeIf { it.isNotBlank() } ?: "请求失败（HTTP " + status + "），请重试", responseError?.optString("code").orEmpty())
            }
            text
        } catch (failure: SocketTimeoutException) {
            throw IOException(if (path == "/auth/register") "请求超时，请先登录确认账号是否已创建" else "请求超时，请刷新确认操作结果后重试", failure)
        } catch (failure: IOException) {
            throw IOException("暂时无法连接服务，请检查网络后重试", failure)
        } finally {
            connection.disconnect()
        }
    }
}
