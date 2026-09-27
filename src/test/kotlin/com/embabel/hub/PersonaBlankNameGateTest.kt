package com.embabel.hub

import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserRepository
import com.embabel.guide.domain.PersonaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Creating a persona is fail-closed when the name is missing.
 * A blank name is refused before lookup or ingestion can write a persona.
 */
class PersonaBlankNameGateTest {

    @Test
    fun `createPersona refuses a blank name and does not ingest`() {
        val ingestion = mock(PersonaIngestionService::class.java)
        val repository = mock(PersonaRepository::class.java)
        val users = mock(GuideUserRepository::class.java)
        val cache = mock(GuideUserCache::class.java)
        val service = PersonaService(
            repository,
            ingestion,
            users,
            cache,
            PersonaPromptCache(),
        )

        for (name in listOf("", "   ")) {
            val ex = assertThrows<IllegalArgumentException> {
                service.createPersona("web-1", name, "a prompt that must not be stored", null, null)
            }
            assertEquals("Persona name must not be blank.", ex.message)
        }

        verifyNoInteractions(ingestion, repository, users, cache)
    }
}
