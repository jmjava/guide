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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path

class SpddMarkdownProjectionServiceTest {

  @TempDir
  lateinit var tempDir: Path

  // ---------------------------------------------------------------- persist

  @Test
  fun `load projects work id canvas and area from markdown fixture`() {
    val fixtureRoot = Path.of("src/test/resources/spdd-fixture").toAbsolutePath()
    val repo = inMemoryRepository()
    val service = service(repo, fixtureRoot.toString())

    val result = service.load()

    assertEquals(fixtureRoot.normalize().toString(), result.rootPath)
    assertTrue(result.workIds >= 1)
    assertTrue(result.canvases >= 1)
    assertTrue(result.areas >= 1)
    assertEquals(0, result.skippedFiles)
    assertTrue(service.entityCountByLabel("WorkId") >= 1)
    assertTrue(repo.findByLabel("Area").any { it.name == "src/billing" })
  }

  @Test
  fun `load is idempotent - reloading does not duplicate entities`() {
    val root = copyFixtureTo(tempDir.resolve("project"))
    val repo = inMemoryRepository()
    val service = service(repo, root.toString())

    val first = service.load()
    val countsAfterFirst = repo.findByLabel("WorkId").size + repo.findByLabel("Canvas").size +
      repo.findByLabel("Area").size + repo.findByLabel("Pitfall").size
    val second = service.load()
    val countsAfterSecond = repo.findByLabel("WorkId").size + repo.findByLabel("Canvas").size +
      repo.findByLabel("Area").size + repo.findByLabel("Pitfall").size

    assertEquals(first.workIds, second.workIds)
    assertEquals(countsAfterFirst, countsAfterSecond, "merge-by-id must not duplicate on reload")
  }

  @Test
  fun `load ignores canvas without work id and projects the rest`() {
    val root = copyFixtureTo(tempDir.resolve("project"))
    Files.writeString(root.resolve("spdd/canvas/no-work-id.md"), "# Not a canvas at all\n")
    val service = service(inMemoryRepository(), root.toString())

    val result = service.load()

    assertEquals(1, result.workIds, "valid canvas still projected")
    assertEquals(1, result.canvases)
  }

  @Test
  fun `load projects decision pitfall and pattern lessons with about edges`() {
    val root = buildProject(
      tempDir.resolve("lessons"),
      canvas = CANVAS,
      contextIndex = """
        # Context Index

        | Area | Kind | Work ID | Phase | Timestamp | Source | Entry |
        |------|------|---------|-------|-----------|--------|-------|
        | src/billing | decision | SPIKE-FIX-001-retrieval-fixture | code | 2026-07-05T13:00:00Z | adr.md | use idempotency keys |
        | src/billing | pitfall | SPIKE-FIX-001-retrieval-fixture | code | 2026-07-05T13:00:00Z | pitfalls.md | retry storms |
        | src/billing | pattern | SPIKE-FIX-001-retrieval-fixture | code | 2026-07-05T13:00:00Z | patterns.md | outbox pattern |
      """.trimIndent(),
    )
    val repo = inMemoryRepository()
    val service = service(repo, root.toString())

    val result = service.load()

    assertEquals(1, result.decisions)
    assertEquals(1, result.pitfalls)
    assertEquals(1, result.patterns)

    val subgraph = service.subgraphForWorkId("SPIKE-FIX-001-retrieval-fixture")
    assertTrue(subgraph.found)
    assertEquals(listOf("use idempotency keys"), subgraph.decisions.map { it.name })
    assertEquals(listOf("retry storms"), subgraph.pitfalls.map { it.name })
    assertEquals(listOf("outbox pattern"), subgraph.patterns.map { it.name })
  }

  // ------------------------------------------------------- root path guard

  @Test
  fun `load rejects root override outside allowed roots`() {
    val root = copyFixtureTo(tempDir.resolve("project"))
    val outside = Files.createDirectory(tempDir.resolve("outside"))
    val service = service(inMemoryRepository(), root.toString())

    val e = assertThrows<IllegalArgumentException> { service.load(outside.toString()) }
    assertTrue(e.message!!.contains("not under an allowed root"))
  }

  @Test
  fun `load accepts override under an explicitly allowed root`() {
    val defaultRoot = copyFixtureTo(tempDir.resolve("default"))
    val allowedParent = tempDir.resolve("allowed")
    val otherProject = copyFixtureTo(allowedParent.resolve("other"))
    val service = service(
      inMemoryRepository(),
      defaultRoot.toString(),
      allowedRoots = listOf(allowedParent.toString()),
    )

    val result = service.load(otherProject.toString())

    assertEquals(otherProject.normalize().toString(), result.rootPath)
  }

  @Test
  fun `load treats blank override as default root`() {
    val root = copyFixtureTo(tempDir.resolve("project"))
    val service = service(inMemoryRepository(), root.toString())

    val result = service.load("   ")

    assertEquals(root.normalize().toString(), result.rootPath)
  }

  @Test
  fun `load rejects missing root directory`() {
    val service = service(inMemoryRepository(), tempDir.resolve("does-not-exist").toString())
    assertThrows<IllegalArgumentException> { service.load() }
  }

  // --------------------------------------------------------------- retrieve

  @Test
  fun `subgraph walk returns canvas and area neighbors`() {
    val root = copyFixtureTo(tempDir.resolve("project"))
    val service = service(inMemoryRepository(), root.toString())
    service.load()

    val subgraph = service.subgraphForWorkId("SPIKE-FIX-001-retrieval-fixture")

    assertTrue(subgraph.found)
    assertTrue(subgraph.canvases.isNotEmpty())
    assertTrue(subgraph.areas.any { it.name == "src/billing" })
    assertTrue(subgraph.pitfalls.any { it.name == "idempotency key" })
  }

