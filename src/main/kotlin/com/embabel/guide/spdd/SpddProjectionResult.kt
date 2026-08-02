package com.embabel.guide.spdd

data class SpddProjectionResult(
    val rootPath: String,
    val workIds: Int,
    val canvases: Int,
    val areas: Int,
    val operations: Int,
    val decisions: Int,
    val pitfalls: Int,
    val patterns: Int = 0,
    val relationships: Int,
    /** Source files that failed to parse/persist and were skipped (load continues past them). */
    val skippedFiles: Int = 0,
)

/** Read-side summary for domain retrieval (leg 3). */
data class SpddEntitySummary(
    val id: String,
    val name: String,
    val description: String,
    val labels: List<String>,
    val uri: String?,
)

/**
 * WorkId-centered subgraph returned by the projection read API.
 * Neighbors are included via typed edges (`canvas`, `area`, `decision`, `pitfall`, `pattern`),
 * not embedding similarity.
 */
data class SpddWorkIdSubgraph(
    val workId: String,
    val found: Boolean,
    val work: SpddEntitySummary? = null,
    val canvases: List<SpddEntitySummary> = emptyList(),
    val areas: List<SpddEntitySummary> = emptyList(),
    val decisions: List<SpddEntitySummary> = emptyList(),
    val pitfalls: List<SpddEntitySummary> = emptyList(),
    val patterns: List<SpddEntitySummary> = emptyList(),
)

/**
 * Area-centered cross-run lessons: what any prior Work ID recorded against a code area.
 * Lessons arrive via incoming `about` edges; Work IDs via incoming `area` edges.
 */
data class SpddAreaLessons(
    val area: String,
    val found: Boolean,
    val areaEntity: SpddEntitySummary? = null,
    val workIds: List<SpddEntitySummary> = emptyList(),
    val decisions: List<SpddEntitySummary> = emptyList(),
    val pitfalls: List<SpddEntitySummary> = emptyList(),
    val patterns: List<SpddEntitySummary> = emptyList(),
)
