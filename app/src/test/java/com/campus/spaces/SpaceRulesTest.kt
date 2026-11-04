package com.campus.spaces

import com.campus.auth.ApiFailure
import org.junit.Assert.*
import org.junit.Test

class SpaceRulesTest {
    private fun status(device: String = "ONLINE") = SpaceStatus(
        100, 30, 0.3, 40.0, 39.0, 5, "VALID", "VALID", device,
        "2026-10-14T02:00:00Z", "2026-10-14T02:00:00Z",
        "2026-10-14T02:00:05Z", 15, "HARDWARE",
    )

    @Test fun staleAndOfflineMetricsAreNeverShownAsFresh() {
        assertTrue(status().peopleRecent(10))
        assertFalse(status().peopleRecent(11))
        assertFalse(status("OFFLINE").noiseRecent(0))
        assertTrue(status("OFFLINE").stale(0))
        assertFalse(status().copy(peopleState = "INVALID").peopleRecent(0))
        assertFalse(status().copy(peopleUpdatedAt = null).peopleRecent(0))
    }

    @Test fun timestampsHandleFractionsOffsetsAndInvalidDates() {
        assertEquals(parseInstant("2026-10-14T02:00:00Z"), parseInstant("2026-10-14T10:00:00+08:00"))
        assertEquals(parseInstant("2026-10-14T02:00:00.123Z"), parseInstant("2026-10-14T02:00:00.123456789Z"))
        assertNull(parseInstant("2026-02-31T02:00:00Z"))
        assertNull(parseInstant("not-a-date"))
    }

    @Test fun filtersEncodeNamesAndMultipleFacilities() {
        val query = SpaceFilters(latitude = 30.0, longitude = 120.0, name = "图书馆 & A", facilities = setOf("POWER", "WIFI")).query(2, 10)
        assertTrue(query.contains("name=%E5%9B%BE%E4%B9%A6%E9%A6%86+%26+A"))
        assertTrue(query.contains("facilities=POWER"))
        assertTrue(query.contains("facilities=WIFI"))
        assertTrue(query.contains("page=2"))
    }

    @Test fun lateReviewResponsesCannotOverwriteNewerVersions() {
        val initial = MyReview(1, 1, "空间", true, 4, 4, "", "PENDING", null, "", 1)
        val withdrawn = initial.copy(version = 2, status = "WITHDRAWN")
        assertTrue(ReviewRules.isOlder(withdrawn, initial))
        assertTrue(ReviewRules.isOlder(withdrawn, null))
        assertFalse(ReviewRules.isOlder(initial, withdrawn))
    }

    @Test fun searchRequiresActualCoordinatesAndRejectsNonHardwareMetrics() {
        assertFalse(SpaceFilters().hasLocation)
        assertTrue(runCatching { SpaceFilters().query() }.isFailure)
        val filters = SpaceFilters(latitude = 30.0, longitude = 120.0)
        assertTrue(filters.hasLocation)
        assertTrue(filters.query(2).contains("page=2"))
        assertTrue(filters.query().contains("latitude=30.0"))
        assertFalse(status().copy(source = "SIMULATED").peopleRecent(0))
        assertFalse(status().copy(source = "SIMULATED").noiseRecent(0))
    }

    @Test fun reviewRatingsAndLongContentAreRejected() {
        assertNotNull(ReviewRules.validation(0, 5, ""))
        assertNotNull(ReviewRules.validation(5, 6, ""))
        assertNotNull(ReviewRules.validation(5, 5, "字".repeat(501)))
        assertNull(ReviewRules.validation(5, 5, "字".repeat(500)))
    }
}
