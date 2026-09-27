package com.embabel.hub

import com.embabel.guide.domain.GuideUser
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaData
import com.embabel.guide.domain.PersonaRepository
import com.embabel.guide.domain.PersonaView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Editing a persona is fail-closed when the persona id is unknown.
 * The refusal happens before revalidation or a graph write.
 */
class PersonaUpdateMissingGateTest {

    @Test
    fun `updatePersona refuses a missing persona and does not save`() {
        val repository = RecordingPersonaRepository()
        val ingestion = mock(PersonaIngestionService::class.java)
        val users = mock(GuideUserRepository::class.java)
        val promptCache = PersonaPromptCache()
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
            service.updatePersona("web-1", "missing-persona", "renamed", "a new prompt", null, null)
        }

        assertEquals("Persona not found: missing-persona", ex.message)
        assertNull(repository.saved)
        assertNull(promptCache.get("missing-persona"))
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
