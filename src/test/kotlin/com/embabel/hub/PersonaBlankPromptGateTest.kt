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
 * Editing a persona is fail-closed when the new prompt is missing.
 * The refusal happens before revalidation or a graph write.
 */
class PersonaBlankPromptGateTest {

    @Test
    fun `updatePersona refuses a blank prompt and does not save`() {
        val owned = PersonaData(
            id = "persona-mine",
            name = "coach",
            prompt = "keep this prompt",
            isSystem = false,
        )
        val repository = RecordingPersonaRepository(
            PersonaView(
                persona = owned,
                owner = GuideUserData(id = "user-1", displayName = "Ada"),
            ),
        )
        val ingestion = mock(PersonaIngestionService::class.java)
        val users = mock(GuideUserRepository::class.java)
        val promptCache = PersonaPromptCache().also { it.put(owned.id, owned.prompt) }
        val caller = GuideUser(
            core = GuideUserData(id = "user-1", displayName = "Ada"),
            persona = owned,
        )
        val service = PersonaService(
            repository,
            ingestion,
            users,
            GuideUserCache(300).also { it.put("web-1", caller) },
            promptCache,
        )

        for (prompt in listOf(null, "   ")) {
            val ex = assertThrows<IllegalArgumentException> {
                service.updatePersona("web-1", owned.id, "coach", prompt, null, null)
            }
            assertEquals("Persona prompt must not be empty.", ex.message)
        }

        assertNull(repository.saved)
        assertEquals("keep this prompt", promptCache.get(owned.id))
        verifyNoInteractions(ingestion, users)
    }

    private class RecordingPersonaRepository(
        private val stored: PersonaView,
    ) : PersonaRepository {
        var saved: PersonaView? = null

        override fun findById(id: String): PersonaView? =
            stored.takeIf { it.persona.id == id }

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
