package com.embabel.hub.oauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class OAuthAuthorizeGateTest {

    private lateinit var login: OAuthLoginService
    private lateinit var controller: OAuthController

    @BeforeEach
    fun setUp() {
        login = mock(OAuthLoginService::class.java)
        controller = OAuthController(
            login,
            GoogleOAuthService("", "", ""),
            GitHubOAuthService("", "", ""),
        )
    }

    @Test
    fun `authorize refuses a provider that is not configured`() {
        val google = assertThrows<OAuthException> {
            controller.authorize("google")
        }
        val github = assertThrows<OAuthException> {
            controller.authorize("github")
        }

        assertEquals("Google OAuth not configured", google.message)
        assertEquals("GitHub OAuth not configured", github.message)
        verifyNoInteractions(login)
    }

    @Test
    fun `authorize refuses an unsupported provider`() {
        val exception = assertThrows<OAuthException> {
            controller.authorize("facebook")
        }

        assertEquals("Unsupported OAuth provider: facebook", exception.message)
        verifyNoInteractions(login)
    }
}
