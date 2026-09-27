package com.embabel.hub

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.FeedbackRepository
import com.embabel.guide.domain.GuideUserService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Token refresh is fail-closed when the subject is no longer a user.
 * A valid signature must not mint a replacement token for a deleted account.
 */
class HubRefreshMissingUserTest {

    @Test
    fun `refresh refuses a token for a user that no longer exists and does not mint a replacement`() {
        val jwtTokenService = mock(JwtTokenService::class.java)
        val guideUserService = mock(GuideUserService::class.java)
        val chatSessionService = mock(ChatSessionService::class.java)
        val claims = mock(Claims::class.java)
        `when`(claims.subject).thenReturn("gone-user")
        `when`(jwtTokenService.parseTokenIgnoringExpiration("signed-token")).thenReturn(claims)
        `when`(guideUserService.findByWebUserId("gone-user")).thenReturn(Optional.empty())

        val controller = HubApiController(
            mock(HubService::class.java),
            mock(PersonaService::class.java),
            guideUserService,
            chatSessionService,
            jwtTokenService,
            mock(FeedbackRepository::class.java),
        )

        val ex = assertThrows(UnauthorizedException::class.java) {
            controller.refreshToken(HubApiController.RefreshTokenRequest("signed-token"))
        }

        assertEquals("User not found", ex.message)
        verify(jwtTokenService, never()).generateRefreshToken("gone-user")
        verifyNoInteractions(chatSessionService)
    }
}
