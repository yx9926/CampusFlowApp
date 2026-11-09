package com.campus.auth

import com.campus.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    @Test fun usernameMatchesWebRegistrationRules() {
        assertNull(AuthValidation.username("student_01"))
        assertNull(AuthValidation.username("a".repeat(64)))
        assertNotNull(AuthValidation.username("ab"))
        assertNotNull(AuthValidation.username("a".repeat(65)))
        assertNotNull(AuthValidation.username("学生"))
        assertNotNull(AuthValidation.username("user name"))
    }

    @Test fun passwordChecksUtf8BytesRatherThanCharacterCount() {
        assertNull(AuthValidation.password("a".repeat(72)))
        assertNotNull(AuthValidation.password("a".repeat(73)))
        assertNull(AuthValidation.password("学".repeat(24)))
        assertNotNull(AuthValidation.password("学".repeat(25)))
        assertNotNull(AuthValidation.password("1234567"))
    }

    @Test fun authenticationTargetsTheCloudBackendWithoutAPreselectedAccount() {
        val initial = AuthState()
        assertEquals("https://139.159.155.248", BuildConfig.BACKEND_BASE_URL)
        assertEquals("", initial.username)
        assertEquals("", initial.password)
        assertNull(initial.account)
    }
}
