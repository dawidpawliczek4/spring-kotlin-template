package com.starter.springkotlintemplate.auth.credentials

import com.starter.springkotlintemplate.auth.TokenResponse
import com.starter.springkotlintemplate.auth.session.SessionService
import com.starter.springkotlintemplate.auth.user.User
import com.starter.springkotlintemplate.auth.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CredentialsService(
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val sessionService: SessionService
) {

    @Transactional
    fun register(request: RegisterRequest): TokenResponse {
        validatePassword(request.password)

        if (credentialsRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }

        val user = userRepository.save(User())

        credentialsRepository.save(
            Credentials(
                email = request.email.lowercase().trim(),
                passwordHash = passwordEncoder.encode(request.password)!!,
                user = user
            )
        )

        return sessionService.issueTokens(user)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val credentials = credentialsRepository.findByEmail(request.email.lowercase().trim())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")

        if (!passwordEncoder.matches(request.password, credentials.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        return sessionService.issueTokens(credentials.user)
    }

    private fun validatePassword(password: String) {
        val errors = mutableListOf<String>()

        if (password.length < 8) {
            errors += "Password must be at least 8 characters long"
        }
        if (!password.any { it.isUpperCase() }) {
            errors += "Password must contain at least one uppercase letter"
        }
        if (!password.any { it.isLowerCase() }) {
            errors += "Password must contain at least one lowercase letter"
        }
        if (!password.any { it.isDigit() }) {
            errors += "Password must contain at least one digit"
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            errors += "Password must contain at least one special character"
        }

        if (errors.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                errors.joinToString("; ")
            )
        }
    }
}
