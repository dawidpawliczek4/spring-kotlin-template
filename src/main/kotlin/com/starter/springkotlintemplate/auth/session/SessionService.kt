package com.starter.springkotlintemplate.auth.session

import com.starter.springkotlintemplate.auth.JwtIssuer
import com.starter.springkotlintemplate.auth.TokenResponse
import com.starter.springkotlintemplate.auth.user.User
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant

@Service
class SessionService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer
) {

    /**
     * Issues a new access + refresh token pair for the given user.
     * Called by any auth provider (credentials, Google, Apple) after successful authentication.
     */
    @Transactional
    fun issueTokens(user: User): TokenResponse {
        val accessToken = jwtIssuer.issueAccessToken(user.id)

        val rawRefreshToken = jwtIssuer.generateRefreshToken()
        val refreshTokenHash = hashToken(rawRefreshToken)

        refreshTokenRepository.save(
            RefreshToken(
                tokenHash = refreshTokenHash,
                expiresAt = jwtIssuer.refreshTokenExpiryDate().toInstant(),
                user = user
            )
        )

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = rawRefreshToken
        )
    }

    /**
     * Validates and rotates a refresh token — deletes the old one, issues a new pair.
     */
    @Transactional
    fun refresh(request: RefreshRequest): TokenResponse {
        val hash = hashToken(request.refreshToken)

        val storedToken = refreshTokenRepository.findByTokenHash(hash)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired")
        }

        // Rotation: delete old token, issue new pair
        refreshTokenRepository.delete(storedToken)

        return issueTokens(storedToken.user)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
