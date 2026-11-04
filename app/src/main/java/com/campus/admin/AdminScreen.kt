package com.campus.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.campus.spaces.*
import com.campus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(model: AdminViewModel, role: String) {
    val page = model.page
    if (page == null) {
        AdminHome(role, model::open)
        return
    }
    var filtering by remember(page) { mutableStateOf(false) }
    val state = model.list
    PullToRefreshBox(
        isRefreshing = state.loading && state.loaded,
        onRefresh = model::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        key(page) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("admin_list"),
                contentPadding = PaddingValues(CampusSpacing.Page),
                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
            ) {
                item {
                    IconButton(onClick = model::back) {
                        CampusIcon(CampusSymbol.Back, description = "返回工作台")
                    }
                    ScreenHeading(page.title) {
                        IconButton(onClick = model::refresh, enabled = !state.loading) {
                            CampusIcon(CampusSymbol.Refresh, description = "刷新")
                        }
                    }
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                    ) {
                        if (page !in listOf(AdminPage.Config, AdminPage.Overview)) {
                            CampusTonalButton(onClick = { filtering = true }) {
                                CampusIcon(
                                    CampusSymbol.Filter,
                                    Modifier.size(CampusComponents.SmallIcon),
                                )
                                Spacer(Modifier.width(CampusSpacing.Small))
                                Text("筛选")
                            }
                        }
                        val create =
                            when (page) {
                                AdminPage.Spaces ->
                                    Triple(AdminAction.CreateSpace, "新增空间", "create_space")
                                AdminPage.Hardware ->
                                    Triple(AdminAction.CreateHardware, "录入设备", "create_hardware")
                                AdminPage.Accounts ->
                                    Triple(AdminAction.CreateAccount, "创建账号", "create_account")
                                else -> null
                            }
                        create?.let { (action, label, tag) ->
                            CampusPrimaryButton(
                                onClick = { model.openEditor(action) },
                                modifier = Modifier.testTag(tag),
                            ) {
                                CampusIcon(
                                    CampusSymbol.Plus,
                                    Modifier.size(CampusComponents.SmallIcon),
                                )
                                Spacer(Modifier.width(CampusSpacing.Small))
                                Text(label)
                            }
                        }
                    }
                    if (page !in listOf(AdminPage.Config, AdminPage.Overview)) {
                        Spacer(Modifier.height(CampusSpacing.Medium))
                        SupportingText(
                            "${state.result.total} 项" +
                                if (page == AdminPage.Hardware)
                                    " · ${state.result.candidates.size} 台待录入"
                                else "",
                        )
                    }
                    val applied = model.filters.filter { it.value.isNotEmpty() && it.key != "kind" }
                    if (applied.isNotEmpty()) {
                        Spacer(Modifier.height(CampusSpacing.Small))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                            applied.forEach { (key, value) -> StatusChip(filterValue(key, value)) }
                        }
                    }
                }
                model.notice?.let { item { StatusChip(it) } }
                state.error?.let { item { ErrorPanel(it, model::refresh, !state.loading) } }
                if (state.loading && !state.loaded) item { LoadingCards() }
                else if (state.loaded && state.result.rows.isEmpty())
                    item {
                        EmptyPanel("暂无记录", "", "刷新") { model.refresh() }
                    }
                items(state.result.rows, key = { "${page.name}_${it.id}" }) { row ->
                    AdminRowCard(model, page, row)
                }
                if (page !in listOf(AdminPage.Config, AdminPage.Overview) && state.result.total > 0)
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { model.nextPage(-1) },
                                enabled = !state.loading && state.page > 1,
                            ) {
                                Text("上一页")
                            }
                            SupportingText("${state.page}")
                            TextButton(
                                onClick = { model.nextPage(1) },
                                enabled = !state.loading && state.page * 20 < state.result.total,
                                modifier = Modifier.testTag("admin_next"),
                            ) {
                                Text("下一页")
                            }
                        }
                    }
            }
        }
    }
    if (filtering)
        AdminFilterDialog(page, model.filters, onClose = { filtering = false }) {
            model.applyFilters(it)
            filtering = false
        }
    model.editor?.let { AdminEditorDialog(model, it) }
}

