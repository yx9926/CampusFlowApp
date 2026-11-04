package com.campus.admin

import com.campus.auth.*
import org.junit.Assert.*
import org.junit.Test

class AssetRulesTest {
    private val values = AssetRules.spaceDefaults + mapOf("name" to "自习室", "address" to "知行楼",
        "latitude" to "31.2304", "longitude" to "121.4737", "coordinateConfirmed" to "true")
    private fun editor(action: AdminAction, row: AdminRow?, values: Map<String, String>) = AdminEditor(action, row, values, values)

    @Test fun coordinatesAndOpeningHoursRequireExplicitValidInput() {
        assertNull(AssetRules.space(values))
        assertNotNull(AssetRules.space(values + ("coordinateConfirmed" to "false")))
        assertNotNull(AssetRules.space(values + ("latitude" to "NaN")))
        assertNotNull(AssetRules.space(values + ("longitude" to "181")))
        assertNotNull(AssetRules.space(values + ("openDays" to "")))
        assertNotNull(AssetRules.space(values + ("closeTime" to "07:00")))
        assertNull(AssetRules.space(values + mapOf("allDay" to "true", "closeTime" to "07:00")))
    }

    @Test fun capacityAndFacilityValidationAllowsCorrectingTheDraft() {
        assertNotNull(AssetRules.space(values + ("capacity" to "0")))
        assertNotNull(AssetRules.space(values + ("capacity" to "10001")))
        assertNotNull(AssetRules.space(values + ("facilities" to "UNKNOWN")))
        assertNull(AssetRules.space(values + ("capacity" to "40")))
    }

    @Test fun registrationOnlyOffersDiscoveredDevicesAndUnboundSpaces() {
        val result = AdminResult(candidates = mapOf("device-2" to "设备二"),
            spaceOptions = mapOf("1" to "空间一", "2" to "空间二"), boundSpaces = mapOf("1" to 7))
        val fields = AssetRules.hardwareFields(editor(AdminAction.CreateHardware, null, AssetRules.hardwareDefaults), result)
        assertEquals(setOf("device-2"), fields.single { it.key == "deviceId" }.choices.keys)
        assertFalse(fields.single { it.key == "spaceId" }.choices.containsKey("1"))
        assertTrue(fields.single { it.key == "spaceId" }.choices.containsKey("2"))
    }

    @Test fun hardwareHistoryLocksIdentityAndBindingAndPreservesVersion() {
        val row = AdminRow(7, "设备", values = mapOf("deviceId" to "device-1", "spaceId" to "1", "lastReceivedAt" to "2026-10-26T00:00:00Z"), version = 9)
        val draft = editor(AdminAction.Hardware, row, row.values)
        val fields = AssetRules.hardwareFields(draft, AdminResult())
        assertTrue(fields.single { it.key == "deviceId" }.readOnly)
        assertTrue(fields.single { it.key == "spaceId" }.readOnly)
        assertEquals(9L, draft.row?.version)
    }

    @Test fun missingDiscoveryDisablesInventedDeviceSelection() {
        val fields = AssetRules.hardwareFields(editor(AdminAction.CreateHardware, null, AssetRules.hardwareDefaults), AdminResult())
        assertTrue(fields.single { it.key == "deviceId" }.readOnly)
        assertNotNull(AssetRules.hardware(AssetRules.hardwareDefaults))
        assertNotNull(AssetRules.hardware(AssetRules.hardwareDefaults + ("deviceId" to "invalid/id")))
    }
}
