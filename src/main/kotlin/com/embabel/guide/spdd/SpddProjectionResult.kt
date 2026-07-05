package com.embabel.guide.spdd

data class SpddProjectionResult(
    val rootPath: String,
    val workIds: Int,
    val canvases: Int,
    val areas: Int,
    val operations: Int,
    val decisions: Int,
    val pitfalls: Int,
    val relationships: Int,
)
