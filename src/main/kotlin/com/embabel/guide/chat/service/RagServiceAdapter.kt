package com.embabel.guide.chat.service

/**
 * Interface for integrating with RAG (Retrieval-Augmented Generation) systems.
 *
 * This adapter provides a non-blocking way to send messages to a RAG system
 * and receive responses, with support for real-time status events during processing.
 */
interface RagServiceAdapter {

    companion object {
        const val TITLE_PROMPT = "Generate a short title (max 6 words) for this message. " +
            "Reply with ONLY the title, no quotes or punctuation: "
    }

    /**
     * Sends a message to the RAG system and returns the response.
     *
     * This is a suspending function to avoid blocking threads during potentially
     * long-running RAG operations (document retrieval, LLM inference, etc.).
     *
     * @param message The user's message to process
     * @param fromUserId The ID of the user sending the message (for context/logging)
     * @param onEvent Callback function to receive real-time status updates during processing
     *                (e.g., "Planning response", "Querying database", "Generating answer")
     * @return The RAG system's response message
     */
    suspend fun sendMessage(
        message: String,
        fromUserId: String,
        onEvent: (String) -> Unit = {}
    ): String

    /**
     * Generates a short title from message content.
     *
     * @param content The message content to generate a title from
     * @param fromUserId The ID of the user (for session context)
     * @return A short title (typically 3-6 words)
     */
    suspend fun generateTitle(content: String, fromUserId: String): String {
        val response = sendMessage(TITLE_PROMPT + content, fromUserId)
        return response.trim().take(100)  // Safety limit
    }
}