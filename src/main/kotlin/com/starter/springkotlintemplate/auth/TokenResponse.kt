package com.starter.springkotlintemplate.auth

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)