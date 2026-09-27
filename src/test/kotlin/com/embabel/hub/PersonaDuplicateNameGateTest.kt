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
 * Creating a persona is fail-closed when that user already has the same name.
 * The refusal happens before ingestion or a graph write.
 */
class PersonaDuplicateNameGateTest {

    @Test
    fun `createPersona refuses a duplicate name and does not ingest`() {
        val ingestion = mock(PersonaIngestionService::class.java)
        val repository = RecordingPersonaRepository()
        val users = mock(GuideUserRepository::class.java)
        val caller = GuideUser(
            core = GuideUserData(id = "user-1", displayName = "Ada"),
            persona = PersonaData(id = "persona-adaptive", name = "adaptive", prompt = "be helpful"),
        )
        val service = PersonaService(
            repository,
            ingestion,
            users,
            GuideUserCache(300).also { it.put("web-1", caller) },
            PersonaPromptCache(),
        )

        val ex = assertThrows<IllegalArgumentException> {
            service.createPersona("web-1", "  Coach  ", "a prompt that must not be stored", null, null)
        }

        assertEquals("You already have a persona named 'Coach'.", ex.message)
        assertFalse(repository.saved)
        verifyNoInteractions(ingestion, users)
    }

    private class RecordingPersonaRepository : PersonaRepository {
        var saved: Boolean = false

        override fun findById(id: String): PersonaView? = error("unused")

        override fun findByOwner(ownerId: String): List<PersonaView> = error("unused")

        override fun findForUser(userId: String): List<PersonaView> = error("unused")

        override fun findByNameAndOwner(name: String, ownerId: String): PersonaView? = error("unused")

        override fun existsByNameAndOwner(name: String, ownerId: String): Boolean =
            name == "Coach" && ownerId == "user-1"

        override fun save(persona: PersonaView): PersonaView {
            saved = true
            return persona
        }

        override fun delete(personaId: String) = error("unused")
    }
}
