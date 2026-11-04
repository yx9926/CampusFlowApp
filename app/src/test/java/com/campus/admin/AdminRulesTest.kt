package com.campus.admin

import com.campus.auth.*
import org.junit.Assert.*
import org.junit.Test

class AdminRulesTest {
    private val config = mapOf(
        "validitySeconds" to "30", "noiseWindowSeconds" to "60",
        "distanceWeight" to "0.3", "quietWeight" to "0.3", "freeWeight" to "0.25", "facilityWeight" to "0.15",
    )
    private fun editor(action: AdminAction, row: AdminRow?, values: Map<String, String>) = AdminEditor(action, row, values, values)

    @Test fun configValidatesRangeRelationshipsAndFiniteWeights() {
        assertNull(AdminRules.validate(AdminAction.Config, config))
        assertNotNull(AdminRules.validate(AdminAction.Config, config + ("validitySeconds" to "4")))
        assertNotNull(AdminRules.validate(AdminAction.Config, config + ("noiseWindowSeconds" to "29")))
        assertNotNull(AdminRules.validate(AdminAction.Config, config + ("distanceWeight" to "NaN")))
        assertNotNull(AdminRules.validate(AdminAction.Config, config + ("distanceWeight" to "0.4")))
    }

    @Test fun filtersValidateCalendarDatesAndEncodeReservedCharacters() {
        assertNull(AdminRules.filterError(mapOf("updatedFrom" to "2026-02-28", "spaceId" to "3")))
        assertNotNull(AdminRules.filterError(mapOf("updatedFrom" to "2026-02-31")))
        assertNotNull(AdminRules.filterError(mapOf("updatedFrom" to "2026-10-14", "updatedTo" to "2026-10-01")))
        assertNotNull(AdminRules.filterError(mapOf("spaceId" to "0")))
        assertTrue(AdminRules.query(mapOf("actor" to "A & B"), 2).contains("actor=A+%26+B"))
    }

    @Test fun moderationRequiresReasonsAndRetainsServerVersionInEditor() {
        val row = AdminRow(1, "空间评价", "PENDING", version = 7)
        assertNotNull(AdminRules.validate(AdminAction.Decision, mapOf("action" to "REJECT", "reason" to " ")))
        assertNull(AdminRules.validate(AdminAction.Decision, mapOf("action" to "REJECT", "reason" to "与空间无关")))
        assertEquals(7L, editor(AdminAction.Decision, row, emptyMap()).row?.version)
    }

    @Test fun accountCreationValidatesActualCredentialsAndRole() {
        val values = mapOf("username" to "staff_01", "password" to "Password123", "role" to "DATA_ADMIN", "enabled" to "true")
        assertNull(AdminRules.validate(AdminAction.CreateAccount, values))
        assertNotNull(AdminRules.validate(AdminAction.CreateAccount, values + ("role" to "ADMIN")))
        assertNotNull(AdminRules.validate(AdminAction.CreateAccount, values + ("password" to "123")))
        assertNotNull(AdminRules.validate(AdminAction.Account, values + ("enabled" to "yes")))
    }

    @Test fun overviewProvidesNoArtificialDataControls() {
        assertFalse(AdminAction.entries.any { it.name in setOf("Pause", "Resume", "Reset", "Scenario", "Device") })
        assertFalse(AdminRules.configFields.any { it.key == "simulationSeconds" })
    }

    @Test fun inspectionPaginationAndValidityRequireExplicitInput() {
        assertTrue(AdminRules.query(mapOf("kind" to "NOISE", "valid" to "false"), 2).contains("page=2"))
        assertTrue(AdminRules.query(mapOf("kind" to "NOISE", "valid" to "false"), 2).contains("valid=false"))
        assertNotNull(AdminRules.validate(AdminAction.Validity, mapOf("reason" to " ")))
        assertNull(AdminRules.validate(AdminAction.Validity, mapOf("reason" to "采样异常")))
    }

    @Test fun configurationConflictPreservesTheUnsavedDraft() {
        val original = AdminRow(0, "运行配置", values = config, version = 3)
        val draft = editor(AdminAction.Config, original, config).copy(values = config + ("validitySeconds" to "40"), conflict = true)
        assertTrue(draft.dirty)
        assertEquals("40", draft.values["validitySeconds"])
        assertEquals(3L, draft.row?.version)
    }

    @Test fun ordinaryUsersHaveNoAdminEntriesAndRoleSeparationIsExact() {
        assertTrue(AdminPage.forRole("USER").isEmpty())
        assertEquals(4, AdminPage.forRole("DATA_ADMIN").size)
        assertEquals(4, AdminPage.forRole("SERVER_ADMIN").size)
    }
}
