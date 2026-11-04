package com.campus.spaces

import com.campus.RealBackendSession
import com.campus.auth.ApiFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CampusFlowTest {
    @Test fun myReviewsUseBackendPaginationWithoutInventedRows() = runBlocking {
        val client = RealBackendSession.client("USER")
        val first = JSONObject(client.api("/user/reviews?page=1&pageSize=10"))
        val next = JSONObject(client.api("/user/reviews?page=2&pageSize=10"))
        val ids = (0 until first.getJSONArray("items").length()).map { first.getJSONArray("items").getJSONObject(it).getLong("id") }.toSet()
        for (index in 0 until next.getJSONArray("items").length()) assertFalse(next.getJSONArray("items").getJSONObject(index).getLong("id") in ids)
        assertTrue(first.getInt("total") >= first.getJSONArray("items").length())
    }

    @Test fun expiredSessionCannotFallBackToSampleData() = runBlocking {
        val client = RealBackendSession.client("USER")
        client.logout()
        val failure = runCatching { client.api("/user/reviews?page=1&pageSize=10") }.exceptionOrNull()
        assertTrue(failure is ApiFailure && failure.status == 401)
    }

    @Test fun reviewStateFilterIsAppliedByTheServer() = runBlocking {
        val client = RealBackendSession.client("USER")
        val result = JSONObject(client.api("/user/reviews?status=PENDING&page=1&pageSize=10"))
        val rows = result.getJSONArray("items")
        for (index in 0 until rows.length()) assertEquals("PENDING", rows.getJSONObject(index).getString("status"))
    }

    @Test fun invalidReviewSubmissionIsRejectedWithoutWritingData() = runBlocking {
        val client = RealBackendSession.client("USER")
        val failure = runCatching { client.api("/user/reviews/9223372036854775807", "PUT", "{\"environmentScore\":0,\"facilityScore\":6,\"content\":\"\",\"expectedVersion\":0}") }.exceptionOrNull()
        assertTrue(failure is ApiFailure && failure.status == 400)
    }
}
