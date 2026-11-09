package com.campus.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.campus.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class AuthPage { Login, Register, SignedIn }

data class AuthState(
    val page: AuthPage = AuthPage.Login,
    val username: String = "",
    val password: String = "",
    val confirmation: String = "",
    val passwordVisible: Boolean = false,
    val confirmationVisible: Boolean = false,
    val busy: Boolean = false,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmationError: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val account: CampusAccount? = null,
)

class AuthViewModel : ViewModel() {
    var state by mutableStateOf(AuthState())
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var httpRepository: HttpAuthRepository? = null

    fun username(value: String) {
        if (!state.busy) state = state.copy(username = value.take(64), usernameError = null, error = null)
    }
    fun password(value: String) {
        if (!state.busy) state = state.copy(password = value.take(256), passwordError = null, error = null)
    }
    fun confirmation(value: String) {
        if (!state.busy) state = state.copy(confirmation = value.take(256), confirmationError = null, error = null)
    }
    fun togglePassword() {
        if (!state.busy) state = state.copy(passwordVisible = !state.passwordVisible)
    }
    fun toggleConfirmation() {
        if (!state.busy) state = state.copy(confirmationVisible = !state.confirmationVisible)
    }
    fun navigate(page: AuthPage) {
        if (!state.busy) state = state.copy(
            page = page, password = "", confirmation = "", passwordVisible = false,
            confirmationVisible = false, usernameError = null, passwordError = null,
            confirmationError = null, error = null, notice = null,
        )
    }

    fun submit() {
        if (state.busy || state.page == AuthPage.SignedIn) return
        val registering = state.page == AuthPage.Register
        val usernameError = if (registering) AuthValidation.username(state.username)
            else if (state.username.isBlank()) "请输入账号" else null
        val passwordError = if (registering) AuthValidation.password(state.password)
            else if (state.password.isEmpty()) "请输入密码" else null
        val confirmationError = if (registering && state.password != state.confirmation)
            "两次密码不一致" else null
        state = state.copy(
            usernameError = usernameError, passwordError = passwordError,
            confirmationError = confirmationError, error = null, notice = null,
        )
        if (usernameError != null || passwordError != null || confirmationError != null) return

        // 在启动协程前锁定状态，防止双击和 IME 提交产生重复请求。
        val input = state
        state = state.copy(busy = true)
        scope.launch {
            try {
                val repository = repository()
                if (registering) {
                    repository.register(input.username, input.password)
                    state = state.copy(
                        page = AuthPage.Login, password = "", confirmation = "",
                        passwordVisible = false, confirmationVisible = false,
                        notice = "注册成功，请使用新账号登录",
                    )
                } else {
                    val account = repository.login(input.username, input.password)
                    state = state.copy(
                        page = AuthPage.SignedIn, account = account, password = "", confirmation = "",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = failure.message ?: "操作失败，请重试")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun logout() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        scope.launch {
            try {
                repository().logout()
                state = state.copy(page = AuthPage.Login, account = null, notice = "已退出登录")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = failure.message ?: "退出失败，请重试")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun activeHttpClient(): HttpAuthRepository? = httpRepository

    fun expireSession() {
        httpRepository = null
        state = state.copy(page = AuthPage.Login, account = null, password = "", confirmation = "", busy = false, error = "登录状态已失效，请重新登录")
    }

    private fun repository(): AuthRepository =
        httpRepository ?: HttpAuthRepository(BuildConfig.BACKEND_BASE_URL).also { httpRepository = it }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
