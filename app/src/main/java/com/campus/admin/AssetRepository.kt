package com.campus.admin

import com.campus.auth.HttpAuthRepository
import com.campus.spaces.displayTime
import com.campus.spaces.spaceTypes
import com.campus.spaces.facilityNames
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun loadAssets(client: HttpAuthRepository, page: AdminPage, filters: Map<String, String>, number: Int): AdminResult {
    val spaces = JSONArray(client.api("/data/spaces")).rows(::spaceRow)
    if (page == AdminPage.Spaces) return assetPage(spaces, filters, number)
    val hardware = JSONArray(client.api("/data/hardware-devices")).rows(::hardwareRow)
    val candidates = JSONArray(client.api("/data/hardware-devices/candidates"))
    val options = (0 until candidates.length()).associate { index ->
        val json = candidates.getJSONObject(index)
        val id = json.getString("deviceId")
        id to (id + if (json.getBoolean("online")) " · 最近在线" else " · 曾发现，当前离线")
    }
    return assetPage(hardware, filters, number).copy(
        spaceOptions = spaces.associate { it.id.toString() to "#${it.id} ${it.title} · ${it.value("address")}" },
        candidates = options,
        boundSpaces = hardware.filter { it.value("spaceId").isNotEmpty() }.associate { it.value("spaceId") to it.id },
    )
}

internal suspend fun loadHardware(client: HttpAuthRepository, id: Long): AdminRow =
    JSONArray(client.api("/data/hardware-devices")).rows(::hardwareRow).find { it.id == id }
        ?: throw com.campus.auth.ApiFailure(404, "设备不存在")

internal suspend fun saveAsset(client: HttpAuthRepository, editor: AdminEditor) {
    val values = editor.values
    val body = JSONObject()
    val space = editor.action in listOf(AdminAction.Space, AdminAction.CreateSpace)
    val creating = editor.action in listOf(AdminAction.CreateSpace, AdminAction.CreateHardware)
    if (space) {
        listOf("name", "type", "address", "openTime", "closeTime", "description").forEach { body.put(it, values[it].orEmpty()) }
        body.put("latitude", values.getValue("latitude").toDouble()).put("longitude", values.getValue("longitude").toDouble())
            .put("capacity", values.getValue("capacity").toInt()).put("allDay", values["allDay"] == "true")
            .put("enabled", values["enabled"] == "true").put("coordinateSystem", "GCJ02")
            .put("openDays", JSONArray(values.getValue("openDays").split(',').map(String::toInt)))
            .put("facilities", JSONArray(values["facilities"].orEmpty().split(',').filter(String::isNotEmpty)))
    } else {
        body.put("deviceId", values["deviceId"]).put("name", values["name"].orEmpty().trim().ifEmpty { "签到设备 ${values["deviceId"]}" })
            .put("enabled", values["enabled"] == "true").put("spaceId", values["spaceId"]?.toLongOrNull() ?: JSONObject.NULL)
        if (!creating) body.put("version", editor.row?.version)
    }
    val root = if (space) "/data/spaces" else "/data/hardware-devices"
    client.api(root + if (creating) "" else "/${editor.row?.id}", if (creating) "POST" else "PUT", body.toString())
}

internal fun assetPage(rows: List<AdminRow>, filters: Map<String, String>, number: Int): AdminResult {
    val result = rows.filter {
        (filters["name"].isNullOrEmpty() || it.title.contains(filters.getValue("name"), ignoreCase = true) ||
            it.value("deviceId").contains(filters.getValue("name"), ignoreCase = true)) &&
            (filters["enabled"].isNullOrEmpty() || filters["enabled"] == it.value("enabled")) &&
            (filters["type"].isNullOrEmpty() || filters["type"] == it.value("type"))
    }
    return AdminResult(result.drop((number - 1) * 20).take(20), result.size)
}

private fun JSONArray.rows(convert: (JSONObject) -> AdminRow) = (0 until length()).map { convert(getJSONObject(it)) }
private fun JSONObject.value(key: String) = if (isNull(key)) "" else optString(key)

internal fun spaceRow(json: JSONObject): AdminRow {
    val values = (AssetRules.spaceDefaults.keys - "coordinateConfirmed").associateWith { json.value(it) }
    return spaceData(json.getLong("id"), values)
}

internal fun spaceData(id: Long, values: Map<String, String>) = AdminRow(id, values["name"].orEmpty(),
    if (values["enabled"] == "true") "ENABLED" else "DISABLED",
    listOf("编号" to "#$id", "类型" to (spaceTypes[values["type"]] ?: values["type"].orEmpty()),
        "地址" to values["address"].orEmpty(), "坐标系" to if (values["coordinateSystem"] == "GCJ02") "GCJ-02" else "位置待确认",
        "经纬度" to "${values["longitude"]}, ${values["latitude"]}", "容量" to values["capacity"].orEmpty(),
        "开放时间" to if (values["allDay"] == "true") "全天" else "${values["openTime"]}—${values["closeTime"]}",
        "开放日" to values["openDays"].orEmpty().split(',').joinToString(" · ") { "周$it" },
        "设施" to values["facilities"].orEmpty().split(',').mapNotNull { facilityNames[it] }.joinToString(" · ").ifEmpty { "无" },
        "说明" to values["description"].orEmpty().ifEmpty { "无" }), values)

internal fun hardwareRow(json: JSONObject): AdminRow {
    val values = listOf("deviceId", "name", "spaceId", "spaceName", "spaceAddress", "enabled", "lastReceivedAt")
        .associateWith { json.value(it) }
    return hardwareData(json.getLong("id"), values, json.getLong("version"))
}

internal fun hardwareData(id: Long, values: Map<String, String>, version: Long) = AdminRow(id,
    values["name"].orEmpty().ifEmpty { "签到设备 ${values["deviceId"]}" }, if (values["enabled"] == "true") "ENABLED" else "DISABLED",
    listOf("物理设备 ID" to values["deviceId"].orEmpty(), "绑定空间" to values["spaceName"].orEmpty().ifEmpty { "未绑定" },
        "位置" to values["spaceAddress"].orEmpty().ifEmpty { "暂无" },
        "最近接收" to displayTime(values["lastReceivedAt"].orEmpty())), values, version)