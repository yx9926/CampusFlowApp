package com.campus.admin

import com.campus.RealBackendSession
import com.campus.auth.ApiFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdminFlowTest {
    @Test fun dataAdminUsesTheReviewEndpointAndCannotReadSystemAccounts() = runBlocking {
        val client = RealBackendSession.client("DATA_ADMIN")
        val rows = JSONObject(client.api("/data/reviews?status=PENDING&page=1&pageSize=20"))
        assertTrue(rows.has("items"))
        val denied = runCatching { client.api("/system/accounts") }.exceptionOrNull()
        assertTrue(denied is ApiFailure && denied.status == 403)
    }

    @Test fun inspectionRequiresReasonWithoutChangingARecord() = runBlocking {
        val client = RealBackendSession.client("DATA_ADMIN")
        val rows = JSONObject(client.api("/data/records?kind=PEOPLE&page=1&pageSize=20"))
        assertTrue(rows.has("total"))
        val invalid = runCatching {
            client.api("/data/records/PEOPLE/9223372036854775807/validity", "PUT", "{\"valid\":false,\"reason\":\"\"}")
        }.exceptionOrNull()
        assertTrue(invalid is ApiFailure && invalid.status == 400)
    }

    @Test fun serverAdminCanReadAccountsAndLogsAndRejectsInvalidCreation() = runBlocking {
        val client = RealBackendSession.client("SERVER_ADMIN")
        assertTrue(JSONObject(client.api("/system/accounts?page=1&pageSize=20")).has("items"))
        assertTrue(JSONObject(client.api("/system/logs?page=1&pageSize=20")).has("items"))
        val invalid = runCatching { client.api("/system/accounts", "POST", "{}") }.exceptionOrNull()
        assertTrue(invalid is ApiFailure && invalid.status == 400)
    }

    @Test fun configurationValidatesBeforeWriting() = runBlocking {
        val client = RealBackendSession.client("SERVER_ADMIN")
        val current = JSONObject(client.api("/system/config"))
        assertFalse(current.has("simulationSeconds"))
        val invalid = runCatching { client.api("/system/config", "PUT", current.put("expectedVersion", current.getLong("version")).put("validitySeconds", 0).toString()) }.exceptionOrNull()
        assertTrue(invalid is ApiFailure && invalid.status == 400)
    }

    @Test fun overviewUsesHardwareAndExposesNoSimulationControls() = runBlocking {
        val client = RealBackendSession.client("SERVER_ADMIN")
        val overview = JSONObject(client.api("/system/overview"))
        assertEquals("HARDWARE", overview.getString("dataSource"))
        assertFalse(overview.has("simulationRunId"))
        val removed = runCatching { client.api("/system/simulation/resume", "POST", "{}") }.exceptionOrNull()
        assertTrue(removed is ApiFailure && removed.status == 404)
        assertNotNull(JSONArray(client.api("/system/devices")))
    }

    @Test fun spaceCreationRejectsUnconfirmedCoordinates() = runBlocking {
        val client = RealBackendSession.client("DATA_ADMIN")
        JSONArray(client.api("/data/spaces"))
        val invalid = runCatching { client.api("/data/spaces", "POST", "{}") }.exceptionOrNull()
        assertTrue(invalid is ApiFailure && invalid.status == 400)
    }

    @Test fun hardwareRegistrationUsesRealDiscoveryAndRejectsInvalidInput() = runBlocking {
        val client = RealBackendSession.client("DATA_ADMIN")
        val hardware = JSONArray(client.api("/data/hardware-devices"))
        val candidates = JSONArray(client.api("/data/hardware-devices/candidates"))
        val registered = (0 until hardware.length()).map { hardware.getJSONObject(it).getString("deviceId") }.toSet()
        for (index in 0 until candidates.length()) assertFalse(candidates.getJSONObject(index).getString("deviceId") in registered)
        val invalid = runCatching { client.api("/data/hardware-devices", "POST", "{}") }.exceptionOrNull()
        assertTrue(invalid is ApiFailure && invalid.status == 400)
    }
}
