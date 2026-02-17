package com.starter.springkotlintemplate.auth.credentials

import com.starter.springkotlintemplate.auth.session.RefreshTokenRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CredentialsControllerTest {

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
    fun `register - creates user, credentials, and returns tokens`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)

        // Verify DB state
        assertEquals(1, userRepository.count())
        assertEquals(1, credentialsRepository.count())
        assertEquals(1, refreshTokenRepository.count())

        val credentials = credentialsRepository.findByEmail("user@test.com")
        assertNotNull(credentials)
        assertEquals("user@test.com", credentials.email)
        assertTrue(credentials.passwordHash.startsWith("\$2a\$") || credentials.passwordHash.startsWith("\$2b\$"))
    }

    @Test
    fun `register - duplicate email returns 409`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        ).andExpect(status().isConflict)
    }

    @Test
    fun `register - weak password returns 400`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(password = "weak"))
        ).andExpect(status().isBadRequest)

        assertEquals(0, userRepository.count(), "No user should be created for invalid password")
    }

    @Test
    fun `register - password without uppercase returns 400`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(password = "test123!@"))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `register - password without special char returns 400`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(password = "Test1234"))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `login - valid credentials return tokens`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/credentials/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `login - wrong password returns 401`() {
        mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/auth/credentials/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(password = "WrongPass1!"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `login - nonexistent email returns 401`() {
        mockMvc.perform(
            post("/auth/credentials/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email = "nobody@test.com"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `access token works on protected endpoint`() {
        val registerResult = mockMvc.perform(
            post("/auth/credentials/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson())
        )
            .andExpect(status().isCreated)
            .andReturn()

        val accessToken = extractJsonField(registerResult.response.contentAsString, "accessToken")

        mockMvc.perform(
            get("/test/me")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isNumber)
    }

    @Test
    fun `protected endpoint without token returns 401`() {
        mockMvc.perform(get("/test/me"))
            .andExpect(status().isForbidden)
    }


    private fun registerJson(email: String = "user@test.com", password: String = "Test123!@") =
        """{"email":"$email","password":"$password"}"""

    private fun loginJson(email: String = "user@test.com", password: String = "Test123!@") =
        """{"email":"$email","password":"$password"}"""

    private fun extractJsonField(json: String, field: String): String {
        val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?: throw AssertionError("Field '$field' not found in: $json")
    }
}
