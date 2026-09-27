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

class PersonaDeleteGateTest {

    @Test
    fun `deletePersona refuses a system persona and does not delete it`() {
        val systemPersona = PersonaData(
            id = "persona-adaptive",
            name = "adaptive",
            prompt = "be helpful",
            isSystem = true,
        )
        val repository = RecordingPersonaRepository(
            PersonaView(
                persona = systemPersona,
                owner = GuideUserData(id = PersonaRepository.SYSTEM_OWNER_ID, displayName = "Jesse"),
            ),
        )
        val promptCache = PersonaPromptCache().also { it.put(systemPersona.id, systemPersona.prompt) }
        val caller = GuideUser(
            core = GuideUserData(id = "user-1", displayName = "Ada"),
            persona = systemPersona,
        )
        val service = PersonaService(
            repository,
            mock(PersonaIngestionService::class.java),
            mock(GuideUserRepository::class.java),
            GuideUserCache(300).also { it.put("web-1", caller) },
            promptCache,
        )

        val ex = assertThrows<ForbiddenException> {
            service.deletePersona("web-1", systemPersona.id)
        }

        assertEquals("System personas cannot be deleted.", ex.message)
        assertFalse(repository.deleted)
        assertEquals("be helpful", promptCache.get(systemPersona.id))
    }

    private class RecordingPersonaRepository(
        private val stored: PersonaView,
    ) : PersonaRepository {
        var deleted: Boolean = false

        override fun findById(id: String): PersonaView? =
            stored.takeIf { it.persona.id == id }

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
