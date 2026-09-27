package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Deleting a persona is fail-closed when the persona id is unknown.
 * The refusal happens before a graph delete or prompt-cache invalidation.
 */
class PersonaDeleteMissingGateTest {

    @Test
    fun `deletePersona refuses a missing persona and does not delete it`() {
        val repository = RecordingPersonaRepository()
        val ingestion = mock(PersonaIngestionService::class.java)
        val users = mock(GuideUserRepository::class.java)
        val promptCache = PersonaPromptCache().also { it.put("missing-persona", "keep this prompt") }
        val caller = GuideUser(
            core = GuideUserData(id = "user-1", displayName = "Ada"),
            persona = PersonaData(id = "persona-mine", name = "coach", prompt = "keep this prompt"),
        )
        val service = PersonaService(
            repository,
            ingestion,
            users,
            GuideUserCache(300).also { it.put("web-1", caller) },
            promptCache,
        )

        val ex = assertThrows<NotFoundException> {
            service.deletePersona("web-1", "missing-persona")
        }

        assertEquals("Persona not found: missing-persona", ex.message)
        assertFalse(repository.deleted)
        assertEquals("keep this prompt", promptCache.get("missing-persona"))
        verifyNoInteractions(ingestion, users)
    }

    private class RecordingPersonaRepository : PersonaRepository {
        var deleted: Boolean = false

        override fun findById(id: String): PersonaView? = null

        override fun findByOwner(ownerId: String): List<PersonaView> = error("unused")

        override fun findForUser(userId: String): List<PersonaView> = error("unused")

        override fun findByNameAndOwner(name: String, ownerId: String): PersonaView? = error("unused")

        override fun existsByNameAndOwner(name: String, ownerId: String): Boolean = error("unused")

        override fun save(persona: PersonaView): PersonaView = error("unused")

        override fun delete(personaId: String) {
            deleted = true
        }
    }
}
