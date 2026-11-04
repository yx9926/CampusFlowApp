package com.campus.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.campus.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(model: CampusViewModel, listState: LazyListState) {
    var showFilters by remember { mutableStateOf(false) }
    val state = model.spaces
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
                        model.spaces.error == null &&
                        model.spaces.items.isNotEmpty()
                )
                    model.loadMoreSpaces()
            }
    }
    PullToRefreshBox(
        isRefreshing = state.loading && state.items.isNotEmpty(),
        onRefresh = { model.refreshSpaces(keepPages = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("space_list"),
            contentPadding = PaddingValues(CampusSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraLarge),
        ) {
            item(key = "heading") {
                ScreenHeading("找空间")
            }
            item(key = "search") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                ) {
                    CampusTextInput(
                        value = model.filters.name,
                        onValueChange = model::search,
                        placeholder = { Text("搜索空间") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("space_search"),
                        shape = MaterialTheme.shapes.small,
                        leadingIcon = { CampusIcon(CampusSymbol.Search) },
                        trailingIcon =
                            if (model.filters.name.isNotEmpty()) {
                                {
                                    IconButton(onClick = { model.search("") }) {
                                        CampusIcon(CampusSymbol.Close, description = "清除搜索")
                                    }
                                }
                            } else null,
                    )
                    FilledIconButton(
                        onClick = { showFilters = true },
                        modifier =
                            Modifier.size(CampusComponents.ControlHeight).testTag("space_filters"),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        CampusIcon(CampusSymbol.Filter, description = "筛选与排序")
                    }
                }
            }
            item(key = "location") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CampusTonalButton(onClick = { showFilters = true }) {
                        CampusIcon(CampusSymbol.Pin, Modifier.size(CampusComponents.SmallIcon))
                        Spacer(Modifier.width(CampusSpacing.Small))
                        Text(if (model.filters.hasLocation) model.filters.originLabel.ifBlank { "当前起点" } else "设置起点", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = CampusComponents.OriginLabelWidth))
                        Spacer(Modifier.width(CampusSpacing.Small))
                        CampusIcon(CampusSymbol.Down, Modifier.size(CampusComponents.SmallIcon))
                    }
                    if (model.filters.hasLocation) SupportingText("${state.total} 个空间")
                }
            }
            item(key = "types") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Small)) {
                    items(listOf("", "LIBRARY", "STUDY_ROOM", "CLASSROOM", "DISCUSSION", "CAFE")) {
                        type ->
                        FilterChip(
                            selected = model.filters.type == type,
                            onClick = { model.applyFilters(model.filters.copy(type = type)) },
                            label = {
                                Text(if (type.isEmpty()) "全部" else spaceTypes[type].orEmpty())
                            },
                        )
                    }
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    ErrorPanel(error, { model.refreshSpaces(keepPages = true) }, !state.loading)
                }
            }
            if (!model.filters.hasLocation)
                item(key = "set_location") {
                    EmptyPanel("发现附近空间", "", "选择起点", { showFilters = true })
                }
            else if (state.loading && state.items.isEmpty())
                item(key = "loading") { LoadingCards() }
            else if (!state.loading && state.items.isEmpty() && state.error == null)
                item(key = "empty") {
                    EmptyPanel("没有符合条件的空间", "", "重置筛选") {
                        model.applyFilters(
                            SpaceFilters(
                                latitude = model.filters.latitude,
                                longitude = model.filters.longitude,
                                originLabel = model.filters.originLabel,
                            ),
                        )
                    }
                }
            items(state.items, key = { it.space.id }) { card ->
                SpaceCardView(card, model.elapsed(card.receivedAtElapsed)) {
                    model.openDetail(card.space.id)
                }
            }
            if (state.items.isNotEmpty())
                item(key = "footer") {
                    if (state.items.size >= MAX_SPACE_RESULTS && state.total > MAX_SPACE_RESULTS)
                        SupportingText("试试缩小筛选范围")
                    else
                        PagingFooter(
                            state.loading,
                            state.items.size < state.total,
                            model::loadMoreSpaces,
                        )
                }
        }
    }
    if (showFilters)
        FiltersDialog(
            model.filters,
            onDismiss = { showFilters = false },
            onApply = {
                model.applyFilters(it)
                showFilters = false
            },
        )
}
