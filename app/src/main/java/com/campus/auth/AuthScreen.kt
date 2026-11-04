@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.campus.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.campus.ui.theme.*

@Composable
fun AuthRoute(model: AuthViewModel) {
    BackHandler(enabled = model.state.page == AuthPage.Register && !model.state.busy) {
        model.navigate(AuthPage.Login)
    }
    AuthScreen(
        state = model.state,
        onUsername = model::username,
        onPassword = model::password,
        onConfirmation = model::confirmation,
        onPasswordVisibility = model::togglePassword,
        onConfirmationVisibility = model::toggleConfirmation,
        onServerAddress = model::serverAddress,
        onNavigate = model::navigate,
        onSubmit = model::submit,
    )
}

@Composable
private fun AuthScreen(
    state: AuthState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onPasswordVisibility: () -> Unit,
    onConfirmationVisibility: () -> Unit,
    onServerAddress: (String) -> Unit,
    onNavigate: (AuthPage) -> Unit,
    onSubmit: () -> Unit,
) {
    val registering = state.page == AuthPage.Register
    val focus = LocalFocusManager.current
    var connectionExpanded by rememberSaveable { mutableStateOf(false) }
    val submit = {
        focus.clearFocus()
        onSubmit()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
        ) {
            val wide = maxWidth >= CampusLayout.WideBreakpoint
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(CampusSpacing.ExtraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraLarge),
            ) {
                Row(
                    Modifier.widthIn(max = CampusLayout.ContentMaxWidth).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Medium),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(
                            Modifier.size(CampusSpacing.MinimumTouchTarget),
                            contentAlignment = Alignment.Center,
                        ) {
                            CampusIcon(
                                CampusSymbol.Leaf,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Text("CampusFlow", style = MaterialTheme.typography.titleLarge)
                }
                val hero: @Composable () -> Unit = {
                    ExpressiveHero(if (registering) "创建账号" else "欢迎回来", "")
                }
                val form: @Composable () -> Unit = {
                    Column(
                        Modifier.widthIn(max = CampusLayout.FormMaxWidth)
                            .fillMaxWidth()
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(CampusSpacing.Large),
                    ) {
                        state.notice?.let { AuthBanner(it, false) }
                        CampusTextInput(
                            value = state.username,
                            onValueChange = onUsername,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (registering) "用户名" else "账号") },
                            leadingIcon = { CampusIcon(CampusSymbol.Person) },
                            singleLine = true,
                            enabled = !state.busy,
                            isError = state.usernameError != null,
                            supportingText = state.usernameError?.let { error -> { Text(error) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = MaterialTheme.shapes.small,
                        )
                        PasswordField(
                            state.password,
                            onPassword,
                            "密码",
                            state.passwordVisible,
                            onPasswordVisibility,
                            !state.busy,
                            state.passwordError,
                            if (registering) ImeAction.Next else ImeAction.Done,
                            submit,
                        )
                        AnimatedVisibility(registering) {
                            PasswordField(
                                state.confirmation,
                                onConfirmation,
                                "确认密码",
                                state.confirmationVisible,
                                onConfirmationVisibility,
                                !state.busy,
                                state.confirmationError,
                                ImeAction.Done,
                                submit,
                            )
                        }
                        state.error?.let { AuthBanner(it, true) }
                        CampusPrimaryButton(
                            onClick = submit,
                            enabled = !state.busy,
                            shape = MaterialTheme.shapes.small,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = CampusComponents.ControlHeight),
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(CampusComponents.SmallIcon),
                                    strokeWidth = CampusElevation.Border,
                                    color = LocalContentColor.current,
                                )
                                Spacer(Modifier.width(CampusSpacing.Small))
                            }
                            Text(
                                if (state.busy) "请稍候…"
                                else if (state.error != null) "重试"
                                else if (registering) "注册" else "登录",
                            )
                            if (!state.busy) {
                                Spacer(Modifier.width(CampusSpacing.Small))
                                CampusIcon(CampusSymbol.Arrow)
                            }
                        }
                        TextButton(
                            onClick = {
                                onNavigate(if (registering) AuthPage.Login else AuthPage.Register)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (registering) "返回登录" else "注册账号")
                        }
                        TextButton(
                            onClick = { connectionExpanded = !connectionExpanded },
                            enabled = !state.busy,
                            modifier =
                                Modifier.align(Alignment.CenterHorizontally)
                                    .testTag("connection_settings"),
                        ) {
                            CampusIcon(
                                CampusSymbol.Server,
                                Modifier.size(CampusComponents.SmallIcon),
                            )
                            Spacer(Modifier.width(CampusSpacing.Small))
                            Text("连接设置")
                            Spacer(Modifier.width(CampusSpacing.Small))
                            CampusIcon(CampusSymbol.Down, Modifier.size(CampusComponents.SmallIcon))
                        }
                        AnimatedVisibility(connectionExpanded) {
                            CampusTextInput(
                                value = state.serverAddress,
                                onValueChange = onServerAddress,
                                label = { Text("服务器地址") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !state.busy,
                                shape = MaterialTheme.shapes.small,
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Done,
                                    ),
                            )
                        }
                    }
                }
                if (wide) {
                    Row(
                        Modifier.widthIn(max = CampusLayout.ContentMaxWidth).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CampusSpacing.Section),
                    ) {
                        Box(Modifier.weight(1f)) { hero() }
                        Box(Modifier.weight(1f)) { form() }
                    }
                } else {
                    Column(
                        Modifier.widthIn(max = CampusLayout.FormMaxWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CampusSpacing.ExtraLarge),
                    ) {
                        hero()
                        form()
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibility: () -> Unit,
    enabled: Boolean,
    error: String?,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
) {
    CampusTextInput(
        value,
        onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { CampusIcon(CampusSymbol.Shield) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onVisibility, enabled = enabled) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun AuthBanner(message: String, error: Boolean) {
    Surface(
        color =
            if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            message,
            Modifier.fillMaxWidth().padding(CampusSpacing.Large),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LoginPreview() {
    CampusFlowTheme { AuthScreen(AuthState(), {}, {}, {}, {}, {}, {}, {}, {}) }
}
