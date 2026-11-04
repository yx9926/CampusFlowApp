package com.campus.spaces

import androidx.compose.material3.Text
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.campus.MainActivity
import com.campus.ui.theme.CampusFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OriginPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun openingMapCentersOnEmulatorLocation() {
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(arguments.getString("verifyMapLocation") == "true")
        var confirmed: OriginPlace? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                CampusFlowTheme { OriginPicker(null, {}, { confirmed = it }) }
            }
        }
        if (compose.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("同意并继续").performClick()
        }
        compose.waitUntil(18_000) {
            compose.onAllNodes(hasText("定位") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("暂时无法定位", substring = true).assertDoesNotExist()
        compose.waitUntil(18_000) {
            compose.onAllNodesWithTag("origin_map_loading").fetchSemanticsNodes().isEmpty()
        }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        var screenshot: android.graphics.Bitmap? = null
        compose.waitUntil(15_000) {
            screenshot?.recycle()
            screenshot = instrumentation.uiAutomation.takeScreenshot()
            val bitmap = screenshot ?: return@waitUntil false
            val mapColors = (3..7).flatMap { row ->
                (1..9).map { column -> bitmap.getPixel(bitmap.width * column / 10, bitmap.height * row / 10) }
            }.toSet()
            mapColors.size > 5
        }
        val rendered = requireNotNull(screenshot)
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "origin-location.png")
            .outputStream().use { rendered.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rendered.recycle()
        compose.onNodeWithText("地图加载失败", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("origin_map").performTouchInput { click(center) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("confirm_origin") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("confirm_origin").assertIsEnabled().performClick()
        compose.runOnIdle {
            val point = requireNotNull(confirmed)
            assertEquals(40.0037, point.latitude, 0.015)
            assertEquals(116.3268, point.longitude, 0.015)
        }
    }

    @Test
    fun confirmedPointReturnsSelectionAndFiltersDoNotExposeCoordinateFields() {
        compose.runOnUiThread {
            compose.activity.setContent {
                var confirmed by remember { mutableStateOf(false) }
                CampusFlowTheme {
                    if (!confirmed) OriginPicker(
                        OriginPlace(30.0, 120.0, "测试起点", "测试选点状态，不写数据库"), {},
                    ) { place ->
                        assertEquals(30.0, place.latitude, 0.0)
                        assertEquals(120.0, place.longitude, 0.0)
                        confirmed = true
                    } else FiltersDialog(SpaceFilters(), {}, {})
                }
            }
        }
        if (compose.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("同意并继续").performClick()
        }
        compose.onNodeWithTag("confirm_origin").assertIsEnabled().performClick()
        compose.onNodeWithText("地图选择起点").assertExists()
        compose.onNodeWithText("纬度", substring = false).assertDoesNotExist()
        compose.onNodeWithText("经度", substring = false).assertDoesNotExist()
    }

    @Test
    fun realAddressSearchSelectsGcj02OriginWithoutBackendLogin() {
        compose.runOnUiThread { compose.activity.setContent {
            var open by remember { mutableStateOf(true) }
            var confirmed by remember { mutableStateOf<OriginPlace?>(null) }
            CampusFlowTheme {
                if (open) OriginPicker(null, { open = false }) {
                    confirmed = it
                    open = false
                }
                confirmed?.let { Text("已确认：${it.title}") }
            }
        } }
        if (compose.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("同意并继续").performClick()
        }
        compose.onNodeWithTag("confirm_origin").assertIsNotEnabled()
        compose.onNodeWithTag("origin_search").performTextInput("清华大学")
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("origin_result").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("地址搜索失败", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("未找到地点", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val errors = compose.onAllNodesWithText("地址搜索失败", substring = true).fetchSemanticsNodes()
            .flatMap { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text] }.joinToString { it.text }
        assertTrue("高德搜索未返回可选坐标：$errors", compose.onAllNodesWithTag("origin_result").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithTag("origin_result")[0].performClick()
        compose.onNodeWithTag("confirm_origin").assertIsEnabled().performClick()
        compose.onNodeWithText("已确认：", substring = true).assertExists()
    }
}