@Composable
private fun AdminHome(role: String, onOpen: (AdminPage) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val singleColumn = maxWidth < CampusLayout.TileBreakpoint || largeText
        LazyColumn(
            Modifier.fillMaxSize().testTag("admin_home"),
            contentPadding = PaddingValues(CampusSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
        ) {
            item { ScreenHeading("管理工作台", adminRoleNames[role]) }
            items(AdminPage.forRole(role).chunked(if (singleColumn) 1 else 2)) { entries ->
                Row(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium)) {
                    entries.forEach { entry ->
                        AdminTile(entry, Modifier.weight(1f)) { onOpen(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTile(page: AdminPage, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("admin_" + page.name.lowercase()),
        border = BorderStroke(CampusElevation.Hairline, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CampusElevation.Card),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(min = CampusComponents.TileHeight)
                .padding(CampusSpacing.ExtraLarge),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CampusIcon(
                when (page) {
                    AdminPage.Spaces -> CampusSymbol.Building
                    AdminPage.Hardware -> CampusSymbol.Device
                    AdminPage.Moderation -> CampusSymbol.Review
                    AdminPage.Inspection -> CampusSymbol.Shield
                    AdminPage.Overview -> CampusSymbol.Server
                    AdminPage.Accounts -> CampusSymbol.People
                    AdminPage.Config -> CampusSymbol.Settings
                    AdminPage.Logs -> CampusSymbol.History
                },
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(CampusSpacing.ExtraLarge))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
            ) {
                Text(
                    page.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CampusIcon(CampusSymbol.Arrow, Modifier.size(CampusComponents.SmallIcon))
            }
        }
    }
}

@Composable
private fun AdminRowCard(model: AdminViewModel, page: AdminPage, row: AdminRow) {
    var expanded by remember(row.id, page) { mutableStateOf(false) }
    CampusPanel(Modifier.testTag("admin_row_${row.id}")) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            Text(
                row.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.status.isNotEmpty())
                StatusChip(
                    adminStatusNames[row.status] ?: row.status,
                    warning =
                        row.status in
                            listOf(
                                "PENDING",
                                "PAUSED",
                                "OFFLINE",
                                "INVALID",
                                "DISABLED",
                                "REJECTED",
                            ),
                )
        }
        val overview = page == AdminPage.Overview && row.id == -1L
        if (overview) {
            row.lines.take(4).chunked(2).forEach { stats ->
                Row(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                    stats.forEach { (label, value) ->
                        Surface(
                            Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(
                                Modifier.padding(CampusSpacing.Large),
                                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                            ) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                SupportingText(label, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        val visibleLines =
            if (overview) emptyList()
            else if (page == AdminPage.Config) row.lines else row.lines.take(2)
        visibleLines.forEach { (label, value) -> AdminDataLine(label, value) }
        val details = row.lines.drop(if (overview) 4 else visibleLines.size)
        if (details.isNotEmpty()) {
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Medium)) {
                    details.forEach { (label, value) -> AdminDataLine(label, value) }
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "详细信息") }
        }
        when (page) {
            AdminPage.Spaces ->
                RowAction("编辑空间", "edit_space_${row.id}") {
                    model.openEditor(AdminAction.Space, row)
                }
            AdminPage.Hardware ->
                RowAction("编辑设备", "edit_hardware_${row.id}") {
                    model.openEditor(AdminAction.Hardware, row)
                }
            AdminPage.Moderation ->
                RowAction("查看 / 审核", "moderate_${row.id}") {
                    model.openEditor(AdminAction.Decision, row)
                }
            AdminPage.Inspection ->
                if (row.status != "VISIT")
                    RowAction(
                        if (row.status == "VALID") "标记无效" else "恢复记录",
                        "validity_${row.id}",
                    ) {
                        model.openEditor(AdminAction.Validity, row)
                    }
            AdminPage.Accounts ->
                RowAction("编辑账号", "edit_account_${row.id}") {
                    model.openEditor(AdminAction.Account, row)
                }
            AdminPage.Config ->
                RowAction("修改配置", "edit_config") { model.openEditor(AdminAction.Config, row) }
            AdminPage.Overview,
            AdminPage.Logs -> Unit
        }
    }
}

@Composable
private fun AdminDataLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall)) {
        SupportingText(label, maxLines = 1)
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowAction(text: String, tag: String, onClick: () -> Unit) {
    CampusTonalButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = CampusSpacing.MinimumTouchTarget).testTag(tag),
    ) {
        Text(text)
    }
}

private val adminStatusNames =
    reviewStatusNames +
        mapOf(
            "RUNNING" to "运行中",
            "PAUSED" to "已暂停",
            "ONLINE" to "在线",
            "OFFLINE" to "离线",
            "ENABLED" to "启用",
            "DISABLED" to "停用",
            "VALID" to "有效",
            "INVALID" to "无效",
            "VISIT" to "进出事件",
            "SUCCESS" to "成功",
        )

private fun filterLabel(key: String) =
    mapOf(
        "name" to "名称",
        "type" to "类型",
        "username" to "用户名",
        "role" to "角色",
        "enabled" to "账号状态",
        "spaceId" to "空间编号",
        "status" to "状态",
        "kind" to "类型",
        "valid" to "有效标记",
        "actor" to "操作人",
        "action" to "动作",
        "updatedFrom" to "日期从",
        "updatedTo" to "日期至",
    )[key] ?: key

private fun filterValue(key: String, value: String) =
    when (key) {
        "type" -> spaceTypes[value] ?: value
        "role" -> adminRoleNames[value] ?: value
        "status" -> reviewStatusNames[value] ?: value
        "kind" -> recordLabels[value] ?: value
        "enabled" -> if (value == "true") "启用" else "停用"
        "valid" -> if (value == "true") "有效" else "无效"
        else -> value
    }

internal fun filterFields(page: AdminPage): List<AdminField> =
    when (page) {
        AdminPage.Spaces ->
            listOf(
                AdminField("name", "空间名称"),
                AdminField("type", "类型", mapOf("" to "全部类型") + spaceTypes),
                AdminField("enabled", "状态", mapOf("" to "全部状态") + AssetRules.enabledChoices),
            )
        AdminPage.Hardware ->
            listOf(
                AdminField("name", "名称或设备 ID"),
                AdminField("enabled", "状态", mapOf("" to "全部状态") + AssetRules.enabledChoices),
            )
        AdminPage.Moderation ->
            listOf(
                AdminField("spaceId", "空间编号", numeric = true),
                AdminField("username", "用户名（精确匹配）"),
                AdminField("status", "状态", mapOf("" to "全部状态") + reviewStatusNames),
                AdminField("updatedFrom", "更新日期从（YYYY-MM-DD）"),
                AdminField("updatedTo", "至（YYYY-MM-DD）"),
            )
        AdminPage.Accounts ->
            listOf(
                AdminField("username", "用户名（包含匹配）"),
                AdminField("role", "角色", mapOf("" to "全部角色") + adminRoleNames),
                AdminField("enabled", "状态", mapOf("" to "全部状态", "true" to "启用", "false" to "停用")),
            )
        AdminPage.Inspection ->
            listOf(
                AdminField("kind", "记录类型", recordLabels),
                AdminField("spaceId", "空间编号", numeric = true),
                AdminField("valid", "有效标记", mapOf("" to "全部记录", "true" to "有效", "false" to "无效")),
            )
        AdminPage.Logs ->
            listOf(
                AdminField("actor", "操作人（精确匹配）"),
                AdminField("action", "动作（如 ACCOUNT_CREATE）"),
                AdminField("updatedFrom", "日期从（YYYY-MM-DD）"),
                AdminField("updatedTo", "至（YYYY-MM-DD）"),
            )
        else -> emptyList()
    }
