package com.embabel.guide.spdd

import com.embabel.agent.rag.model.NamedEntityData
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
 */
@Service
@ConditionalOnProperty(prefix = "guide.spdd-projection", name = ["enabled"], havingValue = "true")
class SpddMarkdownProjectionService(
    private val guideProperties: GuideProperties,
    private val entityRepository: NamedEntityDataRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun load(rootPath: String? = null): SpddProjectionResult {
        val projection = guideProperties.spddProjection
        if (!projection.enabled) {
            throw IllegalStateException("guide.spdd-projection.enabled is false")
        }
        val root = Path.of(guideProperties.resolvePath(rootPath ?: projection.defaultRootPath))
        require(Files.isDirectory(root)) { "SPDD projection root not found: $root" }

        var workIds = 0
        var canvases = 0
        var areas = 0
        var operations = 0
        var decisions = 0
        var pitfalls = 0
        var relationships = 0

        val canvasDir = root.resolve("spdd/canvas")
        if (Files.isDirectory(canvasDir)) {
            Files.list(canvasDir).use { stream ->
                stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".md") }
                    .forEach { path ->
                        val r = projectCanvas(root, path)
                        workIds += r.workIds
                        canvases += r.canvases
                        operations += r.operations
                        relationships += r.relationships
                    }
            }
        }

        val contextIndex = root.resolve("agent-context/memory/context-index.md")
        if (Files.isRegularFile(contextIndex)) {
            val r = projectContextIndex(root, contextIndex)
            areas += r.areas
            decisions += r.decisions
            pitfalls += r.pitfalls
            relationships += r.relationships
        }

        log.info(
            "SPDD projection complete root={} workIds={} canvases={} areas={} ops={} rels={}",
            root, workIds, canvases, areas, operations, relationships,
        )

        return SpddProjectionResult(
            rootPath = root.toString(),
            workIds = workIds,
            canvases = canvases,
            areas = areas,
            operations = operations,
            decisions = decisions,
            pitfalls = pitfalls,
            relationships = relationships,
        )
    }

    fun entityCountByLabel(label: String): Int =
        entityRepository.findByLabel(label).size

    private data class PartialResult(
        val workIds: Int = 0,
        val canvases: Int = 0,
        val areas: Int = 0,
        val operations: Int = 0,
        val decisions: Int = 0,
        val pitfalls: Int = 0,
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

    private fun projectContextIndex(root: Path, indexPath: Path): PartialResult {
        val lines = Files.readAllLines(indexPath)
        var areas = 0
        var decisions = 0
        var pitfalls = 0
        var rels = 0
        val seenAreas = mutableSetOf<String>()

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
            entityRepository.mergeRelationship(workRef, areaRef, RelationshipData("area", emptyMap()))
            rels++

            when (kind) {
                "decision" -> {
                    saveEntity(
                        id = "decision:$workId:$area:${cols[5]}",
                        uri = indexPath.toUri().toString() + "#$workId-decision-$areas",
                        name = cols.getOrElse(6) { "decision" },
                        description = cols.getOrElse(6) { kind },
                        label = "Decision",
                        properties = mapOf("workId" to workId, "area" to area, "source" to cols[5]),
                    )
                    decisions++
                }
                "pitfall" -> {
                    saveEntity(
                        id = "pitfall:$workId:$area:${cols[5]}",
                        uri = indexPath.toUri().toString() + "#$workId-pitfall-$pitfalls",
                        name = cols.getOrElse(6) { "pitfall" },
                        description = cols.getOrElse(6) { kind },
                        label = "Pitfall",
                        properties = mapOf("workId" to workId, "area" to area, "source" to cols[5]),
                    )
                    pitfalls++
                }
            }
        }

        return PartialResult(areas = areas, decisions = decisions, pitfalls = pitfalls, relationships = rels)
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
            linkedDomainType = SpddEntityDictionary.create().domainTypeForLabels(setOf(label)),
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
        private val WORK_ID_PATTERN = Regex("""- Work ID:\s*(\S+)""")
        private val CANVAS_TITLE_PATTERN = Regex("""#\s*REASONS Canvas:\s*([^-]+)\s*-\s*(.+)""")
    }
}
