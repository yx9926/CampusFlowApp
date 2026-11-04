package com.campus.admin

import com.campus.auth.AuthValidation
import com.campus.spaces.parseInstant
import java.net.URLEncoder
import kotlin.math.abs

enum class AdminPage(val title: String, val description: String, val role: String) {
    Spaces("空间管理", "维护空间资料、设施与开放时间。", "DATA_ADMIN"),
    Hardware("设备管理", "录入已发现硬件并绑定学习空间。", "DATA_ADMIN"),
    Moderation("评价审核", "审核通过后公开评价并计入均分。", "DATA_ADMIN"),
    Inspection("数据检查", "检查硬件人数、噪声和进出事件，标记异常记录。", "DATA_ADMIN"),
    Overview("服务器概况", "查看真实服务运行状态与硬件在线情况。", "SERVER_ADMIN"),
    Accounts("账号管理", "创建账号、调整角色和启停状态。", "SERVER_ADMIN"),
    Config("运行配置", "调整指标有效期、统计窗口与推荐权重。", "SERVER_ADMIN"),
    Logs("操作日志", "只读查询业务操作记录，日期采用北京时间。", "SERVER_ADMIN");

    companion object {
        fun forRole(role: String) = entries.filter { it.role == role }
    }
}

val adminRoleNames =
    linkedMapOf(
        "USER" to "普通用户",
        "DATA_ADMIN" to "数据管理员",
        "SERVER_ADMIN" to "服务器管理员",
    )
val recordLabels = linkedMapOf("PEOPLE" to "人数快照", "NOISE" to "噪声读数", "VISIT" to "进出事件")

data class AdminRow(
    val id: Long,
    val title: String,
    val status: String = "",
    val lines: List<Pair<String, String>> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val version: Long = 0,
) {
    fun value(key: String) = values[key].orEmpty()
}

data class AdminResult(
    val rows: List<AdminRow> = emptyList(),
    val total: Int = 0,
    val calculatedAt: String = "",
    val spaceOptions: Map<String, String> = emptyMap(),
    val candidates: Map<String, String> = emptyMap(),
    val boundSpaces: Map<String, Long> = emptyMap(),
)

