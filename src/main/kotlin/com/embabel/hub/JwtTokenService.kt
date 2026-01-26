package com.embabel.hub

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.SecretKey

/**
 * Service for generating and validating JWT tokens.
 */
@Service
class JwtTokenService(
    @Value("\${jwt.secret:defaultSecretKeyThatShouldBeChangedInProductionAndMustBeAtLeast256BitsLong}")
    private val jwtSecret: String,

    @Value("\${jwt.refresh-token-expiry-days:30}")
    private val refreshTokenExpiryDays: Long
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    /**
     * Token expiration in seconds (for use when generating expiresAt timestamps).
     */
    val tokenExpirationSeconds: Long
        get() = refreshTokenExpiryDays * 24 * 60 * 60

    /**
     * Generates a refresh token for the given user ID.
     *
     * @param userId The user ID to create the token for
     * @return A JWT refresh token valid for the configured expiry period
     */
    fun generateRefreshToken(userId: String): String {
        val now = Instant.now()
        val expiry = now.plus(refreshTokenExpiryDays, ChronoUnit.DAYS)

        return Jwts.builder()
            .subject(userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact()
    }

    /**
     * Validates a refresh token and extracts the user ID.
     *
     * @param token The JWT token to validate
     * @return The user ID if the token is valid
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    fun validateRefreshToken(token: String): String {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)

        return claims.payload.subject
    }

    /**
     * Parses a token and returns its claims, even if the token is expired.
     * This is useful for token refresh where we want to verify the signature
     * but allow expired tokens.
     *
     * @param token The JWT token to parse
     * @return The claims from the token
     * @throws io.jsonwebtoken.security.SignatureException if the signature is invalid
     * @throws io.jsonwebtoken.MalformedJwtException if the token is malformed
     */
    fun parseTokenIgnoringExpiration(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: ExpiredJwtException) {
            // Token is expired but signature was valid - return the claims
            ex.claims
        }
    }
}