package com.embabel.guide.spdd

import com.embabel.agent.rag.service.NamedEntityDataRepository
import com.embabel.agent.rag.service.support.InMemoryNamedEntityDataRepository
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.guide.ContentConfig
import com.embabel.guide.GuideProperties
import com.embabel.guide.VersionedContentConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path

/**
 * MCP tool contract: every tool returns JSON, and validation failures surface
 * as `{"error": …}` payloads rather than exceptions escaping to the protocol layer.
 */
class SpddDomainToolsTest {

  @TempDir
  lateinit var tempDir: Path

  private val objectMapper = ObjectMapper()
  private lateinit var tools: SpddDomainTools

  @BeforeEach
  fun setUp() {
    val root = buildProject(tempDir.resolve("project"))
    val service = SpddMarkdownProjectionService(guideProperties(root.toString()), inMemoryRepository())
    service.load()
    tools = SpddDomainTools(service, objectMapper)
  }

  @Test
  fun `workSubgraph returns typed neighbors as json`() {
    val json = objectMapper.readTree(tools.workSubgraph("SPIKE-FIX-001-retrieval-fixture"))
    assertTrue(json["found"].asBoolean())
    assertEquals("SPIKE-FIX-001-retrieval-fixture:canvas", json["canvases"][0]["id"].asText())
    assertEquals("retry storms", json["pitfalls"][0]["name"].asText())
  }

  @Test
  fun `workSubgraph reports not found without error`() {
    val json = objectMapper.readTree(tools.workSubgraph("FEAT-999-unknown"))
    assertFalse(json["found"].asBoolean())
    assertFalse(json.has("error"))
  }

  @Test
  fun `workSubgraph surfaces blank work id as error payload`() {
    val json = objectMapper.readTree(tools.workSubgraph("   "))
    assertTrue(json.has("error"))
  }

  @Test
  fun `projectionStats counts all schema labels`() {
    val json = objectMapper.readTree(tools.projectionStats())
    assertEquals(1, json["workIdCount"].asInt())
    assertEquals(1, json["pitfallCount"].asInt())
    assertEquals("__Entity__", json["entityLabel"].asText())
  }

  @Test
  fun `findByLabel lists entities for a schema label`() {
    val json = objectMapper.readTree(tools.findByLabel("WorkId"))
    assertEquals(1, json.size())
    assertEquals("SPIKE-FIX-001-retrieval-fixture", json[0]["id"].asText())
  }

  @Test
  fun `findByLabel surfaces unknown label as error payload`() {
    val json = objectMapper.readTree(tools.findByLabel("DROP TABLE"))
    assertTrue(json.has("error"))
    assertTrue(json["error"].asText().contains("Known labels"))
  }

  @Test
  fun `areaLessons returns cross-run lessons as json`() {
    val json = objectMapper.readTree(tools.areaLessons("src/billing"))
    assertTrue(json["found"].asBoolean())
    assertEquals("retry storms", json["pitfalls"][0]["name"].asText())
  }

  @Test
  fun `areaLessons surfaces blank area as error payload`() {
    val json = objectMapper.readTree(tools.areaLessons(""))
    assertTrue(json.has("error"))
  }

  private fun buildProject(root: Path): Path {
    Files.createDirectories(root.resolve("spdd/canvas"))
    Files.createDirectories(root.resolve("agent-context/memory"))
    Files.writeString(
      root.resolve("spdd/canvas/SPIKE-FIX-001-retrieval-fixture.md"),
      """
        # REASONS Canvas: SPIKE-FIX-001-retrieval-fixture - Retrieval experiment fixture

        ## Metadata

        - Work ID: SPIKE-FIX-001-retrieval-fixture
      """.trimIndent(),
    )
    Files.writeString(
      root.resolve("agent-context/memory/context-index.md"),
      """
        # Context Index

        | Area | Kind | Work ID | Phase | Timestamp | Source | Entry |
        |------|------|---------|-------|-----------|--------|-------|
        | src/billing | pitfall | SPIKE-FIX-001-retrieval-fixture | code | 2026-07-05T13:00:00Z | pitfalls.md | retry storms |
      """.trimIndent(),
    )
    return root
  }

  private fun guideProperties(defaultRootPath: String) =
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
      spddProjection = GuideProperties.SpddProjection(enabled = true, defaultRootPath = defaultRootPath),
    )

  private fun inMemoryRepository(): NamedEntityDataRepository {
    val embeddingService = Mockito.mock(EmbeddingService::class.java)
    Mockito.`when`(embeddingService.embed(Mockito.anyString())).thenReturn(floatArrayOf(0.1f, 0.2f, 0.3f))
    return InMemoryNamedEntityDataRepository(
      SpddEntityDictionary.create(),
      embeddingService,
      ObjectMapper(),
    )
  }
}
