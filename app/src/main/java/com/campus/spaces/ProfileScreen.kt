package com.campus.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.campus.auth.AuthViewModel
import com.campus.ui.theme.*

@Composable
fun ProfileScreen(auth: AuthViewModel) {
    val state = auth.state
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CampusSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraLarge),
    ) {
        ScreenHeading("我的")
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(CampusSpacing.ExtraLarge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
            ) {
                Surface(
                    Modifier.size(CampusComponents.Avatar),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            state.account?.username?.take(1)?.uppercase().orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CampusSpacing.Small),
                ) {
                    Text(
                        state.account?.username.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (state.account?.role) {
                            "DATA_ADMIN" -> "数据管理员"
                            "SERVER_ADMIN" -> "服务器管理员"
                            else -> "普通用户"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            ProfileRow(CampusSymbol.Sun, "外观", "跟随系统")
        }
        state.error?.let { ErrorPanel(it, auth::logout, !state.busy) }
        CampusSecondaryButton(
            onClick = auth::logout,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = CampusComponents.ControlHeight),
        ) {
            CampusIcon(CampusSymbol.Logout)
            Spacer(Modifier.width(CampusSpacing.Small))
            Text(if (state.busy) "正在退出…" else "退出登录")
        }
    }
}

@Composable
private fun ProfileRow(
    symbol: CampusSymbol,
    title: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(CampusSpacing.ExtraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
    ) {
        CampusIcon(symbol, tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        SupportingText(value, maxLines = 1)
    }
}
