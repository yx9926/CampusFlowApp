package com.campus.spaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.campus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(model: CampusViewModel, canReview: Boolean) {
    val state = model.detail
    var timestampsExpanded by remember(model.selectedId) { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = state.loading && state.card != null,
        onRefresh = {
            model.refreshDetail()
            model.refreshPublic()
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("detail_list"),
            contentPadding = PaddingValues(CampusSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraLarge),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalIconButton(onClick = model::backToList) {
                        CampusIcon(CampusSymbol.Back, description = "返回空间列表")
                    }
                    Text(
                        "空间详情",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { error ->
                item { ErrorPanel(error, model::refreshDetail, !state.loading) }
            }
            if (state.loading && state.card == null) item { LoadingCards() }
            state.card?.let { card ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(CampusSpacing.ExtraLarge),
                            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                CampusIcon(
                                    CampusSymbol.Building,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                StatusChip(
                                    if (card.openNow) "开放中" else "休息中",
                                    warning = !card.openNow,
                                )
                            }
                            Text(
                                card.space.name,
                                style = MaterialTheme.typography.headlineLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${spaceTypes[card.space.type] ?: card.space.type} · ${card.distance.toInt()} m",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(CampusSpacing.Medium)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("此刻的空间", style = MaterialTheme.typography.titleLarge)
                            if (card.status.deviceState != "ONLINE")
                                StatusChip("设备离线", warning = true)
                        }
                        StatusMetrics(card.status, model.elapsed(state.receivedAt))
                        TextButton(onClick = { timestampsExpanded = !timestampsExpanded }) {
                            Text(if (timestampsExpanded) "收起更新时间" else "更新时间")
                        }
                        AnimatedVisibility(timestampsExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                            ) {
                                SupportingText("人数 · ${displayTime(card.status.peopleUpdatedAt)}")
                                SupportingText("噪声 · ${displayTime(card.status.noiseUpdatedAt)}")
                            }
                        }
                    }
                }
                item {
                    CampusPanel {
                        DetailInfo(CampusSymbol.Pin, "位置", card.space.address)
                        DetailInfo(
                            CampusSymbol.Clock,
                            "开放时间",
                            if (card.space.allDay) "全天开放"
                            else "${card.space.openTime.take(5)} — ${card.space.closeTime.take(5)}",
                        )
                        DetailInfo(CampusSymbol.People, "可容纳", "${card.space.capacity} 人")
                        SupportingText(
                            if (card.space.openDays.split(',').size == 7) "每天开放"
                            else "开放日 · 周 ${card.space.openDays}",
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                        ) {
                            card.space.facilities
                                .mapNotNull { facilityNames[it] }
                                .forEach { StatusChip(it) }
                        }
                        if (card.space.description.isNotBlank())
                            Text(
                                card.space.description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                    }
                }
                if (canReview)
                    item {
                        CampusPrimaryButton(
                            onClick = { model.openEditor(card.space.id, card.space.name) },
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = CampusComponents.ControlHeight)
                                    .testTag("open_review"),
                        ) {
                            CampusIcon(CampusSymbol.Review)
                            Spacer(Modifier.width(CampusSpacing.Small))
                            Text("写评价")
                        }
                    }
                item {
                    ScreenHeading("空间评价") {
                        if (card.summary.count > 0) StatusChip("${card.summary.count} 条")
                    }
                }
            }
            val public = model.published
            public.error?.let { error ->
                item { ErrorPanel(error, { model.refreshPublic() }, !public.loading) }
            }
            if (public.loading && public.items.isEmpty()) item { LoadingCards() }
            else if (state.card != null && public.error == null && public.items.isEmpty())
                item {
                    EmptyPanel("还没有评价", "留下你的第一份体验。", "刷新评价", { model.refreshPublic() })
                }
            items(public.items, key = { "review_${it.id}" }) { review ->
                CampusPanel {
                    Text(
                        review.author,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RatingSummary(review.environment, review.facility)
                    if (review.content.isNotBlank())
                        Text(
                            review.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    SupportingText(displayTime(review.updatedAt))
                }
            }
            if (public.items.isNotEmpty())
                item {
                    PagingFooter(
                        public.loading,
                        public.items.size < public.total,
                        { model.refreshPublic(append = true) },
                    )
                }
        }
    }
}

@Composable
private fun DetailInfo(symbol: CampusSymbol, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
    ) {
        CampusIcon(symbol, tint = MaterialTheme.colorScheme.primary)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
        ) {
            SupportingText(label, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
