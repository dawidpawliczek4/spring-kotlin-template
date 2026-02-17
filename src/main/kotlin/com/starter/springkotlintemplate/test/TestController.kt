package com.starter.springkotlintemplate.test

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test")
class TestController {

    @GetMapping("/me")
    fun me(auth: Authentication): Map<String, Any?> =
        mapOf("userId" to auth.principal)
}
