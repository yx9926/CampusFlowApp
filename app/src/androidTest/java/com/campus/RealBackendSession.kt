package com.campus

import androidx.test.platform.app.InstrumentationRegistry
import com.campus.auth.HttpAuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.util.Properties

/** 真实业务验收使用手机私有配置，不向生产 APK 注入账号或测试数据。 */
object RealBackendSession {
    private fun configuration(): Properties {
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "backend-test.properties")
        assumeTrue("需在测试设备配置真实验收账号 backend-test.properties", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    fun client(role: String): HttpAuthRepository = runBlocking {
        val properties = configuration()
        val username = properties.getProperty("$role.username").orEmpty()
        val password = properties.getProperty("$role.password").orEmpty()
        assumeTrue("需配置 $role 的真实账号", username.isNotBlank() && password.isNotEmpty())
        val client = HttpAuthRepository(properties.getProperty("server", BuildConfig.BACKEND_BASE_URL))
        val account = client.login(username, password)
        org.junit.Assert.assertEquals(role, account.role)
        client
    }
}
