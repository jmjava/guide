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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

/**
 * Password change is fail-closed: a blank, mismatched, or short new password is
 * rejected before lookup, and an account that already has a password is not
 * saved unless the current password is present, correct, and different.
 */
class HubServiceChangePasswordTest {

    private val guideUserService = mock(GuideUserService::class.java)
    private val guideUserCache = mock(GuideUserCache::class.java)
    private val service = HubService(
        guideUserService,
        mock(JwtTokenService::class.java),
        mock(WelcomeGreeter::class.java),
        mock(ChatSessionService::class.java),
        guideUserCache,
        mock(EmailService::class.java),
    )
    private val encoder = BCryptPasswordEncoder()

    @Test
    fun `changePassword refuses an unsafe password before saving the user`() {
        val blank = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(newPassword = "   ", confirmation = "   "))
        }
        assertEquals("New password is required", blank.message)

        val mismatch = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(newPassword = "next-password", confirmation = "other-password"))
        }
        assertEquals("New password and confirmation do not match", mismatch.message)

        val short = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(newPassword = "short", confirmation = "short"))
        }
        assertEquals("New password must be at least 8 characters long", short.message)
        verifyNoInteractions(guideUserService, guideUserCache)

        val current = "current-password"
        val user = userWithPassword(encoder.encode(current))
        `when`(guideUserService.findByWebUserId("web-1")).thenReturn(Optional.of(user))

        val missingCurrent = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(currentPassword = null))
        }
        assertEquals("Current password is required", missingCurrent.message)

        val wrongCurrent = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(currentPassword = "wrong-password"))
        }
        assertEquals("Current password is incorrect", wrongCurrent.message)

        val reused = assertThrows(ChangePasswordException::class.java) {
            service.changePassword("web-1", request(currentPassword = current, newPassword = current, confirmation = current))
        }
        assertEquals("New password must be different from current password", reused.message)
        verify(guideUserService, never()).saveUser(argAny())
        verifyNoInteractions(guideUserCache)

        service.changePassword("web-1", request(currentPassword = current))
        assertTrue(encoder.matches("next-password", user.webUser!!.passwordHash))
        verify(guideUserService).saveUser(user)
        verify(guideUserCache).invalidate("web-1")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> argAny(): T {
        any<T>()
        return null as T
    }

    private fun request(
        currentPassword: String? = null,
        newPassword: String = "next-password",
        confirmation: String = "next-password",
    ) = ChangePasswordRequest(currentPassword, newPassword, confirmation)

    private fun userWithPassword(hash: String) = GuideUser(
        core = GuideUserData(id = "guide-1"),
        webUser = WebUserData(
            id = "web-1",
            displayName = "Ada",
            userName = "ada",
            userEmail = "ada@example.com",
            passwordHash = hash,
            refreshToken = null,
        ),
        persona = PersonaData(id = "persona-1", name = "adaptive", prompt = "help"),
    )
}
