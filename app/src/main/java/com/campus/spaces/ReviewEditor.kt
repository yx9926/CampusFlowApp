package com.campus.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.campus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditor(model: CampusViewModel) {
    val value = model.editor ?: return
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val locked by rememberUpdatedState(value.busy)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                if (locked) false
                else if (target == SheetValue.Hidden) {
                    model.closeEditor()
                    false
                } else true
            },
        )
    ModalBottomSheet(
        onDismissRequest = { model.closeEditor() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = CampusComponents.DialogMaxHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(CampusSpacing.ExtraLarge),
                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
            ) {
                Text("分享这次体验", style = MaterialTheme.typography.headlineMedium)
                Text(
                    value.spaceName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (value.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                value.review?.let {
                    StatusChip(reviewStatusNames[it.status] ?: it.status, it.status == "PENDING")
                    SupportingText(displayTime(it.updatedAt))
                    it.reason?.let { reason -> SupportingText("最近审核原因：" + reason) }
                }
                if (!value.spaceEnabled) SupportingText("空间已停用，仅可撤回")
                value.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (value.conflict)
                        TextButton(onClick = model::reloadEditor, enabled = !value.busy) {
                            Text("重新加载并替换当前输入")
                        }
                    else if (value.review == null && !value.loading)
                        TextButton(onClick = model::reloadEditor) { Text("重试加载评价") }
                }
                value.notice?.let { Text(it, color = CampusTheme.colors.success) }
                val enabled =
                    !value.loading &&
                        !value.busy &&
                        value.spaceEnabled &&
                        !value.conflict &&
                        value.ready
                RatingRow("环境评分", "environment", value.environment, enabled) {
                    model.edit(environment = it)
                }
                RatingRow("设施评分", "facility", value.facility, enabled) { model.edit(facility = it) }
                CampusTextInput(
                    value.content,
                    { model.edit(content = it) },
                    label = { Text("说说你的感受") },
                    enabled = enabled,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("review_content"),
                    supportingText = { Text("" + value.content.length + " / 500") },
                    shape = MaterialTheme.shapes.small,
                )
                SupportingText("提交后审核公开；修改后重新审核。")
                CampusPrimaryButton(
                    onClick = {
                        focus.clearFocus()
                        model.saveReview()
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("submit_review"),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (value.busy) "正在处理…" else if (value.review != null) "更新评价" else "提交评价")
                }
                if (value.review != null && value.review.status != "WITHDRAWN") {
                    CampusSecondaryButton(
                        onClick = model::askWithdraw,
                        enabled = !value.busy && !value.loading && !value.conflict,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("撤回评价")
                    }
                }
                TextButton(
                    onClick = { model.closeEditor() },
                    enabled = !value.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (value.notice != null) "完成" else "关闭")
                }
            }
        }
    }
    if (value.confirmWithdraw)
        AlertDialog(
            onDismissRequest = model::cancelWithdraw,
            title = { Text("撤回这条评价？") },
            text = { Text("撤回后不再公开或参与均分，之后可以重新提交。") },
            confirmButton = {
                TextButton(onClick = { model.saveReview(withdraw = true) }) { Text("确认撤回") }
            },
            dismissButton = { TextButton(onClick = model::cancelWithdraw) { Text("取消") } },
        )
    if (value.confirmDiscard)
        AlertDialog(
            onDismissRequest = model::cancelDiscard,
            title = { Text("放弃未提交的更改？") },
            text = { Text("当前评分和文字尚未提交。") },
            confirmButton = {
                TextButton(onClick = { model.closeEditor(discard = true) }) { Text("放弃更改") }
            },
            dismissButton = { TextButton(onClick = model::cancelDiscard) { Text("继续编辑") } },
        )
}

@Composable
private fun RatingRow(
    label: String,
    tag: String,
    score: Int,
    enabled: Boolean,
    onScore: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                if (score == 0) "未评分" else "$score / 5",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall)) {
            (1..5).forEach { value ->
                FilledIconToggleButton(
                    checked = score >= value,
                    onCheckedChange = { onScore(value) },
                    enabled = enabled,
                    modifier =
                        Modifier.weight(1f)
                            .heightIn(min = CampusSpacing.MinimumTouchTarget)
                            .testTag(tag + "_" + value),
                ) {
                    CampusIcon(CampusSymbol.Star, description = "$label $value 分")
                }
            }
        }
    }
}
