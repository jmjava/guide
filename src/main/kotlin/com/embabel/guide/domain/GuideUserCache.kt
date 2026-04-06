package com.embabel.guide.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for GuideUser lookups, keyed by webUserId or Discord user ID.
 * Eliminates repeated Neo4j reads for the same user within and across requests.
 *
 * Invalidate on any write that changes user state visible to ChatActions:
 * persona, customPrompt, welcomed flag.
 */
@Service
class GuideUserCache {

    private val logger = LoggerFactory.getLogger(GuideUserCache::class.java)
    private val cache = ConcurrentHashMap<String, GuideUser>()
    private val byInternalId = ConcurrentHashMap<String, GuideUser>()

    fun get(key: String): GuideUser? = cache[key]

    fun getByInternalId(internalId: String): GuideUser? = byInternalId[internalId]

    fun put(key: String, user: GuideUser) {
        cache[key] = user
        byInternalId[user.core.id] = user
    }

    fun invalidate(key: String) {
        val user = cache.remove(key)
        user?.let { byInternalId.remove(it.core.id) }
        logger.debug("Invalidated GuideUser cache for key {}", key)
    }
}