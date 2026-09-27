package com.embabel.guide.chat.security

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import java.security.Principal

class RequirePrincipalInterceptorTest {

    private val interceptor = RequirePrincipalInterceptor()
    private val channel = MessageChannel { _: Message<*>, _: Long -> false }

    @Test
    fun `drops a SEND that has no principal`() {
        val message = stomp(StompCommand.SEND, user = null)

        assertNull(interceptor.preSend(message, channel))
    }

    @Test
    fun `keeps a SEND that carries a principal`() {
        val message = stomp(StompCommand.SEND, user = Principal { "user-1" })

        assertSame(message, interceptor.preSend(message, channel))
    }

    @Test
    fun `keeps a non-SEND frame that has no principal`() {
        val message = stomp(StompCommand.CONNECT, user = null)

        assertSame(message, interceptor.preSend(message, channel))
    }

    private fun stomp(command: StompCommand, user: Principal?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        accessor.destination = "/app/chat"
        accessor.sessionId = "session-1"
        accessor.user = user
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }
}
