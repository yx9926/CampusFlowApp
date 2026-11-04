package com.campus.admin

import com.campus.spaces.facilityNames
import com.campus.spaces.spaceTypes

object AssetRules {
    val enabledChoices = mapOf("true" to "启用", "false" to "停用")
    val spaceDefaults =
        mapOf(
            "name" to "",
            "type" to "STUDY_ROOM",
            "address" to "",
            "latitude" to "",
            "longitude" to "",
            "capacity" to "30",
            "openTime" to "08:00",
            "closeTime" to "22:00",
            "openDays" to "1,2,3,4,5,6,7",
            "allDay" to "false",
            "facilities" to "SEAT",
            "description" to "",
            "enabled" to "true",
            "coordinateSystem" to "UNKNOWN",
            "coordinateConfirmed" to "false",
        )
    val hardwareDefaults =
        mapOf("deviceId" to "", "name" to "", "spaceId" to "", "enabled" to "true")

    val spaceFields =
        listOf(
            AdminField("name", "空间名称", limit = 100),
            AdminField("address", "地址", limit = 200),
            AdminField("type", "空间类型", spaceTypes),
            AdminField("latitude", "纬度 · GCJ-02", numeric = true),
            AdminField("longitude", "经度 · GCJ-02", numeric = true),
            AdminField(
                "coordinateConfirmed",
                "手动坐标确认",
                mapOf("false" to "尚未确认", "true" to "已确认上述位置为 GCJ-02"),
            ),
            AdminField("capacity", "空间容量", numeric = true),
            AdminField("openTime", "开放时间（HH:mm）"),
            AdminField("closeTime", "关闭时间（HH:mm）"),
            AdminField("allDay", "全天开放", mapOf("true" to "全天开放", "false" to "按时段开放")),
            AdminField("openDays", "开放日", (1..7).associate { "$it" to "周$it" }, multiSelect = true),
            AdminField("facilities", "设施", facilityNames, multiSelect = true),
            AdminField("description", "空间说明", multiline = true, limit = 1000),
            AdminField("enabled", "空间状态", enabledChoices),
        )

    fun hardwareFields(editor: AdminEditor, result: AdminResult): List<AdminField> {
        val editing = editor.action == AdminAction.Hardware
        val available =
            result.spaceOptions.filter { (id, _) ->
                result.boundSpaces[id] == null || result.boundSpaces[id] == editor.row?.id
            }
        return listOf(
            AdminField(
                "deviceId",
                "物理设备 ID",
                if (editing) mapOf(editor.row!!.value("deviceId") to editor.row.value("deviceId"))
                else result.candidates,
                readOnly = editing || result.candidates.isEmpty(),
            ),
            AdminField("name", "设备名称", limit = 100),
            AdminField(
                "spaceId",
                "绑定空间",
                mapOf("" to "未绑定") + available,
                readOnly = editing && !editor.row?.value("lastReceivedAt").isNullOrEmpty(),
            ),
            AdminField("enabled", "设备状态", enabledChoices),
        )
    }

    fun space(values: Map<String, String>): String? {
        fun value(key: String) = values[key].orEmpty()
        val latitude = value("latitude").toDoubleOrNull()
        val longitude = value("longitude").toDoubleOrNull()
        val time = Regex("([01]\\d|2[0-3]):[0-5]\\d")
        val days = value("openDays").split(',').filter { it.isNotEmpty() }
        return when {
            value("name").isBlank() || value("name").length > 100 -> "请输入 1—100 字的空间名称"
            value("address").isBlank() || value("address").length > 200 -> "请输入 1—200 字的地址"
            value("type") !in spaceTypes -> "请选择空间类型"
            latitude == null ||
                !latitude.isFinite() ||
                latitude !in -90.0..90.0 ||
                longitude == null ||
                !longitude.isFinite() ||
                longitude !in -180.0..180.0 -> "请填写范围内的有效坐标"
            value("coordinateConfirmed") != "true" -> "请明确确认手动坐标为 GCJ-02"
            (value("capacity").toIntOrNull() ?: 0) !in 1..10000 -> "容量须为 1—10000 的整数"
            !time.matches(value("openTime")) || !time.matches(value("closeTime")) -> "时间格式须为 HH:mm"
            value("allDay") != "true" && value("openTime") >= value("closeTime") ->
                "关闭时间必须晚于开放时间，不支持跨午夜时段"
            days.isEmpty() || days.any { (it.toIntOrNull() ?: 0) !in 1..7 } -> "请至少选择一个开放日"
            value("facilities")
                .split(',')
                .filter { it.isNotEmpty() }
                .any { it !in facilityNames } -> "设施类型无效"
            value("description").length > 1000 -> "空间说明最多 1000 字"
            value("enabled") !in enabledChoices || value("allDay") !in enabledChoices ->
                "请选择有效的启停与开放状态"
            else -> null
        }
    }

    fun hardware(values: Map<String, String>): String? =
        when {
            !Regex("[A-Za-z0-9_-]{1,64}").matches(values["deviceId"].orEmpty()) -> "请从已发现列表选择有效设备"
            values["name"].orEmpty().length > 100 -> "设备名称最多 100 字"
            !values["spaceId"].isNullOrEmpty() && (values["spaceId"]?.toLongOrNull() ?: 0) < 1 ->
                "空间编号必须为正整数"
            values["enabled"] !in enabledChoices -> "请选择设备状态"
            else -> null
        }
}
