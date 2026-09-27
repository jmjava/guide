package com.embabel.hub

import com.embabel.guide.chat.service.ChatSessionService
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserService
import com.embabel.hub.email.EmailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Switching the signed-in persona is fail-closed on a blank id.
 * The refusal happens before the user is loaded or the cache is invalidated.
 */
class HubServiceBlankPersonaGateTest {

    @Test
    fun `updatePersona refuses a blank persona and does not load the user`() {
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

        for (persona in listOf("", "   ")) {
            val ex = assertThrows<IllegalArgumentException> {
                service.updatePersona("web-1", persona)
            }
            assertEquals("Persona cannot be blank", ex.message)
        }

        verifyNoInteractions(guideUserService, cache)
    }
}