data class AdminListState(
    val result: AdminResult = AdminResult(),
    val page: Int = 1,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

enum class AdminAction(val title: String) {
    Space("编辑空间"),
    CreateSpace("新增空间"),
    Hardware("编辑设备"),
    CreateHardware("录入设备"),
    Decision("评价内容与审核"),
    Validity("调整记录有效标记"),
    Account("编辑账号"),
    CreateAccount("创建账号"),
    Config("修改运行配置"),
}

data class AdminField(
    val key: String,
    val label: String,
    val choices: Map<String, String> = emptyMap(),
    val multiline: Boolean = false,
    val numeric: Boolean = false,
    val password: Boolean = false,
    val limit: Int = 64,
    val multiSelect: Boolean = false,
    val readOnly: Boolean = false,
)

data class AdminEditor(
    val action: AdminAction,
    val row: AdminRow?,
    val initial: Map<String, String>,
    val values: Map<String, String>,
    val busy: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val discard: Boolean = false,
) {
    val dirty
        get() = initial != values
}

object AdminRules {
    val configFields =
        listOf(
            AdminField("validitySeconds", "数据有效期 · 秒", numeric = true),
            AdminField("noiseWindowSeconds", "噪声窗口 · 秒", numeric = true),
            AdminField("distanceWeight", "距离权重", numeric = true),
            AdminField("quietWeight", "安静权重", numeric = true),
            AdminField("freeWeight", "空闲权重", numeric = true),
            AdminField("facilityWeight", "设施权重", numeric = true),
        )

    fun fields(editor: AdminEditor, result: AdminResult = AdminResult()): List<AdminField> =
        when (editor.action) {
            AdminAction.Space,
            AdminAction.CreateSpace -> AssetRules.spaceFields
            AdminAction.Hardware,
            AdminAction.CreateHardware -> AssetRules.hardwareFields(editor, result)
            AdminAction.Decision ->
                listOf(
                    AdminField(
                        "action",
                        "审核动作",
                        if (editor.row?.status == "APPROVED") mapOf("REVOKE" to "撤销通过并驳回")
                        else mapOf("APPROVE" to "通过", "REJECT" to "驳回"),
                    ),
                    AdminField("reason", "原因（驳回或撤销必填）", multiline = true, limit = 500),
                )
            AdminAction.Validity ->
                listOf(AdminField("reason", "操作原因（必填）", multiline = true, limit = 500))
            AdminAction.Account,
            AdminAction.CreateAccount ->
                buildList {
                    if (editor.action == AdminAction.CreateAccount) {
                        add(AdminField("username", "用户名"))
                        add(AdminField("password", "初始密码", password = true, limit = 100))
                    }
                    add(AdminField("role", "角色", adminRoleNames))
                    add(AdminField("enabled", "账号状态", mapOf("true" to "启用", "false" to "停用")))
                }
            AdminAction.Config -> configFields
        }

    fun validate(action: AdminAction, values: Map<String, String>): String? {
        fun value(key: String) = values[key].orEmpty()
        fun integer(key: String, min: Long, max: Long) =
            value(key).toLongOrNull()?.let { it in min..max } == true
        if (value("reason").length > 500) return "原因最多 500 字"
        return when (action) {
            AdminAction.Space,
            AdminAction.CreateSpace -> AssetRules.space(values)
            AdminAction.Hardware,
            AdminAction.CreateHardware -> AssetRules.hardware(values)
            AdminAction.Decision ->
                when {
                    value("action") !in listOf("APPROVE", "REJECT", "REVOKE") -> "请选择有效的审核动作"
                    value("action") != "APPROVE" && value("reason").isBlank() -> "驳回或撤销通过必须填写原因"
                    else -> null
                }
            AdminAction.Validity -> if (value("reason").isBlank()) "请填写操作原因" else null
            AdminAction.CreateAccount ->
                AuthValidation.username(value("username"))
                    ?: AuthValidation.password(value("password"))
                    ?: validate(AdminAction.Account, values)
            AdminAction.Account ->
                when {
                    value("role") !in adminRoleNames -> "请选择有效角色"
                    value("enabled") !in listOf("true", "false") -> "请选择账号状态"
                    else -> null
                }
            AdminAction.Config -> {
                val weights =
                    listOf("distanceWeight", "quietWeight", "freeWeight", "facilityWeight").map {
                        value(it).toDoubleOrNull() ?: Double.NaN
                    }
                when {
                    !integer("validitySeconds", 5, 300) || !integer("noiseWindowSeconds", 5, 600) ->
                        "有效期需为 5—300 秒，噪声窗口需为 5—600 秒的整数"
                    value("noiseWindowSeconds").toInt() < value("validitySeconds").toInt() ->
                        "噪声窗口至少为状态有效期"
                    weights.any { !it.isFinite() || it !in 0.0..1.0 } ||
                        abs(weights.sum() - 1) > 0.000001 -> "各项权重应为 0—1，四项之和必须为 1"
                    else -> null
                }
            }
        }
    }

    fun filterError(filters: Map<String, String>): String? {
        val from = filters["updatedFrom"].orEmpty()
        val to = filters["updatedTo"].orEmpty()
        val dates = listOf(from, to).filter { it.isNotEmpty() }
        if (
            dates.any {
                !Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) ||
                    parseInstant(it + "T00:00:00Z") == null
            }
        )
            return "日期请使用 YYYY-MM-DD，并填写有效日期"
        if (from.isNotEmpty() && to.isNotEmpty() && from > to) return "开始日期不能晚于结束日期"
        val id = filters["spaceId"].orEmpty()
        if (id.isNotEmpty() && (id.toLongOrNull() ?: 0) < 1) return "空间编号必须为正整数"
        return null
    }

    fun query(filters: Map<String, String>, page: Int): String =
        (filters.filterValues { it.isNotBlank() } +
                mapOf("page" to page.toString(), "pageSize" to "20"))
            .entries
            .joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
}
