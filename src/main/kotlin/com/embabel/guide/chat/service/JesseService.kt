package com.embabel.guide.chat.service

import com.embabel.guide.chat.model.DeliveredMessage
import com.embabel.guide.chat.model.MessageWithVersion
import com.embabel.guide.chat.model.StatusMessage
import com.embabel.guide.domain.GuideUserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class JesseService(
    private val chatService: ChatService,
    private val presenceService: PresenceService,
    private val ragAdapter: RagServiceAdapter,
    private val threadService: ThreadService,
    private val guideUserService: GuideUserService
) {
    private val logger = LoggerFactory.getLogger(JesseService::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val JESSE_USER_ID = "bot:jesse"
        const val JESSE_SESSION_ID = "jesse-bot-session"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initializeJesse() {
        logger.info("Initializing Jesse bot")
        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
        logger.info("Jesse bot is now online with ID: {}", JESSE_USER_ID)
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    fun maintainPresence() {
        presenceService.touch(JESSE_USER_ID, JESSE_SESSION_ID, "active")
    }

    private fun sendMessageToUser(toUserId: String, message: MessageWithVersion, threadId: String) {
        logger.debug("Jesse sending message to user: {}", toUserId)
        val deliveredMessage = DeliveredMessage.createFrom(message, threadId)
        chatService.sendToUser(toUserId, deliveredMessage)
        chatService.sendStatusToUser(toUserId, status = StatusMessage(fromUserId = JESSE_USER_ID))
    }

    private fun sendStatusToUser(toUserId: String, status: String) {
        logger.debug("Jesse sending status to user {}: {}", toUserId, status)
        val statusMessage = StatusMessage(
            fromUserId = JESSE_USER_ID,
            status = status
        )
        chatService.sendStatusToUser(toUserId, statusMessage)
    }

    /**
     * Receive a message from a user, persist it, get AI response, and send back.
     *
     * @param threadId the thread to add messages to
     * @param fromWebUserId the WebUser ID from the JWT principal
     * @param message the message text
     */
    fun receiveMessage(threadId: String, fromWebUserId: String, message: String) {
        logger.info("Jesse received message from webUser {} in thread {}", fromWebUserId, threadId)

        coroutineScope.launch {
            try {
                // Look up the GuideUser by WebUser ID
                val guideUser = guideUserService.findByWebUserId(fromWebUserId).orElseThrow {
                    IllegalArgumentException("User not found for webUserId: $fromWebUserId")
                }
                val guideUserId = guideUser.core.id

                // Save the user's message to the thread
                threadService.addMessage(
                    threadId = threadId,
                    text = message,
                    role = ThreadService.ROLE_USER,
                    authorId = guideUserId
                )

                // Load existing thread messages for context
                val timeline = threadService.findByThreadId(threadId).orElse(null)
                val priorMessages = timeline?.messages
                    ?.dropLast(1)  // Exclude the message we just added
                    ?.map { PriorMessage(it.message.role, it.current.text) }
                    ?: emptyList()

                // Send status updates to the user while processing
                // Use WebUser ID for WebSocket delivery (that's the principal in the session)
                val response = ragAdapter.sendMessage(
                    threadId = threadId,
                    message = message,
                    fromUserId = guideUserId,
                    priorMessages = priorMessages
                ) { event ->
                    logger.debug("RAG event for user {}: {}", fromWebUserId, event)
                    sendStatusToUser(fromWebUserId, event)
                }

                // Save the assistant's response to the thread
                val assistantMessage = threadService.addMessage(
                    threadId = threadId,
                    text = response,
                    role = ThreadService.ROLE_ASSISTANT,
                    authorId = null  // System-generated response
                )

                // Send the response to the user via WebSocket (use WebUser ID)
                sendMessageToUser(fromWebUserId, assistantMessage, threadId)
            } catch (e: Exception) {
                logger.error("Error processing message from webUser {}: {}", fromWebUserId, e.message, e)
                sendStatusToUser(fromWebUserId, "Error processing your request")

                // Save error message to thread and send to user
                val errorMessage = threadService.addMessage(
                    threadId = threadId,
                    text = "Sorry, I encountered an error while processing your message. Please try again!",
                    role = ThreadService.ROLE_ASSISTANT,
                    authorId = null
                )
                sendMessageToUser(fromWebUserId, errorMessage, threadId)
            }
        }
    }
}
