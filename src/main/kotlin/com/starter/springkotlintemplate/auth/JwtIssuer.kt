package com.starter.springkotlintemplate.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtIssuer(
    @Value("\${jwt.issuer}") private val issuer: String,
    @Value("\${jwt.access-ttl-seconds}") private val accessTtl: Long,
    @Value("\${jwt.refresh-ttl-seconds}") private val refreshTtl: Long,
    @Value("\${jwt.secret}") secretB64: String
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretB64))

    fun issueAccessToken(userId: Long): String =
        Jwts.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessTtl * 1000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    fun parse(token: String) =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)

    fun refreshTokenExpiryDate(): Date =
        Date(System.currentTimeMillis() + refreshTtl * 1000)

    // todo: uuid may not be strong enough
    fun generateRefreshToken(): String =
        UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")
}
