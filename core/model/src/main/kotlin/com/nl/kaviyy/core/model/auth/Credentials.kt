package com.nl.kaviyy.core.model.auth

/** Secrets for one server. `toString` never prints secret values (they end up in logs and crash reports). */
sealed interface Credentials {
    /** Username/password login result. The password itself is never stored. */
    data class Jwt(
        val username: String,
        val token: String,
        val refreshToken: String,
    ) : Credentials {
        override fun toString() = "Jwt(username=$username, token=<redacted>, refreshToken=<redacted>)"
    }

    /** Kavita Auth Key (`x-api-key`), used by SSO users. */
    data class AuthKey(
        val apiKey: String,
    ) : Credentials {
        override fun toString() = "AuthKey(apiKey=<redacted>)"
    }
}
