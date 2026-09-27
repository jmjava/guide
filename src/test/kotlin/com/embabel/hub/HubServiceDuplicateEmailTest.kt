package com.embabel.hub

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserService
import com.embabel.hub.email.EmailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Registration is fail-closed when the email is already registered.
 * The refusal happens before a token, a stored user, or a verification email.
 */
class HubServiceDuplicateEmailTest {

    @Test
    fun `register refuses an email that is already registered and does not create a user`() {
        val guideUserService = mock(GuideUserService::class.java)
        val jwtTokenService = mock(JwtTokenService::class.java)
        val emailService = mock(EmailService::class.java)
        val service = HubService(
            guideUserService,
            jwtTokenService,
            mock(WelcomeGreeter::class.java),
            mock(ChatSessionService::class.java),
            mock(GuideUserCache::class.java),
            emailService,
        )
        `when`(guideUserService.findByWebUserName("ada")).thenReturn(Optional.empty())
        `when`(guideUserService.findByWebUserEmail("ada@example.com"))
            .thenReturn(Optional.of(mock(GuideUser::class.java)))

        val ex = assertThrows(RegistrationException::class.java) {
            service.registerUser(
                UserRegistrationRequest(
                    userDisplayName = "Ada",
                    username = "ada",
                    userEmail = "ada@example.com",
                    password = "long-enough-password",
                    passwordConfirmation = "long-enough-password",
                )
            )
        }

        assertEquals("An account with this email already exists", ex.message)
        verifyNoInteractions(jwtTokenService, emailService)
    }
}
