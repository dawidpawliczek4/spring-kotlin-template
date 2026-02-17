package com.starter.springkotlintemplate.auth.session

import com.starter.springkotlintemplate.auth.user.User
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun deleteAllByUser(user: User)
}
