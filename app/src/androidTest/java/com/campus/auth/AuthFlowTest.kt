package com.campus.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.campus.MainActivity
import org.junit.Rule
import org.junit.Test

class AuthFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun emptyLoginShowsFieldErrors() {
        compose.onNodeWithText("登录", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("请输入账号", substring = false).assertExists()
        compose.onNodeWithText("请输入密码", substring = false).assertExists()
    }

    @Test
    fun loginOnlyOffersTheRealBackend() {
        compose.onNodeWithTag("connection_settings").performScrollTo().performClick()
        compose.onNodeWithText("服务器地址", substring = false).performScrollTo().assertExists()
        compose.onNodeWithText("http://127.0.0.1:8080", substring = false).assertExists()
        compose.onNodeWithText("使用演示数据", substring = false).assertDoesNotExist()
    }

    @Test
    fun registrationValidatesConfirmationWithoutWritingInvalidData() {
        compose.onNodeWithText("注册账号").performScrollTo().performClick()
        compose.onNodeWithText("用户名", substring = false).performTextInput("new_student")
        compose.onNodeWithText("密码", substring = false).performTextInput("Learning@123")
        compose.onNodeWithText("确认密码", substring = false).performTextInput("wrong")
        compose.onNodeWithText("注册", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("两次密码不一致").assertExists()
        compose.onNodeWithText("返回登录").performScrollTo().performClick()
        compose.onNodeWithText("欢迎回来", substring = false).assertExists()
    }
}
