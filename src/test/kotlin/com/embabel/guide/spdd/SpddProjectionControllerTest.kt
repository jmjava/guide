package com.embabel.guide.spdd

import com.embabel.agent.rag.service.NamedEntityDataRepository
import com.embabel.agent.rag.service.support.InMemoryNamedEntityDataRepository
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.guide.ContentConfig
import com.embabel.guide.GuideProperties
import com.embabel.guide.VersionedContentConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path

/**
 * Wiring test: real service + in-memory repository behind the controller,
 * standalone MockMvc (no Spring context). Verifies status-code mapping added
 * in the hardening pass (400 for validation, 404 for unknown ids).
 */
class SpddProjectionControllerTest {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var mockMvc: MockMvc
  private lateinit var root: Path

  @BeforeEach
  fun setUp() {
    root = buildProject(tempDir.resolve("project"))
    val service = SpddMarkdownProjectionService(guideProperties(root.toString()), inMemoryRepository())
    mockMvc = MockMvcBuilders.standaloneSetup(SpddProjectionController(service)).build()
  }

  @Test
  fun `load returns counts including lessons`() {
    mockMvc.perform(post("/api/v1/data/spdd-projection/load").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.workIds").value(1))
      .andExpect(jsonPath("$.canvases").value(1))
      .andExpect(jsonPath("$.pitfalls").value(1))
      .andExpect(jsonPath("$.skippedFiles").value(0))
  }

  @Test
  fun `load with disallowed root override returns 400 with error body`() {
    val outside = Files.createDirectory(tempDir.resolve("outside"))
    mockMvc.perform(
      post("/api/v1/data/spdd-projection/load")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"rootPath": "$outside"}"""),
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `stats reports counts by label`() {
    mockMvc.perform(post("/api/v1/data/spdd-projection/load").contentType(MediaType.APPLICATION_JSON))
    mockMvc.perform(get("/api/v1/data/spdd-projection/stats"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.workIdCount").value(1))
      .andExpect(jsonPath("$.pitfallCount").value(1))
      .andExpect(jsonPath("$.entityLabel").value("__Entity__"))
  }

  @Test
  fun `work subgraph returns 200 with typed neighbors`() {
    mockMvc.perform(post("/api/v1/data/spdd-projection/load").contentType(MediaType.APPLICATION_JSON))
    mockMvc.perform(get("/api/v1/data/spdd-projection/work/SPIKE-FIX-001-retrieval-fixture"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.found").value(true))
      .andExpect(jsonPath("$.canvases[0].id").value("SPIKE-FIX-001-retrieval-fixture:canvas"))
      .andExpect(jsonPath("$.pitfalls[0].name").value("retry storms"))
  }

  @Test
  fun `work subgraph returns 404 for unknown work id`() {
    mockMvc.perform(get("/api/v1/data/spdd-projection/work/FEAT-999-unknown"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `area lessons returns cross-run lessons`() {
    mockMvc.perform(post("/api/v1/data/spdd-projection/load").contentType(MediaType.APPLICATION_JSON))
    mockMvc.perform(get("/api/v1/data/spdd-projection/area").param("name", "src/billing"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.found").value(true))
      .andExpect(jsonPath("$.pitfalls[0].name").value("retry storms"))
      .andExpect(jsonPath("$.workIds[0].id").value("SPIKE-FIX-001-retrieval-fixture"))
  }

  @Test
  fun `area lessons returns 404 for unknown area`() {
    mockMvc.perform(get("/api/v1/data/spdd-projection/area").param("name", "no/such/area"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `area lessons returns 400 for blank area`() {
    mockMvc.perform(get("/api/v1/data/spdd-projection/area").param("name", "  "))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").exists())
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
