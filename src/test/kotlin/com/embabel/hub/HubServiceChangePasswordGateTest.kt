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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Changing a password is fail-closed when the account already has a hash.
 * A missing or blank current password is refused before the stored hash is replaced.
 */
class HubServiceChangePasswordGateTest {

    @Test
    fun `changePassword refuses a blank current password and leaves the stored hash`() {
        val guideUserService = mock(GuideUserService::class.java)
        val cache = mock(GuideUserCache::class.java)
        val service = HubService(
            guideUserService,
            mock(JwtTokenService::class.java),
            mock(WelcomeGreeter::class.java),
            mock(ChatSessionService::class.java),
            cache,
            mock(EmailService::class.java),
        )
        val webUser = WebUserData(
            id = "web-1",
            displayName = "Ada",
            userName = "ada",
            userEmail = "ada@example.com",
            passwordHash = "existing-hash",
            refreshToken = null,
        )
        val user = GuideUser(
            core = GuideUserData(id = "guide-1", displayName = "Ada"),
            webUser = webUser,
            persona = PersonaData(id = "persona-1", name = "adaptive", prompt = "help"),
        )
        `when`(guideUserService.findByWebUserId("web-1")).thenReturn(Optional.of(user))

        for (current in listOf(null, "  ")) {
            val ex = assertThrows<ChangePasswordException> {
                service.changePassword(
                    "web-1",
                    ChangePasswordRequest(
                        currentPassword = current,
                        newPassword = "replacement-secret",
                        newPasswordConfirmation = "replacement-secret",
                    ),
                )
            }
            assertEquals("Current password is required", ex.message)
        }

        assertEquals("existing-hash", webUser.passwordHash)
        verifyNoInteractions(cache)
    }
}
