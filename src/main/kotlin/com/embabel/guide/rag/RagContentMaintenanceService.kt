package com.embabel.guide.rag

import com.embabel.agent.rag.graph.DrivineCypherSearch
import com.embabel.guide.GuideProperties
import org.drivine.query.QuerySpecification
import org.drivine.manager.PersistenceManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.io.IOException
import java.nio.file.Path

/**
 * Operator helpers for shared Neo4j: purge ContentElement nodes by URI prefix and reset git-ingestion state.
 */
@Service
class RagContentMaintenanceService(
    private val guideProperties: GuideProperties,
    @Qualifier("neo") private val persistenceManager: PersistenceManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Not a Spring bean: [DrivineCypherSearch] is final and cannot be proxied. */
    private val cypherSearch = DrivineCypherSearch(persistenceManager)

    /**
     * Resolves [directory] (YAML-style path) to a [file:] URI prefix, or trims [uriPrefix].
     * Leading/trailing whitespace is trimmed. At least one of the two must be non-blank.
     */
    fun resolveAppliedPrefix(uriPrefix: String?, directory: String?): String {
        val trimmedPrefix = uriPrefix?.trim().orEmpty()
        val trimmedDir = directory?.trim().orEmpty()
        if (trimmedPrefix.isNotEmpty() && trimmedDir.isNotEmpty()) {
            throw IllegalArgumentException("Provide only one of uriPrefix or directory, not both")
        }
        if (trimmedPrefix.isEmpty() && trimmedDir.isEmpty()) {
            throw IllegalArgumentException("Provide uriPrefix or directory")
        }
        if (trimmedDir.isNotEmpty()) {
            val abs = guideProperties.resolvePath(trimmedDir)
            return File(abs).toURI().toString()
        }
        return trimmedPrefix
    }

    fun previewPurge(uriPrefix: String?, directory: String?, sampleLimit: Int): PurgePreviewResult {
        val prefix = resolveAppliedPrefix(uriPrefix, directory)
        validatePrefixSafety(prefix)
        val count = countByUriPrefix(prefix)
        val samples = sampleUris(prefix, sampleLimit.coerceIn(1, 50))
        return PurgePreviewResult(prefix, count, samples)
    }

    @Transactional(transactionManager = "drivineTransactionManager")
    fun executePurge(uriPrefix: String?, directory: String?, confirm: Boolean): PurgeExecuteResult {
        if (!confirm) {
            throw IllegalArgumentException("confirm must be true to delete content")
        }
        val prefix = resolveAppliedPrefix(uriPrefix, directory)
        validatePrefixSafety(prefix)
        val before = countByUriPrefix(prefix)
        deleteByUriPrefix(prefix)
        log.warn("Purged {} ContentElement node(s) with uri STARTS WITH '{}'", before, prefix)
        return PurgeExecuteResult(prefix, before)
    }

    /**
     * Clears stored git HEAD for [directory] so the next incremental ingest does a full tree
     * (or first-time behavior). Requires [GuideProperties.gitIngestion] enabled.
     */
    @Throws(IOException::class)
    fun resetGitIngestionRevision(directory: String): GitRevisionResetResult {
        val git = guideProperties.gitIngestion
            ?: return GitRevisionResetResult(null, false, "guide.git-ingestion is not configured")
        if (!git.enabled) {
            return GitRevisionResetResult(null, false, "guide.git-ingestion.enabled is false")
        }
        val abs = guideProperties.resolvePath(directory.trim())
        val store = GitIngestionRevisionStore(Path.of(guideProperties.resolvePath(git.stateFile)))
        store.load()
        val removed = store.removeRevision(abs)
        if (store.isDirty) {
            store.save()
        }
        val msg = if (removed) {
            "Removed revision entry for $abs"
        } else {
            "No revision entry existed for $abs"
        }
        log.info("Git ingestion revision reset: {}", msg)
        return GitRevisionResetResult(abs, removed, msg)
    }

    private fun validatePrefixSafety(prefix: String) {
        require(prefix.length >= MIN_PREFIX_LENGTH) {
            "uriPrefix too short (min $MIN_PREFIX_LENGTH chars) — refusing to avoid accidental mass delete"
        }
    }

    private fun countByUriPrefix(prefix: String): Long {
        val cypher = """
            MATCH (n:ContentElement)
            WHERE n.uri STARTS WITH ${'$'}prefix
            RETURN count(n) AS cnt
        """.trimIndent().let { "\n$it" }
        return cypherSearch.queryForInt(cypher, mapOf("prefix" to prefix)).toLong()
    }

    private fun sampleUris(prefix: String, limit: Int): List<String> {
        val cypher = """
            MATCH (n:ContentElement)
            WHERE n.uri STARTS WITH ${'$'}prefix
            RETURN n.uri AS uri
            LIMIT ${'$'}lim
        """.trimIndent().let { "\n$it" }
        val qr = cypherSearch.query(
            "purge preview sample uris",
            cypher,
            mapOf("prefix" to prefix, "lim" to limit),
            log,
        )
        return qr.items().mapNotNull { row -> row["uri"]?.toString() }.distinct()
    }

    private fun deleteByUriPrefix(prefix: String) {
        val cypher = """
            MATCH (n:ContentElement)
            WHERE n.uri STARTS WITH ${'$'}prefix
            DETACH DELETE n
        """.trimIndent().let { "\n$it" }
        val spec = QuerySpecification.withStatement(cypher).bind(mapOf("prefix" to prefix))
        persistenceManager.execute(spec)
    }

    data class PurgePreviewResult(
        val appliedUriPrefix: String,
        val matchCount: Long,
        val sampleUris: List<String>,
    )

    data class PurgeExecuteResult(
        val appliedUriPrefix: String,
        val deletedCount: Long,
    )

    data class GitRevisionResetResult(
        val absolutePath: String?,
        val removed: Boolean,
        val message: String,
    )

    companion object {
        private const val MIN_PREFIX_LENGTH = 8
    }
}
