package com.campus.spaces

import com.campus.auth.HttpAuthRepository
import org.json.JSONArray
import org.json.JSONObject

interface CampusRepository {
    suspend fun spaces(filters: SpaceFilters, page: Int, pageSize: Int): CampusPage<SpaceCard>
    suspend fun detail(id: Long, filters: SpaceFilters): SpaceCard
    suspend fun publicReviews(id: Long, page: Int): CampusPage<PublicReview>
    suspend fun ownReview(id: Long): OwnReview
    suspend fun myReviews(status: String, page: Int): CampusPage<MyReview>
    suspend fun saveReview(id: Long, environment: Int, facility: Int, content: String, version: Long): MyReview
    suspend fun withdrawReview(id: Long, version: Long): MyReview
}

class HttpCampusRepository(private val client: HttpAuthRepository) : CampusRepository {
    override suspend fun spaces(filters: SpaceFilters, page: Int, pageSize: Int) =
        parsePage(JSONObject(client.api("/spaces?" + filters.query(page, pageSize))), ::parseCard)

    override suspend fun detail(id: Long, filters: SpaceFilters) =
        parseCard(JSONObject(client.api("/spaces/" + id + "?latitude=" + filters.latitude +
            "&longitude=" + filters.longitude)))

    override suspend fun publicReviews(id: Long, page: Int) =
        parsePage(JSONObject(client.api("/spaces/" + id + "/reviews?page=" + page + "&pageSize=" + PAGE_SIZE))) {
            PublicReview(it.getLong("id"), it.getString("author"), it.getInt("environmentScore"),
                it.getInt("facilityScore"), it.optString("content"), it.getString("updatedAt"))
        }

    override suspend fun ownReview(id: Long): OwnReview {
        val data = JSONObject(client.api("/user/reviews/" + id))
        return OwnReview(data.optJSONObject("review")?.let(::parseReview), data.getBoolean("spaceEnabled"))
    }

    override suspend fun myReviews(status: String, page: Int) =
        parsePage(JSONObject(client.api("/user/reviews?status=" + status + "&page=" + page +
            "&pageSize=" + PAGE_SIZE)), ::parseReview)

    override suspend fun saveReview(id: Long, environment: Int, facility: Int, content: String, version: Long) =
        parseReview(JSONObject(client.api("/user/reviews/" + id, "PUT", JSONObject()
            .put("environmentScore", environment).put("facilityScore", facility)
            .put("content", content).put("expectedVersion", version).toString())))

    override suspend fun withdrawReview(id: Long, version: Long) =
        parseReview(JSONObject(client.api("/user/reviews/" + id + "/withdraw", "POST",
            JSONObject().put("expectedVersion", version).toString())))
}

private fun JSONObject.nullableDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else getDouble(key).takeIf { it.isFinite() }
private fun JSONObject.nullableInt(key: String): Int? =
    if (isNull(key) || !has(key)) null else getInt(key)
private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else getString(key)
private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

internal fun parseCard(value: JSONObject): SpaceCard {
    val space = value.getJSONObject("space")
    val status = value.getJSONObject("status")
    val summary = value.optJSONObject("reviewSummary")
    return SpaceCard(
        space = Space(space.getLong("id"), space.getString("name"), space.getString("type"),
            space.getString("address"), space.getInt("capacity"), space.getString("openTime"),
            space.getString("closeTime"), space.getString("openDays"), space.getBoolean("allDay"),
            space.optString("facilities").split(',').filter { it.isNotBlank() },
            space.optString("description"), space.getBoolean("enabled")),
        status = SpaceStatus(status.getInt("capacity"), status.nullableInt("currentPeople"),
            status.nullableDouble("occupancyRate"), status.nullableDouble("noiseDb"),
            status.nullableDouble("typicalNoiseDb"), status.nullableInt("quietLevel"),
            status.getString("peopleState"), status.getString("noiseState"), status.getString("deviceState"),
            status.nullableString("peopleUpdatedAt"), status.nullableString("noiseUpdatedAt"),
            status.getString("calculatedAt"), status.getInt("validitySeconds"), status.getString("source")),
        distance = value.getDouble("distanceMeters"), score = value.getDouble("score"),
        openNow = value.getBoolean("openNow"), reasons = value.getJSONArray("reasons").strings(),
        summary = ReviewSummary(summary?.optInt("count") ?: 0, summary?.nullableDouble("environmentAverage"),
            summary?.nullableDouble("facilityAverage")),
    )
}

internal fun parseReview(value: JSONObject) = MyReview(
    value.getLong("id"), value.getLong("spaceId"), value.getString("spaceName"),
    value.getBoolean("spaceEnabled"), value.getInt("environmentScore"), value.getInt("facilityScore"),
    value.optString("content"), value.getString("status"), value.nullableString("reviewReason"),
    value.getString("updatedAt"), value.getLong("version"),
)

private fun <T> parsePage(value: JSONObject, item: (JSONObject) -> T): CampusPage<T> {
    val items = value.getJSONArray("items")
    return CampusPage(List(items.length()) { item(items.getJSONObject(it)) }, value.getInt("total"),
        value.getInt("page"), value.getInt("pageSize"), value.optJSONArray("warnings")?.strings().orEmpty())
}
