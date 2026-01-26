package com.embabel.hub

import java.time.Instant

data class StandardErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val code: String? = null,
    val message: String,
    val path: String
)