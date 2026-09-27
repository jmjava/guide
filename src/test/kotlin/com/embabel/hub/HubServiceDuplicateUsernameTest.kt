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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyString
import java.util.Optional

/**
 * Registration is fail-closed when the username is already taken.
 * The refusal happens before an email lookup, a token, a stored user, or a verification email.
 */
class HubServiceDuplicateUsernameTest {

    @Test
    fun `register refuses a username that is already taken and does not create a user`() {
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
        `when`(guideUserService.findByWebUserName("ada")).thenReturn(Optional.of(mock(GuideUser::class.java)))

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

        assertEquals("Username 'ada' is already taken", ex.message)
        verify(guideUserService, never()).findByWebUserEmail(anyString())
        verifyNoInteractions(jwtTokenService, emailService)
    }
}
