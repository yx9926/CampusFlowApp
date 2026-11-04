package com.campus.admin

import com.campus.auth.HttpAuthRepository
import com.campus.spaces.displayTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AdminRepository {
    suspend fun load(page: AdminPage, filters: Map<String, String>, number: Int): AdminResult
    suspend fun mutate(editor: AdminEditor)
    suspend fun account(id: Long): AdminRow
    suspend fun hardware(id: Long): AdminRow
}

class HttpAdminRepository(private val client: HttpAuthRepository) : AdminRepository {
    override suspend fun load(page: AdminPage, filters: Map<String, String>, number: Int): AdminResult = withContext(Dispatchers.Default) {
        if (page in listOf(AdminPage.Spaces, AdminPage.Hardware)) return@withContext loadAssets(client, page, filters, number)
        if (page == AdminPage.Overview) {
            val overview = overviewRow(JSONObject(client.api("/system/overview")))
            val devices = JSONArray(client.api("/system/devices"))
            val rows = listOf(overview) + (0 until devices.length()).map { deviceRow(devices.getJSONObject(it)) }
            return@withContext AdminResult(rows, rows.size)
        }
        if (page == AdminPage.Config) {
            val config = configRow(JSONObject(client.api("/system/config")))
            return@withContext AdminResult(listOf(config), 1)
        }
        val path = when (page) {
            AdminPage.Moderation -> "/data/reviews"
            AdminPage.Inspection -> "/data/records"
            AdminPage.Accounts -> "/system/accounts"
            AdminPage.Logs -> "/system/logs"
            else -> error("页面无效")
        }
        val result = JSONObject(client.api(path + "?" + AdminRules.query(filters, number)))
        val items = result.getJSONArray("items")
        val rows = (0 until items.length()).map { index ->
            val json = items.getJSONObject(index)
            when (page) {
                AdminPage.Moderation -> reviewRow(json)
                AdminPage.Accounts -> accountRow(json)
                AdminPage.Logs -> logRow(json)
                else -> recordRow(json, filters["kind"] ?: "PEOPLE")
            }
        }
        return@withContext AdminResult(rows, result.getInt("total"), result.optString("calculatedAt"))
    }

    override suspend fun account(id: Long) = accountRow(JSONObject(client.api("/system/accounts/$id")))

    override suspend fun hardware(id: Long) = loadHardware(client, id)

    override suspend fun mutate(editor: AdminEditor) {
        if (editor.action in listOf(AdminAction.Space, AdminAction.CreateSpace, AdminAction.Hardware, AdminAction.CreateHardware)) {
            saveAsset(client, editor)
            return
        }
        val row = editor.row
        val values = editor.values
        val body = JSONObject()
        var method = "POST"
        val path = when (editor.action) {
            AdminAction.Space, AdminAction.CreateSpace, AdminAction.Hardware, AdminAction.CreateHardware -> error("资产写入已处理")
            AdminAction.Decision -> {
                body.put("action", values["action"]).put("reason", values["reason"].orEmpty())
                    .put("expectedVersion", requireNotNull(row).version)
                "/data/reviews/${row.id}/decision"
            }
            AdminAction.Validity -> {
                method = "PUT"
                body.put("valid", row?.value("valid") != "true").put("reason", values["reason"].orEmpty().trim())
                "/data/records/${row?.value("kind")}/${row?.id}/validity"
            }
            AdminAction.CreateAccount, AdminAction.Account -> {
                body.put("role", values["role"]).put("enabled", values["enabled"] == "true")
                if (editor.action == AdminAction.CreateAccount) {
                    body.put("username", values["username"]).put("password", values["password"])
                    "/system/accounts"
                } else {
                    method = "PUT"
                    body.put("expectedVersion", requireNotNull(row).version)
                    "/system/accounts/${row.id}"
                }
            }
            AdminAction.Config -> {
                method = "PUT"
                body.put("expectedVersion", requireNotNull(row).version)
                AdminRules.configFields.forEach { field ->
                    body.put(field.key, if (field.key.endsWith("Seconds")) values.getValue(field.key).toInt()
                        else values.getValue(field.key).toDouble())
                }
                "/system/config"
            }

        }
        client.api(path, method, body.toString())
    }
}

