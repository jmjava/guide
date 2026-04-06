package com.embabel.hub

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for persona prompts, keyed by persona ID.
 * Avoids a DB round trip on every chat message and narration.
 */
@Service
class PersonaPromptCache {

    private val cache = ConcurrentHashMap<String, String>()

    fun get(personaId: String): String? = cache[personaId]

    fun put(personaId: String, prompt: String) {
        cache[personaId] = prompt
    }

    fun invalidate(personaId: String) {
        cache.remove(personaId)
    }
}