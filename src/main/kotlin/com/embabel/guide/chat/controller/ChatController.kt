package com.embabel.guide.chat.controller

import com.embabel.guide.chat.model.ChatMessage
import com.embabel.guide.chat.service.JesseService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(private val jesseService: JesseService) {

    /**
     * Receive a chat message and send it to Jesse for processing.
     * Messages are persisted to the specified thread.
     */
    @MessageMapping("chat.send")
    fun receive(principal: Principal, payload: ChatMessage) {
        jesseService.receiveMessage(
            threadId = payload.threadId,
            fromUserId = principal.name,
            message = payload.body
        )
    }
}
