package com.starter.springkotlintemplate.auth.credentials

import com.starter.springkotlintemplate.auth.user.User
import jakarta.persistence.*

@Entity
@Table(name = "credentials")
class Credentials(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User
)
