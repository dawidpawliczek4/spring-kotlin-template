package com.starter.springkotlintemplate.auth.credentials

import com.starter.springkotlintemplate.auth.TokenResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth/credentials")
class CredentialsController(
    private val credentialsService: CredentialsService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest): TokenResponse =
        credentialsService.register(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): TokenResponse =
        credentialsService.login(request)
}
