package com.campus.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.campus.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(model: CampusViewModel, listState: LazyListState) {
    val state = model.reviews
    LaunchedEffect(listState, model) {
        snapshotFlow {
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, count) ->
                if (
                    count > 0 &&
                        last >= count - 2 &&
                        model.reviews.error == null &&
                        model.reviews.items.isNotEmpty()
                )
                    model.refreshReviews(append = true)
            }
    }
    PullToRefreshBox(
        isRefreshing = state.loading && state.items.isNotEmpty(),
        onRefresh = { model.refreshReviews(keepPages = true) },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(CampusSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
        ) {
            item { ScreenHeading("我的评价") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                    items(reviewStatusNames.entries.toList()) { (status, label) ->
                        FilterChip(
                            selected = model.reviewFilter == status,
                            onClick = { model.changeReviewFilter(status) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            state.error?.let { error ->
                item {
                    ErrorPanel(error, { model.refreshReviews(keepPages = true) }, !state.loading)
                }
            }
            if (state.loading && state.items.isEmpty()) item { LoadingCards() }
            else if (state.items.isEmpty() && state.error == null)
                item {
                    EmptyPanel(
                        "暂无评价",
                        "",
                        "去找空间",
                        { model.selectTab(CampusTab.Discover) },
                    )
                }
            items(state.items, key = { it.id }) { review ->
                CampusPanel {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
                    ) {
                        Text(
                            review.spaceName,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusChip(
                            reviewStatusNames[review.status] ?: review.status,
                            warning = review.status in listOf("PENDING", "REJECTED"),
                        )
                    }
                    RatingSummary(review.environment, review.facility)
                    if (review.content.isNotBlank())
                        Text(
                            review.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    review.reason?.takeIf(String::isNotBlank)?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                it,
                                Modifier.fillMaxWidth().padding(CampusSpacing.Large),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!review.spaceEnabled) SupportingText("空间已停用")
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SupportingText(
                            displayTime(review.updatedAt),
                            Modifier.weight(1f),
                            maxLines = 1,
                        )
                        CampusTonalButton(
                            onClick = { model.openEditor(review.spaceId, review.spaceName) },
                        ) {
                            Text(if (review.spaceEnabled) "管理评价" else "查看 / 撤回")
                        }
                    }
                }
            }
            if (state.items.isNotEmpty())
                item {
                    PagingFooter(
                        state.loading,
                        state.items.size < state.total,
                        { model.refreshReviews(append = true) },
                    )
                }
        }
    }
}

@Composable
fun RatingSummary(environment: Int, facility: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
    ) {
        listOf("环境" to environment, "设施" to facility).forEach { (label, score) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraSmall),
            ) {
                CampusIcon(
                    CampusSymbol.Star,
                    Modifier.size(CampusComponents.SmallIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("$label $score", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
