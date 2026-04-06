package com.embabel.guide.chat.model

import com.embabel.hub.integrations.LlmKeyErrorCode

/**
 * Structured error sent to the client via /user/queue/errors
 * when an LLM call fails due to a key/auth issue.
 */
data class LlmKeyError(
    val type: String = "LLM_KEY_ERROR",
    val errorCode: LlmKeyErrorCode,
    val provider: String,
    val message: String,
)