package com.embabel.guide.spdd

import com.embabel.agent.rag.model.NamedEntityData
import com.embabel.agent.rag.model.RelationshipDirection
import com.embabel.agent.rag.model.SimpleNamedEntityData
import com.embabel.agent.rag.service.NamedEntityDataRepository
import com.embabel.agent.rag.service.RelationshipData
import com.embabel.agent.rag.service.RetrievableIdentifier
import com.embabel.guide.GuideProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Leg 3 ingest: project structured SPDD markdown into Neo4j [NamedEntityData.ENTITY_LABEL] nodes.
 *
 * Coexists with leg 2 RAG chunk ingest ([com.embabel.guide.rag.DataManager]) — same Neo4j store,
 * different node layer. Does **not** use the DICE proposition extraction pipeline.
 *
 * Persist contract: markdown is source of truth; [load] is idempotent merge-by-id via
 * [NamedEntityDataRepository.save] + [NamedEntityDataRepository.mergeRelationship].
 * Retrieve contract: [subgraphForWorkId] walks typed edges from the WorkId join key.
 */
@Service
@ConditionalOnProperty(prefix = "guide.spdd-projection", name = ["enabled"], havingValue = "true")
class SpddMarkdownProjectionService(
    private val guideProperties: GuideProperties,
    private val entityRepository: NamedEntityDataRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val entityDictionary = SpddEntityDictionary.create()

    fun load(rootPath: String? = null): SpddProjectionResult {
        val projection = guideProperties.spddProjection
        if (!projection.enabled) {
            throw IllegalStateException("guide.spdd-projection.enabled is false")
        }
        val root = resolveRoot(rootPath)
        require(Files.isDirectory(root)) { "SPDD projection root not found: $root" }

        var workIds = 0
        var canvases = 0
        var areas = 0
        var operations = 0
        var decisions = 0
        var pitfalls = 0
        var patterns = 0
        var relationships = 0
        var skippedFiles = 0

        val canvasDir = root.resolve("spdd/canvas")
        if (Files.isDirectory(canvasDir)) {
            Files.list(canvasDir).use { stream ->
                stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".md") }
                    .sorted()
                    .forEach { path ->
                        // A single malformed canvas must not fail the whole load.
                        val r = runCatching { projectCanvas(root, path) }
                            .onFailure { log.warn("SPDD projection: skipping canvas {}: {}", path, it.message) }
                            .getOrNull()
                        if (r == null) {
                            skippedFiles++
                        } else {
                            workIds += r.workIds
                            canvases += r.canvases
                            operations += r.operations
                            relationships += r.relationships
                        }
                    }
            }
        }

        // Prefer lean stay-set index; fall back / merge legacy agent-context index (#89).
        val contextIndexes = listOf(
            root.resolve("spdd/memory/context-index.md"),
            root.resolve("agent-context/memory/context-index.md"),
        ).filter { Files.isRegularFile(it) }
        val seenAreas = mutableSetOf<String>()
        val seenLessons = mutableSetOf<String>()
        if (contextIndexes.isEmpty()) {
            log.debug("SPDD projection: no context-index.md under spdd/memory or agent-context/memory")
        }
        for (contextIndex in contextIndexes) {
            val r = runCatching { projectContextIndex(root, contextIndex, seenAreas, seenLessons) }
                .onFailure { log.warn("SPDD projection: skipping context index {}: {}", contextIndex, it.message) }
                .getOrNull()
            if (r == null) {
                skippedFiles++
            } else {
                areas += r.areas
                decisions += r.decisions
                pitfalls += r.pitfalls
                patterns += r.patterns
                relationships += r.relationships
            }
        }

        log.info(
            "SPDD projection complete root={} workIds={} canvases={} areas={} ops={} rels={} skipped={}",
            root, workIds, canvases, areas, operations, relationships, skippedFiles,
        )

        return SpddProjectionResult(
            rootPath = root.toString(),
            workIds = workIds,
            canvases = canvases,
            areas = areas,
            operations = operations,
            decisions = decisions,
            pitfalls = pitfalls,
            patterns = patterns,
            relationships = relationships,
            skippedFiles = skippedFiles,
        )
    }

    /**
     * Resolve the projection root. Overrides are only honoured when the resolved path lives
     * under the default root or one of `guide.spdd-projection.allowed-roots` — the load
     * endpoint is reachable without auth, so arbitrary filesystem roots must be rejected.
     */
    private fun resolveRoot(rootPath: String?): Path {
        val projection = guideProperties.spddProjection
        val defaultRoot = Path.of(guideProperties.resolvePath(projection.defaultRootPath)).normalize()
        val requested = rootPath?.trim()?.takeIf { it.isNotEmpty() } ?: return defaultRoot
        val override = Path.of(guideProperties.resolvePath(requested)).normalize()
        // listOf() wrapper matters: Path is Iterable<Path>, so `list + path` would
        // concatenate the path's COMPONENTS, not append the path itself.
        val allowedRoots = projection.allowedRoots
            .map { Path.of(guideProperties.resolvePath(it)).normalize() } + listOf(defaultRoot)
        require(allowedRoots.any { override.startsWith(it) }) {
            "rootPath override '$override' is not under an allowed root $allowedRoots; " +
                "configure guide.spdd-projection.allowed-roots to permit it"
        }
        return override
    }

    fun entityCountByLabel(label: String): Int =
        entityRepository.findByLabel(label).size

    /**
     * List projected entities by schema label. The label must be part of the
     * [SpddEntityDictionary] schema and results are capped at [maxResults]
     * (bounded by [MAX_LIST_RESULTS]) to keep MCP/HTTP payloads predictable.
     */
    fun listByLabel(label: String, maxResults: Int = DEFAULT_LIST_RESULTS): List<SpddEntitySummary> {
        val normalized = requireKnownLabel(label)
        val cap = maxResults.coerceIn(1, MAX_LIST_RESULTS)
        return entityRepository.findByLabel(normalized).take(cap).map { toSummary(it) }
    }

    private fun requireKnownLabel(label: String): String {
        val normalized = label.trim()
        require(normalized in SpddEntityDictionary.knownLabels) {
            "Unknown entity label '$normalized'. Known labels: ${SpddEntityDictionary.knownLabels.sorted()}"
        }
        return normalized
    }

    /**
     * Domain retrieval by Work ID join key: WorkId → canvas / area / decision / pitfall neighbors.
     * Auditability: each neighbor is included because of a typed relationship, not cosine.
     */
    fun subgraphForWorkId(workId: String): SpddWorkIdSubgraph {
        require(workId.isNotBlank()) { "workId must not be blank" }
        val work = entityRepository.findById(workId)
            ?: return SpddWorkIdSubgraph(workId = workId, found = false)
        val workRef = RetrievableIdentifier(workId, "WorkId")
        val canvases = entityRepository.findRelated(workRef, REL_CANVAS, RelationshipDirection.OUTGOING)
        val areas = entityRepository.findRelated(workRef, REL_AREA, RelationshipDirection.OUTGOING)
        val decisions = entityRepository.findRelated(workRef, REL_DECISION, RelationshipDirection.OUTGOING)
        val pitfalls = entityRepository.findRelated(workRef, REL_PITFALL, RelationshipDirection.OUTGOING)
        val patterns = entityRepository.findRelated(workRef, REL_PATTERN, RelationshipDirection.OUTGOING)
        return SpddWorkIdSubgraph(
            workId = workId,
            found = true,
            work = toSummary(work),
            canvases = canvases.map { toSummary(it) },
            areas = areas.map { toSummary(it) },
            decisions = decisions.map { toSummary(it) },
            pitfalls = pitfalls.map { toSummary(it) },
            patterns = patterns.map { toSummary(it) },
        )
    }

    /**
     * Cross-run lesson retrieval by code area: every Decision / Pitfall / Pattern recorded
     * by *any* Work ID against this area (via incoming `about` edges), plus the Work IDs
     * that touched it (via incoming `area` edges). This is the "I'm about to modify area X,
     * what did previous runs learn?" query.
     */
    fun lessonsForArea(area: String): SpddAreaLessons {
        require(area.isNotBlank()) { "area must not be blank" }
        val normalized = area.trim().removePrefix("area:")
        val areaId = "area:$normalized"
        val areaEntity = entityRepository.findById(areaId)
            ?: return SpddAreaLessons(area = normalized, found = false)
        val areaRef = RetrievableIdentifier(areaId, "Area")
        val lessons = entityRepository.findRelated(areaRef, REL_ABOUT, RelationshipDirection.INCOMING)
        val works = entityRepository.findRelated(areaRef, REL_AREA, RelationshipDirection.INCOMING)
        return SpddAreaLessons(
            area = normalized,
            found = true,
            areaEntity = toSummary(areaEntity),
            workIds = works.map { toSummary(it) },
            decisions = lessons.filter { "Decision" in it.labels() }.map { toSummary(it) },
            pitfalls = lessons.filter { "Pitfall" in it.labels() }.map { toSummary(it) },
            patterns = lessons.filter { "Pattern" in it.labels() }.map { toSummary(it) },
        )
    }

    private fun toSummary(entity: NamedEntityData): SpddEntitySummary =
        SpddEntitySummary(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            labels = entity.labels().toList(),
            uri = entity.uri,
        )

    private data class PartialResult(
        val workIds: Int = 0,
        val canvases: Int = 0,
        val areas: Int = 0,
        val operations: Int = 0,
        val decisions: Int = 0,
        val pitfalls: Int = 0,
        val patterns: Int = 0,
        val relationships: Int = 0,
    )

    private fun projectCanvas(root: Path, canvasPath: Path): PartialResult {
        val text = Files.readString(canvasPath)
        val workId = WORK_ID_PATTERN.find(text)?.groupValues?.get(1)?.trim()
            ?: return PartialResult()
        val title = CANVAS_TITLE_PATTERN.find(text)?.groupValues?.get(2)?.trim() ?: workId
        val uri = canvasPath.toUri().toString()

        val workEntity = saveEntity(
            id = workId,
            uri = uri,
            name = workId,
            description = title,
            label = "WorkId",
            properties = mapOf("path" to canvasPath.toString()),
        )
        val canvasEntity = saveEntity(
            id = "$workId:canvas",
            uri = uri,
            name = title,
            description = "REASONS canvas for $workId",
            label = "Canvas",
            properties = mapOf("path" to canvasPath.toString()),
        )
        link(workEntity, canvasEntity, "canvas")

        return PartialResult(workIds = 1, canvases = 1, operations = 0, relationships = 1)
    }

    private fun projectContextIndex(
        root: Path,
        indexPath: Path,
        seenAreas: MutableSet<String> = mutableSetOf(),
        seenLessons: MutableSet<String> = mutableSetOf(),
    ): PartialResult {
        val lines = Files.readAllLines(indexPath)
        var areas = 0
        var decisions = 0
        var pitfalls = 0
        var patterns = 0
        var rels = 0

        for (line in lines) {
            if (!line.startsWith("|") || line.contains("Area | Kind") || line.matches(Regex("^\\|[-| ]+\\|$"))) {
                continue
            }
            val cols = line.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.size < 7) continue
            val area = cols[0]
            val kind = cols[1].lowercase()
            val workId = cols[2]
            if (area.isBlank() || workId.isBlank()) continue

            if (seenAreas.add(area)) {
                saveEntity(
                    id = "area:$area",
                    uri = indexPath.toUri().toString() + "#area-$area",
                    name = area,
                    description = "Code area $area",
                    label = "Area",
                    properties = mapOf("area" to area),
                )
                areas++
            }

            val workRef = RetrievableIdentifier(workId, "WorkId")
            val areaRef = RetrievableIdentifier("area:$area", "Area")
            entityRepository.mergeRelationship(workRef, areaRef, RelationshipData(REL_AREA, emptyMap()))
            rels++

            val lessonLabel = LESSON_KIND_LABELS[kind] ?: continue
            val relName = kind // decision / pitfall / pattern — matches REL_* constants
            val lessonId = "$kind:$workId:$area:${cols[5]}"
            if (!seenLessons.add(lessonId)) {
                continue
            }
            val lesson = saveEntity(
                id = lessonId,
                uri = indexPath.toUri().toString() + "#$workId-$kind",
                name = cols.getOrElse(6) { kind },
                description = cols.getOrElse(6) { kind },
                label = lessonLabel,
                properties = mapOf("workId" to workId, "area" to area, "source" to cols[5]),
            )
            val lessonRef = RetrievableIdentifier(lesson.id, lessonLabel)
            // WorkId → lesson: "what did this work record?"
            entityRepository.mergeRelationship(workRef, lessonRef, RelationshipData(relName, emptyMap()))
            // Lesson → Area: "what has ANY work recorded against this area?" (cross-run lookup)
            entityRepository.mergeRelationship(lessonRef, areaRef, RelationshipData(REL_ABOUT, emptyMap()))
            rels += 2
            when (lessonLabel) {
                "Decision" -> decisions++
                "Pitfall" -> pitfalls++
                "Pattern" -> patterns++
            }
        }

        return PartialResult(
            areas = areas,
            decisions = decisions,
            pitfalls = pitfalls,
            patterns = patterns,
            relationships = rels,
        )
    }

    private fun saveEntity(
        id: String,
        uri: String,
        name: String,
        description: String,
        label: String,
        properties: Map<String, Any> = emptyMap(),
    ): SimpleNamedEntityData {
        val entity = SimpleNamedEntityData(
            id = id,
            uri = uri,
            name = name,
            description = description,
            labels = setOf(label, NamedEntityData.ENTITY_LABEL),
            properties = properties,
            metadata = emptyMap(),
            linkedDomainType = entityDictionary.domainTypeForLabels(setOf(label)),
        )
        entityRepository.save(entity)
        return entity
    }

    private fun link(from: SimpleNamedEntityData, to: SimpleNamedEntityData, rel: String) {
        entityRepository.mergeRelationship(
            RetrievableIdentifier(from.id, from.labels.first { it != NamedEntityData.ENTITY_LABEL }),
            RetrievableIdentifier(to.id, to.labels.first { it != NamedEntityData.ENTITY_LABEL }),
            RelationshipData(rel, emptyMap()),
        )
    }

    companion object {
        const val DEFAULT_LIST_RESULTS = 50
        const val MAX_LIST_RESULTS = 200

        /** Typed relationship names of the SPDD domain graph. */
        const val REL_CANVAS = "canvas"
        const val REL_AREA = "area"
        const val REL_DECISION = "decision"
        const val REL_PITFALL = "pitfall"
        const val REL_PATTERN = "pattern"

        /** Lesson → Area edge: enables cross-WorkId lookup by code area. */
        const val REL_ABOUT = "about"

        /** context-index `Kind` column values that become first-class lesson entities. */
        private val LESSON_KIND_LABELS = mapOf(
            "decision" to "Decision",
            "pitfall" to "Pitfall",
            "pattern" to "Pattern",
        )

        private val WORK_ID_PATTERN = Regex("""- Work ID:\s*(\S+)""")
        private val CANVAS_TITLE_PATTERN = Regex("""#\s*REASONS Canvas:\s*([^-]+)\s*-\s*(.+)""")
    }
}
