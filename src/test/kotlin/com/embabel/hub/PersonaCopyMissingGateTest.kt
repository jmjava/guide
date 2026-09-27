package com.embabel.hub

import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Copying a persona is fail-closed when the source id is unknown.
 * The refusal happens before owner lookup or a graph write.
 */
class PersonaCopyMissingGateTest {

    @Test
    fun `copyPersona refuses a missing persona and does not save`() {
        val repository = RecordingPersonaRepository()
        val ingestion = mock(PersonaIngestionService::class.java)
        val users = mock(GuideUserRepository::class.java)
        val service = PersonaService(
            repository,
            ingestion,
            users,
            GuideUserCache(300),
            PersonaPromptCache(),
        )

        val ex = assertThrows<NotFoundException> {
            service.copyPersona("web-1", "missing-persona")
        }

        assertEquals("Persona not found: missing-persona", ex.message)
        assertNull(repository.saved)
        verifyNoInteractions(ingestion, users)
    }

    private class RecordingPersonaRepository : PersonaRepository {
        var saved: PersonaView? = null

        override fun findById(id: String): PersonaView? = null

        override fun findByOwner(ownerId: String): List<PersonaView> = error("unused")

        override fun findForUser(userId: String): List<PersonaView> = error("unused")

        override fun findByNameAndOwner(name: String, ownerId: String): PersonaView? = error("unused")

        override fun existsByNameAndOwner(name: String, ownerId: String): Boolean = error("unused")

        override fun save(persona: PersonaView): PersonaView {
            saved = persona
            return persona
        }

        override fun delete(personaId: String) = error("unused")
    }
}
