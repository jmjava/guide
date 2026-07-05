package com.embabel.guide.spdd

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/data/spdd-projection")
@ConditionalOnProperty(prefix = "guide.spdd-projection", name = ["enabled"], havingValue = "true")
class SpddProjectionController(
    private val projectionService: SpddMarkdownProjectionService,
) {

    @PostMapping("/load")
    fun load(@RequestBody(required = false) body: LoadRequest?): ResponseEntity<SpddProjectionResult> =
        ResponseEntity.ok(projectionService.load(body?.rootPath))

    @GetMapping("/stats")
    fun stats(): ResponseEntity<StatsResponse> =
        ResponseEntity.ok(
            StatsResponse(
                workIdCount = projectionService.entityCountByLabel("WorkId"),
                canvasCount = projectionService.entityCountByLabel("Canvas"),
                areaCount = projectionService.entityCountByLabel("Area"),
                entityLabel = com.embabel.agent.rag.model.NamedEntityData.ENTITY_LABEL,
            ),
        )

    data class LoadRequest(val rootPath: String? = null)

    data class StatsResponse(
        val workIdCount: Int,
        val canvasCount: Int,
        val areaCount: Int,
        val entityLabel: String,
    )
}
