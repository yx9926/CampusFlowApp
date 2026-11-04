@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.campus.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.campus.spaces.*
import com.campus.ui.theme.*

@Composable
internal fun AdminFilterDialog(
    page: AdminPage,
    initial: Map<String, String>,
    onClose: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    var values by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AdminDialog(onClose) {
        Text("筛选 · ${page.title}", style = MaterialTheme.typography.titleLarge)
        filterFields(page)
            .filter { it.key != "valid" || values["kind"] != "VISIT" }
            .forEach { field ->
                AdminInput(field, values[field.key].orEmpty(), true) {
                    values = values + (field.key to it)
                    error = null
                }
            }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            CampusSecondaryButton(
                shape = MaterialTheme.shapes.small,
                onClick = onClose,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            CampusPrimaryButton(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    error = AdminRules.filterError(values)
                    if (error == null) onApply(values)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("应用筛选")
            }
        }
    }
}

@Composable
internal fun AdminEditorDialog(model: AdminViewModel, state: AdminEditor) {
    val focus = LocalFocusManager.current
    val staleConfig =
        state.action == AdminAction.Config &&
            (model.list.result.rows.firstOrNull()?.version ?: 0) > (state.row?.version ?: 0)
    val readOnly =
        state.action == AdminAction.Decision && state.row?.status !in listOf("PENDING", "APPROVED")
    AdminDialog(onClose = { model.closeEditor() }, busy = state.busy) {
        var showHelp by remember(state.action) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                state.action.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { showHelp = !showHelp }) {
                CampusIcon(CampusSymbol.Info, description = "操作说明")
            }
        }
        state.row?.let { row ->
            if (state.action != AdminAction.Config) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.action in listOf(AdminAction.Decision, AdminAction.Validity)) {
                row.lines.forEach { (label, value) ->
                    Text(
                        "$label：$value",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (label == "内容") 40 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(showHelp) {
            SupportingText(actionDescription(state), maxLines = 8)
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.conflict || staleConfig) {
            StatusChip("数据已更新，请重新加载", warning = true)
            CampusSecondaryButton(
                shape = MaterialTheme.shapes.small,
                onClick = model::reloadEditor,
                enabled = !state.busy,
            ) {
                Text(
                    if (
                        state.action in
                            listOf(AdminAction.Account, AdminAction.Config, AdminAction.Hardware)
                    )
                        "重新加载（替换输入）"
                    else "返回列表重新加载",
                )
            }
        }
        if (state.action == AdminAction.CreateHardware) {
            SupportingText(
                if (model.list.result.candidates.isEmpty()) "暂无新设备，请确认设备已联网。" else "请选择设备与对应空间",
            )
            CampusSecondaryButton(
                shape = MaterialTheme.shapes.small,
                onClick = model::refresh,
                enabled = !state.busy && !model.list.loading,
            ) {
                Text("刷新发现列表")
            }
        }
        if (!readOnly) {
            AdminRules.fields(state, model.list.result).forEach { field ->
                AdminInput(
                    field,
                    state.values[field.key].orEmpty(),
                    !state.busy && !state.conflict && !field.readOnly,
                ) {
                    model.updateField(field.key, it)
                }
            }
            if (state.action == AdminAction.Config) {
                val sum =
                    listOf("distanceWeight", "quietWeight", "freeWeight", "facilityWeight").sumOf {
                        state.values[it]?.toDoubleOrNull() ?: 0.0
                    }
                SupportingText(
                    "当前权重之和：" + java.lang.String.format(java.util.Locale.CHINA, "%.4f", sum),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
        ) {
            CampusSecondaryButton(
                shape = MaterialTheme.shapes.small,
                onClick = { model.closeEditor() },
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("关闭")
            }
            if (!readOnly)
                CampusPrimaryButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = {
                        focus.clearFocus()
                        model.save()
                    },
                    enabled = !state.busy && !state.conflict && !staleConfig,
                    modifier = Modifier.weight(1f).testTag("admin_submit"),
                ) {
                    Text(if (state.busy) "保存中…" else "保存")
                }
        }
    }
    if (state.discard)
        AlertDialog(
            onDismissRequest = model::keepEditing,
            title = { Text("放弃未提交的修改？") },
            text = { Text("当前输入尚未保存，关闭后会丢失。") },
            confirmButton = {
                TextButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = { model.closeEditor(force = true) },
                ) {
                    Text("放弃修改")
                }
            },
            dismissButton = {
                TextButton(shape = MaterialTheme.shapes.small, onClick = model::keepEditing) {
                    Text("继续编辑")
                }
            },
        )
}

@Composable
private fun AdminDialog(
    onClose: () -> Unit,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val locked by rememberUpdatedState(busy)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                if (locked) false
                else if (target == SheetValue.Hidden) {
                    onClose()
                    false
                } else true
            },
        )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = CampusComponents.DialogMaxHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(CampusSpacing.ExtraLarge),
                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
                content = content,
            )
        }
    }
}

