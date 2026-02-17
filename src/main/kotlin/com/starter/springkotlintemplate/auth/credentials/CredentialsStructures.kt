package com.starter.springkotlintemplate.auth.credentials

data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)
