package com.embabel.guide.spdd

import com.embabel.agent.api.annotation.LlmTool
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * MCP / LLM tools for SPIKE-001 leg 3 (DICE domain graph).
 *
 * Complements `docs_*` chunk tools: these return typed `__Entity__` neighbors via
 * relationships (`canvas`, `area`), not embedding similarity.
 *
 * Exported via [com.embabel.agent.mcpserver.McpToolExport.fromToolObject], which discovers
 * Embabel [@LlmTool] methods (not Spring AI `@Tool`).
 */
class SpddDomainTools(
    private val projectionService: SpddMarkdownProjectionService,
    private val objectMapper: ObjectMapper,
) {

    @LlmTool(
        description = "DICE domain retrieve: WorkId subgraph via typed edges (canvas, area). " +
            "Use for auditable SPDD context by Work ID, not chunk similarity.",
    )
    fun workSubgraph(
        @LlmTool.Param(description = "Work ID, e.g. SPIKE-001-guide-rag-context-backend") workId: String,
    ): String = safeJson {
        projectionService.subgraphForWorkId(workId.trim())
    }

    @LlmTool(
        description = "DICE domain stats: counts of projected WorkId, Canvas, Area, Decision, Pitfall, " +
            "and Pattern entities in Neo4j.",
    )
    fun projectionStats(): String = safeJson {
        mapOf(
            "workIdCount" to projectionService.entityCountByLabel("WorkId"),
            "canvasCount" to projectionService.entityCountByLabel("Canvas"),
            "areaCount" to projectionService.entityCountByLabel("Area"),
            "decisionCount" to projectionService.entityCountByLabel("Decision"),
            "pitfallCount" to projectionService.entityCountByLabel("Pitfall"),
            "patternCount" to projectionService.entityCountByLabel("Pattern"),
            "entityLabel" to com.embabel.agent.rag.model.NamedEntityData.ENTITY_LABEL,
        )
    }

    @LlmTool(
        description = "DICE domain list: NamedEntity nodes by label (WorkId, Canvas, Area, Decision, Pitfall). " +
            "Results are capped; use workSubgraph for targeted retrieval.",
    )
    fun findByLabel(
        @LlmTool.Param(description = "Entity label, e.g. WorkId or Area") label: String,
    ): String = safeJson {
        projectionService.listByLabel(label.trim())
    }

    @LlmTool(
        description = "DICE cross-run lessons by code area: decisions, pitfalls, and patterns recorded by ANY " +
            "previous Work ID against this area, plus the Work IDs that touched it. " +
            "Use before modifying code in an area to inherit prior lessons.",
    )
    fun areaLessons(
        @LlmTool.Param(description = "Code area name as recorded in the context index, e.g. 'scripts/'") area: String,
    ): String = safeJson {
        projectionService.lessonsForArea(area)
    }

    /**
     * MCP tool results are consumed by an LLM: surface validation problems as a structured
     * `{"error": …}` payload instead of letting exceptions escape as opaque protocol errors.
     */
    private fun safeJson(block: () -> Any): String =
        runCatching { objectMapper.writeValueAsString(block()) }
            .getOrElse { objectMapper.writeValueAsString(mapOf("error" to (it.message ?: it.javaClass.simpleName))) }
}