@Composable
private fun AdminInput(
    field: AdminField,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    if (field.multiSelect) {
        val selected = value.split(',').filter { it.isNotEmpty() }.toSet()
        Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
            Text(field.label, style = MaterialTheme.typography.labelLarge)
            field.choices.entries.toList().chunked(2).forEach { options ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                ) {
                    options.forEach { (key, label) ->
                        FilterChip(
                            selected = key in selected,
                            enabled = enabled,
                            onClick = {
                                onChange(
                                    (if (key in selected) selected - key else selected + key)
                                        .sorted()
                                        .joinToString(","),
                                )
                            },
                            label = { Text(label) },
                            modifier =
                                Modifier.weight(1f)
                                    .heightIn(min = CampusSpacing.MinimumTouchTarget)
                                    .testTag("admin_field_${field.key}_$key"),
                        )
                    }
                }
            }
        }
    } else if (field.choices.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
            Text(field.label, style = MaterialTheme.typography.labelLarge)
            Box {
                CampusSecondaryButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = CampusSpacing.MinimumTouchTarget)
                            .testTag("admin_field_${field.key}"),
                ) {
                    Text(
                        (field.choices[value] ?: "请选择") + " ▾",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.choices.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onChange(key)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    } else {
        var visible by remember { mutableStateOf(false) }
        CampusTextInput(
            value = value,
            onValueChange = { onChange(it.take(field.limit)) },
            enabled = enabled,
            label = { Text(field.label) },
            modifier = Modifier.fillMaxWidth().testTag("admin_field_${field.key}"),
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
            maxLines = if (field.multiline) 8 else 1,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        when {
                            field.password -> KeyboardType.Password
                            field.numeric -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                ),
            visualTransformation =
                if (field.password && !visible) PasswordVisualTransformation()
                else VisualTransformation.None,
            trailingIcon =
                if (field.password) {
                    {
                        TextButton(
                            shape = MaterialTheme.shapes.small,
                            onClick = { visible = !visible },
                            enabled = enabled,
                        ) {
                            Text(if (visible) "隐藏" else "显示")
                        }
                    }
                } else null,
            supportingText =
                if (field.multiline) {
                    { Text("${value.length} / ${field.limit}") }
                } else null,
        )
    }
}

private fun actionDescription(state: AdminEditor): String =
    when (state.action) {
        AdminAction.Space,
        AdminAction.CreateSpace -> "手动位置仅接受 GCJ-02 坐标，请确认真实学习空间位置。容量不能低于当前在场人数；停用后不再出现在用户列表。"
        AdminAction.Hardware,
        AdminAction.CreateHardware ->
            "每个空间最多绑定一台设备（包括停用设备）。已上传记录的设备不能换绑或解绑；停用拒绝新上传并保留历史。物理设备 ID 在录入后不可修改。"
        AdminAction.Decision ->
            if (state.row?.status !in listOf("PENDING", "APPROVED")) "该状态只可查看；用户修改后才会重新进入待审核。"
            else "通过后公开并计入均分；驳回或撤销通过须填写原因。撤销后将从公开评价和均分中移除。"
        AdminAction.Validity ->
            if (state.row?.value("valid") == "true") "确认将这条记录标记无效？汇总计算将排除它；请填写原因。"
            else "确认恢复这条记录？汇总将重新计算，采样时间保持原值。"
        AdminAction.Account,
        AdminAction.CreateAccount -> "至少保留一个启用的服务器管理员。角色或状态变化会使旧会话失效；初始密码仅在创建时设置。"
        AdminAction.Config -> "噪声窗口至少为有效期；四项权重之和必须为 1。"
    }
