package com.starter.springkotlintemplate.auth.session

import com.starter.springkotlintemplate.auth.TokenResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth/session")
class SessionController(
    private val sessionService: SessionService
) {

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): TokenResponse =
        sessionService.refresh(request)
}
