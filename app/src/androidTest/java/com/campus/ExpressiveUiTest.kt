package com.campus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.campus.admin.AdminViewModel
import com.campus.auth.AuthViewModel
import java.util.Properties
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ExpressiveUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io
            .File(instrumentation.targetContext.filesDir, "expressive-$prefix-$name.png")
            .outputStream()
            .use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        bitmap.recycle()
    }

    @Test
    fun realAdminScreensAndUnsavedEditorRemainUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = java.io.File(context.filesDir, "backend-test.properties")
        assumeTrue("视觉联调需要本机真实管理员配置", configuration.isFile)
        val properties = Properties().apply { configuration.inputStream().use { load(it) } }
        capture("login")
        lateinit var admin: AdminViewModel
        compose.runOnUiThread {
            val auth = ViewModelProvider(compose.activity)[AuthViewModel::class.java]
            admin = ViewModelProvider(compose.activity)[AdminViewModel::class.java]
            auth.serverAddress(properties.getProperty("server"))
            auth.username(properties.getProperty("SERVER_ADMIN.username"))
            auth.password(properties.getProperty("SERVER_ADMIN.password"))
            auth.submit()
        }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("nav_management").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("发现附近空间").assertExists()
        capture("discover")
        compose.onNodeWithTag("space_filters").performClick()
        compose.onNodeWithText("筛选", substring = false).assertExists()
        capture("filters")
        Espresso.pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("地图选择起点").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("nav_management").performClick()
        compose.onNodeWithTag("admin_home").assertExists()
        capture("dashboard")
        compose.onNodeWithTag("admin_overview").performScrollTo().performClick()
        compose.waitUntil(15_000) { admin.list.loaded || admin.list.error != null }
        assertNull(admin.list.error)
        compose.onNodeWithTag("admin_row_-1").assertExists()
        capture("overview")
        compose.runOnUiThread { admin.back() }
        compose.onNodeWithTag("admin_home").performScrollToNode(hasTestTag("admin_config"))
        compose.onNodeWithTag("admin_config").performScrollTo().performClick()
        compose.waitUntil(15_000) { admin.list.loaded || admin.list.error != null }
        assertNull(admin.list.error)
        compose.onNodeWithTag("edit_config").performScrollTo().performClick()
        capture("config")
        val field = compose.onNodeWithTag("admin_field_validitySeconds")
        field.performScrollTo().performTextReplacement("40")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("关闭", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("放弃未提交的修改？").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("admin_submit").assertExists()
        compose.runOnUiThread { admin.closeEditor(force = true) }
        compose.onNodeWithTag("nav_profile").performClick()
        compose.onNodeWithText("admin", substring = false).assertExists()
        capture("profile")
    }
}
