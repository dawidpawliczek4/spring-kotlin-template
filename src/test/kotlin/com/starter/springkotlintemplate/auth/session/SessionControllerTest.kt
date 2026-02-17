package com.starter.springkotlintemplate.auth.session

import com.starter.springkotlintemplate.auth.credentials.CredentialsRepository
import com.starter.springkotlintemplate.auth.user.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        @ServiceConnection
        fun postgresContainer(): PostgreSQLContainer =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var credentialsRepository: CredentialsRepository
    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
        credentialsRepository.deleteAll()
        userRepository.deleteAll()
    }


    @Test
    fun `refresh - returns new token pair`() {
        val (_, refreshToken) = registerAndGetTokens()

        mockMvc.perform(
            post("/auth/session/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `refresh - token rotation invalidates old token`() {
        val (_, refreshToken) = registerAndGetTokens()
        assertEquals(1, refreshTokenRepository.count(), "Should have 1 refresh token after register")

        // First refresh - should work
        mockMvc.perform(
            post("/auth/session/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
        ).andExpect(status().isOk)

        // Still 1 token (old deleted, new created)
        assertEquals(1, refreshTokenRepository.count(), "Should still have 1 refresh token after rotation")

        // Old token - should fail
        mockMvc.perform(
            post("/auth/session/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh - invalid token returns 401`() {
        mockMvc.perform(
            post("/auth/session/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson("completely-invalid-token"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh - new access token works on protected endpoint`() {
        val (_, refreshToken) = registerAndGetTokens()

        val refreshResult = mockMvc.perform(
            post("/auth/session/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(refreshToken))
        )
            .andExpect(status().isOk)
            .andReturn()

        val newAccessToken = extractJsonField(refreshResult.response.contentAsString, "accessToken")

        mockMvc.perform(
            get("/test/me")
                .header("Authorization", "Bearer $newAccessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNumber)
    }


    private fun refreshJson(token: String) =
        """{"refreshToken":"$token"}"""

    private fun registerAndGetTokens(): Pair<String, String> {
        val result = mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"session@test.com","password":"Test123!@"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val body = result.response.contentAsString
        return extractJsonField(body, "accessToken") to extractJsonField(body, "refreshToken")
    }

    private fun extractJsonField(json: String, field: String): String {
        val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?: throw AssertionError("Field '$field' not found in: $json")
    }
}
