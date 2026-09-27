package com.embabel.hub

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserService
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.WebUserData
import com.embabel.hub.email.EmailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Password login is fail-closed for an account that has no password hash.
 * OAuth-only users must get the same generic rejection as a bad password, and
 * login must not mint a token for them.
 */
class HubServiceOAuthOnlyLoginTest {

    @Test
    fun `login refuses an account with no password hash and does not mint a token`() {
        val guideUserService = mock(GuideUserService::class.java)
        val jwtTokenService = mock(JwtTokenService::class.java)
        val service = HubService(
            guideUserService,
            jwtTokenService,
            mock(WelcomeGreeter::class.java),
            mock(ChatSessionService::class.java),
            mock(GuideUserCache::class.java),
            mock(EmailService::class.java),
        )
        val user = GuideUser(
            core = GuideUserData(id = "guide-1", displayName = "Ada"),
            webUser = WebUserData(
                id = "web-1",
                displayName = "Ada",
                userName = "ada",
                userEmail = "ada@example.com",
                passwordHash = null,
                refreshToken = null,
            ),
            persona = PersonaData(id = "persona-1", name = "adaptive", prompt = "help"),
        )
        `when`(guideUserService.findByWebUserName("ada")).thenReturn(Optional.of(user))

        val ex = assertThrows(LoginException::class.java) {
            service.loginUser(UserLoginRequest(username = "ada", password = "any-password"))
        }

        assertEquals("Invalid username or password", ex.message)
        verifyNoInteractions(jwtTokenService)
    }
}
