package com.campus.spaces

import androidx.compose.runtime.Immutable
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.net.URLEncoder

const val PAGE_SIZE = 10
const val MAX_SPACE_RESULTS = 100
const val POLL_MILLIS = 5_000L
val spaceTypes = linkedMapOf("" to "全部类型", "LIBRARY" to "图书馆", "CLASSROOM" to "教室",
    "DISCUSSION" to "研讨区", "OUTDOOR" to "室外", "STUDY_ROOM" to "自习室", "CAFE" to "咖啡厅")
val facilityNames = linkedMapOf("AC" to "空调", "SEAT" to "座椅", "POWER" to "插座", "WIFI" to "网络")
val sortNames = linkedMapOf("SCORE" to "综合推荐", "DISTANCE" to "距离最近", "QUIET" to "最安静",
    "OCCUPANCY" to "最不拥挤", "FACILITY" to "设施最完善")
val reviewStatusNames = linkedMapOf("" to "全部状态", "PENDING" to "待审核", "APPROVED" to "已通过",
    "REJECTED" to "已驳回", "WITHDRAWN" to "已撤回")

@Immutable
data class SpaceFilters(
    val name: String = "", val type: String = "", val maxDistance: Double = 3000.0,
    val minQuiet: Int? = null, val maxOccupancy: Double? = null,
    val facilities: Set<String> = emptySet(), val openOnly: Boolean = true,
    val sort: String = "SCORE", val latitude: Double? = null,
    val longitude: Double? = null, val startAt: String = "", val durationMinutes: Int = 0,
    val originLabel: String = "",
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun query(page: Int = 1, pageSize: Int = PAGE_SIZE): String {
        require(hasLocation) { "请先设置实际 GCJ-02 搜索起点" }
        val params = mutableListOf(
            "latitude" to latitude.toString(), "longitude" to longitude.toString(),
            "maxDistance" to maxDistance.toString(), "openOnly" to openOnly.toString(),
            "sort" to sort, "durationMinutes" to durationMinutes.toString(),
            "page" to page.toString(), "pageSize" to pageSize.toString(),
        )
        if (name.isNotBlank()) params += "name" to name.trim()
        if (type.isNotEmpty()) params += "type" to type
        minQuiet?.let { params += "minQuiet" to it.toString() }
        maxOccupancy?.let { params += "maxOccupancy" to it.toString() }
        if (startAt.isNotEmpty()) params += "startAt" to startAt
        facilities.sorted().forEach { params += "facilities" to it }
        return params.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }
}

@Immutable
data class Space(
    val id: Long, val name: String, val type: String, val address: String,
    val capacity: Int, val openTime: String, val closeTime: String, val openDays: String,
    val allDay: Boolean, val facilities: List<String>, val description: String, val enabled: Boolean = true,
)

@Immutable
data class SpaceStatus(
    val capacity: Int, val people: Int?, val occupancy: Double?, val noise: Double?,
    val typicalNoise: Double?, val quietLevel: Int?, val peopleState: String, val noiseState: String,
    val deviceState: String, val peopleUpdatedAt: String?, val noiseUpdatedAt: String?,
    val calculatedAt: String, val validitySeconds: Int, val source: String,
) {
    fun recent(time: String?, condition: String, elapsedSeconds: Long): Boolean {
        val sampled = parseInstant(time) ?: return false
        val calculated = parseInstant(calculatedAt) ?: return false
        val age = (calculated - sampled) / 1000.0 + elapsedSeconds.coerceAtLeast(0)
        return source == "HARDWARE" && condition == "VALID" && age >= -1 && age <= validitySeconds
    }
    fun peopleRecent(elapsed: Long) = deviceState == "ONLINE" && people != null && recent(peopleUpdatedAt, peopleState, elapsed)
    fun noiseRecent(elapsed: Long) = deviceState == "ONLINE" &&
        recent(noiseUpdatedAt, noiseState, elapsed) && typicalNoise != null
    fun stale(elapsed: Long) =
        (peopleState == "VALID" && !peopleRecent(elapsed)) ||
        (noiseState == "VALID" && !noiseRecent(elapsed))
}

@Immutable
data class ReviewSummary(val count: Int = 0, val environment: Double? = null, val facility: Double? = null)

@Immutable
data class SpaceCard(
    val space: Space, val status: SpaceStatus, val distance: Double, val score: Double,
    val openNow: Boolean, val reasons: List<String>, val summary: ReviewSummary = ReviewSummary(),
    val receivedAtElapsed: Long = 0,
)

data class CampusPage<T>(val items: List<T>, val total: Int, val page: Int, val pageSize: Int,
    val warnings: List<String> = emptyList())

@Immutable
data class PublicReview(val id: Long, val author: String, val environment: Int,
    val facility: Int, val content: String, val updatedAt: String)

@Immutable
data class MyReview(
    val id: Long, val spaceId: Long, val spaceName: String, val spaceEnabled: Boolean,
    val environment: Int, val facility: Int, val content: String, val status: String,
    val reason: String?, val updatedAt: String, val version: Long,
)

data class OwnReview(val review: MyReview?, val spaceEnabled: Boolean)

object ReviewRules {
    fun validation(environment: Int, facility: Int, content: String): String? = when {
        environment !in 1..5 || facility !in 1..5 -> "请分别选择环境和设施评分"
        content.length > 500 -> "评价文字不能超过 500 字"
        else -> null
    }
    fun isOlder(current: MyReview?, incoming: MyReview?): Boolean = current != null &&
        (incoming == null || (current.id == incoming.id && incoming.version < current.version))
}

// 以服务器计算时间与单调时钟的已流逝秒数判断新鲜度，不依赖手机墙上时钟。
fun parseInstant(value: String?): Long? {
    if (value == null) return null
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$")
        .matchEntire(value) ?: return null
    val normalized = match.groupValues[1] + "." +
        match.groupValues[2].take(3).padEnd(3, '0') + match.groupValues[3]
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    format.isLenient = false
    val position = ParsePosition(0)
    val date = format.parse(normalized, position)
    return date?.time?.takeIf { position.index == normalized.length }
}

fun displayTime(value: String?): String {
    val millis = parseInstant(value) ?: return "暂无记录"
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE)
    format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    return format.format(java.util.Date(millis))
}

fun nowIso(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(java.util.Date())
}