private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key)
private fun JSONObject.values(vararg keys: String) = keys.associateWith { text(it) }

internal fun accountRow(json: JSONObject) = AdminRow(
    id = json.getLong("id"), title = json.getString("username"),
    status = if (json.getBoolean("enabled")) "ENABLED" else "DISABLED",
    lines = listOf("角色" to (adminRoleNames[json.getString("role")] ?: json.getString("role")),
        "创建时间" to displayTime(json.text("createdAt"))),
    values = json.values("username", "role", "enabled"), version = json.getLong("version"),
)

internal fun reviewRow(json: JSONObject) = AdminRow(
    id = json.getLong("id"), title = json.getString("spaceName"), status = json.getString("status"),
    lines = listOf("用户" to json.getString("username"),
        "评分" to "环境 ${json.getInt("environmentScore")} / 5 · 设施 ${json.getInt("facilityScore")} / 5",
        "内容" to json.text("content").ifEmpty { "未填写文字说明" },
        "更新时间" to displayTime(json.text("updatedAt")),
        "审核人" to json.text("reviewerName").ifEmpty { "尚未审核" },
        "审核时间" to displayTime(json.text("reviewedAt")),
        "审核原因" to json.text("reviewReason").ifEmpty { "无" }),
    values = json.values("spaceId", "username", "updatedAt"), version = json.getLong("version"),
)

internal fun configRow(json: JSONObject) = AdminRow(
    id = 0, title = "指标与推荐参数", version = json.getLong("version"),
    lines = AdminRules.configFields.map { it.label to json.text(it.key) },
    values = AdminRules.configFields.associate { it.key to json.text(it.key) },
)

internal fun overviewRow(json: JSONObject) = AdminRow(
    id = -1, title = "服务器运行状态", status = "RUNNING",
    lines = listOf("学习空间" to json.text("spaceCount"), "账号总数" to json.text("accountCount"),
        "硬件设备" to json.text("deviceCount"), "在线设备" to json.text("onlineDeviceCount"),
        "数据来源" to "实际硬件",
        "状态有效期" to "${json.text("validitySeconds")} 秒",
        "噪声窗口" to "${json.text("noiseWindowSeconds")} 秒",
        "启动时间" to displayTime(json.text("startedAt")), "系统版本" to json.text("version")),
)

internal fun deviceRow(json: JSONObject) = AdminRow(
    id = json.getLong("id"), title = json.getString("name"), status = json.getString("status"),
    lines = listOf("物理设备 ID" to json.getString("deviceId"),
        "绑定空间" to json.text("spaceName").ifEmpty { "未绑定" },
        "设备状态" to if (json.getBoolean("enabled")) "启用" else "停用",
        "最近接收" to displayTime(json.text("lastReceivedAt"))),
)

internal fun recordRow(json: JSONObject, kind: String) = AdminRow(
    id = json.getLong("id"), title = "空间 #${json.getLong("spaceId")} · 记录 #${json.getLong("id")}",
    status = if (kind == "VISIT") "VISIT" else if (json.getBoolean("valid")) "VALID" else "INVALID",
    lines = buildList {
        add("物理设备 ID" to json.text("deviceId"))
        if (kind == "VISIT") {
            add("事件类型" to when (json.text("eventType")) {
                "CHECK_IN" -> "进入"
                "CHECK_OUT" -> "离开"
                else -> json.text("eventType")
            })
            add("事件编号" to json.text("eventId"))
        }
        add((if (kind == "NOISE") "噪声 dB" else "人数") to
            json.text(when (kind) { "NOISE" -> "noiseDb"; "VISIT" -> "headcount"; else -> "currentPeople" }).ifEmpty { "未知" })
        add("采样时间" to displayTime(json.text("sampledAt")))
        add("异常原因" to json.text("invalidReason").ifEmpty { "无" })
    },
    values = json.values("valid", "spaceId") + mapOf("kind" to kind),
)

internal fun logRow(json: JSONObject) = AdminRow(
    id = json.getLong("id"), title = json.getString("action"), status = json.getString("result"),
    lines = listOf("操作人" to json.getString("actor"), "目标" to json.text("target"),
        "时间" to displayTime(json.text("occurredAt")), "说明" to json.text("reason").ifEmpty { "无" }),
    values = json.values("actor", "action", "occurredAt"),
)