  @Test
  fun `subgraph for unknown work id reports not found`() {
    val service = service(inMemoryRepository(), copyFixtureTo(tempDir.resolve("p")).toString())
    service.load()

    val subgraph = service.subgraphForWorkId("FEAT-999-nope")

    assertFalse(subgraph.found)
  }

  @Test
  fun `subgraph rejects blank work id`() {
    val service = service(inMemoryRepository(), copyFixtureTo(tempDir.resolve("p")).toString())
    assertThrows<IllegalArgumentException> { service.subgraphForWorkId("  ") }
  }

  @Test
  fun `lessonsForArea returns cross-run lessons and touching work ids`() {
    val root = buildProject(
      tempDir.resolve("cross"),
      canvas = CANVAS,
      contextIndex = """
        # Context Index

        | Area | Kind | Work ID | Phase | Timestamp | Source | Entry |
        |------|------|---------|-------|-----------|--------|-------|
        | src/billing | pitfall | SPIKE-FIX-001-retrieval-fixture | code | 2026-07-05T13:00:00Z | pitfalls.md | retry storms |
        | src/billing | decision | FEAT-002-other-work | code | 2026-07-06T13:00:00Z | adr.md | use idempotency keys |
      """.trimIndent(),
    )
    val service = service(inMemoryRepository(), root.toString())
    service.load()

    val lessons = service.lessonsForArea("src/billing")

    assertTrue(lessons.found)
    // Lessons from BOTH work ids arrive via the area, which is the cross-run guarantee.
    assertEquals(listOf("retry storms"), lessons.pitfalls.map { it.name })
    assertEquals(listOf("use idempotency keys"), lessons.decisions.map { it.name })
    assertTrue(lessons.workIds.any { it.id == "SPIKE-FIX-001-retrieval-fixture" })
  }

  @Test
  fun `lessonsForArea for unknown area reports not found`() {
    val service = service(inMemoryRepository(), copyFixtureTo(tempDir.resolve("p")).toString())
    service.load()

    assertFalse(service.lessonsForArea("does/not/exist").found)
  }

  @Test
  fun `lessonsForArea rejects blank area`() {
    val service = service(inMemoryRepository(), copyFixtureTo(tempDir.resolve("p")).toString())
    assertThrows<IllegalArgumentException> { service.lessonsForArea("") }
  }

  @Test
  fun `listByLabel rejects labels outside the schema`() {
    val service = service(inMemoryRepository(), copyFixtureTo(tempDir.resolve("p")).toString())
    val e = assertThrows<IllegalArgumentException> { service.listByLabel("ContentElement") }
    assertTrue(e.message!!.contains("Known labels"))
  }

  @Test
  fun `listByLabel caps results`() {
    val root = copyFixtureTo(tempDir.resolve("p"))
    val service = service(inMemoryRepository(), root.toString())
    service.load()

    assertEquals(1, service.listByLabel("WorkId", maxResults = 1).size)
    // Out-of-range requests are clamped, not rejected.
    assertTrue(service.listByLabel("WorkId", maxResults = 0).isNotEmpty())
    assertTrue(service.listByLabel("WorkId", maxResults = 999999).size <= SpddMarkdownProjectionService.MAX_LIST_RESULTS)
  }

  // ---------------------------------------------------------------- helpers

  private fun service(
    repo: NamedEntityDataRepository,
    defaultRootPath: String,
    allowedRoots: List<String> = emptyList(),
  ): SpddMarkdownProjectionService =
    SpddMarkdownProjectionService(guideProperties(defaultRootPath, allowedRoots), repo)

  private fun guideProperties(defaultRootPath: String, allowedRoots: List<String> = emptyList()) =
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
      spddProjection = GuideProperties.SpddProjection(
        enabled = true,
        defaultRootPath = defaultRootPath,
        allowedRoots = allowedRoots,
      ),
    )

  private fun copyFixtureTo(target: Path): Path {
    val fixture = Path.of("src/test/resources/spdd-fixture")
    Files.walk(fixture).forEach { source ->
      val dest = target.resolve(fixture.relativize(source))
      if (Files.isDirectory(source)) {
        Files.createDirectories(dest)
      } else {
        Files.createDirectories(dest.parent)
        Files.copy(source, dest)
      }
    }
    return target
  }

  private fun buildProject(root: Path, canvas: String, contextIndex: String): Path {
    Files.createDirectories(root.resolve("spdd/canvas"))
    Files.createDirectories(root.resolve("agent-context/memory"))
    Files.writeString(root.resolve("spdd/canvas/SPIKE-FIX-001-retrieval-fixture.md"), canvas)
    Files.writeString(root.resolve("agent-context/memory/context-index.md"), contextIndex)
    return root
  }

  private fun inMemoryRepository(): NamedEntityDataRepository {
    val embeddingService = Mockito.mock(EmbeddingService::class.java)
    Mockito.`when`(embeddingService.embed(Mockito.anyString())).thenReturn(floatArrayOf(0.1f, 0.2f, 0.3f))
    return InMemoryNamedEntityDataRepository(
      SpddEntityDictionary.create(),
      embeddingService,
      ObjectMapper(),
    )
  }

  companion object {
    private val CANVAS = """
      # REASONS Canvas: SPIKE-FIX-001-retrieval-fixture - Retrieval experiment fixture

      ## Metadata

      - Work ID: SPIKE-FIX-001-retrieval-fixture
      - Work Type: Spike
      - Status: Complete
    """.trimIndent()
  }
}
