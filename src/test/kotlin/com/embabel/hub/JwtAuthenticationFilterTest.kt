package com.embabel.hub

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class JwtAuthenticationFilterTest {

    private val jwtTokenService = mock(JwtTokenService::class.java)
    private val filter = JwtAuthenticationFilter(
        jwtTokenService,
        ObjectMapper().registerModule(JavaTimeModule()),
    )

    @Test
    fun `refuses an expired bearer token without continuing the chain`() {
        `when`(jwtTokenService.validateRefreshToken("expired"))
            .thenThrow(ExpiredJwtException(null, null, "expired"))

        val response = dispatch("/api/me", "Bearer expired")

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("TOKEN_EXPIRED"))
    }

    @Test
    fun `refuses an invalid bearer token without continuing the chain`() {
        `when`(jwtTokenService.validateRefreshToken("not-a-jwt"))
            .thenThrow(JwtException("malformed"))

        val response = dispatch("/api/me", "Bearer not-a-jwt")

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("TOKEN_INVALID"))
    }

    @Test
    fun `does not refuse an expired token on the SockJS handshake path`() {
        val request = request("/ws/info", "Bearer expired")
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        verify(chain).doFilter(request, response)
        verifyNoInteractions(jwtTokenService)
    }

    @Test
    fun `still refuses an expired token when the path only shares the ws prefix`() {
        `when`(jwtTokenService.validateRefreshToken("expired"))
            .thenThrow(ExpiredJwtException(null, null, "expired"))

        val response = dispatch("/ws-admin", "Bearer expired")

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("TOKEN_EXPIRED"))
    }

    private fun dispatch(uri: String, authorization: String): MockHttpServletResponse {
        val request = request(uri, authorization)
        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)
        filter.doFilter(request, response, chain)
        verifyNoInteractions(chain)
        return response
    }

    private fun request(uri: String, authorization: String): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.requestURI = uri
        request.addHeader("Authorization", authorization)
        return request
    }
}
