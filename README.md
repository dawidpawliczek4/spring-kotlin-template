# spring-kotlin-template

Production-ready starter template for building REST APIs with Spring Boot and Kotlin. Comes with multi-provider authentication (email/password, Google, Apple), JWT access/refresh tokens, and PostgreSQL — so you can skip the boilerplate and go straight to building features.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 / Java 24 |
| Framework | Spring Boot 4.0 |
| Database | PostgreSQL + Flyway migrations |
| ORM | Spring Data JPA / Hibernate |
| Auth | JWT (jjwt) + BCrypt + SHA-256 hashed refresh tokens |
| Build | Gradle (Kotlin DSL) |
| Testing | Testcontainers + JUnit 5 + MockMvc |

## Project Structure

```
auth/
├── SecurityConfig.kt          # Spring Security filter chain, JWT filter, BCrypt bean
├── JwtIssuer.kt               # Access/refresh token generation & parsing
├── EncryptionService.kt       # AES-GCM encryption (for sensitive data at rest)
├── TokenResponse.kt           # Shared response DTO (accessToken + refreshToken)
├── user/
│   ├── User.kt                # User entity (slim — just id + createdAt)
│   └── UserRepository.kt
├── credentials/
│   ├── Credentials.kt         # Email/password entity (@OneToOne → User)
│   ├── CredentialsRepository.kt
│   ├── CredentialsService.kt  # Register + login logic, password validation
│   ├── CredentialsController.kt
│   └── CredentialsStructures.kt
└── session/
    ├── RefreshToken.kt         # Refresh token entity (SHA-256 hashed)
    ├── RefreshTokenRepository.kt
    ├── SessionService.kt       # Token issuance + refresh rotation (shared by all providers)
    ├── SessionController.kt
    └── RefreshStructures.kt
```

## Auth Architecture

The `User` entity is intentionally minimal — it doesn't store email or any provider-specific data. Instead, each auth provider gets its own entity (`Credentials`, future `GoogleAccount`, `AppleAccount`) linked to User. This makes adding new login methods trivial.

**Session management** is decoupled from auth providers. Any provider authenticates the user, then calls `SessionService.issueTokens(user)` to get a JWT access + refresh token pair. Refresh tokens are rotated on each use and stored as SHA-256 hashes.

```
┌─────────────┐     ┌─────────────────┐     ┌────────────────┐
│  Credentials │────▸│                 │     │                │
│  (email/pwd) │     │                 │     │                │
├─────────────┤     │      User       │◂───▸│ SessionService │──▸ TokenResponse
│ Google (TBD) │────▸│  (id, created)  │     │ (issue/refresh)│
├─────────────┤     │                 │     │                │
│ Apple  (TBD) │────▸│                 │     │                │
└─────────────┘     └─────────────────┘     └────────────────┘
```

## API Endpoints

### Auth — Credentials (email/password)

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| `POST` | `/auth/credentials/register` | ❌ | `{ "email", "password" }` | `TokenResponse` (201) |
| `POST` | `/auth/credentials/login` | ❌ | `{ "email", "password" }` | `TokenResponse` (200) |

### Auth — Session

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| `POST` | `/auth/session/refresh` | ❌ | `{ "refreshToken" }` | `TokenResponse` (200) |

### Password Requirements

- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character

## Getting Started

### Prerequisites

- JDK 24+
- Docker & Docker Compose
- Gradle 9+

### Setup

1. **Create `.env`** from the example and fill in the secrets:

```bash
cp .env.example .env
```

Generate the required keys:

```bash
# JWT signing key
openssl rand -base64 32

# Encryption key
openssl rand -base64 32
```

Paste the generated values into `.env` for `JWT_SECRET` and `APP_ENCRYPTION_KEY`.

2. **Start PostgreSQL:**

```bash
docker compose up -d postgres
```

The database is created automatically by the container (configured in `docker-compose.yml`).

3. **Run the app:**

```bash
./gradlew bootRun
```

Flyway will automatically create the `users`, `credentials`, and `refresh_tokens` tables on first startup.

> **Note:** The app uses [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) to load `.env` automatically — no need to export variables manually. All config in `application.yaml` references env vars with `${VAR:default}` syntax.

## Testing

Integration tests run against a real PostgreSQL database using **Testcontainers** — no mocks, no in-memory DB. Docker must be running.

```bash
./gradlew test
```

### Test Structure

| Class | What it covers |
|---|---|
| `CredentialsControllerTest` | Register (happy path + DB state verification, duplicate email, weak password variants), login (happy path, wrong password, nonexistent email), JWT works on protected endpoint, 403 without token |
| `SessionControllerTest` | Refresh (happy path, token rotation invalidates old token, invalid token), new access token works on protected endpoint |

### How it works

- **Testcontainers** spins up a PostgreSQL container per test class
- **`@ServiceConnection`** auto-configures the datasource — no manual URL/credentials needed
- **Flyway** runs migrations automatically, creating the schema in the test container
- Tests use **MockMvc** to send real HTTP requests through the full Spring Security filter chain
- Each test cleans up the DB in `@BeforeEach` for isolation

## Quick Test

```bash
# Register
curl -s -X POST http://localhost:8080/auth/credentials/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!@"}' | jq

# Login
curl -s -X POST http://localhost:8080/auth/credentials/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!@"}' | jq

# Refresh
curl -s -X POST http://localhost:8080/auth/session/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<token-from-above>"}' | jq

# Access protected resource
curl -s http://localhost:8080/some-endpoint \
  -H "Authorization: Bearer <access-token>"
```

## License

MIT
