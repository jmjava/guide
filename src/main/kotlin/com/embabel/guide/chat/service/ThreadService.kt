package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.ThreadTimeline
import com.embabel.guide.chat.repository.ThreadRepository
import com.embabel.guide.util.UUIDv7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class ThreadService(
    private val threadRepository: ThreadRepository,
    private val ragAdapter: RagServiceAdapter
) {

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        const val DEFAULT_WELCOME_MESSAGE = "Welcome! How can I help you today?"
        const val WELCOME_PROMPT_TEMPLATE = "User %s has created a new account. Could you please greet and welcome them"
    }

    /**
     * Find a thread by its ID.
     */
    fun findByThreadId(threadId: String): Optional<ThreadTimeline> {
        return threadRepository.findByThreadId(threadId)
    }

    /**
     * Find all threads owned by a user.
     */
    fun findByOwnerId(ownerId: String): List<ThreadTimeline> {
        return threadRepository.findByOwnerId(ownerId)
    }

    /**
     * Create a new thread with an initial message.
     *
     * @param ownerId the user who owns the thread
     * @param title optional thread title
     * @param message the initial message text
     * @param role the message role
     * @param authorId optional author of the message (null for system messages)
     */
    fun createThread(
        ownerId: String,
        title: String? = null,
        message: String,
        role: String,
        authorId: String? = null
    ): ThreadTimeline {
        val threadId = UUIDv7.generateString()
        return threadRepository.createWithMessage(
            threadId = threadId,
            ownerId = ownerId,
            title = title,
            message = message,
            role = role,
            authorId = authorId
        )
    }

    /**
     * Create a welcome thread for a new user with an AI-generated greeting.
     *
     * Sends a prompt to the AI asking it to greet and welcome the user.
     * The prompt itself is NOT stored in the thread - only the AI's response.
     * The thread is owned by the user, but the welcome message has no author (system-generated).
     *
     * @param ownerId the user who owns the thread
     * @param displayName the user's display name for the personalized greeting
     */
    suspend fun createWelcomeThread(
        ownerId: String,
        displayName: String
    ): ThreadTimeline = withContext(Dispatchers.IO) {
        val prompt = WELCOME_PROMPT_TEMPLATE.format(displayName)
        val welcomeMessage = ragAdapter.sendMessage(
            message = prompt,
            fromUserId = ownerId,  // Use the new user's ID for the chat session
            onEvent = { } // No status updates needed for welcome message
        )

        createThread(
            ownerId = ownerId,
            title = "Welcome",
            message = welcomeMessage,
            role = ROLE_ASSISTANT,
            authorId = null  // System message - no author
        )
    }

    /**
     * Create a welcome thread with a static message (for testing or fallback).
     */
    fun createWelcomeThreadWithMessage(
        ownerId: String,
        welcomeMessage: String = DEFAULT_WELCOME_MESSAGE
    ): ThreadTimeline {
        return createThread(
            ownerId = ownerId,
            title = "Welcome",
            message = welcomeMessage,
            role = ROLE_ASSISTANT,
            authorId = null
        )
    }
}