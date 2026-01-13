package com.embabel.guide.chat.model

/**
 * Incoming chat message from a client.
 */
data class ChatMessage(
    val threadId: String,
    val body: String
)
