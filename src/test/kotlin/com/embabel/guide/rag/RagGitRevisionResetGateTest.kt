package com.embabel.guide.rag

import com.embabel.guide.ContentConfig
import com.embabel.guide.GuideProperties
import com.embabel.guide.VersionedContentConfig
import org.drivine.manager.PersistenceManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Git revision reset is fail-closed: when incremental ingestion is absent or
 * disabled, the call returns without clearing stored HEAD or touching Neo4j.
 */
class RagGitRevisionResetGateTest {

    private val persistenceManager = mock(PersistenceManager::class.java)

    @Test
    fun `reset refuses when git ingestion is not configured`() {
        val service = RagContentMaintenanceService(guideProperties(gitIngestion = null), persistenceManager)

        val result = service.resetGitIngestionRevision("/tmp/repo")

        assertEquals(null, result.absolutePath)
        assertEquals(false, result.removed)
        assertEquals("guide.git-ingestion is not configured", result.message)
        verifyNoInteractions(persistenceManager)
    }

    @Test
    fun `reset refuses when git ingestion is disabled`() {
        val service = RagContentMaintenanceService(
            guideProperties(gitIngestion = GuideProperties.GitIngestion(enabled = false)),
            persistenceManager,
        )

        val result = service.resetGitIngestionRevision("/tmp/repo")

        assertEquals(null, result.absolutePath)
        assertEquals(false, result.removed)
        assertEquals("guide.git-ingestion.enabled is false", result.message)
        verifyNoInteractions(persistenceManager)
    }

    private fun guideProperties(gitIngestion: GuideProperties.GitIngestion?) =
        GuideProperties(
            reloadContentOnStartup = false,
            defaultPersona = "adaptive",
            projectsPath = ".",
            chunkerConfig = null,
            referencesFile = "references.yml",
            content = ContentConfig(
                versioned = VersionedContentConfig(baseUrl = "https://example.invalid/", versions = emptyList()),
                supplementary = emptyList(),
            ),
            toolPrefix = "",
            directories = emptyList(),
            toolGroups = emptySet(),
            gitIngestion = gitIngestion,
        )
}
