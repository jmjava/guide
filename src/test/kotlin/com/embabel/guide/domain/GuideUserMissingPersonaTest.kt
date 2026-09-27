package com.embabel.guide.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * Switching a user's persona is fail-closed when the persona id does not exist.
 * The repository must not be written.
 */
class GuideUserMissingPersonaTest {

    @Test
    fun `updatePersona refuses an unknown persona and does not write`() {
        val guideUserRepository = mock(GuideUserRepository::class.java)
        val personaRepository = mock(PersonaRepository::class.java)
        `when`(personaRepository.findById("missing-persona")).thenReturn(null)

        val service = GuideUserService(guideUserRepository, personaRepository)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.updatePersona("user-1", "missing-persona")
        }

        assertEquals("Persona not found: missing-persona", ex.message)
        verifyNoInteractions(guideUserRepository)
    }
}
