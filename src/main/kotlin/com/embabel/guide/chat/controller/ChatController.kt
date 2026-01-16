package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.ChatMessage
import com.embabel.guide.chat.service.JesseService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(private val jesseService: JesseService) {

    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    /**
     * Receive a chat message and send it to Jesse for processing.
     * Messages are persisted to the specified thread.
     */
    @MessageMapping("chat.send")
    fun receive(principal: Principal, payload: ChatMessage) {
        logger.info("ChatController received message from webUser {} in thread {}: {}",
            principal.name, payload.threadId, payload.body)
        jesseService.receiveMessage(
            threadId = payload.threadId,
            fromWebUserId = principal.name,
            message = payload.body
        )
    }
}
