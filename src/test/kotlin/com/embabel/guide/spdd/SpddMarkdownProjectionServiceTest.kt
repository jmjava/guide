package com.embabel.guide.spdd

import com.embabel.agent.rag.service.NamedEntityDataRepository
import com.embabel.agent.rag.service.support.InMemoryNamedEntityDataRepository
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.guide.GuideProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path

class SpddMarkdownProjectionServiceTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `load projects work id canvas and area from markdown fixture`() {
    val fixtureRoot = Path.of("src/test/resources/spdd-fixture").toAbsolutePath()
    val repo = inMemoryRepository()
    val guideProperties = GuideProperties(
      reloadContentOnStartup = false,
      defaultPersona = "adaptive",
      projectsPath = ".",
      chunkerConfig = null,
      referencesFile = "references.yml",
      urls = emptyList(),
      toolPrefix = "",
      directories = emptyList(),
      toolGroups = emptySet(),
      spddProjection = GuideProperties.SpddProjection(enabled = true, defaultRootPath = fixtureRoot.toString()),
    )
    val service = SpddMarkdownProjectionService(guideProperties, repo)

    val result = service.load()

    assertEquals("SPIKE-FIX-001-retrieval-fixture", fixtureRoot.fileName.toString().let { _ ->
      // root is fixture path
      fixtureRoot.toString()
    }.let { result.rootPath })
    assertTrue(result.workIds >= 1)
    assertTrue(result.canvases >= 1)
    assertTrue(result.areas >= 1)
    assertTrue(service.entityCountByLabel("WorkId") >= 1)
    assertTrue(repo.findByLabel("Area").any { it.name == "src/billing" })
  }

  @Test
  fun `load writes to temp copy of fixture`() {
    val copy = tempDir.resolve("project")
    Files.walk(Path.of("src/test/resources/spdd-fixture")).forEach { source ->
      val target = copy.resolve(Path.of("src/test/resources/spdd-fixture").relativize(source))
      if (Files.isDirectory(source)) {
        Files.createDirectories(target)
      } else {
        Files.createDirectories(target.parent)
        Files.copy(source, target)
      }
    }
    val repo = inMemoryRepository()
    val guideProperties = GuideProperties(
      reloadContentOnStartup = false,
      defaultPersona = "adaptive",
      projectsPath = ".",
      chunkerConfig = null,
      referencesFile = "references.yml",
      urls = emptyList(),
      toolPrefix = "",
      directories = emptyList(),
      toolGroups = emptySet(),
      spddProjection = GuideProperties.SpddProjection(enabled = true, defaultRootPath = copy.toString()),
    )
    val service = SpddMarkdownProjectionService(guideProperties, repo)
    val result = service.load()
    assertEquals(1, result.workIds)
    assertEquals(1, result.canvases)
    assertTrue(result.areas >= 1)
  }

  private fun inMemoryRepository(): NamedEntityDataRepository {
    val embeddingService = Mockito.mock(EmbeddingService::class.java)
    return InMemoryNamedEntityDataRepository(
      SpddEntityDictionary.create(),
      embeddingService,
      ObjectMapper(),
      null,
    )
  }
}
