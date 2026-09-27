package com.embabel.hub.oauth

import com.embabel.hub.LoginResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Instant

class OAuthControllerStateTest {

    private lateinit var login: OAuthLoginService
    private lateinit var controller: OAuthController

    @BeforeEach
    fun setUp() {
        login = mock(OAuthLoginService::class.java)
        controller = OAuthController(
            login,
            GoogleOAuthService("client-id", "client-secret", "http://localhost/cb"),
            GitHubOAuthService("client-id", "client-secret", "http://localhost/cb"),
        )
    }

    @Test
    fun `callback refuses a state that was not issued`() {
        val exception = assertThrows(OAuthException::class.java) {
            controller.callback("google", OAuthCallbackRequest(code = "auth-code", state = "not-issued"))
        }

        assertEquals("Invalid or expired OAuth state parameter", exception.message)
        verifyNoInteractions(login)
    }

    @Test
    fun `callback refuses a state that was already consumed`() {
        `when`(login.handleOAuthCallback("google", "auth-code")).thenReturn(loginResponse())

        val issued = controller.authorize("google")
        val first = controller.callback(
            "google",
            OAuthCallbackRequest(code = "auth-code", state = issued.state),
        )
        assertEquals("user-1", first.userId)

        val replay = assertThrows(OAuthException::class.java) {
            controller.callback(
                "google",
                OAuthCallbackRequest(code = "auth-code", state = issued.state),
            )
        }

        assertEquals("Invalid or expired OAuth state parameter", replay.message)
        verify(login, times(1)).handleOAuthCallback("google", "auth-code")
    }

    private fun loginResponse() = LoginResponse(
        token = "token",
        expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
        userId = "user-1",
        username = "user",
        displayName = "User",
        email = "user@example.com",
        persona = "persona-1",
    )
}
