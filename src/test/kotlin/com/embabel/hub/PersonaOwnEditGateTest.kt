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

class PersonaOwnEditGateTest {

    @Test
    fun `updatePersona refuses a persona owned by someone else and does not save it`() {
        val ownedByOther = PersonaData(
            id = "persona-other",
            name = "other-voice",
            prompt = "stay private",
            isSystem = false,
        )
        val repository = RecordingPersonaRepository(
            PersonaView(
                persona = ownedByOther,
                owner = GuideUserData(id = "user-other", displayName = "Other"),
            ),
        )
        val promptCache = PersonaPromptCache().also { it.put(ownedByOther.id, ownedByOther.prompt) }
        val caller = GuideUser(
            core = GuideUserData(id = "user-1", displayName = "Ada"),
            persona = ownedByOther,
        )
        val service = PersonaService(
            repository,
            mock(PersonaIngestionService::class.java),
            mock(GuideUserRepository::class.java),
            GuideUserCache(300).also { it.put("web-1", caller) },
            promptCache,
        )

        val ex = assertThrows<ForbiddenException> {
            service.updatePersona("web-1", ownedByOther.id, "stolen", "rewrite me", null, null)
        }

        assertEquals("You can only edit your own personas.", ex.message)
        assertNull(repository.saved)
        assertEquals("stay private", promptCache.get(ownedByOther.id))
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
