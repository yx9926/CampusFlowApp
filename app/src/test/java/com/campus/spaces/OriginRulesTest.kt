package com.campus.spaces

import org.junit.Assert.*
import org.junit.Test

class OriginRulesTest {
    @Test
    fun originRejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFalse(validPoint(Double.NaN, 120.0))
        assertFalse(validPoint(30.0, Double.POSITIVE_INFINITY))
        assertFalse(validPoint(91.0, 120.0))
        assertFalse(validPoint(30.0, -181.0))
        assertTrue(validPoint(30.0, 120.0))
    }

    @Test
    fun originLabelRemainsLocalAndDoesNotChangeBackendContract() {
        val filters = SpaceFilters(latitude = 30.0, longitude = 120.0, originLabel = "校图书馆")
        assertTrue(filters.hasLocation)
        assertFalse(filters.query().contains("originLabel"))
        assertTrue(filters.query().contains("latitude=30.0"))
        assertEquals("校图书馆", filters.copy(type = "LIBRARY").originLabel)
    }
}
