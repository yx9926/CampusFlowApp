package com.campus.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.campus.admin.AdminScreen
import com.campus.admin.AdminViewModel
import com.campus.auth.AuthViewModel
import com.campus.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CampusRoute(
    auth: AuthViewModel,
    model: CampusViewModel,
    admin: AdminViewModel,
    lifecycle: Lifecycle,
) {
    val account = auth.state.account ?: return
    val client = auth.activeHttpClient() ?: return
    val spacesScroll = rememberLazyListState()
    val reviewsScroll = rememberLazyListState()
    LaunchedEffect(account, client) {
        model.attach(account, client)
        admin.attach(account, client)
    }
    LaunchedEffect(admin.sessionExpired, model.sessionExpired, auth.state.busy) {
        if ((admin.sessionExpired || model.sessionExpired) && !auth.state.busy) auth.expireSession()
    }
    // 生命周期取消轮询，避免后台持续请求；界面动画不参与业务刷新。
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var seconds = 0
            if (model.tab == CampusTab.Management) admin.refresh() else model.refreshVisible()
            while (true) {
                model.tick()
                delay(1000)
                seconds++
                if (seconds % (POLL_MILLIS / 1000).toInt() == 0) {
                    if (model.tab == CampusTab.Management) admin.refresh()
                    else model.refreshVisible()
                }
            }
        }
    }
    BackHandler(enabled = model.selectedId != null && model.editor == null) { model.backToList() }
    BackHandler(
        enabled =
            model.selectedId == null && model.tab != CampusTab.Discover && model.editor == null,
    ) {
        model.selectTab(CampusTab.Discover)
    }
    BackHandler(enabled = model.tab == CampusTab.Management && admin.page != null) { admin.back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                Modifier.navigationBarsPadding()
                    .padding(horizontal = CampusSpacing.Large, vertical = CampusSpacing.Small),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    val tabs =
                        if (account.role == "USER")
                            listOf(CampusTab.Discover, CampusTab.Reviews, CampusTab.Profile)
                        else listOf(CampusTab.Discover, CampusTab.Management, CampusTab.Profile)
                    tabs.forEach { tab ->
                        val selected = model.tab == tab
                        val scale by
                            animateFloatAsState(
                                if (selected) 1.10f else 1f,
                                spring(),
                                label = "navigationScale",
                            )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { model.selectTab(tab) },
                            enabled =
                                !auth.state.busy &&
                                    model.editor?.busy != true &&
                                    admin.editor == null,
                            modifier = Modifier.testTag("nav_" + tab.name.lowercase()),
                            icon = {
                                CampusIcon(
                                    when (tab) {
                                        CampusTab.Discover -> CampusSymbol.Search
                                        CampusTab.Reviews -> CampusSymbol.Review
                                        CampusTab.Management -> CampusSymbol.Grid
                                        CampusTab.Profile -> CampusSymbol.Person
                                    },
                                    Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                )
                            },
                            label = {
                                Text(
                                    when (tab) {
                                        CampusTab.Discover -> "找空间"
                                        CampusTab.Reviews -> "评价"
                                        CampusTab.Management -> "管理"
                                        CampusTab.Profile -> "我的"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (model.selectedId != null) DetailScreen(model, canReview = account.role == "USER")
            else
                Crossfade(targetState = model.tab, label = "campusTab") { tab ->
                    when (tab) {
                        CampusTab.Discover -> DiscoverScreen(model, spacesScroll)
                        CampusTab.Reviews -> ReviewsScreen(model, reviewsScroll)
                        CampusTab.Management -> AdminScreen(admin, account.role)
                        CampusTab.Profile -> ProfileScreen(auth)
                    }
                }
        }
    }
    model.editor?.let { ReviewEditor(model) }
}
